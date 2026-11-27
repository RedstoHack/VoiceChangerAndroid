package voice.changer.app;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Страница «Настройки». */
final class SettingsPage {
    private SettingsPage() {}

    static View build(MainActivity m) {
        LinearLayout page = Ui.v(m);
        page.setBackgroundColor(Ui.pageColor());
        page.addView(Ui.pageHeader(m, m.getString(R.string.set_title), v -> m.pop()));
        ScrollView sv = new ScrollView(m);
        LinearLayout col = Ui.v(m);

        // ---- формат результата
        LinearLayout c1 = Ui.card(m);
        c1.addView(Ui.text(m, m.getString(R.string.set_format), 14, Ui.TEXT2));
        List<TextView> chips = new ArrayList<>();
        List<Integer> fmts = Encoders.formats();
        for (int f : fmts) {
            chips.add(Ui.chip(m, Encoders.label(f), Store.format() == f, v -> {
                Store.setFormat(f);
                for (int k = 0; k < chips.size(); k++) Ui.setChip(chips.get(k), fmts.get(k) == f);
            }));
        }
        c1.addView(Ui.chipGrid(m, chips, 2), Ui.wrapMatch(m, 8));
        if (!Mp3Encoder.available()) c1.addView(Ui.text(m, m.getString(R.string.mp3_missing), 12, Ui.TEXT2), Ui.wrapMatch(m, 8));
        col.addView(c1);

        // ---- режим кнопки записи
        LinearLayout c2 = Ui.card(m);
        c2.addView(Ui.text(m, m.getString(R.string.set_ptt), 14, Ui.TEXT2));
        List<TextView> ptt = new ArrayList<>();
        ptt.add(Ui.chip(m, m.getString(R.string.ptt_tap), !Store.pttHold(), v -> {
            Store.setPttHold(false);
            Ui.setChip(ptt.get(0), true); Ui.setChip(ptt.get(1), false);
        }));
        ptt.add(Ui.chip(m, m.getString(R.string.ptt_hold), Store.pttHold(), v -> {
            Store.setPttHold(true);
            Ui.setChip(ptt.get(0), false); Ui.setChip(ptt.get(1), true);
        }));
        c2.addView(Ui.chipGrid(m, ptt, 2), Ui.wrapMatch(m, 8));
        col.addView(c2, Ui.wrapMatch(m, 12));

        // ---- язык
        LinearLayout c3 = Ui.card(m);
        c3.addView(Ui.text(m, m.getString(R.string.set_language), 14, Ui.TEXT2));
        final String[] codes = {"", "ru", "en"};
        final int[] names = {R.string.lang_system, R.string.lang_ru, R.string.lang_en};
        List<TextView> lang = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            final String code = codes[i];
            lang.add(Ui.chip(m, m.getString(names[i]), Store.lang().equals(code), v -> {
                if (Store.lang().equals(code)) return;
                Store.setLang(code);
                m.recreate();
            }));
        }
        c3.addView(Ui.chipGrid(m, lang, 3), Ui.wrapMatch(m, 8));
        col.addView(c3, Ui.wrapMatch(m, 12));

        // ---- переключатели
        LinearLayout c4 = Ui.card(m);
        Switch auto = new Switch(m);
        auto.setText(R.string.set_auto_start);
        auto.setTextColor(Ui.TEXT);
        auto.setChecked(Store.autoStart());
        auto.setOnCheckedChangeListener((b, on) -> Store.setAutoStart(on));
        c4.addView(auto);
        Switch share = new Switch(m);
        share.setText(R.string.set_auto_share);
        share.setTextColor(Ui.TEXT);
        share.setChecked(Store.autoShare());
        share.setOnCheckedChangeListener((b, on) -> Store.setAutoShare(on));
        c4.addView(share, Ui.wrapMatch(m, 16));
        col.addView(c4, Ui.wrapMatch(m, 12));

        col.addView(Ui.navRow(m, m.getString(R.string.theme), m.getString(R.string.theme_sub),
                v -> m.push(ThemePage.build(m), null)), Ui.wrapMatch(m, 12));
        col.addView(Ui.navRow(m, m.getString(R.string.layout), m.getString(R.string.layout_sub),
                v -> m.push(LayoutPage.build(m), null)), Ui.wrapMatch(m, 8));
        col.addView(Ui.navRow(m, m.getString(R.string.log), m.getString(R.string.log_sub),
                v -> m.push(LogPage.build(m), null)), Ui.wrapMatch(m, 8));

        col.addView(Ui.text(m, m.getString(R.string.set_version), 13, Ui.TEXT2), Ui.wrapMatch(m, 18));

        sv.addView(col);
        page.addView(sv, Ui.lp(-1, 0, 1));
        int mg = Ui.dp(m, 16);
        ((LinearLayout.LayoutParams) sv.getLayoutParams()).setMargins(mg, 0, mg, mg);
        return page;
    }
}
