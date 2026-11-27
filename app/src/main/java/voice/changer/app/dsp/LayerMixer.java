package voice.changer.app.dsp;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

/** Сумма нескольких фоновых слоёв, у каждого своя громкость. */
public final class LayerMixer implements Closeable {
    private final List<BackgroundSource> src = new ArrayList<>();
    private final List<Float> gain = new ArrayList<>();

    public void add(BackgroundSource s, float g) { src.add(s); gain.add(g); }
    public boolean isEmpty() { return src.isEmpty(); }
    public int size() { return src.size(); }
    public void setGain(int i, float g) { if (i >= 0 && i < gain.size()) gain.set(i, g); }

    public void mix(float[] st, int n) {
        for (int i = 0; i < src.size(); i++) src.get(i).mix(st, n, gain.get(i));
    }

    @Override public void close() {
        for (BackgroundSource s : src)
            if (s instanceof Closeable) { try { ((Closeable) s).close(); } catch (Exception ignored) { } }
        src.clear(); gain.clear();
    }
}
