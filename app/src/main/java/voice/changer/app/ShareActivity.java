package voice.changer.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import voice.changer.app.dsp.Pcm;
import voice.changer.app.dsp.VoiceSettings;

/**
 * Окно, которое открывается из контекстного меню выделенного текста («VoiceChanger»),
 * из «Поделиться» (текст / аудио / видео) и при открытии аудиофайла этим приложением.
 */
public class ShareActivity extends BaseActivity {
    private String text;
    private Uri audio;
    private TextView action, status;
    private ProgressBar bar;
    private VoiceRow voice;
    private ResultView result;
    private boolean busy;
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final Runnable listener = () -> { if (voice != null) voice.refresh(); };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Store.init(this);
        Theme.load(this);
        parse(getIntent());

        LinearLayout col = Ui.v(this);
        int pad = Ui.dp(this, 16);
        col.setPadding(pad, pad, pad, pad);
        col.setBackgroundColor(Ui.BG | 0xFF000000);

        if (text == null && audio == null) {
            col.addView(Ui.text(this, getString(R.string.share_unsupported), 16, Ui.TEXT));
            setContentView(col);
            return;
        }
        String label = text != null ? getString(R.string.share_text_label)
                : getString(R.string.share_audio_label, Files.displayName(this, audio));
        col.addView(Ui.text(this, label, 13, Ui.TEXT2));
        if (text != null) {
            TextView prev = Ui.text(this, text, 15, Ui.TEXT);
            prev.setMaxLines(4);
            prev.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(prev, Ui.wrapMatch(this, 4));
        }
        voice = new VoiceRow(this);
        col.addView(voice.view, Ui.wrapMatch(this, 14));

        action = Ui.button(this, getString(R.string.share_start), true, v -> onAction());
        col.addView(action, Ui.wrapMatch(this, 16));
        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setVisibility(View.GONE);
        col.addView(bar, Ui.wrapMatch(this, 8));
        status = Ui.text(this, "", 13, Ui.TEXT2);
        col.addView(status, Ui.wrapMatch(this, 4));
        result = new ResultView(this);
        col.addView(result, Ui.wrapMatch(this, 12));
        col.addView(Ui.button(this, getString(R.string.close), false, v -> finish()), Ui.wrapMatch(this, 12));
        setContentView(col);

        if (b == null && Store.autoStart()) onAction();
    }

    @Override protected void onStart() {
        super.onStart();
        if (voice != null) { Store.addListener(listener); voice.refresh(); }
    }

    @Override protected void onStop() {
        Store.removeListener(listener);
        if (result != null) result.pausePlayback();
        super.onStop();
    }

    @SuppressWarnings("deprecation")
    private void parse(Intent i) {
        String a = i.getAction();
        String type = i.getType();
        if (Intent.ACTION_PROCESS_TEXT.equals(a)) {
            CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (t != null && t.toString().trim().length() > 0) text = t.toString().trim();
        } else if (Intent.ACTION_SEND.equals(a)) {
            if (type != null && type.startsWith("text/")) {
                CharSequence t = i.getCharSequenceExtra(Intent.EXTRA_TEXT);
                if (t != null && t.toString().trim().length() > 0) text = t.toString().trim();
            } else {
                audio = Build.VERSION.SDK_INT >= 33 ? i.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class)
                        : (Uri) i.getParcelableExtra(Intent.EXTRA_STREAM);
            }
        } else if (Intent.ACTION_VIEW.equals(a)) {
            audio = i.getData();
        }
    }

    private void onAction() {
        if (busy) { cancel.set(true); return; }
        cancel.set(false);
        result.hide();
        setBusy(true);
        final VoiceSettings s = Store.get();
        final String engine = Store.ttsEngine(), vname = Store.ttsVoice();
        Jobs.IO.execute(() -> {
            try {
                Pcm v;
                if (text != null) {
                    v = Tts.synthesize(this, text, engine, vname, f -> !cancel.get());
                } else {
                    File in = Files.tmp(this, "share_in.dat");
                    Files.copyUri(this, audio, in);
                    try { v = Pipeline.decode(in.getPath(), 1, Pipeline.MAX_VOICE_FRAMES); } finally { in.delete(); }
                }
                Rendered r = Pipeline.render(this, v, s, f -> {
                    final int pct = Math.round(f * 100);
                    Jobs.ui(() -> { bar.setProgress(pct); status.setText(getString(R.string.progress_pct, pct)); });
                    return !cancel.get();
                });
                Jobs.ui(() -> {
                    setBusy(false);
                    status.setText("");
                    result.show(r, !Store.autoShare());
                    if (Store.autoShare()) result.shareNow();
                });
            } catch (Exception e) {
                String msg = Errors.text(this, e);
                Jobs.ui(() -> { setBusy(false); status.setText(msg == null ? "" : msg); });
            }
        });
    }

    private void setBusy(boolean b) {
        busy = b;
        voice.setEnabled(!b);
        bar.setVisibility(b ? View.VISIBLE : View.GONE);
        bar.setProgress(0);
        action.setText(b ? R.string.cancel : R.string.share_start);
        Ui.setButtonColor(action, !b);
        if (b) status.setText(text != null ? R.string.tts_making : R.string.file_processing);
    }

    @Override protected void onDestroy() {
        cancel.set(true);
        if (result != null) result.release();
        super.onDestroy();
    }
}
