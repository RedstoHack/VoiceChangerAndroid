package voice.changer.app;

import android.content.Context;

import voice.changer.app.dsp.OfflineRenderer;
import voice.changer.app.dsp.Pcm;

final class Errors {
    private Errors() {}

    /** Понятный текст ошибки или null, если это просто отмена. */
    static String text(Context c, Throwable e) {
        AppLog.err("operation failed", e);
        if (e instanceof OfflineRenderer.Cancelled) return null;
        if (e instanceof Pcm.TooLongException) return c.getString(R.string.err_too_long);
        String m = e.getMessage();
        if (m == null || m.length() > 120) m = c.getString(R.string.err_decode);
        return c.getString(R.string.error_fmt, m);
    }
}
