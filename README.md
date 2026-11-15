# Speedreader

Offline-Android-App: eigene Texte Wort für Wort lesen (RSVP, "Rapid Serial
Visual Presentation") mit einstellbarem Tempo. Texte kommen aus der
Zwischenablage, einer Textdatei, einem EPUB ohne Kopierschutz oder per
"Teilen" aus einer anderen App.

APK: [dist/speedreader-1.1.apk](dist/speedreader-1.1.apk)

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

## Datenmodell

- IndexedDB `speedreader` / Store `texts`: Text je ID (Rückfall:
  localStorage `speedreader.text.<id>`)
- `speedreader.library.v1`: `[{id, title, count, pos, added, last}]`
- `speedreader.settings.v1`: `wpm`, `chunk`, `font`, `pauses`, `ramp`,
  `rampPerMin`, `rampMax`, `schema`
- `speedreader.stats.v1`: Datum → `{ms, words}`

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
