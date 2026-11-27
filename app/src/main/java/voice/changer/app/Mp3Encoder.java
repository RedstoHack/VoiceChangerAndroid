package voice.changer.app;

import java.io.Closeable;
import java.io.IOException;

/**
 * MP3 через LAME. Библиотека libvcmp3.so собирается скриптом tools/build-lame.sh (в GitHub Actions)
 * и кладётся в jniLibs. Если её нет, MP3 просто недоступен ({@link #available()} == false).
 */
final class Mp3Encoder implements Closeable {
    private static final boolean LOADED;
    static {
        boolean ok;
        try { System.loadLibrary("vcmp3"); ok = true; } catch (Throwable t) { ok = false; }
        LOADED = ok;
    }

    static boolean available() { return LOADED; }

    private static native long nOpen(int rate, int ch, int kbps);
    private static native int nEncode(long h, short[] pcm, int frames, byte[] out);
    private static native int nFlush(long h, byte[] out);
    private static native void nClose(long h);

    private long h;
    private final int ch;

    Mp3Encoder(int rate, int ch, int kbps) throws IOException {
        if (!LOADED) throw new IOException("MP3 encoder library is not installed");
        this.ch = ch;
        h = nOpen(rate, ch, kbps);
        if (h == 0) throw new IOException("MP3 encoder init failed");
    }

    /** pcm: моно — сэмплы подряд, стерео — interleaved. Возвращает закодированные байты. */
    byte[] encode(short[] pcm, int frames) throws IOException {
        byte[] out = new byte[frames * 5 / 4 + 7200];
        int n = nEncode(h, pcm, frames, out);
        if (n < 0) throw new IOException("MP3 encode error " + n);
        return java.util.Arrays.copyOf(out, n);
    }

    byte[] flush() throws IOException {
        byte[] out = new byte[7200];
        int n = nFlush(h, out);
        if (n < 0) throw new IOException("MP3 flush error " + n);
        return java.util.Arrays.copyOf(out, n);
    }

    @Override public void close() {
        if (h != 0) { nClose(h); h = 0; }
    }
}
