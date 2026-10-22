package club.practicaintegral.ronquino;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Llega la hora de una alarma (o el móvil se ha reiniciado y hay que volver a programarlas). */
public class AlarmaReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!"SONAR".equals(i.getAction())) { // reinicio, actualización de la app o cambio de hora
            Alarmas.programarTodas(c);
            return;
        }
        // Las alarmas diarias se vuelven a programar para mañana
        Alarmas.programarTodas(c);
        Intent s = new Intent(c, AlarmaService.class)
                .putExtra("tono", i.getStringExtra("tono"))
                .putExtra("sub", i.getIntExtra("sub", 120));
        try { c.startForegroundService(s); } catch (RuntimeException ignored) {}
    }
}
