package voice.changer.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.util.HashMap;
import java.util.Map;

/** Общая основа окон: язык приложения и удобные запросы результата/разрешений с обработчиками. */
public abstract class BaseActivity extends Activity {
    interface ResultHandler { void onResult(int resultCode, Intent data); }
    interface PermHandler { void onResult(String[] perms, int[] grants); }

    private final Map<Integer, ResultHandler> results = new HashMap<>();
    private final Map<Integer, PermHandler> perms = new HashMap<>();
    private int next = 1000;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    void startForResult(Intent i, ResultHandler h) {
        int code = next++;
        results.put(code, h);
        startActivityForResult(i, code);
    }

    void askPerms(String[] p, PermHandler h) {
        int code = next++;
        perms.put(code, h);
        requestPermissions(p, code);
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        ResultHandler h = results.remove(req);
        if (h != null) h.onResult(res, data);
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] g) {
        super.onRequestPermissionsResult(req, p, g);
        PermHandler h = perms.remove(req);
        if (h != null) h.onResult(p, g);
    }
}
