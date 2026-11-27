package voice.changer.app;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

import voice.changer.app.dsp.Ambient;
import voice.changer.app.dsp.BgLayer;
import voice.changer.app.dsp.VoiceSettings;

/** «Фон и музыка»: несколько слоёв сразу (звуки и свои файлы любой длины), у каждого своя громкость; сохранение комбинаций. */
final class BackgroundPage {
    final View view;
    private final MainActivity m;
    private final LinearLayout layersBox, presetsBox;

    BackgroundPage(MainActivity m) {
        this.m = m;
        LinearLayout page = Ui.v(m);
        page.setBackgroundColor(Ui.pageColor());
        page.addView(Ui.pageHeader(m, m.getString(R.string.background), v -> m.pop()));
        ScrollView sv = new ScrollView(m);
        LinearLayout col = Ui.v(m);

        col.addView(Ui.text(m, m.getString(R.string.bg_layers), 14, Ui.TEXT2));
        layersBox = Ui.card(m);
        col.addView(layersBox, Ui.wrapMatch(m, 8));

        col.addView(Ui.text(m, m.getString(R.string.bg_add), 14, Ui.TEXT2), Ui.wrapMatch(m, 18));
        LinearLayout add = Ui.card(m);
        java.util.List<TextView> chips = new java.util.ArrayList<>();
        for (String k : Ambient.IDS) chips.add(Ui.chip(m, "+ " + m.getString(Labels.bg(k)), false, v -> {
            Store.addLayer(new BgLayer(k));
            rebuild();
        }));
        add.addView(Ui.chipGrid(m, chips, 3));
        add.addView(Ui.button(m, m.getString(R.string.bg_file_pick), false, v -> pickFile()), Ui.wrapMatch(m, 14));
        add.addView(Ui.text(m, m.getString(R.string.bg_file_note), 12, Ui.TEXT2), Ui.wrapMatch(m, 8));
        col.addView(add, Ui.wrapMatch(m, 8));

        col.addView(Ui.text(m, m.getString(R.string.bg_saved), 14, Ui.TEXT2), Ui.wrapMatch(m, 18));
        LinearLayout saved = Ui.card(m);
        saved.addView(Ui.button(m, m.getString(R.string.bg_save_combo), false, v ->
                Dialogs.prompt(m, m.getString(R.string.bg_save_combo), "", name -> {
                    Store.saveBgPreset(name);
                    rebuild();
                })));
        presetsBox = Ui.v(m);
        saved.addView(presetsBox, Ui.wrapMatch(m, 8));
        col.addView(saved, Ui.wrapMatch(m, 8));

        sv.addView(col);
        page.addView(sv, Ui.lp(-1, 0, 1));
        int mg = Ui.dp(m, 16);
        ((LinearLayout.LayoutParams) sv.getLayoutParams()).setMargins(mg, 0, mg, mg);
        view = page;
        rebuild();
    }

    private void rebuild() {
        VoiceSettings s = Store.get();
        layersBox.removeAllViews();
        if (s.bg.isEmpty()) layersBox.addView(Ui.text(m, m.getString(R.string.bg_empty), 14, Ui.TEXT2));
        for (int i = 0; i < s.bg.size(); i++) {
            final int idx = i;
            BgLayer l = s.bg.get(i);
            String title = "file".equals(l.kind) ? (l.name.isEmpty() ? m.getString(Labels.bg("file")) : l.name) : m.getString(Labels.bg(l.kind));
            LinearLayout head = Ui.h(m);
            TextView t = Ui.text(m, title, 15, Ui.TEXT);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            head.addView(t, Ui.lp(0, -2, 1));
            TextView x = Ui.text(m, "✕", 18, Ui.TEXT2);
            x.setPadding(Ui.dp(m, 14), Ui.dp(m, 6), Ui.dp(m, 4), Ui.dp(m, 6));
            x.setClickable(true);
            x.setOnClickListener(v -> { Store.removeLayer(idx); rebuild(); });
            head.addView(x);
            layersBox.addView(head, Ui.wrapMatch(m, i == 0 ? 0 : 14));
            Ui.Slider vol = new Ui.Slider(m, m.getString(R.string.bg_volume), 0f, 1f, 0.05f,
                    v -> Math.round(v * 100) + "%", v -> Store.setLayerVolume(idx, v));
            vol.set(l.volume);
            layersBox.addView(vol);
        }

        presetsBox.removeAllViews();
        List<Store.BgPreset> ps = Store.bgPresets();
        for (Store.BgPreset p : ps) {
            LinearLayout r = Ui.h(m);
            TextView t = Ui.text(m, p.name + "  (" + p.layers.size() + ")", 15, Ui.TEXT);
            t.setPadding(0, Ui.dp(m, 12), 0, Ui.dp(m, 12));
            t.setClickable(true);
            t.setOnClickListener(v -> { Store.applyBgPreset(p); rebuild(); });
            r.addView(t, Ui.lp(0, -2, 1));
            TextView x = Ui.text(m, "✕", 18, Ui.TEXT2);
            x.setPadding(Ui.dp(m, 14), Ui.dp(m, 8), Ui.dp(m, 4), Ui.dp(m, 8));
            x.setClickable(true);
            x.setOnClickListener(v -> Dialogs.confirm(m, m.getString(R.string.delete_voice_q, p.name), () -> {
                Store.deleteBgPreset(p.name);
                rebuild();
            }));
            r.addView(x);
            presetsBox.addView(r);
        }
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        m.startForResult(i, (res, d) -> {
            if (res != BaseActivity.RESULT_OK || d == null || d.getData() == null) return;
            final Uri u = d.getData();
            Toast.makeText(m, R.string.bg_loading, Toast.LENGTH_SHORT).show();
            Jobs.IO.execute(() -> {
                String id = Long.toHexString(System.nanoTime());
                File dst = Files.bgLayerFile(m, id);
                try {
                    String name = Files.copyUri(m, u, dst);
                    new AudioDecoderProbe(dst).run();     // проверяем, что файл вообще читается
                    BgLayer l = new BgLayer("file");
                    l.file = id;
                    l.name = name;
                    Jobs.ui(() -> { Store.addLayer(l); rebuild(); });
                } catch (Exception e) {
                    AppLog.err("bg file", e);
                    dst.delete();
                    Jobs.ui(() -> Toast.makeText(m, R.string.bg_failed, Toast.LENGTH_LONG).show());
                }
            });
        });
    }

    /** Открывает файл и читает пару блоков — быстрая проверка, что формат поддерживается. */
    private static final class AudioDecoderProbe {
        private final File f;
        AudioDecoderProbe(File f) { this.f = f; }
        void run() throws java.io.IOException {
            voice.changer.app.dsp.PcmStream st = voice.changer.app.dsp.WavSource.open(f, 2);
            if (st == null) st = new AudioDecoder(f.getPath(), 2);
            try {
                if (st.read(new float[4096 * 2], 0, 4096) <= 0) throw new java.io.IOException("empty");
            } finally {
                st.close();
            }
        }
    }
}
