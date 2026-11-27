package voice.changer.app;

import android.app.Application;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Store.init(this);
        Theme.load(this);
        Store.addListener(() -> LiveService.ENGINE.setSettings(Store.get()));
        Jobs.IO.execute(() -> Files.cleanup(this));
    }
}
