package voice.changer.app;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;

import java.util.Locale;

import voice.changer.app.dsp.VoiceSettings;

/** Вкладка «Голос»: тонкая настройка текущего голоса, эффекты, фон, сохранение своего голоса. */
final class VoiceScreen implements Screen {
    private final MainActivity m;
    private final ScrollView root;
    private final VoiceRow voice;
    private final Ui.Slider pitch, speed, volume;
    private final Switch reverse;
    private boolean updating;
    private final Runnable listener = this::refresh;

    VoiceScreen(MainActivity m) {
        this.m = m;
        root = new ScrollView(m);
        LinearLayout col = Ui.v(m);
        int pad = Ui.dp(m, 16);
        col.setPadding(pad, pad / 2, pad, pad);
        root.addView(col);

        java.util.LinkedHashMap<String, View> blocks = LayoutPrefs.blocks();
        voice = new VoiceRow(m);
        blocks.put("voice", voice.view);

        LinearLayout card = Ui.card(m);
        pitch = new Ui.Slider(m, m.getString(R.string.pitch), -12f, 12f, 0.5f,
                v -> String.format(Locale.US, m.getString(R.string.pitch_fmt), v), v -> Store.edit(s -> { s.pitch = v; s.preset = "custom"; }));
        speed = new Ui.Slider(m, m.getString(R.string.speed), 0.5f, 2f, 0.05f,
                v -> String.format(Locale.US, m.getString(R.string.speed_fmt), v), v -> Store.edit(s -> { s.speed = v; s.preset = "custom"; }));
        volume = new Ui.Slider(m, m.getString(R.string.volume), 0f, 2f, 0.05f,
                v -> String.format(Locale.US, m.getString(R.string.percent_fmt), Math.round(v * 100)), v -> Store.edit(s -> s.volume = v));
        card.addView(pitch);
        card.addView(speed, Ui.wrapMatch(m, 12));
        card.addView(volume, Ui.wrapMatch(m, 12));
        reverse = new Switch(m);
        reverse.setText(R.string.reverse);
        reverse.setTextColor(Ui.TEXT);
        reverse.setOnCheckedChangeListener((b, on) -> {
            if (!updating) Store.edit(s -> { s.reverse = on; s.preset = "custom"; });
        });
        card.addView(reverse, Ui.wrapMatch(m, 14));
        card.addView(Ui.text(m, m.getString(R.string.live_speed_note), 12, Ui.TEXT2), Ui.wrapMatch(m, 8));
        blocks.put("tune", card);

        blocks.put("save", Ui.button(m, m.getString(R.string.save_current_voice), false, v ->
                Dialogs.prompt(m, m.getString(R.string.save_current_voice), "", name -> {
                    Store.saveUserPreset(name);
                    android.widget.Toast.makeText(m, R.string.saved, android.widget.Toast.LENGTH_SHORT).show();
                })));
        blocks.put("effects", Ui.navRow(m, m.getString(R.string.effects), m.getString(R.string.effects_sub),
                v -> m.push(new EffectsPage(m).view, null)));
        blocks.put("bg", Ui.navRow(m, m.getString(R.string.background), m.getString(R.string.background_sub),
                v -> m.push(new BackgroundPage(m).view, null)));
        LayoutPrefs.fill(m, col, "voice", blocks);
        refresh();
    }

    private void refresh() {
        VoiceSettings s = Store.get();
        voice.refresh();
        updating = true;
        pitch.set(s.pitch);
        speed.set(s.speed);
        volume.set(s.volume);
        reverse.setChecked(s.reverse);
        updating = false;
    }

    @Override public View view() { return root; }
    @Override public void onShow() { Store.addListener(listener); refresh(); }
    @Override public void onHide() { Store.removeListener(listener); }
}
