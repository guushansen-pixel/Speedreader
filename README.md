# Speedreader

Offline-Android-App: eigene Texte Wort für Wort lesen (RSVP, "Rapid Serial
Visual Presentation") mit einstellbarem Tempo. Texte kommen aus der
Zwischenablage, einer Textdatei, einem PDF, einem EPUB ohne Kopierschutz,
von einer Webseite oder per "Teilen" aus einer anderen App.

APK: [dist/speedreader-1.2.apk](dist/speedreader-1.2.apk) (v1.2; die APK für v1.3 ist noch nicht gebaut)

## Funktionen

- **Leser:** ein Wort (oder 2–3) an fester Stelle, ein Buchstabe als
  Blickpunkt farbig. Tippen hält an; dann erscheint der Text ringsum, ein
  Tipp auf ein Wort springt dorthin. Tempo in 25er-Schritten, "Satz" zurück,
  Schieberegler für die Position.
- **Pausen:** lange Wörter, Zahlen, Satzzeichen und Absatzenden werden
  länger gezeigt (`delayFactor`). Abschaltbar.
- **Tempo-Training:** jede Sitzung startet beim eingestellten Tempo und
  wird pro Minute um einen festen Betrag schneller, bis zu einer Obergrenze.
- **Bibliothek:** mehrere Texte mit gespeicherter Position.
- **Statistik:** Lesezeit und Wörter pro Tag. Die dort gezeigte WPM-Zahl ist
  das echte Tempo inklusive Pausen und liegt unter der eingestellten.
- **Verständnis:** Die App stellt keine Fragen (sie ist offline). Unter
  "Tipps" kopiert ein Knopf die zuletzt gelesene Passage (höchstens 1200
  Wörter) samt Auftrag für ein Quiz in einem Claude-Chat.

## Anzeigedauer

Grunddauer = 60 000 ms / WPM. Zuschläge als Vielfaches der Grunddauer:

| Fall | Faktor |
|---|---|
| Wort bis 8 Zeichen | 1 |
| längere Wörter | +0,04 je Zeichen über 8, höchstens +0,6 |
| enthält eine Ziffer | +0,4 |
| endet auf , ; : oder Gedankenstrich | +0,5 |
| Satzende (. ! ? …, auch vor Anführungszeichen) | +1,2 |
| letztes Wort eines Absatzes | +1,5 |

Der Blickpunkt (`orpIndex`) liegt je nach Wortlänge auf dem 1. bis 5.
Buchstaben, führende Satzzeichen zählen nicht mit. Wörter über 22 Zeichen
werden in Stücke von 14 geteilt.

## EPUB

Eigener kleiner ZIP-Leser (`zipIndex`, `zipRead`) mit `DecompressionStream`
des Browsers, kein externes Paket. Gelesen werden `container.xml`, die
OPF-Datei und die Kapitel in Spine-Reihenfolge; aus dem XHTML werden die
Absätze als Text übernommen. Bilder, Fußnoten-Verweise und Formatierung
fallen weg. EPUBs mit Kopierschutz (verschlüsselte Inhalte in
`encryption.xml`) werden mit einer Meldung abgelehnt und nicht entschlüsselt.
Kein ZIP64, höchstens 25 MB.

## PDF

Eigener kleiner PDF-Textleser (`parsePdf`), kein externes Paket:

- Objekte werden direkt aus der Datei gesammelt (`N G obj`), auch aus
  Objekt-Strömen (PDF 1.5); komprimierte Ströme über `DecompressionStream`.
- Seiten in der Reihenfolge des Seitenbaums. In den Inhalts-Strömen werden
  die Text-Befehle ausgewertet (`Tf Td TD T* Tm Tj TJ ' "`, dazu `cm q Q`
  für die Lage auf der Seite).
- Zeichen werden über die ToUnicode-Tabelle der Schrift übersetzt, sonst
  über die Windows-Kodierung und eine kleine Liste von Zeichennamen.
- Mit den Zeichenbreiten der Schrift wird erkannt, wo zwischen zwei
  Textstücken eine Lücke ist (Leerzeichen ohne Leerzeichen-Glyphe).
- Zeilen werden zu Absätzen gefügt (größerer Zeilenabstand oder andere
  Schriftgröße = neuer Absatz), Trennstriche am Zeilenende entfernt,
  reine Seitenzahlen verworfen.

Grenzen: Gescannte PDFs sind Bilder und liefern keinen Text. Verschlüsselte
PDFs werden abgelehnt. Mehrspaltiger Satz, Tabellen, Fußnoten und
Kopfzeilen landen in der Reihenfolge im Text, in der sie in der Datei
stehen. Andere Kompressionen als Flate (LZW, ASCII85) werden übersprungen.
Geprüft mit PDFs aus Chromium und LibreOffice (Text- und Writer-Export,
Blocksatz mit Silbentrennung), mit keinem gekauften Buch und keinem
LaTeX-PDF.

## Webseite

`htmlArticle` nimmt Navigation, Kopf- und Fußbereich, Formulare und Skripte
heraus und sucht den Bereich mit dem meisten Absatztext (`<article>` oder
`<main>`, wenn sie den Großteil enthalten). Den Abruf macht in der App die
Brücke (`fetchUrl`), weil der WebView unter `file://` fremde Seiten nicht
lesen darf. Nur `https`, höchstens 25 MB, ein PDF hinter dem Link wird als
PDF gelesen. Ein geteilter Text, der nur aus einem Link besteht (auch
„Titel + Link"), wird als Link behandelt (`sharedUrl`).

**Das ist der einzige Netzzugriff der App**, und er passiert nur, wenn man
einen Link eingibt oder teilt. Seiten mit Anmeldung, Bezahlschranke oder
nachgeladenem Text gehen nicht.

## Vorlesen, Zeilen-Modus, Markieren, Check

- **Vorlesen (🔊):** Satz für Satz über Androids Sprachausgabe (Brücke
  `ttsSpeak`; `window.speechSynthesis` gibt es im WebView nicht). Der Satz
  ist hell, das gesprochene Wort markiert, wenn die Stimme es meldet (ab
  Android 8). Anhalten setzt auf den Satzanfang zurück. Die Knöpfe ± ändern
  dann das Sprechtempo.
- **Zeilen-Modus (≡):** Der Text bleibt stehen, die Markierung läuft mit
  denselben Zeiten wie im Wort-Modus (`delayFactor`) hindurch. Tippen in den
  Text hält an.
- **Markieren:** angehalten „✎ Markieren", dann einen Satz antippen
  (erneut antippen hebt auf). Unter ☰ stehen alle Stellen, ein Knopf kopiert
  sie mit Seitenzahl.
- **Verständnis-Check:** bis zu drei Runden mit je drei Sätzen; zwei stammen
  aus den zuletzt gelesenen höchstens 700 Wörtern, einer aus dem noch
  ungelesenen Teil (am Textende aus einem anderen Text der Bibliothek).
  Geprüft wird Wiedererkennen, nicht Verstehen; die App sagt das. Bei
  weniger als der Hälfte bietet sie an, das Tempo um 50 zu senken.
- **Tempo-Bremse:** Zweimal „Satz zurück" innerhalb von 90 Sekunden beim
  Lesen senkt das Tempo um 25 (abschaltbar).
- **Restzeit:** bis Kapitelende und Textende, ab 20 Sekunden Lesezeit mit
  dem echten Tempo der Sitzung statt der eingestellten Zahl.

## Datenmodell

- IndexedDB `speedreader` / Store `texts`: Text je ID (Rückfall:
  localStorage `speedreader.text.<id>`)
- `speedreader.library.v1`: `[{id, title, count, pos, added, last}]`
- `speedreader.settings.v1`: `wpm`, `chunk`, `font`, `pauses`, `ramp`,
  `rampPerMin`, `rampMax`, `mode` (wort/zeile), `brake`, `ttsLang`,
  `ttsRate`, `schema`
- `speedreader.stats.v1`: Datum → `{ms, words, cq, cr}` (cq/cr = Fragen und
  richtige Antworten im Verständnis-Check)

## Einordnung

Die Forschung zu RSVP und Speedreading (z. B. Rayner et al. 2016) ist
ernüchternd: Bei sehr hohem Tempo sinkt das Verständnis. Die App ist für
Texte gedacht, die man überfliegen oder wiederholen will, und zum Abtrainieren
des inneren Mitsprechens. Sie verspricht kein Verständnis bei 800 WPM.

## Augen-Gym

Drei kurze Übungen, erreichbar von der Startseite. Stände liegen in
`speedreader.gym.v1`.

| Übung | Ablauf | Anpassung | Ergebnis |
|---|---|---|---|
| Schulte-Tabelle | Zahlen 1 bis n² der Reihe nach antippen, Blick in der Mitte; 3×3 bis 7×7 | Größe wählbar | Zeit ab der 1, Bestzeit je Größe, Fehlgriffe |
| Blickspanne | Ziffernreihe blitzt 300 ms auf, dann eintippen; 10 Durchgänge | zwei richtige in Folge: ein Zeichen mehr; Fehler: eins weniger (3 bis 12) | längste richtige Reihe |
| Blicksprung | 40 Ziffern abwechselnd am linken und rechten Rand, bei der Zielziffer tippen (zählt auch noch während der nächsten Ziffer) | ab 90 % Treffern und höchstens einem Fehlalarm 10 % schneller, unter 70 % oder ab vier Fehlalarmen 15 % langsamer (250 bis 1200 ms) | Treffer, Fehlalarme, Tempo |

Einordnung: Solche Übungen verbessern die geübte Aufgabe. Dass sie das
Lesetempo im Alltag steigern, ist nicht gut belegt; die App sagt das auch.

## Abend-Farbfilter und Fokus-Lesen

- **Abend-Farbfilter:** eine Ebene über der ganzen App, die jede Farbe mit
  einem warmen, dunkleren Ton multipliziert (`mix-blend-mode: multiply`). Bei
  voller Stärke bleibt vom Blau ein Zehntel, vom Grün die Hälfte, und alles
  wird 30 % dunkler. Einstellungen: aus, immer an, oder automatisch nach
  Uhrzeit (steigt von "Beginn" bis "volle Stärke" an, Standard 19 bis 22 Uhr,
  und bleibt bis 6 Uhr). Einen Lichtsensor kann die App nicht abfragen; der
  Filter richtet sich deshalb nur nach der Uhr. Ob Blaufilter das Einschlafen
  verbessern, ist in Studien umstritten; die App sagt das in den
  Einstellungen.
- **Fokus-Lesen:** leises, live erzeugtes Rauschen (tief oder hell), solange
  der Leser offen ist. Umschalten in den Einstellungen oder im Leser über ♪.

## Seiten, Lesezeichen, Kapitel

- **Seiten:** Eine Seite sind 250 Wörter (`PAGE`). Der Leser zeigt "Seite x
  von y"; über ☰ springt man zu einer Seitenzahl. Echte Buchseiten gibt es
  nicht, die Zahl ist dafür unabhängig von Schriftgröße und Gerät.
- **Lesezeichen:** 🔖 setzt eins an der aktuellen Stelle oder entfernt das
  dort liegende (im Umkreis von 25 Wörtern). Liste unter ☰ mit Textanfang,
  Seite und Datum.
- **Kapitel:** Bei EPUBs aus den Teilen des Buchs (erste Überschrift h1 bis
  h3 je Teil; lange Teile ohne Überschrift heißen "Abschnitt n"). Bei
  reinem Text aus Absätzen, die wie Überschriften aussehen (`isHeading`:
  kurz, einzeilig, beginnt mit Kapitel/Teil/Zahl/römischer Zahl oder steht
  ganz in Großbuchstaben). Das aktuelle Kapitel steht beim Anhalten über dem
  Text und ist in der Liste markiert. Texte aus älteren Versionen bekommen
  ihr Verzeichnis beim nächsten Öffnen.

Gespeichert je Text in `speedreader.library.v1`: `chapters: [{t, i}]`,
`marks: [{i, t, d}]` (i = Wortposition), seit v1.3 `hl: [{a, b, t}]`
(markierte Sätze, Wortbereich und Text).
