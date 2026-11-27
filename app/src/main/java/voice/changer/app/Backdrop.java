package voice.changer.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.VideoView;

import java.io.File;

/** Фон приложения: однотонный цвет, картинка, GIF (анимированный на Android 9+) или зацикленное видео без звука. */
final class Backdrop extends FrameLayout {
    private VideoView video;

    Backdrop(Context c) {
        super(c);
        setBackgroundColor(Ui.BG);
        if (!Theme.hasMedia()) return;
        File f = Theme.bgFile(c);
        try {
            if ("video".equals(Theme.bgType)) {
                video = new VideoView(c);
                video.setVideoPath(f.getPath());
                video.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    mp.setVolume(0f, 0f);
                    int vw = mp.getVideoWidth(), vh = mp.getVideoHeight(), w = getWidth(), h = getHeight();
                    if (vw > 0 && vh > 0 && w > 0 && h > 0) {
                        float s = Math.max(w / (float) vw, h / (float) vh);
                        video.setLayoutParams(new FrameLayout.LayoutParams((int) (vw * s), (int) (vh * s), Gravity.CENTER));
                    }
                    video.start();
                });
                addView(video, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
            } else {
                ImageView iv = new ImageView(c);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Drawable d = null;
                if (Build.VERSION.SDK_INT >= 28) d = Api28.load(f);
                if (d != null) {
                    iv.setImageDrawable(d);
                } else {
                    Bitmap b = Theme.decode(f, 1600);
                    if (b != null) iv.setImageBitmap(b);
                }
                addView(iv, new FrameLayout.LayoutParams(-1, -1));
            }
        } catch (Throwable t) {
            AppLog.err("backdrop", t);
        }
        View dim = new View(c);
        dim.setBackgroundColor(Color.argb(Theme.dim * 255 / 100, 0, 0, 0));
        addView(dim, new FrameLayout.LayoutParams(-1, -1));
    }

    void resume() { if (video != null && !video.isPlaying()) video.start(); }
    void pause() { if (video != null) video.pause(); }
}
