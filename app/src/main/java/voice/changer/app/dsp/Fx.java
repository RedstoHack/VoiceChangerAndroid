package voice.changer.app.dsp;

/** Базовый класс эффекта (моно, на месте). amount: 0 — выключен, 1 — максимум. */
public abstract class Fx {
    protected float amount;

    public final void setAmount(float a) {
        float old = amount;
        amount = a;
        if (old <= 0.001f && a > 0.001f) reset();
    }

    public final boolean active() { return amount > 0.001f; }

    public abstract void process(float[] b, int off, int n);

    public void reset() {}
}
