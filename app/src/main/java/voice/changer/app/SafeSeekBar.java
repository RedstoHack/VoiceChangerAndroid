package voice.changer.app;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.widget.SeekBar;

/**
 * Ползунок, который не мешает прокрутке списка: пока палец идёт вертикально, ползунок «спит» и страница листается.
 * Значение меняется только при горизонтальном движении; короткое касание переставляет значение в эту точку.
 */
final class SafeSeekBar extends SeekBar {
    private final int slop;
    private float downX, downY;
    private boolean dragging, vertical;

    SafeSeekBar(Context c) {
        super(c);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    private void feedDown(MotionEvent e) {
        MotionEvent d = MotionEvent.obtain(e);
        d.setAction(MotionEvent.ACTION_DOWN);
        d.setLocation(downX, downY);
        super.onTouchEvent(d);
        d.recycle();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                vertical = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (vertical) return false;
                if (!dragging) {
                    float dx = Math.abs(e.getX() - downX), dy = Math.abs(e.getY() - downY);
                    if (dy > slop && dy >= dx) { vertical = true; return false; }
                    if (dx <= slop) return true;
                    dragging = true;
                    feedDown(e);
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onTouchEvent(e);
            case MotionEvent.ACTION_UP:
                if (vertical) return false;
                if (!dragging) feedDown(e);          // короткое касание = «поставить сюда»
                return super.onTouchEvent(e);
            case MotionEvent.ACTION_CANCEL:
                if (dragging) return super.onTouchEvent(e);
                return false;
            default:
                return true;
        }
    }
}
