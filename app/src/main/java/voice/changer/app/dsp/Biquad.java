package voice.changer.app.dsp;

/** Биквад-фильтр (RBJ cookbook), состояние в double — устойчив на низких частотах. */
public final class Biquad {
    public static final int LP = 0, HP = 1, BP = 2;
    private double b0 = 1, b1, b2, a1, a2, z1, z2;

    public Biquad(int type, float freq, float q) { set(type, freq, q); }

    public void set(int type, float freq, float q) {
        double w = 2 * Math.PI * freq / Dsp.SR, cs = Math.cos(w), al = Math.sin(w) / (2 * q);
        double a0 = 1 + al, nb0, nb1, nb2;
        switch (type) {
            case LP: nb0 = (1 - cs) / 2; nb1 = 1 - cs; nb2 = nb0; break;
            case HP: nb0 = (1 + cs) / 2; nb1 = -(1 + cs); nb2 = nb0; break;
            default: nb0 = al; nb1 = 0; nb2 = -al; break;
        }
        b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0;
        a1 = -2 * cs / a0; a2 = (1 - al) / a0;
    }

    public float process(float x) {
        double y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return (float) y;
    }

    public void reset() { z1 = z2 = 0; }
}
