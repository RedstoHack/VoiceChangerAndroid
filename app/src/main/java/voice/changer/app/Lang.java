package voice.changer.app;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** Язык приложения: системный, русский или английский. */
final class Lang {
    private Lang() {}

    static Context wrap(Context base) {
        String l = base.getSharedPreferences("voice", Context.MODE_PRIVATE).getString("lang", "");
        if (l.isEmpty()) return base;
        Configuration c = new Configuration(base.getResources().getConfiguration());
        c.setLocale(new Locale(l));
        return base.createConfigurationContext(c);
    }

    static boolean russian(Context c) {
        return "ru".equals(c.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }
}
