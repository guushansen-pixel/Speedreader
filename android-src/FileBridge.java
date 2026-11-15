package __PACKAGE__;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Native Bruecke, aus der Web-App erreichbar als window.SrAndroid.
 *
 * pickFile(): System-Dateiauswahl. Ist die Datei gelesen, ruft die Bruecke
 *   window.srFileReady(); die Web-App holt Namen und Inhalt (Base64, weil
 *   EPUB binaer ist) per takePickedName() / takePickedFile() ab.
 * readClipboard(): Text aus der Zwischenablage (im WebView sonst gesperrt).
 * copyText(text): Text in die Zwischenablage.
 * takeSharedText(): Text, den eine andere App per "Teilen" geschickt hat;
 *   MainActivity reicht den Intent an takeIntent() weiter.
 *
 * Gelesen wird nativ ueber den ContentResolver, damit
 * setAllowContentAccess(false) im WebView unangetastet bleibt. Die Quelle
 * liegt in speed-reader\android-src und wird von build.ps1 ins generierte
 * Projekt kopiert.
 */
public class FileBridge {
    static final int REQ_OPEN = 4852;
    private static final int MAX_BYTES = 25 * 1024 * 1024;

    private final Activity activity;
    private final WebView webView;
    private final Object lock = new Object();
    private String pickedName = null;
    private String pickedB64 = null;
    private String shared = null;

    FileBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public void copyText(String text) {
        activity.runOnUiThread(() -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                Toast.makeText(activity, "Kopieren fehlgeschlagen", Toast.LENGTH_SHORT).show();
                return;
            }
            cm.setPrimaryClip(ClipData.newPlainText("Speedreader", text));
        });
    }

    @JavascriptInterface
    public String readClipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return "";
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return "";
            CharSequence t = clip.getItemAt(0).coerceToText(activity);
            return t == null ? "" : t.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @SuppressWarnings("deprecation")
    @JavascriptInterface
    public void pickFile() {
        activity.runOnUiThread(() -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            // EPUB und Textdateien haben auf Android keinen verlaesslichen
            // MIME-Typ, deshalb alles zulassen und in JS pruefen.
            i.setType("*/*");
            try {
                activity.startActivityForResult(i, REQ_OPEN);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(activity, "Keine Dateiauswahl verfuegbar", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public String takePickedName() {
        synchronized (lock) { String s = pickedName; pickedName = null; return s == null ? "" : s; }
    }

    @JavascriptInterface
    public String takePickedFile() {
        synchronized (lock) { String s = pickedB64; pickedB64 = null; return s == null ? "" : s; }
    }

    @JavascriptInterface
    public String takeSharedText() {
        synchronized (lock) { String s = shared; shared = null; return s == null ? "" : s; }
    }

    /** Aus MainActivity.onCreate und onNewIntent: Text aus "Teilen" merken. */
    void takeIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        CharSequence t = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        // Extra entfernen, damit derselbe Text nach einem Neuaufbau der
        // Activity nicht noch einmal importiert wird.
        intent.removeExtra(Intent.EXTRA_TEXT);
        if (t == null || t.length() == 0) return;
        synchronized (lock) { shared = t.toString(); }
        js("srShared");
    }

    /** Aus MainActivity.onActivityResult. true, wenn das Ergebnis uns gehoert. */
    boolean onResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_OPEN) return false;
        Uri uri = (resultCode == Activity.RESULT_OK && data != null) ? data.getData() : null;
        if (uri == null) return true;   // Auswahl abgebrochen
        String name = "";
        try (Cursor c = activity.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = c.getString(idx);
            }
        } catch (Exception e) {
            // Name ist nur Zierde; ohne ihn geht es weiter.
        }
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new java.io.IOException("kein Stream");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) throw new java.io.IOException("Datei zu gross");
            }
            synchronized (lock) {
                pickedName = name == null ? "" : name;
                pickedB64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
            }
            js("srFileReady");
        } catch (Exception e) {
            Toast.makeText(activity, "Datei konnte nicht gelesen werden (max. 25 MB)", Toast.LENGTH_LONG).show();
        }
        return true;
    }

    private void js(String fn) {
        webView.post(() -> webView.evaluateJavascript(
            "window." + fn + " && window." + fn + "()", null));
    }
}
