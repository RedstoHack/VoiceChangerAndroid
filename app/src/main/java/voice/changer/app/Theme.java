package voice.changer.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

/** Оформление: цвета, фон (картинка / GIF / видео) и картинка для кнопок. */
final class Theme {
    private Theme() {}

    static final int D_BG = 0xFF121418, D_SURFACE = 0xFF1B2027, D_SURFACE2 = 0xFF29313B,
            D_ACCENT = 0xFF7C4DFF, D_TEXT = 0xFFE8EAED, D_TEXT2 = 0xFF9AA0A6, D_LINK = 0xFFB39DFF;

    /** none | image | gif | video */
    static String bgType = "none";
    static int dim = 40;            // затемнение фона, %
    static boolean btnImage;
    static float scale = 1f;        // масштаб текста интерфейса
    static float round = 1f;        // множитель скругления углов
    static int micDp = 270;         // размер кнопки записи
    /** Были изменения оформления или расположения — окно перерисуется, когда закроются страницы настроек. */
    static boolean dirty;
    private static Bitmap btnBmp;

    static void load(Context c) {
        SharedPreferences sp = Store.prefs();
        Ui.BG = sp.getInt("c_bg", D_BG);
        Ui.SURFACE = sp.getInt("c_surface", D_SURFACE);
        Ui.SURFACE2 = sp.getInt("c_surface2", D_SURFACE2);
        Ui.ACCENT = sp.getInt("c_accent", D_ACCENT);
        Ui.TEXT = sp.getInt("c_text", D_TEXT);
        Ui.TEXT2 = sp.getInt("c_text2", D_TEXT2);
        Ui.LINK = sp.getInt("c_link", D_LINK);
        scale = sp.getFloat("scale", 1f);
        round = sp.getFloat("round", 1f);
        micDp = sp.getInt("micDp", 270);
        bgType = sp.getString("bgType", "none");
        if (!bgFile(c).exists()) bgType = "none";
        dim = sp.getInt("bgDim", 40);
        btnImage = sp.getBoolean("btnImg", false) && btnFile(c).exists();
        btnBmp = null;
    }

    static void setColor(String key, int color) { Store.prefs().edit().putInt(key, color).apply(); }

    static void setScale(float v) { scale = v; Store.prefs().edit().putFloat("scale", v).apply(); }
    static void setRound(float v) { round = v; Store.prefs().edit().putFloat("round", v).apply(); }
    static void setMic(int dp) { micDp = dp; Store.prefs().edit().putInt("micDp", dp).apply(); }

    static File headerFile(Context c) { return new File(c.getFilesDir(), "header_icon.png"); }

    static void setDim(int d) { dim = d; Store.prefs().edit().putInt("bgDim", d).apply(); }

    static void setBg(Context c, String type) {
        bgType = type;
        Store.prefs().edit().putString("bgType", type).apply();
        if ("none".equals(type)) bgFile(c).delete();
    }

    static void setBtnImage(Context c, boolean on) {
        btnImage = on;
        btnBmp = null;
        Store.prefs().edit().putBoolean("btnImg", on).apply();
        if (!on) btnFile(c).delete();
    }

    static void reset(Context c) {
        SharedPreferences.Editor e = Store.prefs().edit();
        for (String k : new String[]{"c_bg", "c_surface", "c_surface2", "c_accent", "c_text", "c_text2", "c_link", "bgType", "bgDim", "btnImg", "scale", "round", "micDp"}) e.remove(k);
        e.apply();
        bgFile(c).delete();
        btnFile(c).delete();
        headerFile(c).delete();
        load(c);
    }

    static boolean hasMedia() { return !"none".equals(bgType); }

    static File bgFile(Context c) { return new File(c.getFilesDir(), "theme_bg.dat"); }
    static File btnFile(Context c) { return new File(c.getFilesDir(), "theme_btn.dat"); }

    /** Картинка для кнопок (уменьшенная), либо null. */
    static Bitmap btnBitmap(Context c) {
        if (!btnImage) return null;
        if (btnBmp == null) btnBmp = decode(btnFile(c), 720);
        return btnBmp;
    }

    /** Декодирует картинку (для GIF — первый кадр) с ограничением размера. */
    static Bitmap decode(File f, int maxSide) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int s = 1;
            while (o.outWidth / s > maxSide * 2 || o.outHeight / s > maxSide * 2) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            return BitmapFactory.decodeFile(f.getPath(), o2);
        } catch (Throwable t) {
            AppLog.err("decode image", t);
            return null;
        }
    }
}
