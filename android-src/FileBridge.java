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
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;

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
 * ttsSpeak(text, rate, lang, id) / ttsStop(): Vorlesen mit der Stimme des
 *   Geraets (im WebView gibt es window.speechSynthesis nicht). Fortschritt
 *   kommt ueber window.srTts(id, 'range'|'done'|'error', zeichenposition).
 *   ttsSpeak liefert false, solange die Stimme noch startet; die Web-App
 *   versucht es dann kurz darauf erneut. Braucht den <queries>-Eintrag fuer
 *   TTS_SERVICE im Manifest (build.ps1), sonst findet Android keine Stimme.
 * fetchUrl(url, id): laedt genau eine https-Seite (hoechstens 25 MB) auf
 *   einem eigenen Thread und meldet window.srFetched(id, status, typ); den
 *   Inhalt holt die Web-App per takeFetched(id) als Base64. Das ist der
 *   einzige Netzzugriff der App und passiert nur auf Antippen. Braucht die
 *   INTERNET-Berechtigung (new-app.ps1 -Online).
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
    private final HashMap<Integer, String> fetched = new HashMap<>();
    private TextToSpeech tts = null;
    private volatile boolean ttsReady = false;

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

    // ---- Vorlesen -----------------------------------------------------

    /** Stimme erst beim ersten Vorlesen starten; laeuft auf dem UI-Thread. */
    private void initTts() {
        if (tts != null) return;
        tts = new TextToSpeech(activity, status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) return;
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String utteranceId) { }
                @Override public void onDone(String utteranceId) { ttsEvent(utteranceId, "done", 0); }
                @SuppressWarnings("deprecation")
                @Override public void onError(String utteranceId) { ttsEvent(utteranceId, "error", 0); }
                @Override public void onError(String utteranceId, int errorCode) { ttsEvent(utteranceId, "error", 0); }
                // Ab Android 8: welches Wort gerade gesprochen wird.
                @Override public void onRangeStart(String utteranceId, int start, int end, int frame) { ttsEvent(utteranceId, "range", start); }
            });
            ttsReady = true;
        });
    }

    private void ttsEvent(String utteranceId, String kind, int pos) {
        final int id;
        try { id = Integer.parseInt(utteranceId); } catch (Exception e) { return; }
        webView.post(() -> webView.evaluateJavascript(
            "window.srTts && window.srTts(" + id + ",'" + kind + "'," + pos + ")", null));
    }

    @JavascriptInterface
    public boolean ttsSpeak(final String text, final double rate, final String lang, final int id) {
        if (tts == null || !ttsReady) {
            activity.runOnUiThread(this::initTts);
            return false;
        }
        activity.runOnUiThread(() -> {
            if (tts == null) return;
            int r = tts.setLanguage("en".equals(lang) ? Locale.UK : Locale.GERMANY);
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsEvent(String.valueOf(id), "error", 0);
                return;
            }
            tts.setSpeechRate((float) Math.max(0.3, Math.min(3.0, rate)));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, String.valueOf(id));
        });
        return true;
    }

    @JavascriptInterface
    public void ttsStop() {
        activity.runOnUiThread(() -> { if (tts != null) tts.stop(); });
    }

    /** Aus MainActivity.onDestroy. */
    void shutdown() {
        ttsReady = false;
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    // ---- Eine Webseite laden ---------------------------------------------

    @JavascriptInterface
    public void fetchUrl(final String url, final int id) {
        new Thread(() -> {
            int status = 0;
            String type = "";
            HttpURLConnection c = null;
            try {
                if (url == null || !url.startsWith("https://")) throw new java.io.IOException("nur https");
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(12000);
                c.setReadTimeout(15000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) Speedreader");
                c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/pdf;q=0.9,*/*;q=0.5");
                status = c.getResponseCode();
                if (status == 200) {
                    String ct = c.getContentType();
                    if (ct != null) type = ct.replaceAll("[^A-Za-z0-9/+.=; -]", "");
                    try (InputStream in = c.getInputStream()) {
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            if (out.size() > MAX_BYTES) throw new java.io.IOException("zu gross");
                        }
                        synchronized (lock) {
                            fetched.put(id, Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
                        }
                    }
                }
            } catch (Exception e) {
                status = 0;
            } finally {
                if (c != null) c.disconnect();
            }
            final String call = "window.srFetched && window.srFetched(" + id + "," + status + ",'" + type + "')";
            webView.post(() -> webView.evaluateJavascript(call, null));
        }).start();
    }

    @JavascriptInterface
    public String takeFetched(int id) {
        synchronized (lock) { String s = fetched.remove(id); return s == null ? "" : s; }
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
