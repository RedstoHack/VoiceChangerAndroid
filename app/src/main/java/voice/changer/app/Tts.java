package voice.changer.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import voice.changer.app.dsp.Dsp;
import voice.changer.app.dsp.OfflineRenderer;
import voice.changer.app.dsp.Pcm;
import voice.changer.app.dsp.TextChunker;

/**
 * Озвучка через системный интерфейс TextToSpeech. Своего движка в приложении нет, к Google оно не привязано:
 * используется тот движок, который выбран в приложении (любой установленный офлайн-движок).
 */
final class Tts {
    private Tts() {}

    static final class EngineInfo {
        final String pkg, label;
        EngineInfo(String p, String l) { pkg = p; label = l; }
    }

    private static final class Waiter {
        final CountDownLatch latch = new CountDownLatch(1);
        volatile boolean ok;
    }

    private static TextToSpeech tts;
    private static String ttsEngine;
    private static final ConcurrentHashMap<String, Waiter> waiters = new ConcurrentHashMap<>();

    static List<EngineInfo> engines(Context c) {
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> l = pm.queryIntentServices(new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0);
        List<EngineInfo> out = new ArrayList<>();
        for (ResolveInfo ri : l)
            out.add(new EngineInfo(ri.serviceInfo.packageName, String.valueOf(ri.loadLabel(pm))));
        return out;
    }

    private static void done(String id, boolean ok) {
        Waiter w = waiters.get(id);
        if (w != null) { w.ok = ok; w.latch.countDown(); }
    }

    private static synchronized TextToSpeech ensure(Context app, String engine) throws IOException {
        if (tts != null && Objects.equals(engine, ttsEngine)) return tts;
        releaseLocked();
        final CountDownLatch latch = new CountDownLatch(1);
        final int[] status = {TextToSpeech.ERROR};
        TextToSpeech t = new TextToSpeech(app, s -> { status[0] = s; latch.countDown(); }, engine);
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) { t.shutdown(); throw new IOException(app.getString(R.string.tts_init_failed)); }
        } catch (InterruptedException e) {
            t.shutdown();
            throw new IOException(e);
        }
        if (status[0] != TextToSpeech.SUCCESS) { t.shutdown(); throw new IOException(app.getString(R.string.tts_init_failed)); }
        t.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { done(id, true); }
            @Override @SuppressWarnings("deprecation") public void onError(String id) { done(id, false); }
            @Override public void onError(String id, int code) { done(id, false); }
        });
        tts = t;
        ttsEngine = engine;
        return t;
    }

    private static void releaseLocked() {
        if (tts != null) { try { tts.shutdown(); } catch (Exception ignored) { } }
        tts = null;
        ttsEngine = null;
    }

    static synchronized void release() { releaseLocked(); }

    /** Заранее запускает движок, чтобы первая озвучка не ждала его инициализации. */
    static void warmUp(Context c, String engine) {
        final Context app = c.getApplicationContext();
        Jobs.IO.execute(() -> {
            try { ensure(app, engine); } catch (Exception e) { AppLog.err("tts warm-up", e); }
        });
    }

    /** Установленные офлайн-голоса выбранного движка. */
    static List<Voice> voices(Context app, String engine) throws IOException {
        TextToSpeech t = ensure(app, engine);
        Set<Voice> vs = t.getVoices();
        List<Voice> out = new ArrayList<>();
        if (vs != null)
            for (Voice v : vs) {
                if (v.isNetworkConnectionRequired()) continue;
                if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                out.add(v);
            }
        Collections.sort(out, (a, b) -> {
            int c = a.getLocale().getDisplayName().compareToIgnoreCase(b.getLocale().getDisplayName());
            return c != 0 ? c : a.getName().compareToIgnoreCase(b.getName());
        });
        return out;
    }

    static String voiceLabel(Voice v) {
        return v.getLocale().getDisplayName() + " · " + v.getName();
    }

    private static void applyVoice(TextToSpeech t, String voiceName) {
        if (voiceName != null) {
            Set<Voice> vs = t.getVoices();
            if (vs != null)
                for (Voice v : vs)
                    if (v.getName().equals(voiceName)) { t.setVoice(v); return; }
        }
        t.setLanguage(Locale.getDefault());
    }

    /** Синтезирует текст в моно-PCM (без эффектов). Вызывать не из UI-потока. */
    static Pcm synthesize(Context c, String text, String engine, String voiceName, OfflineRenderer.Progress p) throws IOException {
        Context app = c.getApplicationContext();
        TextToSpeech t = ensure(app, engine);
        applyVoice(t, voiceName);
        t.setSpeechRate(1f);   // темп и высоту задаёт сам Voice Changer — одинаково для любого движка
        t.setPitch(1f);
        int max = Math.max(200, Math.min(3500, TextToSpeech.getMaxSpeechInputLength() - 100));
        List<String> chunks = TextChunker.split(text, max);
        List<Pcm> parts = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (p != null && !p.update(i / (float) chunks.size())) throw new OfflineRenderer.Cancelled();
            String id = "vc" + System.nanoTime();
            Waiter w = new Waiter();
            waiters.put(id, w);
            File f = Files.tmp(app, "tts_" + i + ".wav");
            try {
                int rc = t.synthesizeToFile(chunks.get(i), new Bundle(), f, id);
                if (rc != TextToSpeech.SUCCESS) throw new IOException(app.getString(R.string.tts_failed));
                if (!w.latch.await(240, TimeUnit.SECONDS)) throw new IOException("TTS timeout");
                if (!w.ok || !f.exists() || f.length() < 100) throw new IOException(app.getString(R.string.tts_failed));
                parts.add(Pipeline.decode(f.getPath(), 1, Pipeline.MAX_VOICE_FRAMES));
            } catch (InterruptedException e) {
                throw new IOException(e);
            } finally {
                waiters.remove(id);
                f.delete();
            }
        }
        return Pcm.concatMono(parts, Dsp.SR / 5);
    }
}
