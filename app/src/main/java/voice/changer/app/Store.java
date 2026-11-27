package voice.changer.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import voice.changer.app.dsp.Ambient;
import voice.changer.app.dsp.BgLayer;
import voice.changer.app.dsp.Presets;
import voice.changer.app.dsp.VoiceSettings;

/** Текущие настройки голоса, свои пресеты голоса и фона, параметры приложения (SharedPreferences, только на устройстве). */
final class Store {
    private Store() {}

    interface Edit { void apply(VoiceSettings s); }

    static final int FMT_WAV = 0, FMT_M4A = 1, FMT_FLAC = 2, FMT_MP3 = 3, FMT_OGG = 4;

    /** Свой пресет голоса. */
    static final class UserPreset {
        String name;
        float pitch, speed = 1f, volume = 1f;
        boolean reverse;
        float[] fx = new float[voice.changer.app.dsp.FxType.COUNT];
    }

    /** Сохранённая комбинация фоновых звуков. */
    static final class BgPreset {
        String name;
        List<BgLayer> layers = new ArrayList<>();
    }

    private static SharedPreferences sp;
    private static VoiceSettings cur = new VoiceSettings();
    private static final List<Runnable> listeners = new ArrayList<>();

    static synchronized void init(Context c) {
        if (sp != null) return;
        sp = c.getApplicationContext().getSharedPreferences("voice", Context.MODE_PRIVATE);
        try {
            cur = fromJson(new JSONObject(sp.getString("settings", "{}")));
        } catch (Exception e) {
            AppLog.err("load settings", e);
        }
    }

    static SharedPreferences prefs() { return sp; }

    private static float clamp(float v, float lo, float hi) {
        return Float.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    private static boolean validKind(String k) {
        if ("file".equals(k)) return true;
        for (String a : Ambient.IDS) if (a.equals(k)) return true;
        return false;
    }

    private static JSONArray fxJson(float[] fx) throws Exception {
        JSONArray a = new JSONArray();
        for (float v : fx) a.put(v);
        return a;
    }

    private static void fxFrom(JSONArray a, float[] fx) {
        if (a == null) return;
        for (int i = 0; i < Math.min(a.length(), fx.length); i++) fx[i] = clamp((float) a.optDouble(i, 0), 0f, 1f);
    }

    private static JSONArray layersJson(List<BgLayer> l) throws Exception {
        JSONArray a = new JSONArray();
        for (BgLayer b : l)
            a.put(new JSONObject().put("kind", b.kind).put("file", b.file).put("name", b.name).put("volume", b.volume));
        return a;
    }

    private static void layersFrom(JSONArray a, List<BgLayer> out) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            String k = o.optString("kind", "");
            if (!validKind(k)) continue;
            BgLayer b = new BgLayer(k);
            b.file = o.optString("file", "");
            b.name = o.optString("name", "");
            b.volume = clamp((float) o.optDouble("volume", 0.5), 0f, 1f);
            out.add(b);
        }
    }

    private static VoiceSettings fromJson(JSONObject o) {
        VoiceSettings s = new VoiceSettings();
        s.preset = o.optString("preset", "original");
        s.pitch = clamp((float) o.optDouble("pitch", 0), -12f, 12f);
        s.speed = clamp((float) o.optDouble("speed", 1), 0.5f, 2f);
        s.volume = clamp((float) o.optDouble("volume", 1), 0f, 2f);
        s.reverse = o.optBoolean("reverse", false);
        fxFrom(o.optJSONArray("fx"), s.fx);
        layersFrom(o.optJSONArray("bg"), s.bg);
        return s;
    }

    static VoiceSettings get() { return cur; }

    static void edit(Edit e) {
        VoiceSettings n = cur.copy();
        e.apply(n);
        cur = n;
        save();
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }

    private static void save() {
        try {
            VoiceSettings s = cur;
            JSONObject o = new JSONObject();
            o.put("preset", s.preset).put("pitch", s.pitch).put("speed", s.speed).put("volume", s.volume)
                    .put("reverse", s.reverse).put("fx", fxJson(s.fx)).put("bg", layersJson(s.bg));
            sp.edit().putString("settings", o.toString()).apply();
        } catch (Exception e) {
            AppLog.err("save settings", e);
        }
    }

    static void addListener(Runnable r) { if (!listeners.contains(r)) listeners.add(r); }
    static void removeListener(Runnable r) { listeners.remove(r); }

    // ------------------------------------------------------------ готовые голоса
    static void applyPreset(String id) {
        Presets.Preset p = Presets.find(id);
        if (p != null) edit(s -> Presets.apply(p, s));
    }

    static Set<String> hiddenPresets() { return new HashSet<>(sp.getStringSet("hidden", new HashSet<>())); }

    static void hidePreset(String id) {
        Set<String> h = hiddenPresets();
        h.add(id);
        sp.edit().putStringSet("hidden", h).apply();
    }

    static void restorePresets() { sp.edit().remove("hidden").apply(); }

    // ------------------------------------------------------------- свои голоса
    static List<UserPreset> userPresets() {
        List<UserPreset> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("userPresets", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                UserPreset p = new UserPreset();
                p.name = o.getString("name");
                p.pitch = clamp((float) o.optDouble("pitch", 0), -12f, 12f);
                p.speed = clamp((float) o.optDouble("speed", 1), 0.5f, 2f);
                p.volume = clamp((float) o.optDouble("volume", 1), 0f, 2f);
                p.reverse = o.optBoolean("reverse", false);
                fxFrom(o.optJSONArray("fx"), p.fx);
                out.add(p);
            }
        } catch (Exception e) {
            AppLog.err("load user presets", e);
        }
        return out;
    }

    private static void saveUserPresets(List<UserPreset> l) {
        try {
            JSONArray a = new JSONArray();
            for (UserPreset p : l)
                a.put(new JSONObject().put("name", p.name).put("pitch", p.pitch).put("speed", p.speed)
                        .put("volume", p.volume).put("reverse", p.reverse).put("fx", fxJson(p.fx)));
            sp.edit().putString("userPresets", a.toString()).apply();
        } catch (Exception e) {
            AppLog.err("save user presets", e);
        }
    }

    /** Сохраняет текущие настройки голоса под именем (одноимённый пресет заменяется). */
    static void saveUserPreset(String name) {
        VoiceSettings s = cur;
        UserPreset p = new UserPreset();
        p.name = name; p.pitch = s.pitch; p.speed = s.speed; p.volume = s.volume; p.reverse = s.reverse;
        System.arraycopy(s.fx, 0, p.fx, 0, p.fx.length);
        List<UserPreset> l = userPresets();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).name.equals(name)) l.remove(i);
        l.add(p);
        saveUserPresets(l);
        edit(x -> x.preset = "user:" + name);
    }

    static void deleteUserPreset(String name) {
        List<UserPreset> l = userPresets();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).name.equals(name)) l.remove(i);
        saveUserPresets(l);
        if (("user:" + name).equals(cur.preset)) edit(x -> x.preset = "custom");
    }

    static void applyUserPreset(UserPreset p) {
        edit(s -> {
            s.preset = "user:" + p.name; s.pitch = p.pitch; s.speed = p.speed; s.volume = p.volume; s.reverse = p.reverse;
            System.arraycopy(p.fx, 0, s.fx, 0, s.fx.length);
        });
    }

    // -------------------------------------------------------------- фон: слои и пресеты
    static void addLayer(BgLayer b) { edit(s -> s.bg.add(b)); }
    static void removeLayer(int i) { edit(s -> { if (i >= 0 && i < s.bg.size()) s.bg.remove(i); }); }
    static void setLayerVolume(int i, float v) { edit(s -> { if (i >= 0 && i < s.bg.size()) s.bg.get(i).volume = v; }); }

    static List<BgPreset> bgPresets() {
        List<BgPreset> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("bgPresets", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                BgPreset p = new BgPreset();
                p.name = o.getString("name");
                layersFrom(o.optJSONArray("layers"), p.layers);
                out.add(p);
            }
        } catch (Exception e) {
            AppLog.err("load bg presets", e);
        }
        return out;
    }

    private static void saveBgPresets(List<BgPreset> l) {
        try {
            JSONArray a = new JSONArray();
            for (BgPreset p : l) a.put(new JSONObject().put("name", p.name).put("layers", layersJson(p.layers)));
            sp.edit().putString("bgPresets", a.toString()).apply();
        } catch (Exception e) {
            AppLog.err("save bg presets", e);
        }
    }

    static void saveBgPreset(String name) {
        BgPreset p = new BgPreset();
        p.name = name;
        for (BgLayer b : cur.bg) p.layers.add(b.copy());
        List<BgPreset> l = bgPresets();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).name.equals(name)) l.remove(i);
        l.add(p);
        saveBgPresets(l);
    }

    static void deleteBgPreset(String name) {
        List<BgPreset> l = bgPresets();
        for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).name.equals(name)) l.remove(i);
        saveBgPresets(l);
    }

    static void applyBgPreset(BgPreset p) {
        edit(s -> { s.bg.clear(); for (BgLayer b : p.layers) s.bg.add(b.copy()); });
    }

    // ------------------------------------------------------------- параметры приложения
    static int format() { return sp.getInt("format", FMT_M4A); }
    static void setFormat(int f) { sp.edit().putInt("format", f).apply(); }
    static boolean autoStart() { return sp.getBoolean("autoStart", true); }
    static void setAutoStart(boolean b) { sp.edit().putBoolean("autoStart", b).apply(); }
    static boolean autoShare() { return sp.getBoolean("autoShare", true); }
    static void setAutoShare(boolean b) { sp.edit().putBoolean("autoShare", b).apply(); }
    /** true — кнопка записи работает, пока её держат; false — нажал и говори, нажал ещё раз — стоп. */
    static boolean pttHold() { return sp.getBoolean("pttHold", false); }
    static void setPttHold(boolean b) { sp.edit().putBoolean("pttHold", b).apply(); }
    static boolean liveMode() { return sp.getBoolean("liveMode", true); }
    static void setLiveMode(boolean b) { sp.edit().putBoolean("liveMode", b).apply(); }
    static String lang() { return sp.getString("lang", ""); }
    static void setLang(String l) { sp.edit().putString("lang", l).apply(); }
    static String ttsEngine() { return sp.getString("ttsEngine", null); }
    static String ttsVoice() { return sp.getString("ttsVoice", null); }
    static void setTts(String engine, String voice) {
        sp.edit().putString("ttsEngine", engine).putString("ttsVoice", voice).apply();
    }
}
