package voice.changer.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.IBinder;

/** Служба переднего плана: держит обработку микрофона живой, когда приложение свёрнуто. */
public class LiveService extends Service {
    static final LiveEngine ENGINE = new LiveEngine();
    static final String ACT_START = "voice.changer.app.START", ACT_STOP = "voice.changer.app.STOP";
    private static final String CHANNEL = "live";

    @Override protected void attachBaseContext(android.content.Context b) { super.attachBaseContext(Lang.wrap(b)); }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String act = intent == null ? null : intent.getAction();
        if (ACT_START.equals(act)) {
            try {
                startAsForeground();
            } catch (RuntimeException e) {
                AppLog.err("startForeground", e);
                ENGINE.fail(e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
                stopSelf();
                return START_NOT_STICKY;
            }
            ENGINE.start(this, () -> { stopForeground(true); stopSelf(); });
        } else {
            ENGINE.stop();
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, LiveService.class).setAction(ACT_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notify),
                        getString(R.string.notif_stop), stop).build())
                .build();
        if (Build.VERSION.SDK_INT >= 30) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(1, n);
    }

    @Override public void onDestroy() { ENGINE.stop(); super.onDestroy(); }
}
