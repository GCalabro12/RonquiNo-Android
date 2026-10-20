package club.practicaintegral.ronquino;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Da a otra app (WhatsApp, correo…) una grabación para compartirla. Solo sirve archivos de la carpeta «compartir». */
public class ArchivoProvider extends ContentProvider {
    static final String AUTORIDAD = "club.practicaintegral.ronquino.archivos";

    static File carpeta(android.content.Context c) {
        File d = new File(c.getCacheDir(), "compartir");
        d.mkdirs();
        return d;
    }

    private File archivo(Uri uri) throws FileNotFoundException {
        String nombre = uri.getLastPathSegment();
        if (nombre == null || nombre.contains("/") || nombre.contains("..")) throw new FileNotFoundException();
        File f = new File(carpeta(getContext()), nombre);
        if (!f.exists()) throw new FileNotFoundException();
        return f;
    }

    @Override public boolean onCreate() { return true; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(archivo(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) { return "audio/wav"; }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        try {
            File f = archivo(uri);
            MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
            c.addRow(new Object[]{f.getName(), f.length()});
            return c;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
