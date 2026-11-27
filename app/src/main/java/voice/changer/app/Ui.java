package voice.changer.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

/** Помощники для программной разметки. Цвета берутся из {@link Theme} и меняются в Настройки → Оформление. */
final class Ui {
    private Ui() {}

    static int BG = Theme.D_BG, SURFACE = Theme.D_SURFACE, SURFACE2 = Theme.D_SURFACE2, ACCENT = Theme.D_ACCENT,
            TEXT = Theme.D_TEXT, TEXT2 = Theme.D_TEXT2, LINK = Theme.D_LINK;
    static final int ERR = 0xFFFF6B6B;

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }

    /** Цвет карточек: полупрозрачный, если на фоне картинка или видео. */
    static int cardColor() { return Theme.hasMedia() ? alpha(SURFACE, 0xCC) : SURFACE; }
    static int pageColor() { return Theme.hasMedia() ? alpha(BG, 0xE6) : BG; }

    static GradientDrawable rect(Context c, int color, float rdp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, rdp * Theme.round));
        return g;
    }

    static TextView text(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp * Theme.scale);
        t.setTextColor(color);
        return t;
    }

    static LinearLayout v(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout h(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static LinearLayout.LayoutParams wrapMatch(Context c, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(c, topDp);
        return p;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = v(c);
        l.setBackground(rect(c, cardColor(), 16));
        int p = dp(c, 16);
        l.setPadding(p, p, p, p);
        return l;
    }

    private static void roundClip(View v, float rdp) {
        final float r = dp(v.getContext(), rdp * Theme.round);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline o) { o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r); }
        });
        v.setClipToOutline(true);
    }

    private static void ripple(View v, Drawable bg) {
        v.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), bg, null));
    }

    /** Фон главной кнопки: цвет или своя картинка. */
    private static void primaryBg(TextView t) {
        Context c = t.getContext();
        Bitmap bmp = Theme.btnBitmap(c);
        if (bmp != null) {
            Drawable[] layers = {new BitmapDrawable(c.getResources(), bmp), rect(c, alpha(ACCENT, 0x55), 14)};
            ripple(t, new LayerDrawable(layers));
            roundClip(t, 14);
        } else {
            ripple(t, rect(c, ACCENT, 14));
        }
    }

    static TextView button(Context c, CharSequence s, boolean primary, View.OnClickListener l) {
        TextView t = text(c, s, 16, primary ? Color.WHITE : TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        if (primary) primaryBg(t); else ripple(t, rect(c, SURFACE2, 14));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    static void setButtonColor(TextView t, boolean primary) {
        if (primary) primaryBg(t); else ripple(t, rect(t.getContext(), SURFACE2, 14));
        t.setTextColor(primary ? Color.WHITE : TEXT);
    }

    static TextView chip(Context c, CharSequence s, boolean selected, View.OnClickListener l) {
        TextView t = text(c, s, 14, selected ? Color.WHITE : TEXT);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(dp(c, 8), dp(c, 12), dp(c, 8), dp(c, 12));
        ripple(t, rect(c, selected ? ACCENT : SURFACE2, 12));
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    static void setChip(TextView t, boolean selected) {
        ripple(t, rect(t.getContext(), selected ? ACCENT : SURFACE2, 12));
        t.setTextColor(selected ? Color.WHITE : TEXT);
    }

    /** Сетка чипов по cols в ряд. */
    static LinearLayout chipGrid(Context c, List<TextView> chips, int cols) {
        LinearLayout grid = v(c);
        LinearLayout row = null;
        for (int i = 0; i < chips.size(); i++) {
            if (i % cols == 0) {
                row = h(c);
                grid.addView(row, wrapMatch(c, i == 0 ? 0 : 8));
            }
            LinearLayout.LayoutParams p = lp(0, -2, 1);
            p.leftMargin = i % cols == 0 ? 0 : dp(c, 8);
            row.addView(chips.get(i), p);
        }
        if (row != null) {
            int rest = (cols - chips.size() % cols) % cols;
            for (int i = 0; i < rest; i++) {
                LinearLayout.LayoutParams p = lp(0, 1, 1);
                p.leftMargin = dp(c, 8);
                row.addView(new View(c), p);
            }
        }
        return grid;
    }

    /** Строка-переход «заголовок / подпись ›». Заголовок — первый TextView внутри первого дочернего блока. */
    static LinearLayout navRow(Context c, CharSequence title, CharSequence sub, View.OnClickListener l) {
        LinearLayout r = h(c);
        int p = dp(c, 16);
        r.setPadding(p, p, p, p);
        ripple(r, rect(c, cardColor(), 16));
        r.setClickable(true);
        r.setOnClickListener(l);
        LinearLayout col = v(c);
        TextView t = text(c, title, 17, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(t);
        if (sub != null) col.addView(text(c, sub, 13, TEXT2));
        r.addView(col, lp(0, -2, 1));
        r.addView(text(c, "›", 24, TEXT2));
        return r;
    }

    static TextView navTitle(LinearLayout navRow) {
        return (TextView) ((LinearLayout) navRow.getChildAt(0)).getChildAt(0);
    }

    static LinearLayout pageHeader(Context c, CharSequence title, View.OnClickListener back) {
        LinearLayout hd = h(c);
        TextView b = text(c, c.getString(R.string.back), 16, LINK);
        b.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        b.setOnClickListener(back);
        hd.addView(b);
        TextView t = text(c, title, 18, TEXT);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        hd.addView(t);
        return hd;
    }

    static HorizontalScrollView hscroll(Context c, View content) {
        HorizontalScrollView s = new HorizontalScrollView(c);
        s.setHorizontalScrollBarEnabled(false);
        s.addView(content);
        return s;
    }

    /** Ползунок с подписью и значением. */
    static final class Slider extends LinearLayout {
        interface Fmt { String f(float v); }
        interface Listener { void changed(float v); }

        private final SeekBar bar;
        private final TextView value;
        private final float min, step;
        private final Fmt fmt;

        Slider(Context c, CharSequence title, float min, float max, float step, Fmt fmt, Listener l) {
            super(c);
            setOrientation(VERTICAL);
            this.min = min; this.step = step; this.fmt = fmt;
            LinearLayout top = h(c);
            top.addView(text(c, title, 15, TEXT), lp(0, -2, 1));
            value = text(c, "", 15, TEXT2);
            top.addView(value);
            addView(top);
            bar = new SafeSeekBar(c);
            bar.setMax(Math.round((max - min) / step));
            bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
            bar.setThumbTintList(ColorStateList.valueOf(ACCENT));
            bar.setProgressBackgroundTintList(ColorStateList.valueOf(SURFACE2));
            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                    float v = Slider.this.min + p * Slider.this.step;
                    value.setText(Slider.this.fmt.f(v));
                    if (user && l != null) l.changed(v);
                }
                @Override public void onStartTrackingTouch(SeekBar s) { }
                @Override public void onStopTrackingTouch(SeekBar s) { }
            });
            addView(bar, new LayoutParams(-1, -2));
            set(min);
        }

        void set(float v) {
            bar.setProgress(Math.round((v - min) / step));
            value.setText(fmt.f(min + bar.getProgress() * step));
        }
    }
}
