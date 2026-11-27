package voice.changer.app;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;

import java.io.IOException;
import java.util.Arrays;

import voice.changer.app.dsp.Dsp;
import voice.changer.app.dsp.EffectChain;
import voice.changer.app.dsp.GranularPitch;
import voice.changer.app.dsp.LayerMixer;
import voice.changer.app.dsp.Pcm;
import voice.changer.app.dsp.VoiceSettings;

/**
 * Работа с микрофоном. Два режима:
 * LIVE — голос обрабатывается и сразу выводится (AudioRecord → питч → эффекты → фон → AudioTrack);
 * RECORD — голос накапливается в памяти (с паузой без «пустых секунд»), обрабатывается после «Готово».
 */
final class LiveEngine {
    static final int MODE_LIVE = 0, MODE_RECORD = 1;
    private static final int BLOCK = 512;

    private volatile VoiceSettings settings = new VoiceSettings();
    private volatile boolean running, paused;
    private volatile float peak;
    private volatile String error;
    private volatile Rendered liveResult;
    private volatile Pcm recording;
    private volatile int recFrames;

    private Thread thread;
    private AudioDeviceInfo inDev, outDev;
    private int mode;
    private boolean recordLive;
    private Context app;

    private final Object bgLock = new Object();
    private String bgSig = "";
    private volatile LayerMixer mixer;

    void configure(int mode, AudioDeviceInfo in, AudioDeviceInfo out, boolean recordLive) {
        this.mode = mode; inDev = in; outDev = out; this.recordLive = recordLive;
    }

    boolean isRunning() { return running; }
    boolean isPaused() { return paused; }
    void setPaused(boolean p) { paused = p; }
    float takePeak() { float p = peak; peak = 0; return p; }
    String error() { return error; }
    void fail(String msg) { error = msg; }
    float recSeconds() { return recFrames / (float) Dsp.SR; }
    Rendered takeLiveResult() { Rendered r = liveResult; liveResult = null; return r; }
    Pcm takeRecording() { Pcm r = recording; recording = null; return r; }

    /** Вызывается из UI-потока при любом изменении настроек. */
    void setSettings(VoiceSettings s) {
        settings = s;
        if (running && mode == MODE_LIVE) updateBg(s);
    }

    private void updateBg(VoiceSettings s) {
        final Context c = app;
        if (c == null) return;
        synchronized (bgLock) {
            String sig = Bg.signature(s);
            if (!sig.equals(bgSig)) {
                bgSig = sig;
                Jobs.IO.execute(() -> {
                    LayerMixer m = Bg.mixer(c, s, false);
                    LayerMixer old;
                    synchronized (bgLock) {
                        if (!sig.equals(bgSig) || !running) { m.close(); return; }
                        old = mixer;
                        mixer = m;
                    }
                    if (old != null) old.close();
                });
            } else {
                LayerMixer m = mixer;
                if (m != null) for (int i = 0; i < s.bg.size(); i++) m.setGain(i, s.bg.get(i).volume);
            }
        }
    }

    synchronized void start(Context ctx, Runnable onEnd) {
        if (running) return;
        error = null;
        liveResult = null;
        recording = null;
        recFrames = 0;
        paused = false;
        app = ctx.getApplicationContext();
        settings = Store.get();
        running = true;
        synchronized (bgLock) { bgSig = ""; }
        if (mode == MODE_LIVE) updateBg(settings);
        AppLog.add("start " + (mode == MODE_LIVE ? "live" : "record"));
        thread = new Thread(() -> {
            try {
                if (mode == MODE_LIVE) liveLoop(app); else recordLoop(app);
            } catch (SecurityException e) {
                AppLog.err("audio", e);
                error = app.getString(R.string.live_perm_denied);
            } catch (Exception e) {
                AppLog.err("audio", e);
                error = app.getString(R.string.live_mic_busy) + " (" + e.getClass().getSimpleName()
                        + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")";
            } finally {
                running = false;
                synchronized (bgLock) {
                    LayerMixer m = mixer;
                    mixer = null;
                    if (m != null) m.close();
                }
                AppLog.add("stopped");
                if (onEnd != null) Jobs.ui(onEnd);
            }
        }, "voice-audio");
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    synchronized void stop() {
        running = false;
        Thread t = thread;
        if (t != null && t != Thread.currentThread()) {
            try { t.join(2500); } catch (InterruptedException ignored) { }
        }
    }

    private static boolean isComm(AudioDeviceInfo d) {
        if (d == null) return false;
        int t = d.getType();
        return t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || (Build.VERSION.SDK_INT >= 31 && t == AudioDeviceInfo.TYPE_BLE_HEADSET);
    }

    /** Пробует несколько источников звука: на разных прошивках работает не каждый. */
    private AudioRecord openRecord(boolean comm) throws IOException {
        int minRec = AudioRecord.getMinBufferSize(Dsp.SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int[] sources = comm
                ? new int[]{MediaRecorder.AudioSource.VOICE_COMMUNICATION, MediaRecorder.AudioSource.MIC}
                : new int[]{MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION};
        for (int src : sources) {
            AudioRecord r = new AudioRecord(src, Dsp.SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minRec, BLOCK * 8));
            if (r.getState() == AudioRecord.STATE_INITIALIZED) {
                if (inDev != null) r.setPreferredDevice(inDev);
                AppLog.add("AudioRecord source " + src + " ok");
                return r;
            }
            r.release();
            AppLog.add("AudioRecord source " + src + " not initialized");
        }
        throw new IOException("AudioRecord init failed");
    }

    private AudioTrack openTrack(boolean comm) {
        int minTrk = AudioTrack.getMinBufferSize(Dsp.SR, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioAttributes attr = new AudioAttributes.Builder()
                .setUsage(comm ? AudioAttributes.USAGE_VOICE_COMMUNICATION : AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        AudioFormat fmt = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(Dsp.SR).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build();
        AudioTrack t;
        try {
            t = new AudioTrack.Builder().setAudioAttributes(attr).setAudioFormat(fmt)
                    .setBufferSizeInBytes(Math.max(minTrk, BLOCK * 4 * 3))
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
        } catch (RuntimeException e) {
            AppLog.err("low-latency track", e);
            t = new AudioTrack.Builder().setAudioAttributes(attr).setAudioFormat(fmt)
                    .setBufferSizeInBytes(Math.max(minTrk, BLOCK * 4 * 6))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
        }
        if (outDev != null) t.setPreferredDevice(outDev);
        return t;
    }

    // ------------------------------------------------------------------ режим «в реальном времени»
    private void liveLoop(Context app) throws IOException {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        int oldMode = am.getMode();
        boolean modeChanged = false, sco = false;
        AudioRecord rec = null;
        AudioTrack trk = null;
        short[] lbuf = recordLive ? new short[Dsp.SR * 2 * 30] : null;
        int lframes = 0;
        try {
            AudioDeviceInfo comm = isComm(inDev) ? inDev : (isComm(outDev) ? outDev : null);
            if (comm != null) {
                am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                modeChanged = true;
                if (Build.VERSION.SDK_INT >= 31) {
                    for (AudioDeviceInfo d : am.getAvailableCommunicationDevices())
                        if (d.getId() == comm.getId()) am.setCommunicationDevice(d);
                } else {
                    am.startBluetoothSco();
                    am.setBluetoothScoOn(true);
                    sco = true;
                }
            }
            rec = openRecord(comm != null);
            trk = openTrack(comm != null);

            GranularPitch gp = new GranularPitch();
            EffectChain chain = new EffectChain();
            VoiceSettings cur = null;
            short[] in = new short[BLOCK], out = new short[BLOCK * 2];
            float[] f = new float[BLOCK], st = new float[BLOCK * 2];

            rec.startRecording();
            trk.play();
            while (running) {
                int n = rec.read(in, 0, BLOCK);
                if (n < 0) throw new IOException("read " + n);
                if (n == 0) continue;
                VoiceSettings s = settings;
                if (s != cur) { cur = s; gp.setRatio(s.ratio()); chain.apply(s); }
                float pk = 0;
                for (int i = 0; i < n; i++) f[i] = in[i] * (1f / 32768f);
                gp.process(f, 0, n);
                chain.process(f, 0, n);
                for (int i = 0; i < n; i++) {
                    float v = f[i] * s.volume;
                    st[2 * i] = v; st[2 * i + 1] = v;
                    pk = Math.max(pk, Math.abs(v));
                }
                LayerMixer mx = mixer;
                if (mx != null) mx.mix(st, n);
                for (int i = 0; i < 2 * n; i++) out[i] = (short) (Dsp.softClip(st[i]) * 32767f);
                if (pk > peak) peak = pk;
                trk.write(out, 0, n * 2);
                if (lbuf != null && lframes + n < Dsp.SR * 60 * 10) {
                    if ((lframes + n) * 2 > lbuf.length) lbuf = Arrays.copyOf(lbuf, lbuf.length * 2);
                    System.arraycopy(out, 0, lbuf, lframes * 2, n * 2);
                    lframes += n;
                }
            }
        } finally {
            if (rec != null) { try { rec.stop(); } catch (Exception ignored) { } rec.release(); }
            if (trk != null) { try { trk.stop(); } catch (Exception ignored) { } trk.release(); }
            if (lbuf != null && lframes > 0) liveResult = new Rendered(lbuf, lframes);
            if (Build.VERSION.SDK_INT >= 31) { try { am.clearCommunicationDevice(); } catch (Exception ignored) { } }
            if (sco) { try { am.stopBluetoothSco(); am.setBluetoothScoOn(false); } catch (Exception ignored) { } }
            if (modeChanged) am.setMode(oldMode);
        }
    }

    // ------------------------------------------------------------------ режим «запись»
    private void recordLoop(Context app) throws IOException {
        AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        int oldMode = am.getMode();
        boolean modeChanged = false, sco = false;
        AudioRecord rec = null;
        short[] buf = new short[Dsp.SR * 30];
        int len = 0;
        try {
            if (isComm(inDev)) {
                am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                modeChanged = true;
                if (Build.VERSION.SDK_INT >= 31) {
                    for (AudioDeviceInfo d : am.getAvailableCommunicationDevices())
                        if (d.getId() == inDev.getId()) am.setCommunicationDevice(d);
                } else {
                    am.startBluetoothSco();
                    am.setBluetoothScoOn(true);
                    sco = true;
                }
            }
            rec = openRecord(isComm(inDev));
            short[] in = new short[BLOCK];
            rec.startRecording();
            while (running) {
                int n = rec.read(in, 0, BLOCK);
                if (n < 0) throw new IOException("read " + n);
                if (n == 0) continue;
                float pk = 0;
                for (int i = 0; i < n; i++) pk = Math.max(pk, Math.abs(in[i]) / 32768f);
                if (pk > peak) peak = pk;
                if (paused) continue;                       // пауза: ничего не пишем, тишина не накапливается
                if (len + n > Pipeline.MAX_VOICE_FRAMES) { error = app.getString(R.string.err_too_long); break; }
                if (len + n > buf.length) buf = Arrays.copyOf(buf, Math.min(Pipeline.MAX_VOICE_FRAMES, buf.length * 2));
                System.arraycopy(in, 0, buf, len, n);
                len += n;
                recFrames = len;
            }
        } finally {
            if (rec != null) { try { rec.stop(); } catch (Exception ignored) { } rec.release(); }
            if (len > 0) recording = new Pcm(Arrays.copyOf(buf, len + Pcm.PAD), len, 1);
            if (Build.VERSION.SDK_INT >= 31) { try { am.clearCommunicationDevice(); } catch (Exception ignored) { } }
            if (sco) { try { am.stopBluetoothSco(); am.setBluetoothScoOn(false); } catch (Exception ignored) { } }
            if (modeChanged) am.setMode(oldMode);
        }
    }
}
