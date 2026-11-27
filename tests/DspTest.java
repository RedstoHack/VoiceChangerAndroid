import voice.changer.app.dsp.*;
import java.io.*;
import java.util.*;

public class DspTest {
    static int fails = 0, passes = 0;
    static void check(String name, boolean ok, String info) {
        if (ok) passes++; else fails++;
        System.out.printf("[%s] %s  %s%n", ok ? "PASS" : "FAIL", name, info);
    }
    static final int SR = Dsp.SR;

    static float[] sine(double hz, double sec, double amp) {
        float[] x = new float[(int) (sec * SR)];
        for (int i = 0; i < x.length; i++) x[i] = (float) (amp * Math.sin(2 * Math.PI * hz * i / SR));
        return x;
    }
    /** «Голосоподобный» сигнал: гармоники 140 Гц с огибающей слогов. */
    static float[] voiceLike(double sec) {
        float[] x = new float[(int) (sec * SR)];
        for (int i = 0; i < x.length; i++) {
            double t = i / (double) SR, s = 0;
            for (int h = 1; h <= 12; h++) s += Math.sin(2 * Math.PI * 140 * (1 + 0.03 * Math.sin(2 * Math.PI * 3 * t)) * h * t) / h;
            double env = 0.5 + 0.5 * Math.sin(2 * Math.PI * 2.5 * t); env *= env;
            x[i] = (float) (0.4 * s * env);
        }
        return x;
    }
    static double freq(float[] x, int from, int to) {   // нулевые переходы вверх
        int c = 0; double first = -1, last = -1;
        for (int i = from + 1; i < to; i++) if (x[i - 1] < 0 && x[i] >= 0) {
            double fr = i - 1 + (-x[i - 1]) / (x[i] - x[i - 1]);
            if (first < 0) first = fr; last = fr; c++;
        }
        return c < 2 ? 0 : (c - 1) * SR / (last - first);
    }
    /** Частота по максимуму спектра (Гёрцель, шаг 0.25 Гц) — не зависит от модуляции амплитуды. */
    static double peakFreq(float[] x, int from, int to, double lo, double hi) {
        double best = 0, bf = 0;
        for (double f = lo; f <= hi; f += 0.25) {
            double w = 2 * Math.PI * f / SR, c = 2 * Math.cos(w), s1 = 0, s2 = 0;
            for (int i = from; i < to; i++) { double s0 = x[i] + c * s1 - s2; s2 = s1; s1 = s0; }
            double pw = s1 * s1 + s2 * s2 - c * s1 * s2;
            if (pw > best) { best = pw; bf = f; }
        }
        return bf;
    }
    static Pcm pcm(float[] x) {
        short[] d = new short[x.length + Pcm.PAD];
        for (int i = 0; i < x.length; i++) d[i] = (short) (x[i] * 32767);
        return new Pcm(d, x.length, 1);
    }
    static boolean finite(float[] x, int n) { for (int i = 0; i < n; i++) if (Float.isNaN(x[i]) || Float.isInfinite(x[i])) return false; return true; }
    static float peak(float[] x, int n) { float p = 0; for (int i = 0; i < n; i++) p = Math.max(p, Math.abs(x[i])); return p; }
    static double rms(float[] x, int a, int b) { double s = 0; for (int i = a; i < b; i++) s += x[i] * x[i]; return Math.sqrt(s / Math.max(1, b - a)); }

    static float[] renderMono(Pcm v, VoiceSettings s, BackgroundTrack bg) throws IOException { return renderMono(v, s, bg, 0.5f); }
    static float[] renderMono(Pcm v, VoiceSettings s, BackgroundTrack bg, float bgGain) throws IOException {
        LayerMixer mx = null;
        if (bg != null) { mx = new LayerMixer(); mx.add(bg.player(), bgGain); }
        List<float[]> parts = new ArrayList<>(); int[] tot = {0};
        OfflineRenderer.render(v, s, mx, (st, n) -> { parts.add(Arrays.copyOf(st, n * 2)); tot[0] += n; }, null);
        float[] out = new float[tot[0] * 2]; int p = 0;
        for (float[] a : parts) { System.arraycopy(a, 0, out, p, a.length); p += a.length; }
        return out; // interleaved stereo
    }
    static float[] left(float[] st) { float[] l = new float[st.length / 2]; for (int i = 0; i < l.length; i++) l[i] = st[2 * i]; return l; }

    public static void main(String[] a) throws Exception {
        // 1. Живой питч-шифтер
        for (float semis : new float[]{-12, -5, 4, 7, 12}) {
            GranularPitch gp = new GranularPitch(); gp.setRatio(Dsp.semitonesToRatio(semis));
            float[] x = sine(440, 2, 0.5);
            for (int off = 0; off < x.length; off += 512) gp.process(x, off, Math.min(512, x.length - off)); // блоками как в живом режиме
            double want = 440 * Dsp.semitonesToRatio(semis), f = peakFreq(x, SR / 2, SR + SR / 2, want * 0.8, want * 1.2);
            // Предел метода: скачок фазы при перескоке линии задержки смещает пик максимум на ±½·|1-r|·SR/W
            double bound = 0.5 * Math.abs(1 - Dsp.semitonesToRatio(semis)) * SR / 2048.0 + 1.0;
            check("GranularPitch " + semis + " st", Math.abs(f - want) <= bound, String.format("спектр: %.1f Гц, ожидалось %.1f Гц (допуск метода ±%.1f)", f, want, bound));
        }
        { // не должен «плавать» по громкости слишком сильно на синусе
            GranularPitch gp = new GranularPitch(); gp.setRatio(Dsp.semitonesToRatio(4));
            float[] x = sine(220, 2, 0.5); gp.process(x, 0, x.length);
            double mn = 9, mx = 0;
            for (int s = SR / 2; s + 441 < x.length; s += 441) { double r = rms(x, s, s + 441); mn = Math.min(mn, r); mx = Math.max(mx, r); }
            check("GranularPitch громкость", mx < 0.5 && mn > 0.05, String.format("rms 10ms окна: min %.3f max %.3f (ожид. ≈0.35)", mn, mx));
        }

        // 2. WSOLA
        for (double f : new double[]{0.5, 0.8, 1.5, 2.0}) {
            Pcm p = pcm(sine(440, 3, 0.5));
            Wsola w = new Wsola(p.data, p.frames, f);
            float[] out = new float[(int) (p.frames * f) + 5000]; int n = 0, r;
            while ((r = w.read(out, n, 4096)) > 0) n += r;
            double fr = freq(out, n / 4, n * 3 / 4);
            check("WSOLA f=" + f, Math.abs(n - p.frames * f) < 2 && Math.abs(fr - 440) < 4, String.format("len %d (want %.0f), freq %.1f Hz", n, p.frames * f, fr));
        }

        // 3. Рендер: высота + скорость независимо
        {
            float[][] cases = {{0, 1.5f}, {5, 1f}, {5, 1.25f}, {-7, 0.8f}, {12, 2f}, {-12, 0.5f}};
            for (float[] c : cases) {
                Pcm p = pcm(sine(300, 4, 0.5)); VoiceSettings s = new VoiceSettings(); s.pitch = c[0]; s.speed = c[1];
                float[] l = left(renderMono(p, s, null));
                double want = 300 * Dsp.semitonesToRatio(c[0]), len = p.frames / (double) c[1];
                double f = freq(l, l.length / 4, l.length * 3 / 4);
                double rr = rms(l, l.length / 4, l.length * 3 / 4);
                check(String.format("Render pitch %+.0f st, speed %.2fx", c[0], c[1]),
                        Math.abs(l.length - len) < 4 && Math.abs(f - want) / want < 0.015 && Math.abs(rr - 0.3536) < 0.04,
                        String.format("len %d (want %.0f), freq %.1f (want %.1f), rms %.3f (want 0.354)", l.length, len, f, want, rr));
            }
        }

        { // громкость не должна меняться от смены высоты/скорости (баг масштаба short→float ловится здесь)
            Pcm p = pcm(voiceLike(4)); double base = rms(voiceLike(4), 0, p.frames);
            for (float[] c : new float[][]{{4, 1}, {-5, 1}, {0, 1.3f}, {7, 0.9f}}) {
                VoiceSettings s = new VoiceSettings(); s.pitch = c[0]; s.speed = c[1];
                float[] l = left(renderMono(p, s, null));
                double r = rms(l, 0, l.length);
                check(String.format("Громкость голоса %+.0f st / %.1fx", c[0], c[1]), Math.abs(r / base - 1) < 0.25 && peak(l, l.length) < 0.9,
                        String.format("rms %.3f / оригинал %.3f, peak %.2f", r, base, peak(l, l.length)));
            }
        }

        // 4. Каждый эффект: конечные значения, нет взрыва уровня, не молчит
        Pcm vp = pcm(voiceLike(4));
        double inRms = rms(left(renderMono(vp, new VoiceSettings(), null)), 0, vp.frames);
        for (FxType t : FxType.values()) {
            VoiceSettings s = new VoiceSettings(); s.fx[t.ordinal()] = 1f;
            float[] st = renderMono(vp, s, null);
            double r = rms(left(st), 0, vp.frames);
            check("Effect " + t, finite(st, st.length) && peak(st, st.length) <= 1.0001f && r > 0.02,
                    String.format("rms %.3f (оригинал %.3f), peak %.2f", r, inRms, peak(st, st.length)));
        }
        { // шёпот: должен терять тональность → нулевых переходов намного больше, чем у исходника
            VoiceSettings s = new VoiceSettings(); s.fx[FxType.WHISPER.ordinal()] = 1f;
            float[] w = left(renderMono(vp, s, null)), o = voiceLike(4);
            int zw = 0, zo = 0;
            for (int i = 1; i < o.length; i++) { if ((w[i - 1] < 0) != (w[i] < 0)) zw++; if ((o[i - 1] < 0) != (o[i] < 0)) zo++; }
            check("Whisper шумовой", zw > zo * 3, "zero-crossings " + zw + " vs " + zo);
        }
        { // эхо/реверб: хвост после конца голоса есть и затухает
            for (FxType t : new FxType[]{FxType.ECHO, FxType.REVERB}) {
                Pcm burst = pcm(sine(500, 0.3, 0.6)); VoiceSettings s = new VoiceSettings(); s.fx[t.ordinal()] = 1f;
                float[] l = left(renderMono(burst, s, null));
                double tail1 = rms(l, burst.frames + 2000, burst.frames + 12000), tail2 = rms(l, l.length - 8000, l.length);
                check(t + " хвост", l.length > burst.frames + SR && tail1 > 0.002 && tail2 < tail1, String.format("len +%.1fs, rms хвоста %.4f → %.4f", (l.length - burst.frames) / (double) SR, tail1, tail2));
            }
        }

        { // реальный реверс по кускам: после задержки на длину куска идёт перевёрнутый предыдущий кусок
            VoiceSettings vs = new VoiceSettings(); vs.fx[FxType.REVERSE.ordinal()] = 0.1f;   // 0.24 с
            int n = (int) ((0.1f + 1.4f * 0.1f) * SR);
            float[] ramp = new float[n * 4];
            for (int i = 0; i < ramp.length; i++) ramp[i] = (i % n) / (float) n * 0.5f;   // пила в каждом куске
            EffectChain ch = new EffectChain(); ch.apply(vs);
            float[] y = ramp.clone();
            for (int off = 0; off < y.length; off += 512) ch.process(y, off, Math.min(512, y.length - off));   // блоками, как в живом режиме
            boolean silent = true; for (int i = 0; i < n; i++) if (y[i] != 0f) silent = false;
            double err = 0; int cnt = 0;
            for (int k = 250; k < n - 250; k++) {               // середина (без 5-мс плавных краёв) = перевёрнутый первый кусок
                double want = ramp[n - 1 - k]; err += Math.abs(y[n + k] - want); cnt++;
            }
            check("Реверс в реальном времени", silent && err / cnt < 1e-4,
                    String.format("задержка %.2f с (первый кусок тишина: %b), ошибка %.1e", n / (double) SR, silent, err / cnt));
            check("Реверс: хвост учитывает кусок", ch.tailFrames() >= n, "tail " + ch.tailFrames() + " кадров");
        }

        // 5. Все пресеты
        for (Presets.Preset pr : Presets.ALL) {
            VoiceSettings s = new VoiceSettings(); Presets.apply(pr, s);
            float[] st = renderMono(vp, s, null);
            check("Preset " + pr.id, finite(st, st.length) && peak(st, st.length) <= 1.0001f && rms(left(st), 0, st.length / 2) > 0.01, String.format("peak %.2f", peak(st, st.length)));
        }

        // 6. Фон
        for (String id : Ambient.IDS) {
            BackgroundTrack t = Ambient.generate(id, 10);
            float pk = 0; double sum = 0;
            for (int i = 0; i < t.frames * 2; i++) { float v = t.data[i] / 32768f; pk = Math.max(pk, Math.abs(v)); sum += v * v; }
            double seam = Math.abs(t.data[2 * (t.frames - 1)] - t.data[0]) / 32768.0;
            check("Ambient " + id, pk > 0.45 && pk <= 0.51 && Math.sqrt(sum / (t.frames * 2.0)) > 0.02 && seam < 0.35, String.format("peak %.2f rms %.3f шов %.3f", pk, Math.sqrt(sum / (t.frames * 2.0)), seam));
        }
        { // микс фона: длина = голос, громкость слоя регулируется, зацикливание
            Pcm p = pcm(sine(300, 3, 0.3)); BackgroundTrack bg = Ambient.generate("rain", 2);
            float[] st = renderMono(p, new VoiceSettings(), bg, 0.5f);
            float[] st0 = renderMono(p, new VoiceSettings(), bg, 0f);
            double d = 0; for (int i = 0; i < st.length; i++) d += Math.abs(st[i] - st0[i]);
            double lastSecDiff = 0; for (int i = st.length - SR; i < st.length; i++) lastSecDiff += Math.abs(st[i] - st0[i]);
            check("Фон: микс и цикл", st.length / 2 == p.frames && d > 100 && lastSecDiff > 50, String.format("кадров %d, разница %.0f, в последней секунде (после петли) %.0f", st.length / 2, d, lastSecDiff));
        }
        { // несколько слоёв фона суммируются и у каждого своя громкость
            Pcm p = pcm(new float[SR]);   // тишина: слышен только фон
            LayerMixer one = new LayerMixer(), two = new LayerMixer();
            one.add(Ambient.generate("rain", 1).player(), 0.4f);
            two.add(Ambient.generate("rain", 1).player(), 0.4f); two.add(Ambient.generate("wind", 1).player(), 0.4f);
            double[] e = new double[2]; LayerMixer[] mm = {one, two};
            for (int k = 0; k < 2; k++) { double[] acc = {0}; OfflineRenderer.render(p, new VoiceSettings(), mm[k], (st, n) -> { for (int i = 0; i < 2 * n; i++) acc[0] += st[i] * st[i]; }, null); e[k] = acc[0]; }
            check("Фон: два слоя суммируются", e[1] > e[0] * 1.5 && e[0] > 1, String.format("энергия %.0f → %.0f", e[0], e[1]));
        }
        { // реверс: результат = исходник, развёрнутый во времени
            float[] src = voiceLike(2); Pcm p = pcm(src);
            VoiceSettings s = new VoiceSettings(); s.reverse = true;
            float[] l = left(renderMono(p, s, null));
            double diff = 0, ref = 0;
            for (int i = 0; i < src.length; i++) { double d = l[i] - Dsp.softClip(src[src.length - 1 - i]); diff += d * d; ref += src[i] * src[i]; }
            check("Реверс", l.length == src.length && diff / ref < 1e-4, String.format("длина %d, относит. ошибка %.2e", l.length, diff / ref));
        }

        // 7. WAV туда-обратно + ресэмплинг при чтении
        {
            File f = File.createTempFile("tst1", ".wav");
            short[] d = new short[22050 * 2];
            for (int i = 0; i < d.length; i++) d[i] = (short) (12000 * Math.sin(2 * Math.PI * 1000 * i / 22050.0));
            try (WavWriter w = new WavWriter(f, 1, 22050)) { w.write(d, d.length); }
            WavSource ws = WavSource.open(f, 1);
            Pcm p = Pcm.fromStream(ws, 1, SR * 60); ws.close();
            float[] x = new float[p.frames]; for (int i = 0; i < x.length; i++) x[i] = p.data[i] / 32768f;
            double fr = freq(x, 1000, x.length - 1000);
            check("WAV round-trip 22.05k→44.1k", Math.abs(p.frames - SR * 2) <= 2 && Math.abs(fr - 1000) < 5, String.format("frames %d, freq %.1f", p.frames, fr));
            // стерео → моно, 48 кГц
            File f2 = File.createTempFile("tst2", ".wav");
            short[] st = new short[48000 * 2 * 2];
            for (int i = 0; i < st.length / 2; i++) { st[2 * i] = (short) (10000 * Math.sin(2 * Math.PI * 500 * i / 48000.0)); st[2 * i + 1] = st[2 * i]; }
            try (WavWriter w = new WavWriter(f2, 2, 48000)) { w.write(st, st.length); }
            WavSource w2 = WavSource.open(f2, 1); Pcm p2 = Pcm.fromStream(w2, 1, SR * 60);
            float[] x2 = new float[p2.frames]; for (int i = 0; i < x2.length; i++) x2[i] = p2.data[i] / 32768f;
            check("WAV 48k стерео→моно", Math.abs(p2.frames - SR * 2) <= 2 && Math.abs(freq(x2, 1000, x2.length - 1000) - 500) < 3, "frames " + p2.frames);
            // заголовок с нулевым размером data (так иногда пишут TTS-движки)
            try (RandomAccessFile r = new RandomAccessFile(f, "rw")) { r.seek(40); r.write(new byte[4]); }
            WavSource w3 = WavSource.open(f, 1);
            check("WAV data size = 0", w3 != null && Pcm.fromStream(w3, 1, SR * 60).frames > SR * 1.9, "читается до конца файла");
            File junk = File.createTempFile("junk", ".bin"); try (FileOutputStream o = new FileOutputStream(junk)) { o.write("not a wav at all, just text".getBytes()); }
            check("Не-WAV → null (уйдёт в MediaCodec)", WavSource.open(junk, 1) == null, "");
            Pcm big = null; boolean thrown = false;
            try { WavSource w4 = WavSource.open(f2, 1); big = Pcm.fromStream(w4, 1, SR); } catch (Pcm.TooLongException e) { thrown = true; }
            check("Лимит длины", thrown, "TooLongException");
        }

        // 7b. FLAC: побитово совпадает после декодирования ffmpeg
        {
            Random rnd = new Random(7);
            int[][] cases = {{10000, 0}, {9000, 1}, {100, 0}, {5000, 2}, {8192, 0}, {6000, 3}, {1, 0}, {4097, 1}};
            String[] names = {"стерео тон+шум", "моно (L=R)", "короткий", "тишина", "ровно 2 блока", "полный шум (verbatim)", "1 кадр", "4097 кадров"};
            for (int c = 0; c < cases.length; c++) {
                int n = cases[c][0], kind = cases[c][1];
                short[] st = new short[n * 2];
                for (int i = 0; i < n; i++) {
                    short l, r;
                    switch (kind) {
                        case 0: l = (short) (8000 * Math.sin(i * 0.05) + rnd.nextInt(200) - 100); r = (short) (6000 * Math.sin(i * 0.031) + rnd.nextInt(100)); break;
                        case 1: l = r = (short) (12000 * Math.sin(i * 0.02)); break;
                        case 2: l = r = 0; break;
                        default: l = (short) (rnd.nextInt(65536) - 32768); r = (short) (rnd.nextInt(65536) - 32768);
                    }
                    st[2 * i] = l; st[2 * i + 1] = r;
                }
                File f = File.createTempFile("flac", ".flac");
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(f))) { FlacWriter.write(st, n, SR, os); }
                byte[] raw;
                try {
                    Process pr = new ProcessBuilder("ffmpeg", "-v", "error", "-i", f.getPath(), "-f", "s16le", "-acodec", "pcm_s16le", "-").redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    raw = pr.getInputStream().readAllBytes(); pr.waitFor();
                } catch (IOException noFfmpeg) {
                    System.out.println("[SKIP] FLAC " + names[c] + "  ffmpeg не найден — проверка декодером пропущена");
                    continue;
                }
                int chn = kind == 1 || kind == 2 ? 1 : 2;
                boolean ok = raw.length == n * chn * 2;
                if (ok) for (int i = 0; i < n && ok; i++) for (int k = 0; k < chn; k++) {
                    int lo = raw[(i * chn + k) * 2] & 0xFF, hi = raw[(i * chn + k) * 2 + 1];
                    if ((short) ((hi << 8) | lo) != st[2 * i + k]) ok = false;
                }
                check("FLAC " + names[c], ok, String.format("%d кадров → %d байт FLAC, декодировано %d байт (%s)", n, f.length(), raw.length, chn == 1 ? "моно" : "стерео"));
            }
        }

        // 7c. Пресеты: количество и уникальность id
        {
            Set<String> ids = new HashSet<>(); boolean uniq = true;
            for (Presets.Preset p : Presets.ALL) if (!ids.add(p.id)) uniq = false;
            check("Пресеты: уникальные id", uniq && Presets.ALL.size() >= 55, Presets.ALL.size() + " готовых голосов");
        }

        // 8. Разбиение текста
        {
            String t = "Привет! Как дела? Это длинный текст. ".repeat(200) + "Предложениебезпробеловидлинное".repeat(30);
            List<String> ch = TextChunker.split(t, 300); boolean ok = true; int len = 0;
            for (String c : ch) { if (c.length() > 300) ok = false; len += c.replace(" ", "").length(); }
            check("TextChunker", ok && len == t.replace(" ", "").length(), ch.size() + " кусков, потерь нет");
            check("TextChunker пустой", TextChunker.split("   ", 100).isEmpty(), "");
            check("TextChunker короткий", TextChunker.split("Один.", 100).equals(List.of("Один.")), "");
        }

        // 9. Производительность: 10 минут голоса, высота +4 и скорость 1.2
        {
            float[] x = voiceLike(60); float[] big = new float[x.length * 10];
            for (int i = 0; i < 10; i++) System.arraycopy(x, 0, big, i * x.length, x.length);
            Pcm p = pcm(big); VoiceSettings s = new VoiceSettings(); s.pitch = 4; s.speed = 1.2f; s.fx[FxType.REVERB.ordinal()] = 0.5f; s.fx[FxType.CHORUS.ordinal()] = 0.5f;
            long t0 = System.nanoTime(); long[] cnt = {0};
            OfflineRenderer.render(p, s, null, (st, n) -> cnt[0] += n, null);
            double sec = (System.nanoTime() - t0) / 1e9;
            check("Скорость 10 мин аудио", cnt[0] > 0, String.format("%.1f c на десктопе (%.0fx реального времени)", sec, 600 / sec));
        }
        { // отмена
            Pcm p = pcm(sine(300, 5, 0.3)); boolean cancelled = false;
            try { OfflineRenderer.render(p, new VoiceSettings(), null, (st, n) -> {}, f -> false); } catch (OfflineRenderer.Cancelled e) { cancelled = true; }
            check("Отмена рендера", cancelled, "");
        }
        { // пустой вход
            Pcm p = pcm(new float[0]); VoiceSettings s = new VoiceSettings(); s.pitch = 3; s.speed = 1.3f;
            float[] st = renderMono(p, s, null);
            check("Пустой вход", st.length == 0, "");
        }

        System.out.printf("%n%d passed, %d failed%n", passes, fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
