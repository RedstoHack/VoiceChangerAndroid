package voice.changer.app.dsp;

/** Источник фонового звука: подмешивает n стерео-кадров в буфер. */
public interface BackgroundSource {
    void mix(float[] st, int n, float gain);
}
