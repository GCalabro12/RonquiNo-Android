package club.practicaintegral.ronquino;

import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;

/**
 * «No molestar» y wifi durante la noche. Se guarda cómo estaban antes (también si la app se cierra)
 * para dejarlos igual al terminar la noche.
 */
final class Modos {
    private static final String PREFS = "modos";
    private static final String DND_PREVIO = "dndPrevio";
    private static final String WIFI_APAGADO = "wifiApagado";

    private Modos() {}

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static NotificationManager nm(Context c) { return c.getSystemService(NotificationManager.class); }

    /** ¿Ha dado la persona el permiso «Acceso a No molestar»? */
    static boolean dndAcceso(Context c) { return nm(c).isNotificationPolicyAccessGranted(); }

    /** ¿Está puesto «No molestar» ahora (por quien sea)? */
    static boolean dndActivo(Context c) {
        int f = nm(c).getCurrentInterruptionFilter();
        return f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    }

    /** ¿Lo ha puesto RonquiNo (y lo quitará al terminar)? */
    static boolean dndMio(Context c) {
        return prefs(c).getInt(DND_PREVIO, -1) == NotificationManager.INTERRUPTION_FILTER_ALL && dndActivo(c);
    }

    /** Pone «No molestar» como el icono de la luna (prioridad: alarmas y excepciones según los ajustes del móvil). */
    static String ponerDnd(Context c) {
        if (!dndAcceso(c)) return "sin-permiso";
        try {
            NotificationManager n = nm(c);
            int ahora = n.getCurrentInterruptionFilter();
            if (!prefs(c).contains(DND_PREVIO)) prefs(c).edit().putInt(DND_PREVIO, ahora).apply();
            if (ahora == NotificationManager.INTERRUPTION_FILTER_ALL)
                n.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            return "ok";
        } catch (RuntimeException e) {
            return "error";
        }
    }

    /** Deja «No molestar» como estaba antes de la noche. */
    static void quitarDnd(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.contains(DND_PREVIO)) return;
        int previo = p.getInt(DND_PREVIO, NotificationManager.INTERRUPTION_FILTER_ALL);
        p.edit().remove(DND_PREVIO).apply();
        try {
            NotificationManager n = nm(c);
            if (dndAcceso(c) && previo != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
                    && n.getCurrentInterruptionFilter() != previo)
                n.setInterruptionFilter(previo);
        } catch (RuntimeException ignored) {}
    }

    private static WifiManager wm(Context c) { return c.getApplicationContext().getSystemService(WifiManager.class); }

    static boolean wifiEncendido(Context c) {
        WifiManager w = wm(c);
        return w != null && w.isWifiEnabled();
    }

    /** ¿Puede la app apagar el wifi ella sola? Solo hasta Android 9. */
    static boolean wifiDirecto() { return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q; }

    @SuppressWarnings("deprecation")
    static boolean apagarWifi(Context c) {
        if (!wifiDirecto()) return false;
        WifiManager w = wm(c);
        if (w == null || !w.isWifiEnabled()) return false;
        if (w.setWifiEnabled(false)) { prefs(c).edit().putBoolean(WIFI_APAGADO, true).apply(); return true; }
        return false;
    }

    static boolean wifiMio(Context c) { return prefs(c).getBoolean(WIFI_APAGADO, false); }

    /** Si RonquiNo apagó el wifi, lo vuelve a encender. */
    @SuppressWarnings("deprecation")
    static void encenderWifi(Context c) {
        if (!wifiMio(c)) return;
        prefs(c).edit().remove(WIFI_APAGADO).apply();
        WifiManager w = wm(c);
        try { if (w != null && wifiDirecto()) w.setWifiEnabled(true); } catch (RuntimeException ignored) {}
    }

    /** Fin de la noche (o noche interrumpida): todo como estaba. */
    static void restaurar(Context c) {
        quitarDnd(c);
        encenderWifi(c);
    }

    /** ¿Quedó algo puesto de una noche anterior? */
    static boolean pendiente(Context c) { return prefs(c).contains(DND_PREVIO) || wifiMio(c); }
}
