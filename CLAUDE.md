# Speedreader

Offline-Android-App zum Wort-für-Wort-Lesen eigener Texte (RSVP). Reine
Web-App in einer Datei, die per [apk-builder](../apk-builder) zur APK wird.
Funktionen, Zeitregeln, EPUB-Umfang und Datenmodell stehen in
[README.md](README.md), Versionen in [CHANGELOG.md](CHANGELOG.md).

## Build & Test

Kein Bundler, kein Build-Step. Testserver `speed-reader` aus der
Workspace-`.claude/launch.json` (Port 8170), dann
`http://localhost:8170/web/`. `?debug=1` führt den Selbsttest aus (Konsole
und Zeile auf der Startseite, auch per `await srTests()`; er ist asynchron,
weil er ein EPUB im Speicher baut und entpackt). Er muss komplett `OK`
zeigen.

Den Leser im Browser prüfen: Text öffnen, in der Konsole `setWpm(600);
play()`, kurz warten, `pause()`, dann `P.i`, `db.stats` und `db.lib[0].pos`
ansehen.

APK bauen, **immer über den eigenen Wrapper**:

```powershell
cd "D:\claude code projects\speed-reader"
.\build.ps1 -VersionName "1.1" -VersionCode 2 -Release   # signiert -> dist\speedreader-<v>.apk
```

Der Wrapper ergänzt im Manifest einen zweiten `intent-filter` (ACTION_SEND,
text/plain), baut die Brücke `SrAndroid` ein und leitet `onNewIntent` weiter.

## Konventionen

- Alles in `web/index.html`, kein Framework, keine Pakete. Netzwerk nur an
  einer Stelle: `fetchBytes`/`addUrl` holen die eine Seite, deren Link der
  Nutzer eingibt oder teilt. Nichts anderes darf ins Netz.
- Textaufbereitung (`tokenize`, `orpIndex`, `delayFactor`, `sentenceStart`)
  und EPUB-Lesen bleiben frei von DOM-Zustand der App und bekommen für jede
  Änderung einen Fall in `srTests()`.
- Texte liegen in IndexedDB, nur Metadaten in localStorage.
- `P.i` ist das nächste zu zeigende Wort; `pause()` setzt es auf das zuletzt
  gezeigte zurück, damit Weiterlesen dort beginnt.
- Kopiergeschützte EPUBs werden erkannt und abgelehnt, nie entschlüsselt.
- Kein `confirm()`/`alert()`: eigene Dialoge über `openModal`.
- Keine Health-OS-Anbindung: `lesen_ok` im Health OS meint das Lesen am
  Vorabend im Bett, nicht Speedreading am Handy.

## Aktueller Stand

v1.0 (code 1, 2026-11-15): Leser, Bibliothek, Import (Einfügen,
Zwischenablage, Textdatei, EPUB, Teilen), Tempo-Training, Statistik,
Quiz-Auftrag für Claude. Selbsttest 22/22, Leser im Browser geprüft.
Signierte APK `dist/speedreader-1.0.apk`.

**Am Gerät noch nichts getestet.** Nur dort prüfbar: Dateiauswahl samt
EPUB-Import (im Browser nur mit dem selbst gebauten Test-EPUB geprüft, mit
keinem echten Buch), IndexedDB unter `file://`, Zwischenablage lesen,
"Teilen" aus einer anderen App (kalt gestartet und bei laufender App), ob
die Wortanzeige bei hohem Tempo ruckelfrei bleibt.

v1.1 (code 2, 2026-11-15): Augen-Gym auf Daniels Wunsch als Bereich im
Speedreader (Idee 20): `gymSchulte`, `gymSpanne`, `gymSprung`, Regeln
`spanStep`, `sprungNext`, `schulteRecord`, eigene Vollbild-Ansicht `#gym`
mit Hash `gymrun`. Selbsttest 30/30; alle drei Übungen im Browser
durchgespielt. v1.0 ist seit 15.11. auf dem Pixel installiert, aber noch
ungetestet; v1.1 liegt als `dist/speedreader-1.1.apk` bereit.

v1.2 (code 3, 2026-11-16): Abend-Farbfilter (`filterStrength`, `tintColor`,
`applyFilter`, Ebene `#tint`) und Fokus-Lesen (`focusUpdate`, `noiseData`,
Knopf ♪ im Leser) auf Daniels Wunsch; er will abends vor dem Schlafen lesen.
Der Filter ist uhrzeitgesteuert, weil der WebView keinen Lichtsensor hat.
Neue Farben müssen nicht eigens behandelt werden: Die Ebene wirkt auf alles.
Selbsttest 36/36, Filter und Klang im Browser geprüft. APK
`dist/speedreader-1.2.apk`.
Nachtrag v1.2 (gleicher Tag, Daniels Wunsch mitten im Bau): Seitenzahlen
(`PAGE`, `pageOf`, `pageStart`), Lesezeichen (`toggleMark`, Knopf 🔖) und
Kapitelverzeichnis (`detectChapters`, `chaptersFromParts`, `isHeading`,
Blatt hinter ☰). `parseEpub` liefert dafür zusätzlich `parts` mit
Überschrift. Die Info-Zeile steht jetzt unter der Kopfzeile, damit drei
Knöpfe Platz haben. Selbsttest 45/45; mit einem erzeugten Vier-Kapitel-Text
im Browser geprüft, mit keinem echten Buch.

v1.3 (code 4, 2026-11-16): sieben Punkte auf Daniels Wunsch: PDF-Import
(`parsePdf`, `pdfValue`, `pdfCMap`, `pdfParagraphs`), Artikel von einer
Webseite (`htmlArticle`, `addUrl`, `sharedUrl`), Vorlesen (`Say`,
`sayNext`), Zeilen-Modus (`lineMode`, `lineShow`), Verständnis-Check
(`sentencesIn`, `checkBuild`, `startCheck`), Tempo-Bremse (`brakeHit`),
Markieren (`toggleHl`, `hlExport`) und Restzeit (`realWpm`, `remainParts`).
Beschreibung im README.

In einer Cloud-Sitzung gebaut, deshalb **noch keine APK** und der native
Teil ist nie gelaufen. Am Laptop `.\build.ps1 -Release` (Standardwerte
1.3 / 4). Was dort neu ist:

- `build.ps1` ruft `new-app.ps1` jetzt mit `-Online` auf (INTERNET für den
  Link-Import) und setzt den `<queries>`-Block für `TTS_SERVICE`; beides mit
  Throw-Guard.
- `FileBridge.java` hat `ttsSpeak`/`ttsStop`/`shutdown` und
  `fetchUrl`/`takeFetched`; `MainActivity` bekommt `onDestroy`.
- Geprüft ist nur, dass `FileBridge` und die gepatchte `MainActivity` gegen
  die Android-14-Bibliothek kompilieren (Patches am Template von
  apk-builder nachgespielt) und dass das Manifest gültig bleibt.

Selbsttest 62/62. Im Browser mit einer Attrappe von `SrAndroid`
durchgespielt: Artikel und PDF per Link, geteilter Link, Fehlerfälle,
Zeilen-Modus, Tempo-Bremse, Markieren samt Export, Check, Vorlesen.
PDF-Leser zusätzlich mit drei erzeugten PDFs (Chromium, LibreOffice Text
und Writer) gegen den Ausgangstext verglichen.

Am Gerät offen: ob die Stimme startet und Wörter meldet, wie das Vorlesen
bei langen Sätzen klingt, ob der Link-Import echte Nachrichtenseiten
brauchbar erwischt, echte Buch-PDFs, und ob der Zeilen-Modus bei hohem
Tempo flüssig scrollt.
