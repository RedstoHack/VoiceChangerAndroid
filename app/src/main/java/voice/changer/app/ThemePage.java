package voice.changer.app;

import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

/** Оформление: цвета, фон (картинка / GIF / видео), картинка на кнопках, ярлык со своей иконкой. */
final class ThemePage {
    private ThemePage() {}

    private static void colorRow(MainActivity m, LinearLayout parent, int labelRes, String key, int current) {
        LinearLayout r = Ui.h(m);
        r.setPadding(0, Ui.dp(m, 10), 0, Ui.dp(m, 10));
        r.addView(Ui.text(m, m.getString(labelRes), 15, Ui.TEXT), Ui.lp(0, -2, 1));
        View sw = new View(m);
        sw.setBackground(Ui.rect(m, current, 8));
        r.addView(sw, new LinearLayout.LayoutParams(Ui.dp(m, 44), Ui.dp(m, 28)));
        r.setClickable(true);
        r.setOnClickListener(v -> Dialogs.color(m, m.getString(labelRes), current, col -> {
            Theme.setColor(key, col);
            Theme.load(m);
            sw.setBackground(Ui.rect(m, col, 8));
            Theme.dirty = true;
            m.applyThemeIfChanged();
        }));
        parent.addView(r);
    }

    private static void pickImage(MainActivity m, boolean allowVideo, java.util.function.Consumer<Uri> done) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, allowVideo ? new String[]{"image/*", "video/*"} : new String[]{"image/*"});
        m.startForResult(i, (res, d) -> {
            if (res == BaseActivity.RESULT_OK && d != null && d.getData() != null) done.accept(d.getData());
        });
    }

    static View build(MainActivity m) {
        Theme.dirty = false;
        LinearLayout page = Ui.v(m);
        page.setBackgroundColor(Ui.pageColor());
        page.addView(Ui.pageHeader(m, m.getString(R.string.theme), v -> m.pop()));
        ScrollView sv = new ScrollView(m);
        LinearLayout col = Ui.v(m);

        LinearLayout colors = Ui.card(m);
        colors.addView(Ui.text(m, m.getString(R.string.theme_colors), 14, Ui.TEXT2));
        colorRow(m, colors, R.string.color_bg, "c_bg", Ui.BG);
        colorRow(m, colors, R.string.color_surface, "c_surface", Ui.SURFACE);
        colorRow(m, colors, R.string.color_buttons, "c_accent", Ui.ACCENT);
        colorRow(m, colors, R.string.color_buttons2, "c_surface2", Ui.SURFACE2);
        colorRow(m, colors, R.string.color_text, "c_text", Ui.TEXT);
        colorRow(m, colors, R.string.color_text2, "c_text2", Ui.TEXT2);
        colorRow(m, colors, R.string.color_link, "c_link", Ui.LINK);
        col.addView(colors);

        LinearLayout look = Ui.card(m);
        look.addView(Ui.text(m, m.getString(R.string.theme_look), 14, Ui.TEXT2));
        Ui.Slider scale = new Ui.Slider(m, m.getString(R.string.theme_scale), 0.8f, 1.5f, 0.05f, v -> Math.round(v * 100) + "%", v -> { Theme.setScale(v); Theme.dirty = true; });
        scale.set(Theme.scale);
        look.addView(scale, Ui.wrapMatch(m, 8));
        Ui.Slider round = new Ui.Slider(m, m.getString(R.string.theme_round), 0f, 2f, 0.1f, v -> Math.round(v * 100) + "%", v -> { Theme.setRound(v); Theme.dirty = true; });
        round.set(Theme.round);
        look.addView(round, Ui.wrapMatch(m, 12));
        Ui.Slider micSz = new Ui.Slider(m, m.getString(R.string.theme_mic), 160, 320, 10, v -> Math.round(v) + " dp", v -> { Theme.setMic(Math.round(v)); Theme.dirty = true; });
        micSz.set(Theme.micDp);
        look.addView(micSz, Ui.wrapMatch(m, 12));
        col.addView(look, Ui.wrapMatch(m, 12));

        LinearLayout media = Ui.card(m);
        media.addView(Ui.text(m, m.getString(R.string.theme_bg), 14, Ui.TEXT2));
        TextView state = Ui.text(m, bgState(m), 13, Ui.TEXT2);
        media.addView(state, Ui.wrapMatch(m, 6));
        media.addView(Ui.button(m, m.getString(R.string.theme_bg_pick), false, v -> pickImage(m, true, u -> copy(m, u, Theme.bgFile(m), true, state))), Ui.wrapMatch(m, 8));
        media.addView(Ui.button(m, m.getString(R.string.theme_remove), false, v -> {
            Theme.setBg(m, "none");
            state.setText(bgState(m));
            Theme.dirty = true;
            m.applyThemeIfChanged();
        }), Ui.wrapMatch(m, 8));
        Ui.Slider dim = new Ui.Slider(m, m.getString(R.string.theme_dim), 0, 90, 5, v -> Math.round(v) + "%", v -> {
            Theme.setDim(Math.round(v));
            Theme.dirty = true;
        });
        dim.set(Theme.dim);
        media.addView(dim, Ui.wrapMatch(m, 14));
        col.addView(media, Ui.wrapMatch(m, 12));

        LinearLayout btn = Ui.card(m);
        btn.addView(Ui.text(m, m.getString(R.string.theme_btn), 14, Ui.TEXT2));
        btn.addView(Ui.text(m, m.getString(R.string.theme_btn_note), 12, Ui.TEXT2), Ui.wrapMatch(m, 4));
        TextView btnState = Ui.text(m, Theme.btnImage ? m.getString(R.string.theme_set) : m.getString(R.string.theme_not_set), 13, Ui.TEXT2);
        btn.addView(btnState, Ui.wrapMatch(m, 6));
        btn.addView(Ui.button(m, m.getString(R.string.theme_btn_pick), false, v -> pickImage(m, false, u -> {
            Jobs.IO.execute(() -> {
                try {
                    Files.copyUri(m, u, Theme.btnFile(m));
                    Jobs.ui(() -> {
                        Theme.setBtnImage(m, true);
                        btnState.setText(R.string.theme_set);
                        Theme.dirty = true;
                        m.applyThemeIfChanged();
                    });
                } catch (Exception e) {
                    AppLog.err("button image", e);
                    Jobs.ui(() -> Toast.makeText(m, R.string.bg_failed, Toast.LENGTH_LONG).show());
                }
            });
        })), Ui.wrapMatch(m, 8));
        btn.addView(Ui.button(m, m.getString(R.string.theme_remove), false, v -> {
            Theme.setBtnImage(m, false);
            btnState.setText(R.string.theme_not_set);
            Theme.dirty = true;
            m.applyThemeIfChanged();
        }), Ui.wrapMatch(m, 8));
        col.addView(btn, Ui.wrapMatch(m, 12));

        LinearLayout icon = Ui.card(m);
        icon.addView(Ui.text(m, m.getString(R.string.theme_icon), 14, Ui.TEXT2));
        icon.addView(Ui.text(m, m.getString(R.string.theme_icon_note), 12, Ui.TEXT2), Ui.wrapMatch(m, 4));
        icon.addView(Ui.button(m, m.getString(R.string.theme_icon_pick), false, v -> pickImage(m, false, u -> makeShortcut(m, u))), Ui.wrapMatch(m, 10));
        icon.addView(Ui.button(m, m.getString(R.string.theme_header_remove), false, v -> {
            Theme.headerFile(m).delete();
            Theme.dirty = true;
            m.applyThemeIfChanged();
        }), Ui.wrapMatch(m, 8));
        col.addView(icon, Ui.wrapMatch(m, 12));

        col.addView(Ui.button(m, m.getString(R.string.theme_reset), false, v -> Dialogs.confirm(m, m.getString(R.string.theme_reset_q), () -> {
            Theme.reset(m);
            Theme.dirty = true;
            m.pop();
            m.applyThemeIfChanged();
        })), Ui.wrapMatch(m, 14));

        sv.addView(col);
        page.addView(sv, Ui.lp(-1, 0, 1));
        int mg = Ui.dp(m, 16);
        ((LinearLayout.LayoutParams) sv.getLayoutParams()).setMargins(mg, 0, mg, mg);
        return page;
    }

    private static String bgState(MainActivity m) {
        switch (Theme.bgType) {
            case "image": return m.getString(R.string.theme_state_image);
            case "gif": return m.getString(R.string.theme_state_gif);
            case "video": return m.getString(R.string.theme_state_video);
            default: return m.getString(R.string.theme_not_set);
        }
    }

    /** Копирует выбранный файл в закрытую папку и определяет его тип: картинка, GIF или видео. */
    private static void copy(MainActivity m, Uri u, File dst, boolean isBg, TextView state) {
        Toast.makeText(m, R.string.bg_loading, Toast.LENGTH_SHORT).show();
        Jobs.IO.execute(() -> {
            try {
                String mime = m.getContentResolver().getType(u);
                Files.copyUri(m, u, dst);
                String type = mime == null ? "image" : mime.startsWith("video/") ? "video" : mime.equals("image/gif") ? "gif" : "image";
                Jobs.ui(() -> {
                    Theme.setBg(m, type);
                    state.setText(bgState(m));
                    Theme.dirty = true;
                    m.applyThemeIfChanged();
                });
            } catch (Exception e) {
                AppLog.err("theme background", e);
                dst.delete();
                Jobs.ui(() -> Toast.makeText(m, R.string.bg_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    /**
     * Android не позволяет приложению сменить собственную иконку в лаунчере, поэтому с выбранной картинкой можно:
     * создать ярлык на главном экране (он запускает это приложение) и/или поставить её в шапку приложения.
     */
    private static void makeShortcut(MainActivity m, Uri u) {
        Jobs.IO.execute(() -> {
            try {
                File tmp = Files.tmp(m, "icon.dat");
                Files.copyUri(m, u, tmp);
                Bitmap src = Theme.decode(tmp, 256);
                tmp.delete();
                if (src == null) throw new java.io.IOException("decode");
                int side = Math.min(src.getWidth(), src.getHeight());
                Bitmap sq = Bitmap.createBitmap(src, (src.getWidth() - side) / 2, (src.getHeight() - side) / 2, side, side);
                Bitmap icon = Bitmap.createScaledBitmap(sq, 192, 192, true);
                Jobs.ui(() -> preview(m, icon));
            } catch (Exception e) {
                AppLog.err("icon", e);
                Jobs.ui(() -> Toast.makeText(m, R.string.bg_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void preview(MainActivity m, Bitmap icon) {
        Dialogs.Sheet s = Dialogs.sheet(m, m.getString(R.string.theme_icon_preview), false);
        android.widget.ImageView iv = new android.widget.ImageView(m);
        iv.setImageBitmap(icon);
        s.body.addView(iv, new LinearLayout.LayoutParams(-1, Ui.dp(m, 120)));
        s.body.addView(Ui.button(m, m.getString(R.string.theme_icon_add), true, v -> {
            s.dismiss();
            addShortcut(m, icon);
        }), Ui.wrapMatch(m, 14));
        s.body.addView(Ui.button(m, m.getString(R.string.theme_header_icon), false, v -> {
            s.dismiss();
            Jobs.IO.execute(() -> {
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(Theme.headerFile(m))) {
                    icon.compress(Bitmap.CompressFormat.PNG, 100, out);
                    Jobs.ui(() -> { Theme.dirty = true; m.applyThemeIfChanged(); });
                } catch (Exception e) {
                    AppLog.err("header icon", e);
                }
            });
        }), Ui.wrapMatch(m, 8));
        s.body.addView(Ui.button(m, m.getString(R.string.cancel), false, v -> s.dismiss()), Ui.wrapMatch(m, 8));
        Dialogs.show(m, s);
    }

    private static void addShortcut(MainActivity m, Bitmap icon) {
        ShortcutManager sm = m.getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            Toast.makeText(m, R.string.theme_icon_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        Intent launch = new Intent(Intent.ACTION_MAIN).setClass(m, MainActivity.class);
        ShortcutInfo info = new ShortcutInfo.Builder(m, "vc_" + System.currentTimeMillis())
                .setShortLabel(m.getString(R.string.app_name))
                .setIcon(Icon.createWithBitmap(icon))
                .setIntent(launch)
                .build();
        sm.requestPinShortcut(info, null);
    }
}
