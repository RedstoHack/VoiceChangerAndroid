package voice.changer.app;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.List;

import voice.changer.app.dsp.FxType;

/** Страница «Эффекты»: каждый эффект отдельным ползунком. */
final class EffectsPage {
    final View view;

    EffectsPage(MainActivity m) {
        LinearLayout page = Ui.v(m);
        page.setBackgroundColor(Ui.pageColor());
        page.addView(Ui.pageHeader(m, m.getString(R.string.effects), v -> m.pop()));
        ScrollView sv = new ScrollView(m);
        LinearLayout col = Ui.card(m);
        android.widget.Switch whole = new android.widget.Switch(m);
        whole.setText(R.string.reverse);
        whole.setTextColor(Ui.TEXT);
        whole.setChecked(Store.get().reverse);
        whole.setOnCheckedChangeListener((b, on) -> Store.edit(s -> { s.reverse = on; s.preset = "custom"; }));
        col.addView(whole);
        List<Ui.Slider> sliders = new ArrayList<>();
        for (FxType t : FxType.values()) {
            final int idx = t.ordinal();
            Ui.Slider sl = new Ui.Slider(m, m.getString(Labels.fx(t)), 0f, 1f, 0.05f,
                    t == FxType.REVERSE
                            ? (Ui.Slider.Fmt) v -> v <= 0.001f ? "—" : String.format(java.util.Locale.US, m.getString(R.string.reverse_fmt), 0.1f + 1.4f * v)
                            : (Ui.Slider.Fmt) v -> v <= 0.001f ? "—" : Math.round(v * 100) + "%",
                    v -> Store.edit(s -> { s.fx[idx] = v; s.preset = "custom"; }));
            sl.set(Store.get().fx[idx]);
            sliders.add(sl);
            col.addView(sl, Ui.wrapMatch(m, 14));
        }
        col.addView(Ui.button(m, m.getString(R.string.reset_effects), false, v -> {
            Store.edit(s -> { java.util.Arrays.fill(s.fx, 0f); s.preset = "custom"; });
            for (Ui.Slider s : sliders) s.set(0f);
        }), Ui.wrapMatch(m, 20));
        sv.addView(col);
        page.addView(sv, Ui.lp(-1, 0, 1));
        int mg = Ui.dp(m, 16);
        ((LinearLayout.LayoutParams) sv.getLayoutParams()).setMargins(mg, 0, mg, mg);
        view = page;
    }
}
