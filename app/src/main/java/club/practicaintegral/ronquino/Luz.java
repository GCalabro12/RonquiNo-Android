package club.practicaintegral.ronquino;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/** Sensor de luz del móvil durante la noche: para saber si se ha encendido la luz de la habitación. */
final class Luz {
    private static SensorEventListener oyente;
    static volatile float lux = -1;

    private Luz() {}

    static void empezar(Context c) {
        if (oyente != null) return;
        SensorManager sm = c.getSystemService(SensorManager.class);
        Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (s == null) { lux = -1; return; }
        oyente = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent e) { lux = e.values[0]; }
            @Override public void onAccuracyChanged(Sensor sensor, int a) {}
        };
        sm.registerListener(oyente, s, SensorManager.SENSOR_DELAY_NORMAL);
    }

    static void parar(Context c) {
        if (oyente == null) return;
        SensorManager sm = c.getSystemService(SensorManager.class);
        if (sm != null) sm.unregisterListener(oyente);
        oyente = null; lux = -1;
    }
}
