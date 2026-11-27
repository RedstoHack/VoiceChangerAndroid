package voice.changer.app;

import android.speech.tts.Voice;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import voice.changer.app.dsp.Pcm;
import voice.changer.app.dsp.VoiceSettings;

/** Вкладка «Текст»: озвучка системным движком + выбранный голос и эффекты. */
final class TextScreen implements Screen {
    private final MainActivity m;
    private final ScrollView root;
    private final EditText input;
    private final VoiceRow voice;
    private final TextView engineVal, voiceVal, action, status, noEngine, openSettings;
    private final ProgressBar bar;
    private final ResultView result;
    private final Runnable listener = this::refresh;
    private boolean busy;
    private AtomicBoolean cancel = new AtomicBoolean();

    TextScreen(MainActivity m) {
        this.m = m;
        root = new ScrollView(m);
        LinearLayout col = Ui.v(m);
        int pad = Ui.dp(m, 16);
        col.setPadding(pad, pad / 2, pad, pad);
        root.addView(col);

        java.util.LinkedHashMap<String, View> blocks = LayoutPrefs.blocks();
        voice = new VoiceRow(m);
        blocks.put("voice", voice.view);

        LinearLayout inBox = Ui.v(m);
        input = new EditText(m);
        input.setHint(R.string.text_hint);
        input.setTextColor(Ui.TEXT);
        input.setHintTextColor(Ui.TEXT2);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(6);
        input.setMaxLines(9);          // дальше поле прокручивается внутри себя
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setVerticalScrollBarEnabled(true);
        input.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        input.setBackground(Ui.rect(m, Ui.cardColor(), 16));
        input.setPadding(pad, pad, pad, pad);
        // чтобы длинный текст прокручивался пальцем, а не уезжала вся страница
        input.setOnTouchListener((v, e) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            int a = e.getAction() & MotionEvent.ACTION_MASK;
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) v.getParent().requestDisallowInterceptTouchEvent(false);
            return false;
        });
        inBox.addView(input);
        // «Очистить» — обычная кнопка: цвет текста берётся из оформления
        inBox.addView(Ui.button(m, m.getString(R.string.text_clear), false, v -> input.setText("")), Ui.wrapMatch(m, 8));
        blocks.put("input", inBox);

        LinearLayout tts = Ui.card(m);
        engineVal = row(tts, R.string.tts_engine, v -> pickEngine());
        voiceVal = row(tts, R.string.tts_voice, v -> pickVoice());
        noEngine = Ui.text(m, m.getString(R.string.tts_none), 13, Ui.ERR);
        tts.addView(noEngine, Ui.wrapMatch(m, 8));
        openSettings = Ui.button(m, m.getString(R.string.tts_open_settings), false, v -> {
            try { m.startActivity(new android.content.Intent("com.android.settings.TTS_SETTINGS")); }
            catch (Exception e) { Toast.makeText(m, R.string.tts_init_failed, Toast.LENGTH_SHORT).show(); }
        });
        tts.addView(openSettings, Ui.wrapMatch(m, 8));
        blocks.put("tts", tts);

        LinearLayout actBox = Ui.v(m);
        action = Ui.button(m, m.getString(R.string.text_make), true, v -> make());
        actBox.addView(action);
        bar = new ProgressBar(m, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        bar.setVisibility(View.GONE);
        actBox.addView(bar, Ui.wrapMatch(m, 8));
        status = Ui.text(m, "", 13, Ui.TEXT2);
        actBox.addView(status, Ui.wrapMatch(m, 4));
        blocks.put("action", actBox);

        result = new ResultView(m);
        blocks.put("result", result);
        LayoutPrefs.fill(m, col, "text", blocks);
        refresh();
    }

    private TextView row(LinearLayout parent, int labelRes, View.OnClickListener l) {
        LinearLayout r = Ui.h(m);
        r.setPadding(0, Ui.dp(m, 8), 0, Ui.dp(m, 8));
        r.addView(Ui.text(m, m.getString(labelRes), 15, Ui.TEXT), Ui.lp(0, -2, 1));
        TextView val = Ui.text(m, "", 15, Ui.LINK);
        val.setSingleLine(true);
        val.setEllipsize(android.text.TextUtils.TruncateAt.END);
        val.setMaxWidth(Ui.dp(m, 200));
        r.addView(val);
        r.setOnClickListener(l);
        parent.addView(r);
        return val;
    }

    private void refresh() {
        voice.refresh();
        List<Tts.EngineInfo> eng = Tts.engines(m);
        String cur = Store.ttsEngine();
        String label = m.getString(R.string.tts_default);
        for (Tts.EngineInfo e : eng) if (e.pkg.equals(cur)) label = e.label;
        engineVal.setText(label + " ›");
        String v = Store.ttsVoice();
        voiceVal.setText((v == null ? m.getString(R.string.tts_default) : v) + " ›");
        int vis = eng.isEmpty() ? View.VISIBLE : View.GONE;
        noEngine.setVisibility(vis);
        openSettings.setVisibility(vis);
    }

    private void pickEngine() {
        if (busy) return;
        List<Tts.EngineInfo> eng = Tts.engines(m);
        List<String> names = new ArrayList<>();
        names.add(m.getString(R.string.tts_default));
        for (Tts.EngineInfo e : eng) names.add(e.label);
        Dialogs.list(m, m.getString(R.string.tts_engine), names.toArray(new String[0]), which -> {
            Store.setTts(which == 0 ? null : eng.get(which - 1).pkg, null);
            refresh();
            Tts.warmUp(m, Store.ttsEngine());
        });
    }

    private void pickVoice() {
        if (busy) return;
        Toast.makeText(m, R.string.tts_loading_voices, Toast.LENGTH_SHORT).show();
        final String engine = Store.ttsEngine();
        Jobs.IO.execute(() -> {
            try {
                List<Voice> vs = Tts.voices(m, engine);
                Jobs.ui(() -> {
                    if (vs.isEmpty()) { Toast.makeText(m, R.string.tts_no_voices, Toast.LENGTH_LONG).show(); return; }
                    String[] names = new String[vs.size() + 1];
                    names[0] = m.getString(R.string.tts_default);
                    for (int i = 0; i < vs.size(); i++) names[i + 1] = Tts.voiceLabel(vs.get(i));
                    Dialogs.list(m, m.getString(R.string.tts_voice), names, which -> {
                        Store.setTts(engine, which == 0 ? null : vs.get(which - 1).getName());
                        refresh();
                    });
                });
            } catch (Exception e) {
                AppLog.err("voices", e);
                Jobs.ui(() -> Toast.makeText(m, R.string.tts_init_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void make() {
        if (busy) { cancel.set(true); return; }
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) { Toast.makeText(m, R.string.tts_empty, Toast.LENGTH_SHORT).show(); return; }
        final VoiceSettings s = Store.get();
        final String engine = Store.ttsEngine(), vname = Store.ttsVoice();
        cancel = new AtomicBoolean();
        final AtomicBoolean c = cancel;
        result.hide();
        status.setText(R.string.tts_making);
        setBusy(true);
        Jobs.IO.execute(() -> {
            try {
                Pcm v = Tts.synthesize(m, text, engine, vname, f -> !c.get());
                Rendered r = Pipeline.render(m, v, s, f -> {
                    final int pct = Math.round(f * 100);
                    Jobs.ui(() -> { bar.setProgress(pct); status.setText(m.getString(R.string.progress_pct, pct)); });
                    return !c.get();
                });
                Jobs.ui(() -> { setBusy(false); status.setText(""); result.show(r, true); });
            } catch (Exception e) {
                String msg = Errors.text(m, e);
                Jobs.ui(() -> { setBusy(false); status.setText(msg == null ? "" : msg); });
            }
        });
    }

    private void setBusy(boolean b) {
        busy = b;
        voice.setEnabled(!b);
        bar.setVisibility(b ? View.VISIBLE : View.GONE);
        bar.setProgress(0);
        action.setText(b ? R.string.cancel : R.string.text_make);
        Ui.setButtonColor(action, !b);
    }

    @Override public View view() { return root; }
    @Override public void onShow() {
        Store.addListener(listener);
        refresh();
        Tts.warmUp(m, Store.ttsEngine());
    }
    @Override public void onHide() { Store.removeListener(listener); result.pausePlayback(); }
}
