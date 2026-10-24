package club.practicaintegral.ronquino;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/**
 * «Entrar con Google» dentro de la app: Google no deja abrir su ventana de entrar en un WebView,
 * así que se usa el selector de cuentas de Android (Credential Manager). Devuelve el «token» de Google,
 * que el servidor comprueba igual que el del botón de la web (mismo ID de cliente web como «servidor»).
 */
class Google {
    interface Vuelta { void listo(String token, String error); }

    static void entrar(Activity a, String clientId, Vuelta v) {
        GetSignInWithGoogleOption opcion = new GetSignInWithGoogleOption.Builder(clientId).build();
        GetCredentialRequest peticion = new GetCredentialRequest.Builder().addCredentialOption(opcion).build();
        CredentialManager.create(a).getCredentialAsync(a, peticion, new CancellationSignal(), a.getMainExecutor(),
            new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                @Override public void onResult(GetCredentialResponse r) {
                    Credential c = r.getCredential();
                    if (c instanceof CustomCredential && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
                        try { v.listo(GoogleIdTokenCredential.createFrom(((CustomCredential) c).getData()).getIdToken(), ""); }
                        catch (Exception e) { v.listo("", "token"); }
                    } else v.listo("", "tipo");
                }
                @Override public void onError(GetCredentialException e) {
                    v.listo("", e instanceof GetCredentialCancellationException ? "cancelado" : e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            });
    }
}
