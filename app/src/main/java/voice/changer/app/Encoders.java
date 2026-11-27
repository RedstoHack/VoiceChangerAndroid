package voice.changer.app;

import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import voice.changer.app.dsp.Dsp;
import voice.changer.app.dsp.FlacWriter;
import voice.changer.app.dsp.FloatSource;
import voice.changer.app.dsp.Resampler;
import voice.changer.app.dsp.WavWriter;

/** Кодирование результата в файл: WAV, M4A (AAC), FLAC, MP3, OGG (Opus). */
final class Encoders {
    private Encoders() {}

    static String ext(int fmt) {
        switch (fmt) {
            case Store.FMT_WAV: return "wav";
            case Store.FMT_FLAC: return "flac";
            case Store.FMT_MP3: return "mp3";
            case Store.FMT_OGG: return "ogg";
            default: return "m4a";
        }
    }

    static String label(int fmt) {
        switch (fmt) {
            case Store.FMT_WAV: return "WAV";
            case Store.FMT_FLAC: return "FLAC";
            case Store.FMT_MP3: return "MP3";
            case Store.FMT_OGG: return "OGG (Opus)";
            default: return "M4A (AAC)";
        }
    }

    static boolean available(int fmt) {
        if (fmt == Store.FMT_MP3) return Mp3Encoder.available();
        if (fmt == Store.FMT_OGG) return Build.VERSION.SDK_INT >= 29;
        return true;
    }

    static List<Integer> formats() {
        List<Integer> l = new ArrayList<>();
        for (int f = 0; f <= 4; f++) if (available(f)) l.add(f);
        return l;
    }

    /** Кодирует результат в файл out. При ошибке удаляет недописанный файл. */
    static void encode(Rendered r, int fmt, File out) throws IOException {
        try {
            switch (fmt) {
                case Store.FMT_WAV: wav(r, out); break;
                case Store.FMT_FLAC: flac(r, out); break;
                case Store.FMT_MP3: mp3(r, out); break;
                case Store.FMT_OGG: ogg(r, out); break;
                default: aac(r, out);
            }
        } catch (IOException | RuntimeException e) {
            out.delete();
            AppLog.err("encode " + label(fmt), e);
            throw e;
        }
    }

    private static void wav(Rendered r, File out) throws IOException {
        try (WavWriter w = new WavWriter(out, 2, Dsp.SR)) { w.write(r.st, r.frames * 2); }
    }

    private static void flac(Rendered r, File out) throws IOException {
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1 << 16)) {
            FlacWriter.write(r.st, r.frames, Dsp.SR, os);
        }
    }

    private static void mp3(Rendered r, File out) throws IOException {
        boolean mono = r.isMono();
        int ch = mono ? 1 : 2, block = 8192;
        try (Mp3Encoder enc = new Mp3Encoder(Dsp.SR, ch, 192);
             OutputStream os = new BufferedOutputStream(new FileOutputStream(out), 1 << 16)) {
            short[] buf = new short[block * ch];
            for (int pos = 0; pos < r.frames; pos += block) {
                int n = Math.min(block, r.frames - pos);
                if (mono) for (int i = 0; i < n; i++) buf[i] = r.st[2 * (pos + i)];
                else System.arraycopy(r.st, 2 * pos, buf, 0, n * 2);
                os.write(enc.encode(buf, n));
            }
            os.write(enc.flush());
        }
    }

    private static void aac(Rendered r, File out) throws IOException {
        CodecWriter w = new CodecWriter(out, MediaFormat.MIMETYPE_AUDIO_AAC, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, Dsp.SR, 2, 160_000);
        try {
            for (int pos = 0; pos < r.frames; pos += 16384) {
                int n = Math.min(16384, r.frames - pos);
                w.write(r.st, pos * 2, n * 2);
            }
            w.close();
        } catch (IOException | RuntimeException e) {
            w.abort();
            throw e;
        }
    }

    /** Opus принимает 48 кГц, поэтому результат сначала пересчитывается. */
    private static void ogg(Rendered r, File out) throws IOException {
        if (Build.VERSION.SDK_INT < 29) throw new IOException("OGG needs Android 10+");
        final short[] src = r.st;
        final int total = r.frames;
        FloatSource in = new FloatSource() {
            int pos;
            @Override public int read(float[] o, int off, int frames) {
                if (pos >= total) return -1;
                int n = Math.min(frames, total - pos);
                for (int i = 0; i < n * 2; i++) o[off * 2 + i] = src[pos * 2 + i] * (1f / 32768f);
                pos += n;
                return n;
            }
        };
        Resampler rs = new Resampler(in, 2, Dsp.SR / 48000.0);
        CodecWriter w = new CodecWriter(out, "audio/opus", MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG, 48000, 2, 96_000);
        try {
            float[] f = new float[8192 * 2];
            short[] s = new short[8192 * 2];
            int n;
            while ((n = rs.read(f, 0, 8192)) > 0) {
                for (int i = 0; i < n * 2; i++) s[i] = (short) Math.round(Math.max(-1f, Math.min(1f, f[i])) * 32767f);
                w.write(s, 0, n * 2);
            }
            w.close();
        } catch (IOException | RuntimeException e) {
            w.abort();
            throw e;
        }
    }
}
