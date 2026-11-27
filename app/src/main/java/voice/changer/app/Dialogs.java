package voice.changer.app;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Диалоги в цветах приложения (системные берут цвета темы устройства и могли бы стать нечитаемыми). */
final class Dialogs {
    private Dialogs() {}

    interface Pick { void on(int index); }
    interface Text { void on(String text); }
    interface ColorPick { void on(int color); }

    /** Пустое окно с заголовком; содержимое добавляется в возвращённый контейнер. */
    static final class Sheet {
        final Dialog dialog;
        final LinearLayout body;
        Sheet(Dialog d, LinearLayout b) { dialog = d; body = b; }
        void dismiss() { dialog.dismiss(); }
    }

    static Sheet sheet(Context c, CharSequence title, boolean scroll) {
        Dialog d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = Ui.v(c);
        root.setBackground(Ui.rect(c, Ui.pageColor() | 0xFF000000, 20));
        int p = Ui.dp(c, 18);
        root.setPadding(p, p, p, p);
        if (title != null) {
            TextView t = Ui.text(c, title, 19, Ui.TEXT);
            t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            root.addView(t);
        }
        LinearLayout body = Ui.v(c);
        if (scroll) {
            ScrollView sv = new ScrollView(c);
            sv.addView(body);
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2, 0);
            lp.topMargin = Ui.dp(c, 10);
            root.addView(sv, lp);
        } else {
            root.addView(body, Ui.wrapMatch(c, 10));
        }
        d.setContentView(root);
        d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        return new Sheet(d, body);
    }

    static void show(Context c, Sheet s) {
        s.dialog.show();
        int w = c.getResources().getDisplayMetrics().widthPixels;
        int h = c.getResources().getDisplayMetrics().heightPixels;
        s.dialog.getWindow().setLayout(Math.min(Ui.dp(c, 400), (int) (w * 0.92f)), -2);
        // длинные списки не должны вылезать за экран
        if (s.body.getParent() instanceof ScrollView) {
            ScrollView sv = (ScrollView) s.body.getParent();
            sv.setMinimumHeight(0);
            android.view.ViewGroup.LayoutParams lp = sv.getLayoutParams();
            lp.height = Math.min(Ui.dp(c, 520), (int) (h * 0.7f));
            sv.setLayoutParams(lp);
        }
    }

    static void list(Context c, CharSequence title, String[] items, Pick pick) {
        Sheet s = sheet(c, title, true);
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView t = Ui.text(c, items[i], 16, Ui.TEXT);
            t.setPadding(Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14));
            t.setClickable(true);
            t.setOnClickListener(v -> { s.dismiss(); pick.on(idx); });
            s.body.addView(t);
        }
        show(c, s);
    }

    static void confirm(Context c, CharSequence msg, Runnable yes) {
        Sheet s = sheet(c, null, false);
        s.body.addView(Ui.text(c, msg, 16, Ui.TEXT));
        LinearLayout row = Ui.h(c);
        row.addView(Ui.button(c, c.getString(R.string.cancel), false, v -> s.dismiss()), Ui.lp(0, -2, 1));
        LinearLayout.LayoutParams lp = Ui.lp(0, -2, 1);
        lp.leftMargin = Ui.dp(c, 8);
        row.addView(Ui.button(c, c.getString(R.string.ok), true, v -> { s.dismiss(); yes.run(); }), lp);
        s.body.addView(row, Ui.wrapMatch(c, 16));
        show(c, s);
    }

    static void info(Context c, CharSequence title, CharSequence msg) {
        Sheet s = sheet(c, title, true);
        s.body.addView(Ui.text(c, msg, 15, Ui.TEXT));
        show(c, s);
    }

    static void prompt(Context c, CharSequence title, String def, Text done) {
        Sheet s = sheet(c, title, false);
        EditText e = new EditText(c);
        e.setText(def);
        e.setSingleLine(true);
        e.setTextColor(Ui.TEXT);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        s.body.addView(e, Ui.wrapMatch(c, 0));
        LinearLayout row = Ui.h(c);
        row.addView(Ui.button(c, c.getString(R.string.cancel), false, v -> s.dismiss()), Ui.lp(0, -2, 1));
        LinearLayout.LayoutParams lp = Ui.lp(0, -2, 1);
        lp.leftMargin = Ui.dp(c, 8);
        row.addView(Ui.button(c, c.getString(R.string.ok), true, v -> {
            String t = e.getText().toString().trim();
            if (t.isEmpty()) return;
            s.dismiss();
            done.on(t);
        }), lp);
        s.body.addView(row, Ui.wrapMatch(c, 16));
        show(c, s);
    }

    /** Выбор цвета тремя ползунками. */
    static void color(Context c, CharSequence title, int initial, ColorPick done) {
        Sheet s = sheet(c, title, false);
        final int[] rgb = {Color.red(initial), Color.green(initial), Color.blue(initial)};
        View_ swatch = new View_(c);
        s.body.addView(swatch.v, Ui.lp(-1, Ui.dp(c, 48), 0));
        TextView hex = Ui.text(c, "", 14, Ui.TEXT2);
        s.body.addView(hex, Ui.wrapMatch(c, 6));
        Runnable upd = () -> {
            int col = Color.rgb(rgb[0], rgb[1], rgb[2]);
            swatch.v.setBackground(Ui.rect(c, col, 10));
            hex.setText(String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]));
        };
        String[] names = {"R", "G", "B"};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            Ui.Slider sl = new Ui.Slider(c, names[i], 0, 255, 1, v -> String.valueOf(Math.round(v)), v -> { rgb[k] = Math.round(v); upd.run(); });
            sl.set(rgb[i]);
            s.body.addView(sl, Ui.wrapMatch(c, 8));
        }
        upd.run();
        LinearLayout row = Ui.h(c);
        row.addView(Ui.button(c, c.getString(R.string.cancel), false, v -> s.dismiss()), Ui.lp(0, -2, 1));
        LinearLayout.LayoutParams lp = Ui.lp(0, -2, 1);
        lp.leftMargin = Ui.dp(c, 8);
        row.addView(Ui.button(c, c.getString(R.string.ok), true, v -> { s.dismiss(); done.on(Color.rgb(rgb[0], rgb[1], rgb[2])); }), lp);
        s.body.addView(row, Ui.wrapMatch(c, 16));
        show(c, s);
    }

    /** Простой образец цвета. */
    private static final class View_ {
        final android.view.View v;
        View_(Context c) { v = new android.view.View(c); }
    }
}
