package voice.changer.app.dsp;

import java.util.Arrays;

/** Реализации эффектов. */
final class Effects {
    private Effects() {}

    static Fx create(FxType t) {
        switch (t) {
            case ROBOT: return new Robot();
            case ALIEN: return new Alien();
            case WHISPER: return new Whisper();
            case ECHO: return new Echo();
            case REVERB: return new Reverb();
            case CHORUS: return new Chorus();
            case FLANGER: return new Flanger();
            case VIBRATO: return new Vibrato();
            case TREMOLO: return new Tremolo();
            case DISTORTION: return new Distortion();
            case BITCRUSH: return new BitCrush();
            case TELEPHONE: return new Telephone();
            case MEGAPHONE: return new Megaphone();
            case VOCODER: return new Vocoder();
            case FAN: return new Fan();
            case REVERSE: return new Reverse();
            default: return new Underwater();
        }
    }

    /** Линия задержки с дробным чтением. */
    static final class ModDelay {
        private final float[] buf = new float[8192];
        private int w;
        void write(float x) { buf[w] = x; w = (w + 1) & 8191; }
        float read(float d) {
            float rp = w - d;
            int i0 = (int) Math.floor(rp);
            float fr = rp - i0;
            float a = buf[i0 & 8191], c = buf[(i0 + 1) & 8191];
            return a + (c - a) * fr;
        }
        void clear() { Arrays.fill(buf, 0f); }
    }

    /** Синусный LFO. */
    static final class Lfo {
        private float ph;
        float next(float hz) {
            ph += hz / Dsp.SR;
            if (ph >= 1f) ph -= 1f;
            return (float) Math.sin(2 * Math.PI * ph);
        }
    }

    static final class Rng {
        private int s = 0x2545F491;
        float next() { // [-1, 1)
            s ^= s << 13; s ^= s >>> 17; s ^= s << 5;
            return s / 2147483648f;
        }
        void seed(int v) { s = v == 0 ? 0x2545F491 : v; }
    }

    // ---------------------------------------------------------------- Робот
    static final class Robot extends Fx {
        private float ph;
        private final float[] comb = new float[400];
        private int ci;
        @Override public void process(float[] b, int o, int n) {
            float a = amount;
            for (int i = 0; i < n; i++) {
                float x = b[o + i];
                ph += 60f / Dsp.SR; if (ph >= 1f) ph -= 1f;
                float ring = x * (float) Math.sin(2 * Math.PI * ph);
                float c = ring + 0.55f * comb[ci];
                comb[ci] = c; ci = (ci + 1) % comb.length;
                b[o + i] = (1f - a) * x + a * 0.8f * c;
            }
        }
        @Override public void reset() { Arrays.fill(comb, 0f); ph = 0; }
    }

    // ------------------------------------------------------------- Пришелец
    static final class Alien extends Fx {
        private float ph; private final Lfo lfo = new Lfo();
        @Override public void process(float[] b, int o, int n) {
            float a = amount;
            for (int i = 0; i < n; i++) {
                float x = b[o + i];
                float f = 230f + 160f * lfo.next(0.9f);
                ph += f / Dsp.SR; if (ph >= 1f) ph -= 1f;
                float ring = x * (float) Math.sin(2 * Math.PI * ph);
                b[o + i] = (1f - 0.85f * a) * x + a * 1.1f * ring;
            }
        }
    }

    // ---------------------------------------------------------------- Шёпот
    /** Шумовой вокодер: огибающие 6 полос из голоса модулируют полосовой шум. */
    static final class Whisper extends Fx {
        private static final float[] F = {250, 500, 1000, 2000, 3500, 6000};
        private final Biquad[] bpIn = new Biquad[6], bpNoise = new Biquad[6];
        private final float[] env = new float[6], gain = new float[6];
        private final Rng rng = new Rng();
        private final float coef = 1f - (float) Math.exp(-1.0 / (0.008 * Dsp.SR));
        Whisper() {
            Rng cal = new Rng(); cal.seed(12345);
            for (int k = 0; k < 6; k++) {
                bpIn[k] = new Biquad(Biquad.BP, F[k], 2f);
                bpNoise[k] = new Biquad(Biquad.BP, F[k], 2f);
                double e = 0; int m = Dsp.SR;
                for (int i = 0; i < m; i++) { float y = bpNoise[k].process(cal.next()); e += y * y; }
                gain[k] = (float) (1.0 / (0.9 * Math.sqrt(e / m)));
                bpNoise[k].reset();
            }
        }
        @Override public void process(float[] b, int o, int n) {
            float a = amount;
            for (int i = 0; i < n; i++) {
                float x = b[o + i], nz = rng.next(), w = 0;
                for (int k = 0; k < 6; k++) {
                    float y = bpIn[k].process(x);
                    env[k] += coef * (Math.abs(y) - env[k]);
                    w += env[k] * gain[k] * bpNoise[k].process(nz);
                }
                b[o + i] = (1f - a) * x + a * w;
            }
        }
        @Override public void reset() {
            for (int k = 0; k < 6; k++) { bpIn[k].reset(); bpNoise[k].reset(); env[k] = 0; }
        }
    }

    // ------------------------------------------------------------------ Эхо
    static final class Echo extends Fx {
        private final float[] buf = new float[(int) (0.31 * Dsp.SR)];
        private int idx;
        @Override public void process(float[] b, int o, int n) {
            float wet = 0.7f * amount, fb = 0.25f + 0.4f * amount;
            for (int i = 0; i < n; i++) {
                float x = b[o + i], d = buf[idx];
                b[o + i] = x + wet * d;
                buf[idx] = x + fb * d;
                if (++idx >= buf.length) idx = 0;
            }
        }
        @Override public void reset() { Arrays.fill(buf, 0f); idx = 0; }
    }

    // ----------------------------------------------------------- Реверберация
    /** Freeverb: 8 гребней + 4 all-pass. */
    static final class Reverb extends Fx {
        private static final int[] CT = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        private static final int[] AT = {556, 441, 341, 225};
        private final float[][] cb = new float[8][];
        private final int[] ci = new int[8];
        private final float[] cf = new float[8];
        private final float[][] ab = new float[4][];
        private final int[] ai = new int[4];
        Reverb() {
            for (int i = 0; i < 8; i++) cb[i] = new float[CT[i]];
            for (int i = 0; i < 4; i++) ab[i] = new float[AT[i]];
        }
        @Override public void process(float[] b, int o, int n) {
            float fb = 0.78f + 0.16f * amount, damp = 0.25f, wet = 1.2f * amount;
            for (int s = 0; s < n; s++) {
                float x = b[o + s], in = x * 0.015f, sum = 0;
                for (int i = 0; i < 8; i++) {
                    float out = cb[i][ci[i]];
                    cf[i] = out * (1f - damp) + cf[i] * damp;
                    cb[i][ci[i]] = in + cf[i] * fb;
                    if (++ci[i] >= cb[i].length) ci[i] = 0;
                    sum += out;
                }
                for (int i = 0; i < 4; i++) {
                    float bo = ab[i][ai[i]];
                    float out = -sum + bo;
                    ab[i][ai[i]] = sum + bo * 0.5f;
                    if (++ai[i] >= ab[i].length) ai[i] = 0;
                    sum = out;
                }
                b[o + s] = x + wet * sum;
            }
        }
        @Override public void reset() {
            for (float[] a : cb) Arrays.fill(a, 0f);
            for (float[] a : ab) Arrays.fill(a, 0f);
            Arrays.fill(cf, 0f);
        }
    }

    // ----------------------------------------------------------------- Хорус
    static final class Chorus extends Fx {
        private final ModDelay md = new ModDelay();
        private final Lfo l1 = new Lfo(), l2 = new Lfo(), l3 = new Lfo();
        @Override public void process(float[] b, int o, int n) {
            float a = amount;
            for (int i = 0; i < n; i++) {
                float x = b[o + i];
                md.write(x);
                float d1 = (0.018f + 0.003f * l1.next(0.35f)) * Dsp.SR;
                float d2 = (0.024f + 0.003f * l2.next(0.50f)) * Dsp.SR;
                float d3 = (0.031f + 0.003f * l3.next(0.70f)) * Dsp.SR;
                float wet = (md.read(d1) + md.read(d2) + md.read(d3)) / 3f;
                b[o + i] = (1f - 0.4f * a) * x + 0.9f * a * wet;
            }
        }
        @Override public void reset() { md.clear(); }
    }

    // -------------------------------------------------------------- Флэнджер
    static final class Flanger extends Fx {
        private final ModDelay md = new ModDelay();
        private final Lfo lfo = new Lfo();
        @Override public void process(float[] b, int o, int n) {
            float a = amount;
            for (int i = 0; i < n; i++) {
                float x = b[o + i];
                float d = (0.0032f + 0.0025f * lfo.next(0.25f)) * Dsp.SR;
                float dl = md.read(d);
                md.write(x + 0.5f * a * dl);
                b[o + i] = (x + 0.9f * a * dl) / (1f + 0.45f * a);
            }
        }
        @Override public void reset() { md.clear(); }
    }

    // ---------------------------------------------------------------- Вибрато
    static final class Vibrato extends Fx {
        private final ModDelay md = new ModDelay();
        private final Lfo lfo = new Lfo();
        @Override public void process(float[] b, int o, int n) {
            float depth = 0.0035f * amount;
            for (int i = 0; i < n; i++) {
                md.write(b[o + i]);
                float d = (depth + 0.001f + depth * lfo.next(5.5f)) * Dsp.SR;
                b[o + i] = md.read(d);
            }
        }
        @Override public void reset() { md.clear(); }
    }

    // ---------------------------------------------------------------- Тремоло
    static final class Tremolo extends Fx {
        private final Lfo lfo = new Lfo();
        @Override public void process(float[] b, int o, int n) {
            float depth = 0.85f * amount;
            for (int i = 0; i < n; i++)
                b[o + i] *= 1f - depth * (0.5f + 0.5f * lfo.next(5.5f));
        }
    }

    // -------------------------------------------------------------- Искажение
    static final class Distortion extends Fx {
        @Override public void process(float[] b, int o, int n) {
            float drive = 1f + 30f * amount, wet = Math.min(1f, 4f * amount);
            for (int i = 0; i < n; i++) {
                float x = b[o + i];
                float y = (float) Math.tanh(drive * x) * 0.4f;
                b[o + i] = x * (1f - wet) + y * wet;
            }
        }
    }

    // ---------------------------------------------------------------- Биткраш
    static final class BitCrush extends Fx {
        private int cnt; private float held;
        @Override public void process(float[] b, int o, int n) {
            float bits = Math.max(4f, 16f - 12f * amount);
            float levels = (float) Math.pow(2.0, bits - 1);
            int hold = 1 + (int) (amount * 9f);
            for (int i = 0; i < n; i++) {
                if (cnt == 0) held = Math.round(b[o + i] * levels) / levels;
                cnt = (cnt + 1) % hold;
                b[o + i] = held;
            }
        }
    }

    // ---------------------------------------------------------------- Телефон
    static final class Telephone extends Fx {
        private final Biquad hp = new Biquad(Biquad.HP, 400f, 0.7f), lp = new Biquad(Biquad.LP, 3300f, 0.7f);
        @Override public void process(float[] b, int o, int n) {
            float a = amount;
            for (int i = 0; i < n; i++) {
                float x = b[o + i];
                float y = (float) Math.tanh(1.8f * lp.process(hp.process(x))) * 0.8f;
                b[o + i] = (1f - a) * x + a * 1.4f * y;
            }
        }
        @Override public void reset() { hp.reset(); lp.reset(); }
    }

    // ---------------------------------------------------------------- Мегафон
    static final class Megaphone extends Fx {
        private final Biquad hp = new Biquad(Biquad.HP, 600f, 0.7f), lp = new Biquad(Biquad.LP, 3800f, 0.7f);
        @Override public void process(float[] b, int o, int n) {
            float a = amount;
            for (int i = 0; i < n; i++) {
                float x = b[o + i];
                float y = (float) Math.tanh(5f * lp.process(hp.process(x))) * 0.6f;
                b[o + i] = (1f - a) * x + a * y;
            }
        }
        @Override public void reset() { hp.reset(); lp.reset(); }
    }

    // -------------------------------------------------------------- Под водой
    static final class Underwater extends Fx {
        private final Biquad lp = new Biquad(Biquad.LP, 500f, 1.2f);
        private final ModDelay md = new ModDelay();
        private final Lfo lfo = new Lfo();
        @Override public void process(float[] b, int o, int n) {
            float a = amount;
            for (int i = 0; i < n; i++) {
                float x = b[o + i];
                md.write(lp.process(x));
                float d = (0.004f + 0.002f * lfo.next(3f)) * Dsp.SR;
                b[o + i] = (1f - a) * x + a * md.read(d);
            }
        }
        @Override public void reset() { lp.reset(); md.clear(); }
    }

    // --------------------------------------------------------------- Вокодер
    /** Канальный вокодер: огибающие 10 полос голоса модулируют «пилу» 110 Гц — звучит как поющий робот. */
    static final class Vocoder extends Fx {
        private static final float[] F = {180, 300, 450, 650, 900, 1300, 1800, 2500, 3500, 5000};
        private final Biquad[] bpIn = new Biquad[F.length], bpCar = new Biquad[F.length];
        private final float[] env = new float[F.length], gain = new float[F.length];
        private final float coef = 1f - (float) Math.exp(-1.0 / (0.006 * Dsp.SR));
        private float ph;
        Vocoder() {
            for (int k = 0; k < F.length; k++) {
                bpIn[k] = new Biquad(Biquad.BP, F[k], 3.5f);
                bpCar[k] = new Biquad(Biquad.BP, F[k], 3.5f);
                double e = 0; float p = 0; int m = Dsp.SR;
                for (int i = 0; i < m; i++) {
                    p += 110f / Dsp.SR; if (p >= 1f) p -= 1f;
                    float y = bpCar[k].process(2f * p - 1f);
                    e += y * y;
                }
                gain[k] = (float) (1.0 / (0.9 * Math.sqrt(e / m)));
                bpCar[k].reset();
            }
        }
        @Override public void process(float[] b, int o, int n) {
            float a = amount;
            for (int i = 0; i < n; i++) {
                float x = b[o + i];
                ph += 110f / Dsp.SR; if (ph >= 1f) ph -= 1f;
                float car = 2f * ph - 1f, w = 0;
                for (int k = 0; k < F.length; k++) {
                    float y = bpIn[k].process(x);
                    env[k] += coef * (Math.abs(y) - env[k]);
                    w += env[k] * gain[k] * bpCar[k].process(car);
                }
                b[o + i] = (1f - a) * x + a * 0.9f * w;
            }
        }
        @Override public void reset() {
            for (int k = 0; k < F.length; k++) { bpIn[k].reset(); bpCar[k].reset(); env[k] = 0; }
        }
    }

    // ------------------------------------------------------------ Вентилятор
    /** Быстрая амплитудная модуляция (~25 Гц) — голос «режут лопасти». */
    static final class Fan extends Fx {
        private final Lfo lfo = new Lfo();
        @Override public void process(float[] b, int o, int n) {
            float depth = 0.85f * amount;
            for (int i = 0; i < n; i++)
                b[o + i] *= 1f - depth * (0.5f + 0.5f * lfo.next(25f));
        }
    }

    // ------------------------------------------------- Реверс в реальном времени
    /**
     * Речь переворачивается кусками: накопили кусок длиной L, выдали его задом наперёд, пока копится следующий.
     * Поэтому звук идёт с задержкой, равной длине куска. amount 0..1 → длина 0.1…1.5 с.
     */
    static final class Reverse extends Fx {
        static int frames(float amount) { return (int) ((0.1f + 1.4f * amount) * Dsp.SR); }

        private float[] acc = new float[0], out = new float[0];
        private int n, w, rp;
        private boolean haveOut;

        private void init(int len) {
            n = len;
            acc = new float[len];
            out = new float[len];
            w = 0; rp = 0; haveOut = false;
        }

        @Override public void process(float[] b, int o, int cnt) {
            int len = frames(amount);
            if (len != n) init(len);
            final int fade = Math.min(Dsp.SR / 200, n / 4);       // 5 мс — чтобы на стыках кусков не щёлкало
            for (int i = 0; i < cnt; i++) {
                acc[w++] = b[o + i];
                float y = 0f;
                if (haveOut && rp < n) {
                    y = out[rp];
                    float g = 1f;
                    if (rp < fade) g = rp / (float) fade;
                    else if (n - 1 - rp < fade) g = (n - 1 - rp) / (float) fade;
                    y *= g;
                    rp++;
                }
                b[o + i] = y;
                if (w == n) {
                    for (int k = 0; k < n; k++) out[k] = acc[n - 1 - k];
                    w = 0; rp = 0; haveOut = true;
                }
            }
        }

        @Override public void reset() { n = 0; w = 0; rp = 0; haveOut = false; }
    }
}
