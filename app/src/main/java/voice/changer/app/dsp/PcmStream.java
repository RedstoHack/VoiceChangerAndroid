package voice.changer.app.dsp;

import java.io.Closeable;

/** Источник с известной частотой дискретизации (декодер файла). */
public interface PcmStream extends FloatSource, Closeable {
    int rate();
}
