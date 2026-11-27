package voice.changer.app.dsp;

/** Цепочка эффектов в фиксированном порядке. */
public final class EffectChain {
    private final Fx[] fx = new Fx[FxType.COUNT];

    public EffectChain() {
        for (FxType t : FxType.values()) fx[t.ordinal()] = Effects.create(t);
    }

    public void apply(VoiceSettings s) {
        for (int i = 0; i < fx.length; i++) fx[i].setAmount(s.fx[i]);
    }

    public void process(float[] b, int off, int n) {
        for (FxType t : FxType.ORDER) {
            Fx f = fx[t.ordinal()];
            if (f.active()) f.process(b, off, n);
        }
    }

    /** Сколько кадров тишины дорендерить, чтобы эхо/реверберация затихли. */
    public int tailFrames() {
        float echo = fx[FxType.ECHO.ordinal()].active() ? 3f : 0f;
        float rev = fx[FxType.REVERB.ordinal()].active() ? 4f : 0f;
        int tail = (int) (Math.max(echo, rev) * Dsp.SR);
        Fx r = fx[FxType.REVERSE.ordinal()];
        if (r.active()) tail += Effects.Reverse.frames(r.amount);   // дозаканчиваем последний кусок
        return tail;
    }
}
