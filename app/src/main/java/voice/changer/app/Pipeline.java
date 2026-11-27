package voice.changer.app;

import android.content.Context;

import java.io.File;
import java.io.IOException;

import voice.changer.app.dsp.Dsp;
import voice.changer.app.dsp.LayerMixer;
import voice.changer.app.dsp.OfflineRenderer;
import voice.changer.app.dsp.Pcm;
import voice.changer.app.dsp.PcmStream;
import voice.changer.app.dsp.VoiceSettings;
import voice.changer.app.dsp.WavSource;

/** Склейка: декодирование → обработка (в памяти). Кодирование в файл — отдельно, в {@link Encoders}. */
final class Pipeline {
    private Pipeline() {}

    static final int MAX_VOICE_FRAMES = 20 * 60 * Dsp.SR;

    /** Читает файл в PCM 44.1 кГц (ch = 1 или 2). */
    static Pcm decode(String path, int ch, int maxFrames) throws IOException {
        PcmStream st = WavSource.open(new File(path), ch);
        if (st == null) st = new AudioDecoder(path, ch);
        try {
            return Pcm.fromStream(st, ch, maxFrames);
        } finally {
            st.close();
        }
    }

    /** Обрабатывает голос и возвращает результат в памяти — слушать можно сразу. */
    static Rendered render(Context c, Pcm voice, VoiceSettings s, OfflineRenderer.Progress p) throws IOException {
        LayerMixer mx = Bg.mixer(c, s, true);
        try {
            Rendered.Sink sink = new Rendered.Sink(MAX_VOICE_FRAMES + 10 * Dsp.SR);
            OfflineRenderer.render(voice, s, mx, sink, p);
            return sink.result();
        } finally {
            mx.close();
        }
    }
}
