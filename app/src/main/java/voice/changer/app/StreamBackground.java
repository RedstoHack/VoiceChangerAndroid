package voice.changer.app;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import voice.changer.app.dsp.BackgroundSource;
import voice.changer.app.dsp.Dsp;
import voice.changer.app.dsp.FloatSource;
import voice.changer.app.dsp.PcmStream;
import voice.changer.app.dsp.Resampler;
import voice.changer.app.dsp.WavSource;

/**
 * Фон из файла любой длины: декодируется потоком в отдельном потоке и зацикливается,
 * в памяти держится только небольшой буфер (поэтому можно хоть часовой файл).
 */
final class StreamBackground implements BackgroundSource, Closeable {
    private static final int CAP = 1 << 16;   // кадров в кольцевом буфере

    private final float[] ring = new float[CAP * 2];
    private int rd, count;
    private final Object lock = new Object();
    private volatile boolean closed, failed;
    private final boolean blocking;
    private final File file;

    /** blocking = true для офлайн-рендера (ждём данные), false для живого режима (при нехватке — тишина). */
    StreamBackground(File file, boolean blocking) {
        this.file = file;
        this.blocking = blocking;
        Thread t = new Thread(this::run, "bg-stream");
        t.setDaemon(true);
        t.start();
    }

    private void run() {
        float[] tmp = new float[4096 * 2];
        try {
            while (!closed) {
                PcmStream st = WavSource.open(file, 2);
                if (st == null) st = new AudioDecoder(file.getPath(), 2);
                boolean any = false;
                try {
                    FloatSource src = st.rate() == Dsp.SR ? st : new Resampler(st, 2, st.rate() / (double) Dsp.SR);
                    int got;
                    while (!closed && (got = src.read(tmp, 0, 4096)) > 0) {
                        any = true;
                        put(tmp, got);
                    }
                } finally {
                    st.close();
                }
                if (!any) { failed = true; break; }
            }
        } catch (IOException | RuntimeException | InterruptedException e) {
            if (!closed) { failed = true; AppLog.err("background stream", e); }
        } finally {
            synchronized (lock) { lock.notifyAll(); }
        }
    }

    private void put(float[] a, int n) throws InterruptedException {
        int i = 0;
        synchronized (lock) {
            while (i < n && !closed) {
                while (count >= CAP && !closed) lock.wait(50);
                if (closed) return;
                int w = (rd + count) % CAP;
                ring[2 * w] = a[2 * i];
                ring[2 * w + 1] = a[2 * i + 1];
                count++;
                i++;
            }
            lock.notifyAll();
        }
    }

    @Override public void mix(float[] st, int n, float gain) {
        synchronized (lock) {
            int waited = 0;
            for (int i = 0; i < n; ) {
                if (count == 0) {
                    if (!blocking || failed || closed || waited > 50) break;
                    try { lock.wait(100); } catch (InterruptedException e) { break; }
                    waited++;
                    continue;
                }
                waited = 0;
                st[2 * i] += gain * ring[2 * rd];
                st[2 * i + 1] += gain * ring[2 * rd + 1];
                rd = (rd + 1) % CAP;
                count--;
                i++;
            }
            lock.notifyAll();
        }
    }

    @Override public void close() {
        closed = true;
        synchronized (lock) { lock.notifyAll(); }
    }
}
