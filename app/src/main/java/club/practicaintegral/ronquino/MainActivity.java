package club.practicaintegral.ronquino;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * App Android de RonquiNo: muestra la web de siempre (https://gcalabro12.github.io/RonquiNo/) y añade
 * lo que una web no puede hacer: poner y quitar «No molestar», apagar el wifi (Android 9 o anterior),
 * seguir escuchando / sonando con la pantalla apagada y que la musiquita de bienvenida suene sola.
 * La web sabe que está dentro de la app porque existe window.RonquiNoAndroid.
 */
public class MainActivity extends Activity {
    private static final String WEB = "https://gcalabro12.github.io/RonquiNo/";
    private static final String HOST = "gcalabro12.github.io";
    private static final int REQ_MIC = 1, REQ_ARCHIVO = 2;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView web;
    private TextView cargando;
    private boolean cargada;
    private PermissionRequest micPendiente;
    private ValueCallback<Uri[]> archivoPendiente;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        // Si la app se cerró a media noche, «No molestar» y el wifi vuelven a como estaban
        if (saved == null && !NocheService.activo() && Modos.pendiente(this)) Modos.restaurar(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B1020);

        web = new WebView(this);
        web.setBackgroundColor(0xFF0B1020);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        cargando = new TextView(this);
        cargando.setText("RonquiNo");
        cargando.setTextColor(0xFFE8ECFA);
        cargando.setTextSize(30);
        cargando.setGravity(Gravity.CENTER);
        cargando.setBackgroundColor(0xFF0B1020);
        root.addView(cargando, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        // La musiquita de bienvenida puede sonar sin que la persona toque la pantalla
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        // Con la pantalla apagada la web sigue a pleno rendimiento (detector y ruido)
        web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        web.addJavascriptInterface(new Puente(), "RonquiNoAndroid");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost()) || "file".equals(u.getScheme())) return false;
                if ("blob".equals(u.getScheme()) || "data".equals(u.getScheme())) return true;
                // El resto de enlaces (YouTube, practicaintegral.club…) se abren fuera
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (RuntimeException ignored) {}
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                cargada = true;
                cargando.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (!req.isForMainFrame()) return;
                int c = err.getErrorCode();
                if (c == ERROR_HOST_LOOKUP || c == ERROR_CONNECT || c == ERROR_TIMEOUT)
                    view.loadUrl("file:///android_asset/sin-conexion.html");
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler h, SslError e) {
                h.cancel();
                view.loadUrl("file:///android_asset/sin-conexion.html");
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            /** La web pide el micrófono: se da si Android ya dio el permiso; si no, se pide. */
            @Override
            public void onPermissionRequest(PermissionRequest req) {
                runOnUiThread(() -> {
                    boolean audio = Arrays.asList(req.getResources()).contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
                    if (!audio || !HOST.equals(req.getOrigin().getHost())) { req.deny(); return; }
                    if (micOk()) { req.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE}); return; }
                    if (micPendiente != null) micPendiente.deny();
                    micPendiente = req;
                    List<String> p = new ArrayList<>();
                    p.add(Manifest.permission.RECORD_AUDIO);
                    if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS);
                    requestPermissions(p.toArray(new String[0]), REQ_MIC);
                });
            }

            /** «Analizar una grabación»: abre el selector de archivos del teléfono. */
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (archivoPendiente != null) archivoPendiente.onReceiveValue(null);
                archivoPendiente = cb;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"audio/*", "video/*"});
                try {
                    startActivityForResult(Intent.createChooser(i, "Elige la grabación"), REQ_ARCHIVO);
                } catch (RuntimeException e) {
                    archivoPendiente = null;
                    cb.onReceiveValue(null);
                }
                return true;
            }
        });

        web.loadUrl(WEB);
        handler.postDelayed(() -> { if (!cargada) cargando.setVisibility(View.GONE); }, 15000);
    }

    private boolean micOk() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQ_MIC || micPendiente == null) return;
        if (micOk()) micPendiente.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        else micPendiente.deny();
        micPendiente = null;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_ARCHIVO || archivoPendiente == null) return;
        archivoPendiente.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
        archivoPendiente = null;
    }

    /** Al volver de los ajustes de Android (permiso de «No molestar», wifi…), la web se pone al día. */
    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.nativeVuelta && window.nativeVuelta()", null);
    }

    @Override
    public void onBackPressed() {
        // La web decide: cerrar la hoja o volver al menú. Si ya está en el menú, la app se va al fondo.
        web.evaluateJavascript("(function(){try{return window.atrasNativo ? atrasNativo() : false}catch(e){return false}})()", v -> {
            if (!"true".equals(v)) moveTaskToBack(true);
        });
    }

    @Override
    protected void onDestroy() {
        if (isFinishing() && NocheService.activo()) {
            NocheService.escuchando = false;
            NocheService.ruido = false;
            NocheService.actualizar(this);
            Modos.restaurar(this);
        }
        super.onDestroy();
    }

    private void abrir(Intent i) {
        runOnUiThread(() -> { try { startActivity(i); } catch (RuntimeException ignored) {} });
    }

    private void pantalla(boolean encendida) {
        runOnUiThread(() -> {
            if (encendida) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        });
    }

    /** Funciones que la web puede llamar con window.RonquiNoAndroid.xxx() */
    class Puente {
        @JavascriptInterface public String version() {
            try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
            catch (Exception e) { return "?"; }
        }

        @JavascriptInterface public int android() { return Build.VERSION.SDK_INT; }

        // ---------- No molestar ----------
        @JavascriptInterface public boolean hasDndAccess() { return Modos.dndAcceso(MainActivity.this); }

        @JavascriptInterface public boolean dndActivo() { return Modos.dndActivo(MainActivity.this); }

        /** true si lo puso RonquiNo (y lo quitará al terminar la noche). */
        @JavascriptInterface public boolean dndMio() { return Modos.dndMio(MainActivity.this); }

        /** Pone (true) o quita (false) «No molestar». Devuelve "ok", "sin-permiso" o "error". */
        @JavascriptInterface public String setDnd(boolean on) {
            if (on) return Modos.ponerDnd(MainActivity.this);
            Modos.quitarDnd(MainActivity.this);
            return "ok";
        }

        /** Abre la pantalla de Android «Acceso a No molestar» (solo hace falta la primera vez). */
        @JavascriptInterface public void pedirDnd() {
            abrir(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        }

        // ---------- Wifi y modo avión ----------
        @JavascriptInterface public boolean wifiEncendido() { return Modos.wifiEncendido(MainActivity.this); }

        /** ¿Puede RonquiNo apagar el wifi él solo? Solo en Android 9 o anterior. */
        @JavascriptInterface public boolean wifiDirecto() { return Modos.wifiDirecto(); }

        @JavascriptInterface public boolean wifiMio() { return Modos.wifiMio(MainActivity.this); }

        /** Apaga el wifi (Android 9 o anterior) o abre el panel del wifi (Android 10 o más). Devuelve "ok", "panel" o "error". */
        @JavascriptInterface public String apagarWifi() {
            if (Modos.wifiDirecto()) return Modos.apagarWifi(MainActivity.this) ? "ok" : "error";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) abrir(new Intent(Settings.Panel.ACTION_WIFI));
            return "panel";
        }

        @JavascriptInterface public void encenderWifi() { Modos.encenderWifi(MainActivity.this); }

        /** Android no deja que ninguna app ponga el modo avión: solo se puede abrir su pantalla de ajustes. */
        @JavascriptInterface public void abrirModoAvion() {
            abrir(new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS));
        }

        /** Deja «No molestar» y el wifi como estaban antes de la noche. */
        @JavascriptInterface public void restaurar() { Modos.restaurar(MainActivity.this); }

        // ---------- La noche ----------
        /** Empieza (true) o termina (false) la escucha: pantalla encendida + servicio con el micrófono. Al terminar, todo como estaba. */
        @JavascriptInterface public void noche(boolean on) {
            NocheService.escuchando = on;
            NocheService.actualizar(MainActivity.this);
            pantalla(on);
            if (!on) {
                atenuar(false);
                Modos.restaurar(MainActivity.this);
            }
        }

        /** El ruido para dormir empieza o se para: con él sonando, el servicio mantiene el sonido con la pantalla apagada. */
        @JavascriptInterface public void ruido(boolean on) {
            NocheService.ruido = on;
            NocheService.actualizar(MainActivity.this);
        }

        /** Mientras escucha, la pantalla se queda encendida pero al mínimo de brillo. */
        @JavascriptInterface public void atenuar(boolean poco) {
            runOnUiThread(() -> {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = poco ? 0.01f : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
                getWindow().setAttributes(lp);
            });
        }

        /** Compartir una grabación (WhatsApp, correo, Drive…). La web la manda en base64. */
        @JavascriptInterface public boolean compartir(String nombre, String base64) {
            try {
                String limpio = nombre.replaceAll("[^A-Za-z0-9_.-]", "_");
                File dir = ArchivoProvider.carpeta(MainActivity.this);
                File[] viejos = dir.listFiles();
                if (viejos != null) for (File f : viejos) f.delete();
                File f = new File(dir, limpio);
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(Base64.decode(base64, Base64.DEFAULT)); }
                Uri u = Uri.parse("content://" + ArchivoProvider.AUTORIDAD + "/" + limpio);
                Intent i = new Intent(Intent.ACTION_SEND).setType("audio/wav")
                        .putExtra(Intent.EXTRA_STREAM, u)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                i.setClipData(ClipData.newRawUri(limpio, u));
                abrir(Intent.createChooser(i, "Compartir la grabación"));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
