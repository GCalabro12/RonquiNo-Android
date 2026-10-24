package club.practicaintegral.ronquino;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * «Estás roncando, cambia de posición»: una voz que solo suena por los auriculares (pinganillos),
 * nunca por el altavoz, para no despertar a quien duerme al lado.
 */
final class Aviso {
    private static TextToSpeech tts;
    private static boolean listo;
    private static String pendiente;

    private Aviso() {}

    /** Prepara la voz (tarda un momento la primera vez). */
    static void preparar(Context c) {
        if (tts != null) return;
        tts = new TextToSpeech(c.getApplicationContext(), estado -> {
            listo = estado == TextToSpeech.SUCCESS;
            if (!listo) return;
            tts.setLanguage(new Locale("es", "ES"));
            mejorVoz();
            tts.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            if (pendiente != null) { tts.speak(pendiente, TextToSpeech.QUEUE_FLUSH, null, "aviso"); pendiente = null; }
        });
    }

    /** De las voces en español del móvil, la de más calidad que no necesite internet (si no, la de más calidad). */
    private static void mejorVoz() {
        try {
            android.speech.tts.Voice mejor = null;
            for (android.speech.tts.Voice v : tts.getVoices()) {
                if (v.getLocale() == null || !"es".equals(v.getLocale().getLanguage())) continue;
                if (v.getFeatures() != null && v.getFeatures().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) continue;
                int puntos = v.getQuality() + (v.isNetworkConnectionRequired() ? -50 : 0) + ("ES".equals(v.getLocale().getCountry()) ? 20 : 0);
                int puntosMejor = mejor == null ? Integer.MIN_VALUE : mejor.getQuality() + (mejor.isNetworkConnectionRequired() ? -50 : 0) + ("ES".equals(mejor.getLocale().getCountry()) ? 20 : 0);
                if (puntos > puntosMejor) mejor = v;
            }
            if (mejor != null) tts.setVoice(mejor);
        } catch (RuntimeException ignored) {}
    }

    /** ¿Hay auriculares conectados (con cable, bluetooth o USB)? */
    static boolean auriculares(Context c) {
        AudioManager am = c.getSystemService(AudioManager.class);
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            switch (d.getType()) {
                case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                case AudioDeviceInfo.TYPE_USB_HEADSET:
                case AudioDeviceInfo.TYPE_HEARING_AID:
                case AudioDeviceInfo.TYPE_BLE_HEADSET:
                    return true;
            }
        }
        return false;
    }

    /** Lo dice por donde suene el móvil (altavoz o auriculares): la voz de Luna cuando la de Gemini no llega. */
    static void hablar(Context c, String texto) {
        preparar(c);
        if (listo) tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "luna");
        else pendiente = texto;
    }

    static void callar() { if (tts != null) tts.stop(); pendiente = null; }

    /** Lo dice solo si hay auriculares; sin ellos no suena nada y devuelve false. */
    static boolean decir(Context c, String texto) {
        if (!auriculares(c)) return false;
        preparar(c);
        if (listo) tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "aviso");
        else pendiente = texto;
        return true;
    }
}
