package voice.changer.app;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Журнал в памяти: показывается в Настройки → Журнал, чтобы разбирать сбои без компьютера. */
final class AppLog {
    private AppLog() {}
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    static synchronized void add(String msg) {
        Log.i("VoiceChanger", msg);
        lines.addLast(fmt.format(new Date()) + "  " + msg);
        while (lines.size() > 300) lines.removeFirst();
    }

    static void err(String where, Throwable t) {
        add(where + ": " + t.getClass().getSimpleName() + (t.getMessage() == null ? "" : " — " + t.getMessage()));
    }

    static synchronized String dump() {
        StringBuilder b = new StringBuilder();
        for (String l : lines) b.append(l).append('\n');
        return b.toString();
    }

    static synchronized void clear() { lines.clear(); }
}
