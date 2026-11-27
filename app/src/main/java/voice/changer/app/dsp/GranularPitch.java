package voice.changer.app.dsp;

/**
 * Питч-шифтер реального времени: две линии задержки с «пилообразной» модуляцией и кроссфейдом.
 * Задержка ≈ W/2 (~23 мс). Меняет высоту без изменения длительности, блоками любого размера.
 */
public final class GranularPitch {
    private static final int W = 2048, MASK = 8191;
    private final float[] buf = new float[MASK + 1];
    private int w = 0;
    private double phase = 0;
    private float ratio = 1f;

    public void setRatio(float r) { ratio = r; }

    public void process(float[] b, int off, int n) {
        if (Math.abs(ratio - 1f) < 1e-4f) {
            for (int i = 0; i < n; i++) { buf[w] = b[off + i]; w = (w + 1) & MASK; }
            return;
        }
        double dphi = (1.0 - ratio) / W;
        for (int i = 0; i < n; i++) {
            buf[w] = b[off + i];
            double p1 = phase, p2 = phase + 0.5;
            if (p2 >= 1) p2 -= 1;
            float g1 = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * p1));
            b[off + i] = g1 * read(p1) + (1f - g1) * read(p2);
            w = (w + 1) & MASK;
            phase += dphi;
            if (phase >= 1) phase -= 1; else if (phase < 0) phase += 1;
        }
    }

    private float read(double p) {
        double rp = w - (2.0 + p * W);
        int i0 = (int) Math.floor(rp);
        float fr = (float) (rp - i0);
        float a = buf[i0 & MASK], c = buf[(i0 + 1) & MASK];
        return a + (c - a) * fr;
    }
}
