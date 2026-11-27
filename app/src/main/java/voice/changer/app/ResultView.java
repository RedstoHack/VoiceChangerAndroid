package voice.changer.app;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Результат: плеер с перемоткой, «Поделиться» и «Сохранить как…» (WAV, M4A, FLAC, MP3, OGG). */
final class ResultView extends LinearLayout {
    private final BaseActivity act;
    private final TextView info, playBtn, tNow, tTotal;
    private final SeekBar seek;
    private Rendered rendered;
    private PcmPlayer player;
    private boolean dragging, busy;
    /** Уже закодированные файлы этого результата по формату: повторное «Поделиться» отправляет их сразу. */
    private final java.util.Map<Integer, File> cache = new java.util.HashMap<>();
    private final java.util.Map<Integer, java.util.List<Done>> inflight = new java.util.HashMap<>();

    private interface Done { void run(File f); }
    private String name = "";

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (player != null && !dragging) {
                int total = player.durationFrames();
                int pos = player.positionFrames();
                seek.setProgress(total == 0 ? 0 : (int) ((long) pos * 1000 / total));
                tNow.setText(Files.fmtTime(pos / 44100f));
                playBtn.setText(player.isPlaying() ? R.string.pause : R.string.play);
            }
            if (player != null) postDelayed(this, 100);
        }
    };

    ResultView(BaseActivity a) {
        super(a);
        act = a;
        setOrientation(VERTICAL);
        setBackground(Ui.rect(a, Ui.cardColor(), 16));
        int p = Ui.dp(a, 16);
        setPadding(p, p, p, p);

        info = Ui.text(a, "", 15, Ui.TEXT);
        addView(info);

        LinearLayout row = Ui.h(a);
        playBtn = Ui.button(a, a.getString(R.string.play), true, v -> togglePlay());
        row.addView(playBtn, Ui.lp(-2, -2, 0));
        tNow = Ui.text(a, "0:00", 13, Ui.TEXT2);
        tNow.setPadding(Ui.dp(a, 10), 0, Ui.dp(a, 4), 0);
        row.addView(tNow);
        seek = new SafeSeekBar(a);
        seek.setMax(1000);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                if (user && player != null) tNow.setText(Files.fmtTime(player.durationFrames() / 44100f * prog / 1000f));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { dragging = true; }
            @Override public void onStopTrackingTouch(SeekBar s) {
                dragging = false;
                if (player != null) player.seek((int) ((long) player.durationFrames() * s.getProgress() / 1000));
            }
        });
        row.addView(seek, Ui.lp(0, -2, 1));
        tTotal = Ui.text(a, "0:00", 13, Ui.TEXT2);
        tTotal.setPadding(Ui.dp(a, 4), 0, 0, 0);
        row.addView(tTotal);
        addView(row, Ui.wrapMatch(a, 12));

        LinearLayout row2 = Ui.h(a);
        row2.addView(Ui.button(a, a.getString(R.string.share), false, v -> shareNow()), Ui.lp(0, -2, 1));
        LayoutParams lp = Ui.lp(0, -2, 1);
        lp.leftMargin = Ui.dp(a, 8);
        row2.addView(Ui.button(a, a.getString(R.string.save), false, v -> save()), lp);
        addView(row2, Ui.wrapMatch(a, 8));
        setVisibility(GONE);
    }

    void show(Rendered r, boolean autoplay) {
        release();
        rendered = r;
        cache.clear();
        inflight.clear();
        info.setText(act.getString(R.string.result_ready, Files.fmtTime(r.seconds())));
        tTotal.setText(Files.fmtTime(r.seconds()));
        seek.setProgress(0);
        player = new PcmPlayer(r);
        setVisibility(VISIBLE);
        if (autoplay) player.play();
        post(tick);
        prepareInBackground(r);
    }

    /** Пока результат слушают, файл для «Поделиться» уже кодируется — к нажатию он обычно готов. */
    private void prepareInBackground(Rendered r) {
        ensureEncoded(r, defaultFormat(), f -> { });
    }

    void hide() { release(); rendered = null; setVisibility(GONE); }

    void release() {
        removeCallbacks(tick);
        if (player != null) { player.release(); player = null; }
        playBtn.setText(R.string.play);
    }

    /** Остановить воспроизведение, но оставить результат (при уходе с вкладки). */
    void pausePlayback() { if (player != null) player.pause(); }

    private void togglePlay() {
        if (player == null) return;
        if (player.isPlaying()) player.pause(); else player.play();
        playBtn.setText(player.isPlaying() ? R.string.pause : R.string.play);
    }

    private int defaultFormat() {
        int f = Store.format();
        return Encoders.available(f) ? f : Store.FMT_M4A;
    }

    /** Возвращает готовый файл нужного формата: из кэша, из текущего кодирования или кодирует заново. */
    private void ensureEncoded(Rendered r, int fmt, Done cb) {
        File have = cache.get(fmt);
        if (have != null && have.exists()) { cb.run(have); return; }
        java.util.List<Done> waiting = inflight.get(fmt);
        if (waiting != null) { waiting.add(cb); return; }
        waiting = new java.util.ArrayList<>();
        waiting.add(cb);
        inflight.put(fmt, waiting);
        Jobs.IO.execute(() -> {
            File out = Files.newOut(act, Encoders.ext(fmt));
            File res = null;
            try {
                Encoders.encode(r, fmt, out);
                res = out;
            } catch (Exception e) {
                AppLog.err("encode", e);
            }
            final File done = res;
            Jobs.ui(() -> {
                if (rendered != r) { if (done != null) done.delete(); return; }     // за это время появился новый результат
                java.util.List<Done> cbs = inflight.remove(fmt);
                if (done != null) cache.put(fmt, done);
                if (cbs != null) for (Done d : cbs) d.run(done);
            });
        });
    }

    /** «Поделиться»: уже готовый файл отправляется сразу, без повторного кодирования. */
    void shareNow() {
        final Rendered r = rendered;
        if (r == null) return;
        final int fmt = defaultFormat();
        File have = cache.get(fmt);
        if (have != null && have.exists()) { Files.share(act, have); return; }
        info.setText(R.string.preparing_file);
        ensureEncoded(r, fmt, f -> {
            resetInfo();
            if (f != null) Files.share(act, f);
            else Toast.makeText(act, R.string.err_encode, Toast.LENGTH_LONG).show();
        });
    }

    private void resetInfo() {
        if (rendered != null) info.setText(act.getString(R.string.result_ready, Files.fmtTime(rendered.seconds())));
    }

    private void save() {
        final Rendered r = rendered;
        if (r == null || busy) return;
        final List<Integer> fmts = Encoders.formats();
        String[] names = new String[fmts.size()];
        for (int i = 0; i < names.length; i++) names[i] = Encoders.label(fmts.get(i));
        Dialogs.list(act, act.getString(R.string.save_as_format), names, which -> {
            final int fmt = fmts.get(which);
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType(Files.mime(new File("x." + Encoders.ext(fmt))));
            i.putExtra(Intent.EXTRA_TITLE, "VoiceChanger." + Encoders.ext(fmt));
            act.startForResult(i, (res, data) -> {
                if (res != BaseActivity.RESULT_OK || data == null || data.getData() == null) return;
                final Uri u = data.getData();
                busy = true;
                info.setText(R.string.preparing_file);
                ensureEncoded(r, fmt, f -> {
                    if (f == null) { busy = false; resetInfo(); Toast.makeText(act, R.string.save_failed, Toast.LENGTH_SHORT).show(); return; }
                    Jobs.IO.execute(() -> {
                        boolean ok = true;
                        try { Files.copyToUri(act, f, u); } catch (Exception e) { AppLog.err("save", e); ok = false; }
                        final boolean good = ok;
                        Jobs.ui(() -> {
                            busy = false;
                            resetInfo();
                            Toast.makeText(act, good ? R.string.saved : R.string.save_failed, Toast.LENGTH_SHORT).show();
                        });
                    });
                });
            });
        });
    }
}
