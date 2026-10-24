package club.practicaintegral.ronquino;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** Batería de los auriculares bluetooth (pinganillos): para recordar que estén al 100 % antes de dormir. */
final class Bateria {
    static final int SIN_PERMISO = -2;

    private Bateria() {}

    static boolean permiso(Context c) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || c.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    /** % de batería de los auriculares conectados; -1 si no se sabe, -2 si falta el permiso de bluetooth. */
    static int auriculares(Context c) {
        if (!permiso(c)) return SIN_PERMISO;
        try {
            BluetoothManager bm = c.getSystemService(BluetoothManager.class);
            BluetoothAdapter ad = bm == null ? null : bm.getAdapter();
            if (ad == null || !ad.isEnabled()) return -1;
            for (BluetoothDevice d : ad.getBondedDevices()) {
                // Android lo sabe (lo enseña en Ajustes) pero no lo publica: se pregunta por reflexión
                Object conectado = d.getClass().getMethod("isConnected").invoke(d);
                if (!Boolean.TRUE.equals(conectado)) continue;
                Object nivel = d.getClass().getMethod("getBatteryLevel").invoke(d);
                if (nivel instanceof Integer && (Integer) nivel >= 0) return (Integer) nivel;
            }
        } catch (Exception ignored) {}
        return -1;
    }
}
