package voice.changer.app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import voice.changer.app.dsp.FxType;
import voice.changer.app.dsp.Pcm;

/**
 * Вкладка «Микрофон».
 * Режим «В реальном времени»: голос сразу меняется и выводится на выбранное устройство.
 * Режим «Запись»: пишем голос (с паузой, без пустых секунд), по «Готово» меняем голос и слушаем/сохраняем/отправляем.
 */
final class LiveScreen implements Screen {
    private final MainActivity m;
    private final ScrollView root;
    private final VoiceRow voice;
    private final TextView chipLive, chipRec, status, timer, pauseBtn, doneBtn, inVal, outVal;
    private final MicButton mic;
    private final LinearLayout recRow, outBox;
    private final Switch recLive;
    private final Ui.Slider pitch, reverse;
    private final ResultView result;
    private final Runnable listener = this::refresh;
    private AudioDeviceInfo inDev, outDev;
    private boolean wasRunning, starting, processing, pendingRelease;
    private long startedAt;
    private final Runnable poll = new Runnable() {
        @Override public void run() { tick(); root.postDelayed(this, 60); }
    };

    LiveScreen(MainActivity m) {
        this.m = m;
        root = new ScrollView(m);
        LinearLayout col = Ui.v(m);
        int pad = Ui.dp(m, 16);
        col.setPadding(pad, pad / 2, pad, pad);
        root.addView(col);

        java.util.LinkedHashMap<String, View> blocks = LayoutPrefs.blocks();

        voice = new VoiceRow(m);
        blocks.put("voice", voice.view);

        // переключатель режима
        List<TextView> chips = new ArrayList<>();
        chipLive = Ui.chip(m, m.getString(R.string.mode_live), true, v -> setMode(true));
        chipRec = Ui.chip(m, m.getString(R.string.mode_record), false, v -> setMode(false));
        chips.add(chipLive);
        chips.add(chipRec);
        blocks.put("mode", Ui.chipGrid(m, chips, 2));

        // кнопка записи, таймер, подпись и «Пауза / Готово» — одним блоком
        LinearLayout micBlock = Ui.v(m);
        mic = new MicButton(m);
        mic.setListener(new MicButton.Listener() {
            @Override public void onDown() { press(true, true); }
            @Override public void onUp(boolean inside) { press(false, inside); }
        });
        LinearLayout.LayoutParams mlp = Ui.lp(-1, -2, 0);
        mlp.gravity = Gravity.CENTER_HORIZONTAL;
        micBlock.addView(mic, mlp);
        timer = Ui.text(m, "", 22, Ui.TEXT);
        timer.setGravity(Gravity.CENTER);
        micBlock.addView(timer);
        status = Ui.text(m, "", 14, Ui.TEXT2);
        status.setGravity(Gravity.CENTER);
        micBlock.addView(status, Ui.wrapMatch(m, 4));
        recRow = Ui.h(m);
        pauseBtn = Ui.button(m, m.getString(R.string.mic_pause), false, v -> press(false, true));
        doneBtn = Ui.button(m, m.getString(R.string.mic_done), true, v -> finishRecording());
        recRow.addView(pauseBtn, Ui.lp(0, -2, 1));
        LinearLayout.LayoutParams dl = Ui.lp(0, -2, 1);
        dl.leftMargin = Ui.dp(m, 8);
        recRow.addView(doneBtn, dl);
        micBlock.addView(recRow, Ui.wrapMatch(m, 12));
        blocks.put("mic", micBlock);

        // быстрые ползунки: высота и реверс в реальном времени
        LinearLayout quick = Ui.card(m);
        pitch = new Ui.Slider(m, m.getString(R.string.pitch), -12f, 12f, 0.5f,
                v -> String.format(Locale.US, m.getString(R.string.pitch_fmt), v), v -> Store.edit(s -> { s.pitch = v; s.preset = "custom"; }));
        quick.addView(pitch);
        final int rev = FxType.REVERSE.ordinal();
        reverse = new Ui.Slider(m, m.getString(R.string.reverse_live), 0f, 1f, 0.05f,
                v -> v <= 0.001f ? m.getString(R.string.reverse_off)
                        : String.format(Locale.US, m.getString(R.string.reverse_fmt), 0.1f + 1.4f * v),
                v -> Store.edit(s -> { s.fx[rev] = v; s.preset = "custom"; }));
        quick.addView(reverse, Ui.wrapMatch(m, 14));
        quick.addView(Ui.text(m, m.getString(R.string.reverse_live_note), 12, Ui.TEXT2), Ui.wrapMatch(m, 6));
        blocks.put("quick", quick);

        LinearLayout dev = Ui.card(m);
        inVal = deviceRow(dev, R.string.live_input, true);
        outBox = Ui.v(m);
        outVal = deviceRow(outBox, R.string.live_output, false);
        recLive = new Switch(m);
        recLive.setText(R.string.live_record);
        recLive.setTextColor(Ui.TEXT);
        outBox.addView(recLive, Ui.wrapMatch(m, 12));
        dev.addView(outBox);
        blocks.put("devices", dev);

        result = new ResultView(m);
        blocks.put("result", result);

        LayoutPrefs.fill(m, col, "live", blocks);
        refresh();
        applyMode();
    }

    private boolean liveMode() { return Store.liveMode(); }

    private void setMode(boolean live) {
        if (LiveService.ENGINE.isRunning() || processing) return;
        Store.setLiveMode(live);
        applyMode();
    }

    private void applyMode() {
        boolean live = liveMode();
        Ui.setChip(chipLive, live);
        Ui.setChip(chipRec, !live);
        outBox.setVisibility(live ? View.VISIBLE : View.GONE);
        refreshUi(LiveService.ENGINE.isRunning());
    }

    private TextView deviceRow(LinearLayout parent, int labelRes, boolean input) {
        LinearLayout r = Ui.h(m);
        r.setPadding(0, Ui.dp(m, 8), 0, Ui.dp(m, 8));
        r.addView(Ui.text(m, m.getString(labelRes), 15, Ui.TEXT), Ui.lp(0, -2, 1));
        TextView val = Ui.text(m, m.getString(R.string.device_auto) + " ›", 15, Ui.LINK);
        r.addView(val);
        r.setOnClickListener(v -> pickDevice(input, val));
        parent.addView(r);
        return val;
    }

    private void pickDevice(boolean input, TextView val) {
        if (LiveService.ENGINE.isRunning()) return;
        AudioManager am = (AudioManager) m.getSystemService(Context.AUDIO_SERVICE);
        AudioDeviceInfo[] all = am.getDevices(input ? AudioManager.GET_DEVICES_INPUTS : AudioManager.GET_DEVICES_OUTPUTS);
        List<AudioDeviceInfo> devs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        devs.add(null);
        names.add(m.getString(R.string.device_auto));
        for (AudioDeviceInfo d : all) {
            String n = Labels.device(m, d);
            if (n != null) { devs.add(d); names.add(n); }
        }
        Dialogs.list(m, m.getString(input ? R.string.live_pick_input : R.string.live_pick_output),
                names.toArray(new String[0]), which -> {
                    AudioDeviceInfo d = devs.get(which);
                    if (input) inDev = d; else outDev = d;
                    val.setText(names.get(which) + " ›");
                    int t = d == null ? -1 : d.getType();
                    if (Build.VERSION.SDK_INT >= 31 && (t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                            || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || t == AudioDeviceInfo.TYPE_BLE_HEADSET)
                            && m.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                        m.askPerms(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, (p, g) -> { });
                });
    }

    private void refresh() {
        voice.refresh();
        pitch.set(Store.get().pitch);
        reverse.set(Store.get().fx[FxType.REVERSE.ordinal()]);
    }

    // ------------------------------------------------------------------ управление кнопкой
    /** Нажатие/отпускание большой кнопки. Режим «нажал — нажал» или «удерживать» задаётся в Настройках. */
    private void press(boolean down, boolean inside) {
        if (processing) return;
        if (starting) {                                   // кнопку отпустили, пока микрофон ещё запускался
            if (Store.pttHold() && !down) pendingRelease = true;
            return;
        }
        LiveEngine e = LiveService.ENGINE;
        boolean running = e.isRunning();
        if (Store.pttHold()) {
            if (down) {
                if (!running) startEngine(); else if (!liveMode()) e.setPaused(false);
            } else if (running) {
                if (liveMode()) stopEngine(); else e.setPaused(true);
            }
        } else if (!down && inside) {
            if (!running) startEngine();
            else if (liveMode()) stopEngine();
            else e.setPaused(!e.isPaused());
        }
    }

    private void startEngine() {
        List<String> need = new ArrayList<>();
        if (m.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && m.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (need.isEmpty()) { begin(); return; }
        m.askPerms(need.toArray(new String[0]), (p, g) -> {
            if (m.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin();
            else showError(m.getString(R.string.live_perm_denied));
        });
    }

    private void begin() {
        result.hide();
        status.setTextColor(Ui.TEXT2);
        LiveEngine e = LiveService.ENGINE;
        e.configure(liveMode() ? LiveEngine.MODE_LIVE : LiveEngine.MODE_RECORD, inDev, outDev, recLive.isChecked());
        e.setSettings(Store.get());
        starting = true;
        pendingRelease = false;
        startedAt = System.currentTimeMillis();
        status.setText(R.string.mic_starting);
        try {
            m.startForegroundService(new Intent(m, LiveService.class).setAction(LiveService.ACT_START));
        } catch (RuntimeException ex) {
            starting = false;
            AppLog.err("startForegroundService", ex);
            showError(ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage()));
        }
    }

    private void stopEngine() {
        m.startService(new Intent(m, LiveService.class).setAction(LiveService.ACT_STOP));
    }

    private void finishRecording() {
        if (LiveService.ENGINE.isRunning() && !processing) stopEngine();
    }

    private void showError(String msg) {
        status.setTextColor(Ui.ERR);
        status.setText(msg);
        Toast.makeText(m, msg, Toast.LENGTH_LONG).show();
    }

    // ------------------------------------------------------------------ обновление экрана
    private void tick() {
        LiveEngine e = LiveService.ENGINE;
        boolean run = e.isRunning();
        if (starting) {
            if (run) {
                starting = false;
                if (pendingRelease) {
                    pendingRelease = false;
                    if (liveMode()) stopEngine(); else e.setPaused(true);
                }
            }
            else if (e.error() != null) { starting = false; showError(e.error()); }
            else if (System.currentTimeMillis() - startedAt > 5000) {
                starting = false;
                showError(m.getString(R.string.mic_no_response));
            }
        }
        if (wasRunning && !run) onEngineStopped();
        wasRunning = run;
        mic.setLevel(run ? e.takePeak() * 1.8f : 0f);
        mic.setActive(run && !e.isPaused());
        refreshUi(run);
    }

    private void refreshUi(boolean run) {
        LiveEngine e = LiveService.ENGINE;
        boolean live = liveMode();
        recRow.setVisibility(!live && run ? View.VISIBLE : View.GONE);
        pauseBtn.setText(e.isPaused() ? R.string.mic_resume : R.string.mic_pause);
        timer.setText(!live && (run || e.recSeconds() > 0) ? Files.fmtTime(e.recSeconds()) : "");
        boolean hold = Store.pttHold();
        String label;
        if (processing) label = m.getString(R.string.mic_processing);
        else if (live) label = m.getString(run ? R.string.mic_stop : R.string.mic_start);
        else if (!run) label = m.getString(R.string.mic_rec);
        else label = m.getString(e.isPaused() ? R.string.mic_resume : R.string.mic_pause);
        mic.setLabel(label);
        if (!starting && status.getCurrentTextColor() != Ui.ERR) {
            int hint;
            if (processing) hint = R.string.mic_processing;
            else if (live) hint = run ? R.string.live_hint_run : (hold ? R.string.hint_hold_live : R.string.live_hint_idle);
            else if (!run) hint = hold ? R.string.hint_hold_rec : 0;
            else hint = e.isPaused() ? R.string.rec_hint_paused : R.string.rec_hint_run;
            status.setText(hint == 0 ? "" : m.getString(hint));
        }
        voice.setEnabled(!run && !processing);
        inVal.setEnabled(!run);
        outVal.setEnabled(!run);
        recLive.setEnabled(!run);
        chipLive.setEnabled(!run && !processing);
        chipRec.setEnabled(!run && !processing);
    }

    private void onEngineStopped() {
        LiveEngine e = LiveService.ENGINE;
        if (e.error() != null) showError(e.error());
        if (liveMode()) {
            Rendered r = e.takeLiveResult();
            if (r != null) result.show(r, false);
        } else {
            Pcm rec = e.takeRecording();
            if (rec != null) process(rec);
        }
    }

    private void process(Pcm rec) {
        processing = true;
        status.setTextColor(Ui.TEXT2);
        final voice.changer.app.dsp.VoiceSettings s = Store.get();
        Jobs.IO.execute(() -> {
            try {
                Rendered r = Pipeline.render(m, rec, s, null);
                Jobs.ui(() -> { processing = false; result.show(r, true); });
            } catch (Exception ex) {
                String msg = Errors.text(m, ex);
                Jobs.ui(() -> { processing = false; if (msg != null) showError(msg); });
            }
        });
    }

    @Override public View view() { return root; }

    @Override public void onShow() {
        Store.addListener(listener);
        refresh();
        root.post(poll);
    }

    @Override public void onHide() {
        Store.removeListener(listener);
        root.removeCallbacks(poll);
        result.pausePlayback();
    }
}
