package voice.changer.app;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Запись сжатого аудио средствами системы: AAC → .m4a, Opus → .ogg (MediaCodec + MediaMuxer). */
final class CodecWriter implements Closeable {
    private final MediaCodec codec;
    private final MediaMuxer muxer;
    private final int rate, ch;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private int track = -1;
    private boolean started, closed;
    private long frames;

    CodecWriter(File f, String mime, int muxerFormat, int rate, int ch, int bitrate) throws IOException {
        this.rate = rate; this.ch = ch;
        MediaFormat fmt = MediaFormat.createAudioFormat(mime, rate, ch);
        if (MediaFormat.MIMETYPE_AUDIO_AAC.equals(mime))
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 << 16);
        MediaCodec c = MediaCodec.createEncoderByType(mime);
        try {
            c.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            c.start();
            muxer = new MediaMuxer(f.getPath(), muxerFormat);
        } catch (RuntimeException | IOException e) {
            c.release();
            throw e;
        }
        codec = c;
    }

    /** pcm — interleaved short, count — число short. */
    void write(short[] pcm, int offset, int count) throws IOException {
        int pos = 0;
        while (pos < count) {
            int ii = codec.dequeueInputBuffer(10000);
            if (ii >= 0) {
                ByteBuffer ib = codec.getInputBuffer(ii);
                ib.clear();
                int c = Math.min(count - pos, ib.capacity() / 2);
                c -= c % ch;
                ib.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, offset + pos, c);
                codec.queueInputBuffer(ii, 0, c * 2, frames * 1_000_000L / rate, 0);
                pos += c;
                frames += c / ch;
            }
            drain(false);
        }
    }

    private void drain(boolean eos) throws IOException {
        int idle = 0;
        while (true) {
            int oi = codec.dequeueOutputBuffer(info, eos ? 10000 : 0);
            if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!eos || ++idle > 500) return;
            } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.getOutputFormat());
                muxer.start();
                started = true;
            } else if (oi >= 0) {
                idle = 0;
                ByteBuffer ob = codec.getOutputBuffer(oi);
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0;
                if (info.size > 0 && started) {
                    ob.position(info.offset);
                    ob.limit(info.offset + info.size);
                    muxer.writeSampleData(track, ob, info);
                }
                codec.releaseOutputBuffer(oi, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
            }
        }
    }

    @Override public void close() throws IOException {
        if (closed) return;
        closed = true;
        try {
            int ii, tries = 0;
            do { ii = codec.dequeueInputBuffer(10000); } while (ii < 0 && ++tries < 500);
            if (ii >= 0) codec.queueInputBuffer(ii, 0, 0, frames * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            drain(true);
            if (started) muxer.stop();
        } catch (RuntimeException e) {
            throw new IOException(e);
        } finally {
            release();
        }
    }

    void abort() {
        if (closed) return;
        closed = true;
        release();
    }

    private void release() {
        try { codec.stop(); } catch (Exception ignored) { }
        try { codec.release(); } catch (Exception ignored) { }
        try { muxer.release(); } catch (Exception ignored) { }
    }
}
