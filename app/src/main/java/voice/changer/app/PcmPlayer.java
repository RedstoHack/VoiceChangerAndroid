package voice.changer.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import voice.changer.app.dsp.Dsp;

/** Воспроизведение результата из памяти с паузой и перемоткой — начинается мгновенно, без кодирования. */
final class PcmPlayer {
    private final short[] st;
    private final int frames;
    private volatile int pos;
    private volatile int seekTo = -1;
    private volatile boolean playing, closed, ended;
    private final Object lock = new Object();
    private final Thread thread;

    PcmPlayer(Rendered r) {
        st = r.st;
        frames = r.frames;
        thread = new Thread(this::run, "pcm-player");
        thread.start();
    }

    int durationFrames() { return frames; }
    int positionFrames() { return pos; }
    boolean isPlaying() { return playing; }
    boolean hasEnded() { return ended; }

    void play() {
        synchronized (lock) {
            if (pos >= frames) seekTo = 0;
            ended = false;
            playing = true;
            lock.notifyAll();
        }
    }

    void pause() {
        synchronized (lock) { playing = false; lock.notifyAll(); }
    }

    void seek(int frame) {
        synchronized (lock) {
            seekTo = Math.max(0, Math.min(frames, frame));
            pos = seekTo;
            lock.notifyAll();
        }
    }

    void release() {
        synchronized (lock) { closed = true; playing = false; lock.notifyAll(); }
    }

    private void run() {
        AudioTrack trk = null;
        try {
            int min = AudioTrack.getMinBufferSize(Dsp.SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            trk = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(Dsp.SR).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setBufferSizeInBytes(Math.max(min, 16384))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            boolean started = false;
            while (true) {
                synchronized (lock) {
                    while (!closed && !playing && seekTo < 0) lock.wait();
                    if (closed) break;
                }
                if (seekTo >= 0) {
                    int s = seekTo;
                    seekTo = -1;
                    trk.pause();
                    trk.flush();
                    pos = s;
                    started = false;
                }
                if (!playing) { trk.pause(); started = false; continue; }
                if (!started) { trk.play(); started = true; }
                if (pos >= frames) {
                    // дожидаемся, пока доиграет буфер, и останавливаемся
                    trk.stop();
                    started = false;
                    playing = false;
                    ended = true;
                    pos = frames;
                    continue;
                }
                int n = Math.min(4096, frames - pos);
                int w = trk.write(st, pos * 2, n * 2);
                if (w < 0) break;
                if (seekTo < 0) pos += w / 2;
            }
        } catch (InterruptedException | RuntimeException e) {
            AppLog.err("player", e);
        } finally {
            playing = false;
            if (trk != null) {
                try { trk.stop(); } catch (Exception ignored) { }
                trk.release();
            }
        }
    }
}
