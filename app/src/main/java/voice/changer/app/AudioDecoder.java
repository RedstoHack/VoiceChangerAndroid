package voice.changer.app;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import voice.changer.app.dsp.PcmStream;

/** Декодер любого аудио/видео, которое понимает система (MediaExtractor + MediaCodec, без сервисов Google). */
final class AudioDecoder implements PcmStream {
    private final MediaExtractor ex = new MediaExtractor();
    private MediaCodec codec;
    private final int outCh;
    private int rate, inCh, enc = AudioFormat.ENCODING_PCM_16BIT;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private boolean inEos, outEos;
    private float[] pend = new float[0];
    private int pendPos, pendLen;

    AudioDecoder(String path, int outCh) throws IOException {
        this.outCh = outCh;
        try {
            ex.setDataSource(path);
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String m = f.getString(MediaFormat.KEY_MIME);
                if (m != null && m.startsWith("audio/")) { track = i; fmt = f; break; }
            }
            if (track < 0) throw new IOException("no audio track");
            ex.selectTrack(track);
            rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            inCh = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();
            fill();   // «прогрев»: после первого буфера частота и каналы точно известны
        } catch (IOException | RuntimeException e) {
            close();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }

    @Override public int rate() { return rate; }

    private boolean fill() {
        while (pendPos >= pendLen) {
            if (outEos) return false;
            if (!inEos) {
                int ii = codec.dequeueInputBuffer(0);
                if (ii >= 0) {
                    ByteBuffer ib = codec.getInputBuffer(ii);
                    int n = ex.readSampleData(ib, 0);
                    if (n < 0) {
                        codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inEos = true;
                    } else {
                        codec.queueInputBuffer(ii, 0, n, ex.getSampleTime(), 0);
                        ex.advance();
                    }
                }
            }
            int oi = codec.dequeueOutputBuffer(info, 10000);
            if (oi >= 0) {
                if (info.size > 0) {
                    ByteBuffer ob = codec.getOutputBuffer(oi);
                    ob.position(info.offset);
                    ob.limit(info.offset + info.size);
                    convert(ob);
                }
                codec.releaseOutputBuffer(oi, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outEos = true;
            } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat f = codec.getOutputFormat();
                if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) inCh = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) enc = f.getInteger(MediaFormat.KEY_PCM_ENCODING);
            }
        }
        return true;
    }

    private void convert(ByteBuffer ob) {
        ob.order(ByteOrder.LITTLE_ENDIAN);
        boolean flt = enc == AudioFormat.ENCODING_PCM_FLOAT;
        FloatBuffer fb = flt ? ob.asFloatBuffer() : null;
        ShortBuffer sb = flt ? null : ob.asShortBuffer();
        int frames = (flt ? fb.remaining() : sb.remaining()) / inCh;
        if (pend.length < frames * outCh) pend = new float[frames * outCh];
        for (int i = 0; i < frames; i++) {
            float l = 0, r = 0, sum = 0;
            for (int c = 0; c < inCh; c++) {
                float v = flt ? fb.get(i * inCh + c) : sb.get(i * inCh + c) / 32768f;
                if (c == 0) l = v;
                if (c == 1) r = v;
                sum += v;
            }
            if (outCh == 1) pend[i] = sum / inCh;
            else { pend[2 * i] = l; pend[2 * i + 1] = inCh == 1 ? l : r; }
        }
        pendPos = 0;
        pendLen = frames * outCh;
    }

    @Override public int read(float[] out, int off, int frames) throws IOException {
        try {
            if (!fill()) return -1;
        } catch (IllegalStateException e) {   // MediaCodec.CodecException — его подкласс
            throw new IOException(e);
        }
        int n = Math.min(frames, (pendLen - pendPos) / outCh);
        System.arraycopy(pend, pendPos, out, off * outCh, n * outCh);
        pendPos += n * outCh;
        return n;
    }

    @Override public void close() {
        try { if (codec != null) { codec.stop(); codec.release(); } } catch (Exception ignored) { }
        codec = null;
        try { ex.release(); } catch (Exception ignored) { }
    }
}
