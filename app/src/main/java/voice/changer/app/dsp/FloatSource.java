package voice.changer.app.dsp;

import java.io.IOException;

/** Потоковый источник float-сэмплов (интерливинг по каналам). */
public interface FloatSource {
    /** Читает до {@code frames} кадров, возвращает число прочитанных кадров или -1 в конце. */
    int read(float[] out, int off, int frames) throws IOException;
}
