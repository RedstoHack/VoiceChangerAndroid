package voice.changer.app.dsp;

/** Зацикленная фоновая дорожка (стерео 16 бит, 44.1 кГц). Данные неизменяемы, у каждого потребителя свой Player. */
public final class BackgroundTrack {
    public final short[] data;
    public final int frames;

    public BackgroundTrack(short[] data, int frames) { this.data = data; this.frames = frames; }

    public Player player() { return new Player(); }

    public final class Player implements BackgroundSource {
        private int pos;
        /** Подмешивает фон в interleaved-стерео буфер. */
        @Override public void mix(float[] st, int n, float gain) {
            if (frames == 0) return;
            for (int f = 0; f < n; f++) {
                int i = pos * 2;
                st[2 * f] += gain * data[i] * (1f / 32768f);
                st[2 * f + 1] += gain * data[i + 1] * (1f / 32768f);
                if (++pos >= frames) pos = 0;
            }
        }
    }
}
