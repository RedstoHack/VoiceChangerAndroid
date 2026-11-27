package voice.changer.app.dsp;

/** Эффекты в порядке показа в интерфейсе (новые — только в конец: порядок хранится в настройках). */
public enum FxType {
    ROBOT, ALIEN, WHISPER, ECHO, REVERB, CHORUS, FLANGER, VIBRATO, TREMOLO,
    DISTORTION, BITCRUSH, TELEPHONE, MEGAPHONE, UNDERWATER, VOCODER, FAN, REVERSE;

    public static final int COUNT = values().length;

    /** Порядок обработки в цепочке. */
    static final FxType[] ORDER = {
        REVERSE, WHISPER, VOCODER, ROBOT, ALIEN, BITCRUSH, DISTORTION, TELEPHONE, MEGAPHONE, UNDERWATER,
        VIBRATO, TREMOLO, FAN, CHORUS, FLANGER, ECHO, REVERB
    };
}
