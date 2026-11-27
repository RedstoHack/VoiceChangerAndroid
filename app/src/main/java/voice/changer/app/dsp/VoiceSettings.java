package voice.changer.app.dsp;

import java.util.ArrayList;
import java.util.List;

/** Настройки голоса. Экземпляры не меняются «на месте» после публикации — только copy(). */
public final class VoiceSettings {
    /** id готового голоса, "user:Имя" для своего или "custom" */
    public String preset = "original";
    public float pitch = 0f;      // полутоны, -12..+12
    public float speed = 1f;      // 0.5..2.0 (темп, высоту не меняет)
    public float volume = 1f;     // 0..2
    /** Проиграть задом наперёд (только для файлов, текста и записи — в реальном времени невозможно). */
    public boolean reverse = false;
    public final float[] fx = new float[FxType.COUNT];
    public final List<BgLayer> bg = new ArrayList<>();

    public float ratio() { return Dsp.semitonesToRatio(pitch); }

    public VoiceSettings copy() {
        VoiceSettings s = new VoiceSettings();
        s.preset = preset; s.pitch = pitch; s.speed = speed; s.volume = volume; s.reverse = reverse;
        System.arraycopy(fx, 0, s.fx, 0, fx.length);
        for (BgLayer b : bg) s.bg.add(b.copy());
        return s;
    }
}
