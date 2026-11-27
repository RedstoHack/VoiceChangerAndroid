package voice.changer.app;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Set;

import voice.changer.app.dsp.Presets;

/** Окно выбора голоса: свои и готовые; сохранить текущий, удалить ненужные, вернуть удалённые. */
final class VoicePicker {
    private VoicePicker() {}

    static void show(Context c, Runnable changed) {
        Dialogs.Sheet s = Dialogs.sheet(c, c.getString(R.string.pick_voice), true);
        build(c, s, changed);
        Dialogs.show(c, s);
    }

    private static void build(Context c, Dialogs.Sheet s, Runnable changed) {
        LinearLayout col = s.body;
        col.removeAllViews();
        String cur = Store.get().preset;

        col.addView(Ui.button(c, c.getString(R.string.save_current_voice), false, v ->
                Dialogs.prompt(c, c.getString(R.string.save_current_voice), "", name -> {
                    Store.saveUserPreset(name);
                    changed.run();
                    build(c, s, changed);
                })));

        List<Store.UserPreset> mine = Store.userPresets();
        if (!mine.isEmpty()) {
            col.addView(section(c, R.string.my_voices), Ui.wrapMatch(c, 14));
            for (Store.UserPreset p : mine) {
                final String id = "user:" + p.name;
                col.addView(row(c, p.name, id.equals(cur),
                        v -> { Store.applyUserPreset(p); s.dismiss(); changed.run(); },
                        v -> Dialogs.confirm(c, c.getString(R.string.delete_voice_q, p.name), () -> {
                            Store.deleteUserPreset(p.name);
                            changed.run();
                            build(c, s, changed);
                        })));
            }
        }

        col.addView(section(c, R.string.sec_presets), Ui.wrapMatch(c, 14));
        Set<String> hidden = Store.hiddenPresets();
        for (Presets.Preset p : Presets.ALL) {
            if (hidden.contains(p.id)) continue;
            String name = p.name(Lang.russian(c));
            col.addView(row(c, name, p.id.equals(cur),
                    v -> { Store.applyPreset(p.id); s.dismiss(); changed.run(); },
                    v -> Dialogs.confirm(c, c.getString(R.string.delete_voice_q, name), () -> {
                        Store.hidePreset(p.id);
                        build(c, s, changed);
                    })));
        }
        if (!hidden.isEmpty())
            col.addView(Ui.button(c, c.getString(R.string.restore_voices), false, v -> {
                Store.restorePresets();
                build(c, s, changed);
            }), Ui.wrapMatch(c, 14));
    }

    private static TextView section(Context c, int res) {
        return Ui.text(c, c.getString(res), 13, Ui.TEXT2);
    }

    private static LinearLayout row(Context c, String name, boolean selected, android.view.View.OnClickListener pick, android.view.View.OnClickListener del) {
        LinearLayout r = Ui.h(c);
        TextView t = Ui.text(c, name, 16, selected ? Ui.LINK : Ui.TEXT);
        t.setPadding(Ui.dp(c, 8), Ui.dp(c, 13), Ui.dp(c, 8), Ui.dp(c, 13));
        t.setClickable(true);
        t.setOnClickListener(pick);
        r.addView(t, Ui.lp(0, -2, 1));
        TextView x = Ui.text(c, "✕", 18, Ui.TEXT2);
        x.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        x.setClickable(true);
        x.setOnClickListener(del);
        r.addView(x);
        return r;
    }
}
