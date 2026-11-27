package voice.changer.app.dsp;

/** Общие константы и мелкие функции DSP. Весь пакет dsp — чистая Java, без Android API. */
public final class Dsp {
    private Dsp() {}

    /** Внутренняя частота дискретизации всего приложения. */
    public static final int SR = 44100;

    /** Мягкий ограничитель: ниже 0.8 не трогает сигнал, выше плавно прижимает к 1.0. */
    public static float softClip(float x) {
        float a = Math.abs(x);
        if (a < 0.8f) return x;
        float y = 0.8f + 0.2f * (float) Math.tanh((a - 0.8f) / 0.2f);
        return x < 0 ? -y : y;
    }

    public static float semitonesToRatio(float st) {
        return (float) Math.pow(2.0, st / 12.0);
    }
}
