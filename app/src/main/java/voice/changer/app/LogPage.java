package voice.changer.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Журнал работы — для разбора проблем без компьютера (можно скопировать и прислать). */
final class LogPage {
    private LogPage() {}

    static View build(MainActivity m) {
        LinearLayout page = Ui.v(m);
        page.setBackgroundColor(Ui.pageColor());
        page.addView(Ui.pageHeader(m, m.getString(R.string.log), v -> m.pop()));
        TextView tv = Ui.text(m, AppLog.dump(), 12, Ui.TEXT);
        tv.setTextIsSelectable(true);
        ScrollView sv = new ScrollView(m);
        sv.addView(tv);
        LinearLayout.LayoutParams lp = Ui.lp(-1, 0, 1);
        int mg = Ui.dp(m, 16);
        lp.setMargins(mg, 0, mg, 0);
        page.addView(sv, lp);

        LinearLayout row = Ui.h(m);
        row.addView(Ui.button(m, m.getString(R.string.log_refresh), false, v -> tv.setText(AppLog.dump())), Ui.lp(0, -2, 1));
        LinearLayout.LayoutParams l2 = Ui.lp(0, -2, 1);
        l2.leftMargin = Ui.dp(m, 8);
        row.addView(Ui.button(m, m.getString(R.string.log_copy), false, v -> {
            ClipboardManager cm = (ClipboardManager) m.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("log", AppLog.dump()));
            Toast.makeText(m, R.string.log_copied, Toast.LENGTH_SHORT).show();
        }), l2);
        LinearLayout.LayoutParams l3 = Ui.lp(0, -2, 1);
        l3.leftMargin = Ui.dp(m, 8);
        row.addView(Ui.button(m, m.getString(R.string.log_clear), false, v -> { AppLog.clear(); tv.setText(""); }), l3);
        LinearLayout.LayoutParams rl = Ui.lp(-1, -2, 0);
        rl.setMargins(mg, mg / 2, mg, mg);
        page.addView(row, rl);
        return page;
    }
}
