package voice.changer.app;

import android.view.View;

interface Screen {
    View view();
    default void onShow() { }
    default void onHide() { }
}
