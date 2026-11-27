package voice.changer.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Расположение элементов: порядок и видимость блоков на каждой вкладке, порядок самих вкладок. */
final class LayoutPrefs {
    private LayoutPrefs() {}

    static final String[] LIVE = {"voice", "mode", "mic", "quick", "devices", "result"};
    static final String[] FILE = {"voice", "pick", "action", "result"};
    static final String[] TEXT = {"voice", "input", "tts", "action", "result"};
    static final String[] VOICE = {"voice", "tune", "save", "effects", "bg"};
    static final String[] TABS = {"0", "1", "2", "3"};

    static String[] defaults(String screen) {
        switch (screen) {
            case "live": return LIVE;
            case "file": return FILE;
            case "text": return TEXT;
            case "voice": return VOICE;
            default: return TABS;
        }
    }

    private static SharedPreferences sp() { return Store.prefs(); }

    private static List<String> split(String s) {
        List<String> l = new ArrayList<>();
        if (s != null && !s.isEmpty()) for (String x : s.split(",")) if (!x.isEmpty()) l.add(x);
        return l;
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String x : l) { if (b.length() > 0) b.append(','); b.append(x); }
        return b.toString();
    }

    /** Сохранённый порядок + новые блоки, которых в сохранении ещё нет. */
    static List<String> order(String screen) {
        String[] def = defaults(screen);
        List<String> valid = new ArrayList<>();
        for (String d : def) valid.add(d);
        List<String> out = new ArrayList<>();
        for (String s : split(sp().getString("lo_" + screen, ""))) if (valid.contains(s) && !out.contains(s)) out.add(s);
        for (String d : def) if (!out.contains(d)) out.add(d);
        return out;
    }

    static boolean hidden(String screen, String id) { return split(sp().getString("lh_" + screen, "")).contains(id); }

    static void move(String screen, String id, int delta) {
        List<String> o = order(screen);
        int i = o.indexOf(id), j = i + delta;
        if (i < 0 || j < 0 || j >= o.size()) return;
        o.remove(i);
        o.add(j, id);
        sp().edit().putString("lo_" + screen, join(o)).apply();
        Theme.dirty = true;
    }

    static void setHidden(String screen, String id, boolean hide) {
        List<String> h = split(sp().getString("lh_" + screen, ""));
        h.remove(id);
        if (hide) h.add(id);
        sp().edit().putString("lh_" + screen, join(h)).apply();
        Theme.dirty = true;
    }

    static void reset() {
        SharedPreferences.Editor e = sp().edit();
        for (String s : new String[]{"live", "file", "text", "voice", "tabs"}) { e.remove("lo_" + s); e.remove("lh_" + s); }
        e.apply();
        Theme.dirty = true;
    }

    /** Порядок вкладок: id 0 — Микрофон, 1 — Файл, 2 — Текст, 3 — Голос. */
    static int[] tabOrder() {
        List<String> o = order("tabs");
        int[] r = new int[o.size()];
        for (int i = 0; i < r.length; i++) r[i] = Integer.parseInt(o.get(i));
        return r;
    }

    /** Раскладывает блоки экрана по сохранённому порядку, скрытые пропускает. */
    static void fill(Context c, LinearLayout col, String screen, Map<String, View> blocks) {
        boolean first = true;
        for (String id : order(screen)) {
            if (hidden(screen, id)) continue;
            View v = blocks.get(id);
            if (v == null) continue;
            col.addView(v, Ui.wrapMatch(c, first ? 0 : 14));
            first = false;
        }
    }

    static LinkedHashMap<String, View> blocks() { return new LinkedHashMap<>(); }
}
