package club.practicaintegral.ronquino;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

/**
 * Hace sonar la alarma: tono propio en bucle, con el volumen subiendo poco a poco («sub» segundos),
 * y abre la pantalla «¡Buenos días!» aunque el móvil esté bloqueado.
 */
public class AlarmaService extends Service {
    private static final String CANAL = "alarma";
    static volatile boolean sonando;
    static String tonoActual = "amanecer";
    static int subActual = 120;
    private MediaPlayer mp;
    private PowerManager.WakeLock cpu;
    private final Handler h = new Handler(Looper.getMainLooper());
    private long t0;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String accion = intent != null ? intent.getAction() : null;
        if ("APAGAR".equals(accion)) { parar(); return START_NOT_STICKY; }
        if ("POSPONER".equals(accion)) { Alarmas.posponer(this, tonoActual, subActual, intent.getIntExtra("min", 9)); parar(); return START_NOT_STICKY; }

        if (intent != null) {
            tonoActual = intent.getStringExtra("tono") != null ? intent.getStringExtra("tono") : "amanecer";
            subActual = intent.getIntExtra("sub", 120);
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CANAL, "Alarmas", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(null, null); // el sonido lo pone la app, con su subida de volumen
        nm.createNotificationChannel(ch);
        PendingIntent pantalla = PendingIntent.getActivity(this, 2, new Intent(this, AlarmaActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_USER_ACTION), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, CANAL)
                .setSmallIcon(R.drawable.ic_stat_luna)
                .setContentTitle("☀️ ¡Buenos días!")
                .setContentText("Amable Sleep · alarma")
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(pantalla, true)
                .setContentIntent(pantalla)
                .addAction(new Notification.Action.Builder(null, "Posponer 9 min", accionPI("POSPONER", 3)).build())
                .addAction(new Notification.Action.Builder(null, "Apagar", accionPI("APAGAR", 4)).build())
                .setOngoing(true)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(7, n);
        } catch (RuntimeException e) { stopSelf(); return START_NOT_STICKY; }

        if (cpu == null) {
            cpu = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ronquino:alarma");
            cpu.acquire(30 * 60 * 1000L);
        }
        sonar();
        try { startActivity(new Intent(this, AlarmaActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (RuntimeException ignored) {}
        return START_NOT_STICKY;
    }

    private PendingIntent accionPI(String accion, int code) {
        return PendingIntent.getService(this, code, new Intent(this, AlarmaService.class).setAction(accion).putExtra("min", 9),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void sonar() {
        if (mp != null) return;
        int res = getResources().getIdentifier(tonoActual, "raw", getPackageName());
        if (res == 0) res = getResources().getIdentifier("amanecer", "raw", getPackageName());
        try {
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            mp.setDataSource(this, Uri.parse("android.resource://" + getPackageName() + "/" + res));
            mp.setLooping(true);
            mp.prepare();
            float v0 = subActual > 0 ? .03f : 1f;
            mp.setVolume(v0, v0);
            mp.start();
            sonando = true;
            t0 = System.currentTimeMillis();
            if (subActual > 0) h.post(subir);
        } catch (Exception e) { parar(); }
    }

    /** Despertar suave: el volumen sube de 3 % a 100 % en «sub» segundos. */
    private final Runnable subir = new Runnable() {
        @Override public void run() {
            if (mp == null) return;
            float k = Math.min(1f, (System.currentTimeMillis() - t0) / (subActual * 1000f));
            float v = .03f + .97f * k;
            try { mp.setVolume(v, v); } catch (IllegalStateException ignored) {}
            if (k < 1f) h.postDelayed(this, 200);
        }
    };

    void parar() {
        h.removeCallbacks(subir);
        if (mp != null) { try { mp.stop(); } catch (IllegalStateException ignored) {} mp.release(); mp = null; }
        sonando = false;
        if (cpu != null && cpu.isHeld()) cpu.release();
        cpu = null;
        AlarmaActivity.cerrar();
        stopForeground(true);
        stopSelf();
    }

    @Override public void onDestroy() { if (mp != null) parar(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
