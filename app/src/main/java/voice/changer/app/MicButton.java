package voice.changer.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Большая круглая кнопка записи: пока идёт звук, вокруг неё расходятся волны и «дышат» лучи — видно, что голос слышен. */
final class MicButton extends View {
    interface Listener {
        void onDown();
        void onUp(boolean inside);
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float level, shown;
    private boolean active;
    private String label = "";
    private Listener listener;
    private final long t0 = System.nanoTime();

    MicButton(Context c) {
        super(c);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setTextAlign(Paint.Align.CENTER);
    }

    void setListener(Listener l) { listener = l; }

    void setLevel(float v) { level = Math.max(0f, Math.min(1f, v)); }

    void setActive(boolean a) {
        if (a != active) { active = a; invalidate(); }
    }

    void setLabel(String s) {
        if (!s.equals(label)) { label = s; invalidate(); }
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = View.MeasureSpec.getSize(wSpec);
        int sz = Math.min(w, Ui.dp(getContext(), Theme.micDp));
        setMeasuredDimension(sz, sz);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f, r = w * 0.27f;
        float t = (System.nanoTime() - t0) / 1e9f;
        shown += (level - shown) * 0.25f;
        float stroke = Ui.dp(getContext(), 3);

        if (active) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke);
            for (int k = 0; k < 3; k++) {
                float ph = (t * 0.7f + k / 3f) % 1f;
                float rad = r * (1.08f + ph * (0.35f + 0.6f * shown));
                p.setColor(Ui.ACCENT);
                p.setAlpha((int) ((1f - ph) * 150f));
                c.drawCircle(cx, cy, rad, p);
            }
        }

        // лучи вокруг кнопки: высота зависит от громкости
        int bars = 44;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setColor(Ui.ACCENT);
        p.setAlpha(active ? 235 : 90);
        for (int i = 0; i < bars; i++) {
            double ang = 2 * Math.PI * i / bars;
            float wob = 0.5f + 0.5f * (float) (Math.sin(t * 7 + i * 0.9) * Math.sin(t * 2.3 + i * 0.35));
            float amp = active ? (0.12f + shown * 0.88f) * (0.35f + 0.65f * Math.abs(wob)) : 0.04f;
            float r1 = r * 1.12f, r2 = r1 + r * 0.06f + r * 0.42f * amp;
            c.drawLine(cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1,
                    cx + (float) Math.cos(ang) * r2, cy + (float) Math.sin(ang) * r2, p);
        }

        p.setStyle(Paint.Style.FILL);
        p.setAlpha(255);
        p.setColor(active ? Ui.ACCENT : Ui.SURFACE2);
        c.drawCircle(cx, cy, r * (1f + 0.05f * shown), p);
        p.setColor(active ? Color.WHITE : Ui.TEXT);
        p.setTextSize(r * 0.27f);
        c.drawText(label, cx, cy + r * 0.09f, p);

        if (active || shown > 0.01f) postInvalidateOnAnimation();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (listener != null) listener.onDown();
                return true;
            case MotionEvent.ACTION_UP:
                if (listener != null) {
                    boolean in = e.getX() >= 0 && e.getX() <= getWidth() && e.getY() >= 0 && e.getY() <= getHeight();
                    listener.onUp(in);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (listener != null) listener.onUp(false);
                return true;
            default:
                return true;
        }
    }
}
