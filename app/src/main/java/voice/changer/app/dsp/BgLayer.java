package voice.changer.app.dsp;

/** Один слой фона: синтезированный звук или файл пользователя. */
public final class BgLayer {
    /** rain | wind | waves | white | brown | file */
    public String kind;
    /** для kind == file: идентификатор копии файла внутри приложения */
    public String file = "";
    /** название для показа (имя файла) */
    public String name = "";
    public float volume = 0.5f;

    public BgLayer(String kind) { this.kind = kind; }

    public BgLayer copy() {
        BgLayer b = new BgLayer(kind);
        b.file = file; b.name = name; b.volume = volume;
        return b;
    }
}
