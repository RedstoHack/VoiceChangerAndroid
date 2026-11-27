package voice.changer.app.dsp;

/** Синтезируемые фоновые звуки (без файлов и интернета). */
public final class Ambient {
    private Ambient() {}

    public static final String[] IDS = {"rain", "wind", "waves", "white", "brown"};
    private static final int XF = (int) (0.06 * Dsp.SR);

    public static BackgroundTrack generate(String id, int seconds) {
        int len = seconds * Dsp.SR;
        float[] l = channel(id, len, 101), r = channel(id, len, 977);
        short[] out = new short[len * 2];
        for (int i = 0; i < len; i++) {
            out[2 * i] = (short) (l[i] * 32767f);
            out[2 * i + 1] = (short) (r[i] * 32767f);
        }
        return new BackgroundTrack(out, len);
    }

    private static float[] channel(String id, int len, int seed) {
        int n = len + XF;
        float[] g = new float[n];
        Effects.Rng rng = new Effects.Rng();
        rng.seed(seed * 7919 + id.hashCode());
        double tw = 2 * Math.PI / (double) len; // LFO кратны длине петли — шов не слышен
        switch (id) {
            case "rain": {
                Biquad hp = new Biquad(Biquad.HP, 800f, 0.7f), lp = new Biquad(Biquad.LP, 9000f, 0.7f);
                Biquad drop = new Biquad(Biquad.BP, 3500f, 1.5f);
                for (int i = 0; i < n; i++) {
                    float x = lp.process(hp.process(rng.next())) * 0.55f;
                    float imp = (rng.next() > 0.9992f) ? (0.4f + 0.6f * Math.abs(rng.next())) * 6f : 0f;
                    float mod = 0.85f + 0.15f * (float) Math.sin(tw * 5 * i);
                    g[i] = (x + drop.process(imp) * 0.5f) * mod;
                }
                break;
            }
            case "wind": {
                Biquad lp = new Biquad(Biquad.LP, 350f, 0.9f), bp = new Biquad(Biquad.BP, 900f, 1.2f);
                for (int i = 0; i < n; i++) {
                    float w = rng.next();
                    float gust = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(tw * 2 * i + seed))
                            * (0.6f + 0.4f * (float) Math.sin(tw * 5 * i));
                    g[i] = (lp.process(w) * 2.5f + bp.process(w) * 0.8f) * gust;
                }
                break;
            }
            case "waves": {
                Biquad lp = new Biquad(Biquad.LP, 1500f, 0.7f);
                for (int i = 0; i < n; i++) {
                    float e = 0.5f + 0.5f * (float) Math.sin(tw * 4 * i + seed);
                    g[i] = lp.process(rng.next()) * (0.1f + 0.9f * e * e);
                }
                break;
            }
            case "brown": {
                float b = 0;
                for (int i = 0; i < n; i++) { b = (b + 0.02f * rng.next()) / 1.02f; g[i] = b; }
                break;
            }
            default:
                for (int i = 0; i < n; i++) g[i] = rng.next();
        }
        float[] out = new float[len];
        for (int i = 0; i < len; i++) {
            if (i < XF) { float t = i / (float) XF; out[i] = g[i] * t + g[len + i] * (1f - t); }
            else out[i] = g[i];
        }
        float peak = 1e-6f;
        for (float v : out) peak = Math.max(peak, Math.abs(v));
        float k = 0.5f / peak;
        for (int i = 0; i < len; i++) out[i] *= k;
        return out;
    }
}
