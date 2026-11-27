package voice.changer.app.dsp;

import java.io.IOException;
import java.util.Arrays;

/**
 * Офлайн-обработка: высота и скорость (WSOLA + ресэмплинг) → цепочка эффектов → фон → ограничитель.
 * Результат — стерео float порциями в {@link Sink}.
 */
public final class OfflineRenderer {
    private OfflineRenderer() {}

    public interface Sink { void write(float[] stereo, int frames) throws IOException; }
    /** Возвращает false, если нужно отменить. */
    public interface Progress { boolean update(float fraction); }

    public static final class Cancelled extends IOException {
        public Cancelled() { super("cancelled"); }
    }

    private static final class ShortSource implements FloatSource {
        private final short[] d; private final int len; private int pos;
        ShortSource(short[] d, int len) { this.d = d; this.len = len; }
        @Override public int read(float[] out, int off, int n) {
            if (pos >= len) return -1;
            int c = Math.min(n, len - pos);
            for (int i = 0; i < c; i++) out[off + i] = d[pos + i] * (1f / 32768f);
            pos += c;
            return c;
        }
    }

    /** Копия записи, проигранная задом наперёд. */
    public static Pcm reversed(Pcm p) {
        short[] d = new short[p.frames + Pcm.PAD];
        for (int i = 0; i < p.frames; i++) d[i] = p.data[p.frames - 1 - i];
        return new Pcm(d, p.frames, 1);
    }

    public static void render(Pcm voice, VoiceSettings s, LayerMixer bg, Sink sink, Progress prog) throws IOException {
        if (voice.channels != 1) throw new IllegalArgumentException("mono voice expected");
        if (s.reverse) voice = reversed(voice);
        float r = s.ratio(), t = s.speed;
        double f = r / t;
        FloatSource src = new ShortSource(voice.data, voice.frames);
        if (Math.abs(f - 1.0) > 1e-3) src = new Wsola(voice.data, voice.frames, f);
        if (Math.abs(r - 1f) > 1e-3) src = new Resampler(src, 1, r);

        EffectChain chain = new EffectChain();
        chain.apply(s);
        float[] blk = new float[4096], st = new float[8192];
        long expected = (long) (voice.frames / t) + chain.tailFrames() + 1, total = 0;
        int tailLeft = chain.tailFrames();
        boolean voiceDone = false;

        while (true) {
            int n = 0;
            if (!voiceDone) {
                n = src.read(blk, 0, 4096);
                if (n < 0) { voiceDone = true; n = 0; }
            }
            if (voiceDone) {
                if (tailLeft <= 0) break;
                n = Math.min(4096, tailLeft);
                Arrays.fill(blk, 0, n, 0f);
                tailLeft -= n;
            }
            chain.process(blk, 0, n);
            for (int i = 0; i < n; i++) { float v = blk[i] * s.volume; st[2 * i] = v; st[2 * i + 1] = v; }
            if (bg != null && !bg.isEmpty()) bg.mix(st, n);
            for (int i = 0; i < 2 * n; i++) st[i] = Dsp.softClip(st[i]);
            sink.write(st, n);
            total += n;
            if (prog != null && !prog.update(Math.min(1f, total / (float) expected))) throw new Cancelled();
        }
    }
}
