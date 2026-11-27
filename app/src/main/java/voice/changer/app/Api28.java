package voice.changer.app;

import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;

import java.io.File;

/** Вызовы Android 9+ вынесены в отдельный класс, чтобы старые версии его просто не загружали. */
final class Api28 {
    private Api28() {}

    static Drawable load(File f) {
        try {
            Drawable d = ImageDecoder.decodeDrawable(ImageDecoder.createSource(f));
            if (d instanceof AnimatedImageDrawable) {
                AnimatedImageDrawable a = (AnimatedImageDrawable) d;
                a.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
                a.start();
            }
            return d;
        } catch (Throwable t) {
            AppLog.err("ImageDecoder", t);
            return null;
        }
    }
}
