package voice.changer.app;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

import voice.changer.app.dsp.Ambient;
import voice.changer.app.dsp.BackgroundTrack;
import voice.changer.app.dsp.BgLayer;
import voice.changer.app.dsp.LayerMixer;
import voice.changer.app.dsp.VoiceSettings;

/** Сборка фоновых слоёв в микшер: синтезированные звуки кэшируются, файлы читаются потоком. */
final class Bg {
    private Bg() {}

    private static final Map<String, BackgroundTrack> ambient = new HashMap<>();

    static synchronized BackgroundTrack ambient(String kind) {
        BackgroundTrack t = ambient.get(kind);
        if (t == null) { t = Ambient.generate(kind, 30); ambient.put(kind, t); }
        return t;
    }

    /** Структура слоёв без громкостей — чтобы понимать, нужно ли пересобирать микшер. */
    static String signature(VoiceSettings s) {
        StringBuilder b = new StringBuilder();
        for (BgLayer l : s.bg) b.append(l.kind).append(':').append(l.file).append(';');
        return b.toString();
    }

    /** Микшер для настроек. Вызывать не из UI-потока и не из аудиопотока. Закрыть после использования. */
    static LayerMixer mixer(Context c, VoiceSettings s, boolean blocking) {
        LayerMixer m = new LayerMixer();
        for (BgLayer l : s.bg) {
            if ("file".equals(l.kind)) {
                java.io.File f = Files.bgLayerFile(c, l.file);
                if (f.exists()) m.add(new StreamBackground(f, blocking), l.volume);
                else m.add((st, n, g) -> { }, l.volume);   // файл пропал — тихий слой, чтобы номера слоёв не сдвигались
            } else {
                m.add(ambient(l.kind).player(), l.volume);
            }
        }
        return m;
    }
}
