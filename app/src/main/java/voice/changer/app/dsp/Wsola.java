package voice.changer.app.dsp;

/**
 * WSOLA — растяжение/сжатие по времени без изменения высоты (моно, офлайн).
 * Выходная длина = len * f. Источник — short[] с запасом нулей не меньше 2*N после len.
 */
public final class Wsola implements FloatSource {
    public static final int N = 2048, H = N / 2, DELTA = 441, DEC = 4;

    private static final float SCALE = 1f / 32768f;   // на выходе — float в диапазоне ±1

    private final short[] src;
    private final int len;
    private final double hopA;
    private final long limit;
    private long produced;
    private final float[] win = new float[N];
    private final float[] acc = new float[N];
    private final float[] outBuf = new float[H];
    private final float[] tpl = new float[H / DEC];
    private int outPos = H;
    private int k = 0, prevQ = 0;
    private boolean first = true, finished;

    public Wsola(short[] src, int len, double f) {
        if (src.length < len + 2 * N) throw new IllegalArgumentException("source needs padding");
        this.src = src; this.len = len;
        this.hopA = H / f;
        this.limit = (long) Math.floor(len * f);
        for (int i = 0; i < N; i++) win[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));
    }

    private double score(int q, int tstart, boolean full) {
        double c = 0, e = 0;
        if (full) {
            for (int i = 0; i < H; i++) { float s = src[q + i]; c += src[tstart + i] * s; e += s * s; }
        } else {
            for (int j = 0; j < tpl.length; j++) { float s = src[q + j * DEC]; c += tpl[j] * s; e += s * s; }
        }
        return c / Math.sqrt(e + 1e-3);
    }

    private int bestMatch(int q0) {
        int lo = Math.max(0, q0 - DELTA), hi = Math.min(len, q0 + DELTA);
        if (hi < lo) return lo;
        int center = Math.min(hi, Math.max(lo, q0));
        int tstart = prevQ + H;
        for (int j = 0; j < tpl.length; j++) tpl[j] = src[tstart + j * DEC];
        int bq = center;
        double best = score(center, tstart, false);
        for (int q = lo; q <= hi; q += DEC) {
            double s = score(q, tstart, false);
            if (s > best + 1e-9) { best = s; bq = q; }
        }
        int rlo = Math.max(lo, bq - DEC + 1), rhi = Math.min(hi, bq + DEC - 1);
        int fq = bq;
        double fb = score(bq, tstart, true);
        for (int q = rlo; q <= rhi; q++) {
            if (q == bq) continue;
            double s = score(q, tstart, true);
            if (s > fb + 1e-9) { fb = s; fq = q; }
        }
        return fq;
    }

    private void produceFrame() {
        if (first) {
            for (int i = 0; i < N; i++) acc[i] += (i < H ? 1f : win[i]) * src[i] * SCALE;
            first = false; prevQ = 0;
        } else {
            int q0 = (int) Math.round(k * hopA);
            if (q0 >= len) {
                System.arraycopy(acc, 0, outBuf, 0, H);
                finished = true;
                return;
            }
            int q = bestMatch(q0);
            for (int i = 0; i < N; i++) acc[i] += win[i] * src[q + i] * SCALE;
            prevQ = q;
        }
        System.arraycopy(acc, 0, outBuf, 0, H);
        System.arraycopy(acc, H, acc, 0, H);
        java.util.Arrays.fill(acc, H, N, 0f);
        k++;
    }

    @Override
    public int read(float[] out, int off, int n) {
        int p = 0;
        while (p < n && produced < limit) {
            if (outPos >= H) {
                if (finished) break;
                produceFrame();
                outPos = 0;
            }
            int c = (int) Math.min(Math.min(n - p, H - outPos), limit - produced);
            for (int i = 0; i < c; i++) out[off + p + i] = outBuf[outPos + i];
            outPos += c; p += c; produced += c;
        }
        return p == 0 ? -1 : p;
    }
}
