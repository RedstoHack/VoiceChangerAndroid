package voice.changer.app.dsp;

import java.io.IOException;
import java.util.List;

/** Декодированное аудио 16 бит, {@link Dsp#SR} Гц. Массив всегда с запасом нулей в конце (нужен WSOLA). */
public final class Pcm {
    public static final int PAD = 2 * Wsola.N;

    public final short[] data;   // interleaved
    public final int frames;
    public final int channels;

    public Pcm(short[] data, int frames, int channels) {
        this.data = data; this.frames = frames; this.channels = channels;
    }

    public float seconds() { return frames / (float) Dsp.SR; }

    public static final class TooLongException extends IOException {
        public TooLongException() { super("audio too long"); }
    }

    /** Читает поток целиком, приводя к {@link Dsp#SR}. {@code ch} — число каналов потока (1 или 2). */
    public static Pcm fromStream(PcmStream in, int ch, int maxFrames) throws IOException {
        FloatSource src = in;
        if (in.rate() != Dsp.SR) src = new Resampler(in, ch, in.rate() / (double) Dsp.SR);
        short[] buf = new short[Math.min(maxFrames, Dsp.SR * 30) * ch + PAD * ch];
        int frames = 0;
        float[] tmp = new float[4096 * ch];
        while (true) {
            int n = src.read(tmp, 0, 4096);
            if (n < 0) break;
            if (frames + n > maxFrames) throw new TooLongException();
            if ((frames + n) * ch + PAD * ch > buf.length) {
                int cap = (int) Math.min((long) maxFrames * ch + PAD * ch, (long) (buf.length * 1.5));
                if (cap < (frames + n) * ch + PAD * ch) cap = (frames + n) * ch + PAD * ch;
                buf = java.util.Arrays.copyOf(buf, cap);
            }
            for (int i = 0; i < n * ch; i++) {
                float v = tmp[i];
                if (v > 1f) v = 1f; else if (v < -1f) v = -1f;
                buf[frames * ch + i] = (short) Math.round(v * 32767f);
            }
            frames += n;
        }
        return new Pcm(buf, frames, ch);
    }

    /** Склеивает моно-куски через паузу {@code gapFrames}. */
    public static Pcm concatMono(List<Pcm> parts, int gapFrames) {
        long total = 0;
        for (Pcm p : parts) total += p.frames + gapFrames;
        short[] out = new short[(int) total + PAD];
        int pos = 0;
        for (Pcm p : parts) {
            System.arraycopy(p.data, 0, out, pos, p.frames);
            pos += p.frames + gapFrames;
        }
        return new Pcm(out, (int) total, 1);
    }
}
