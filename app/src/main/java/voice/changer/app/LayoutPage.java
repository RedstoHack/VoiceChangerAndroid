package voice.changer.app;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Редактор расположения: порядок и видимость блоков на каждой вкладке, порядок вкладок. */
final class LayoutPage {
    private LayoutPage() {}

    static View build(MainActivity m) {
        LinearLayout page = Ui.v(m);
        page.setBackgroundColor(Ui.pageColor());
        page.addView(Ui.pageHeader(m, m.getString(R.string.layout), v -> m.pop()));
        ScrollView sv = new ScrollView(m);
        LinearLayout col = Ui.v(m);
        sv.addView(col);
        page.addView(sv, Ui.lp(-1, 0, 1));
        int mg = Ui.dp(m, 16);
        ((LinearLayout.LayoutParams) sv.getLayoutParams()).setMargins(mg, 0, mg, mg);
        rebuild(m, col);
        return page;
    }

    private static void rebuild(MainActivity m, LinearLayout col) {
        col.removeAllViews();
        col.addView(Ui.text(m, m.getString(R.string.layout_note), 13, Ui.TEXT2));

        LinearLayout tabs = Ui.card(m);
        tabs.addView(Ui.text(m, m.getString(R.string.layout_tabs), 15, Ui.TEXT));
        for (int id : LayoutPrefs.tabOrder())
            tabs.addView(row(m, col, "tabs", String.valueOf(id), m.getString(Labels.tab(id)), false), Ui.wrapMatch(m, 4));
        col.addView(tabs, Ui.wrapMatch(m, 12));

        String[] screens = {"live", "file", "text", "voice"};
        int[] names = {R.string.tab_live, R.string.tab_file, R.string.tab_text, R.string.tab_voice};
        for (int i = 0; i < screens.length; i++) {
            LinearLayout card = Ui.card(m);
            card.addView(Ui.text(m, m.getString(names[i]), 15, Ui.TEXT));
            for (String id : LayoutPrefs.order(screens[i]))
                card.addView(row(m, col, screens[i], id, m.getString(Labels.block(id)), true), Ui.wrapMatch(m, 4));
            col.addView(card, Ui.wrapMatch(m, 12));
        }

        col.addView(Ui.button(m, m.getString(R.string.layout_reset), false, v -> {
            LayoutPrefs.reset();
            rebuild(m, col);
        }), Ui.wrapMatch(m, 14));
    }

    private static LinearLayout row(MainActivity m, LinearLayout col, String screen, String id, String label, boolean canHide) {
        LinearLayout r = Ui.h(m);
        boolean hidden = canHide && LayoutPrefs.hidden(screen, id);
        TextView t = Ui.text(m, label, 15, hidden ? Ui.TEXT2 : Ui.TEXT);
        t.setPadding(0, Ui.dp(m, 10), 0, Ui.dp(m, 10));
        r.addView(t, Ui.lp(0, -2, 1));
        r.addView(arrow(m, "↑", () -> { LayoutPrefs.move(screen, id, -1); rebuild(m, col); }));
        r.addView(arrow(m, "↓", () -> { LayoutPrefs.move(screen, id, 1); rebuild(m, col); }));
        if (canHide)
            r.addView(arrow(m, hidden ? "○" : "●", () -> { LayoutPrefs.setHidden(screen, id, !hidden); rebuild(m, col); }));
        return r;
    }

    private static TextView arrow(MainActivity m, String s, Runnable act) {
        TextView a = Ui.text(m, s, 20, Ui.LINK);
        a.setPadding(Ui.dp(m, 14), Ui.dp(m, 6), Ui.dp(m, 14), Ui.dp(m, 6));
        a.setClickable(true);
        a.setOnClickListener(v -> act.run());
        return a;
    }
}
