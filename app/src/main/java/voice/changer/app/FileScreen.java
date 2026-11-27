package voice.changer.app;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import voice.changer.app.dsp.Pcm;
import voice.changer.app.dsp.VoiceSettings;

/** Вкладка «Файл»: изменить голос в готовой записи (аудио или видео). */
final class FileScreen implements Screen {
    private final MainActivity m;
    private final ScrollView root;
    private final VoiceRow voice;
    private final TextView fileInfo, action, status;
    private final ProgressBar bar;
    private final ResultView result;
    private final Runnable listener = this::refresh;
    private File input;
    private boolean busy;
    private AtomicBoolean cancel = new AtomicBoolean();

    FileScreen(MainActivity m) {
        this.m = m;
        root = new ScrollView(m);
        LinearLayout col = Ui.v(m);
        int pad = Ui.dp(m, 16);
        col.setPadding(pad, pad / 2, pad, pad);
        root.addView(col);

        java.util.LinkedHashMap<String, View> blocks = LayoutPrefs.blocks();
        voice = new VoiceRow(m);
        blocks.put("voice", voice.view);

        LinearLayout pickBox = Ui.v(m);
        pickBox.addView(Ui.button(m, m.getString(R.string.file_pick), false, v -> pick()));
        fileInfo = Ui.text(m, m.getString(R.string.file_none), 13, Ui.TEXT2);
        pickBox.addView(fileInfo, Ui.wrapMatch(m, 8));
        blocks.put("pick", pickBox);

        LinearLayout actBox = Ui.v(m);
        action = Ui.button(m, m.getString(R.string.file_process), true, v -> onAction());
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
        LayoutPrefs.fill(m, col, "file", blocks);
        refresh();
    }

    private void refresh() { voice.refresh(); }

    private void pick() {
        if (busy) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*", "application/ogg"});
        m.startForResult(i, (res, d) -> {
            if (res != BaseActivity.RESULT_OK || d == null || d.getData() == null) return;
            final Uri u = d.getData();
            input = null;
            result.hide();
            status.setText(R.string.file_loading);
            setBusy(true, false);
            Jobs.IO.execute(() -> {
                File dst = Files.tmp(m, "input.dat");
                try {
                    String name = Files.copyUri(m, u, dst);
                    input = dst;
                    Jobs.ui(() -> { fileInfo.setText(name); status.setText(""); setBusy(false, false); });
                } catch (Exception e) {
                    AppLog.err("copy input", e);
                    Jobs.ui(() -> { status.setText(R.string.err_decode); setBusy(false, false); });
                }
            });
        });
    }

    private void onAction() {
        if (busy) { cancel.set(true); return; }
        if (input == null) { pick(); return; }
        final VoiceSettings s = Store.get();
        final File in = input;
        cancel = new AtomicBoolean();
        final AtomicBoolean c = cancel;
        result.hide();
        status.setText(R.string.file_processing);
        setBusy(true, true);
        Jobs.IO.execute(() -> {
            try {
                Pcm v = Pipeline.decode(in.getPath(), 1, Pipeline.MAX_VOICE_FRAMES);
                Rendered r = Pipeline.render(m, v, s, f -> {
                    final int pct = Math.round(f * 100);
                    Jobs.ui(() -> { bar.setProgress(pct); status.setText(m.getString(R.string.progress_pct, pct)); });
                    return !c.get();
                });
                Jobs.ui(() -> { setBusy(false, false); status.setText(""); result.show(r, true); });
            } catch (Exception e) {
                String msg = Errors.text(m, e);
                Jobs.ui(() -> { setBusy(false, false); status.setText(msg == null ? "" : msg); });
            }
        });
    }

    private void setBusy(boolean b, boolean cancellable) {
        busy = b;
        voice.setEnabled(!b);
        bar.setVisibility(b && cancellable ? View.VISIBLE : View.GONE);
        bar.setProgress(0);
        action.setText(b ? (cancellable ? m.getString(R.string.cancel) : m.getString(R.string.file_loading)) : m.getString(R.string.file_process));
        Ui.setButtonColor(action, !b);
    }

    @Override public View view() { return root; }
    @Override public void onShow() { Store.addListener(listener); refresh(); }
    @Override public void onHide() { Store.removeListener(listener); result.pausePlayback(); }
}
