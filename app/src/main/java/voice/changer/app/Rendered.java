package voice.changer.app;

import java.util.Arrays;

import voice.changer.app.dsp.Dsp;
import voice.changer.app.dsp.OfflineRenderer;

/** Готовый результат в памяти (стерео 16 бит, 44.1 кГц): сразу слушается, кодируется только при «Поделиться»/«Сохранить». */
final class Rendered {
    final short[] st;
    final int frames;

    Rendered(short[] st, int frames) { this.st = st; this.frames = frames; }

    float seconds() { return frames / (float) Dsp.SR; }

    /** Левый и правый каналы одинаковы — можно сохранять как моно. */
    boolean isMono() {
        for (int i = 0; i < frames; i++) if (st[2 * i] != st[2 * i + 1]) return false;
        return true;
    }

    static final class Sink implements OfflineRenderer.Sink {
        private short[] buf = new short[Dsp.SR * 2 * 10];
        private int frames;
        private final int maxFrames;

        Sink(int maxFrames) { this.maxFrames = maxFrames; }

        @Override public void write(float[] s, int n) throws java.io.IOException {
            if (frames + n > maxFrames) throw new voice.changer.app.dsp.Pcm.TooLongException();
            if ((frames + n) * 2 > buf.length) buf = Arrays.copyOf(buf, Math.max(buf.length * 3 / 2, (frames + n) * 2));
            for (int i = 0; i < n * 2; i++) {
                float v = Math.max(-1f, Math.min(1f, s[i]));
                buf[frames * 2 + i] = (short) Math.round(v * 32767f);
            }
            frames += n;
        }

        Rendered result() { return new Rendered(buf, frames); }
    }
}
