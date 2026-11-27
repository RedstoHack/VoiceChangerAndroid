package voice.changer.app.dsp;

import java.io.IOException;

/**
 * Потоковый ресэмплер (кубическая интерполяция Эрмита), любое число каналов.
 * step = (скорость чтения входа): step&gt;1 — выход короче и выше по тону.
 */
public final class Resampler implements FloatSource {
    private final FloatSource in;
    private final int ch;
    private final double step;
    private final float[] buf;
    private int bl = 1;          // валидных кадров в buf (кадр 0 — «история» из нулей)
    private double pos = 1.0;
    private boolean eof;

    public Resampler(FloatSource in, int ch, double step) {
        this.in = in; this.ch = ch; this.step = step;
        this.buf = new float[(8192 + 8) * ch];
    }

    private float at(int idx, int c) {
        return (idx < 0 || idx >= bl) ? 0f : buf[idx * ch + c];
    }

    private void refill(int i) throws IOException {
        int shift = Math.max(0, i - 1);
        if (shift > 0) {
            System.arraycopy(buf, shift * ch, buf, 0, (bl - shift) * ch);
            bl -= shift;
            pos -= shift;
        }
        int cap = buf.length / ch;
        int n = in.read(buf, bl * ch, cap - bl);
        if (n < 0) eof = true; else bl += n;
    }

    @Override
    public int read(float[] out, int off, int frames) throws IOException {
        int p = 0;
        while (p < frames) {
            int i = (int) pos;
            if (!eof && i + 2 >= bl) { refill(i); continue; }
            if (eof && i >= bl) break;
            float t = (float) (pos - i);
            for (int c = 0; c < ch; c++) {
                float y0 = at(i - 1, c), y1 = at(i, c), y2 = at(i + 1, c), y3 = at(i + 2, c);
                float c1 = 0.5f * (y2 - y0);
                float c2 = y0 - 2.5f * y1 + 2f * y2 - 0.5f * y3;
                float c3 = 0.5f * (y3 - y0) + 1.5f * (y1 - y2);
                out[(off + p) * ch + c] = ((c3 * t + c2) * t + c1) * t + y1;
            }
            pos += step;
            p++;
        }
        return p == 0 ? -1 : p;
    }
}
