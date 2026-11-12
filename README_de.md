# Mova/Dreame Vacuum Binding (inoffiziell, experimentell)

Spricht Mova-/Dreame-/Trouver-Saugroboter über die DreameHome-Cloud an (z. B. Mova V50 Ultra).
Protokoll-Vorlage: Tasshack/dreame-vacuum (Home-Assistant-Integration, MIT-Lizenz), Stand v2.0.3.
Englische Fassung: [README.md](README.md). Die Texte des Bindings richten sich nach der Sprache in den openHAB-Regionaleinstellungen (Deutsch oder Englisch).

## Unterstützt

- Zustand, Akkustand, Lädt-Status, Fehlercode, Status-Code (Polling)
- Befehle: start, pause, stop, dock, locate
- Einstellungen: Saugstufe (schreiben), Lautstärke (schreiben), Nicht stören (nur lesen)
- Statistik: Dauer und Fläche der aktuellen Reinigung, Gesamtwerte, Anzahl Reinigungen
- Verbrauchsmaterial (Rest in Prozent): Hauptbürste, Seitenbürste, Filter, Sensoren, Räder, Kalkschutz (jeweils Rest in Prozent; Räder und Kalkschutz zusätzlich Restzeit in Stunden)
- Raumreinigung über Raum-Nummern
- Reinigungsmodus und Wasserstufe: nur als Rohwerte (Diagnose)

- Teppich- und Bodeneinstellungen (lesen und schreiben, experimentell, siehe unten)

### Räume und Karte

Das Binding lädt die Karte beim Start und danach alle 30 Minuten aus der Cloud, entschlüsselt sie
(AES-Anfangswert je Modell, für die V50 Ultra `r2582a` eingebaut; sonst Thing-Parameter `mapKey`)
und liest die Raumliste.

| Kanal | Typ | Beschreibung |
| --- | --- | --- |
| rooms | String | Räume als Liste, z. B. `1: Wohnzimmer, 2: Küche` |
| map-refresh | Switch | ON lädt die Karte sofort neu |

Der Kanal `clean-rooms` nimmt Nummern oder Raumnamen, durch Komma getrennt (`1,2` oder `Küche,Flur`).
Ausgeblendete Räume (Kartenfeld `delsr`) werden nicht aufgeführt. Pro Raum sind Optionen nach einem `@`
möglich, mehrere durch `/` getrennt:

| Option | Bedeutung |
| --- | --- |
| times=N | Wiederholungen (1 bis 3) |
| suction=quiet/standard/strong/turbo | Saugstufe (oder Zahl 0 bis 3) |
| water=1/2/3 | Wasser niedrig, mittel, hoch |
| room | übernimmt die in der App gespeicherten Einstellungen des Raums |

Beispiel: `Küche@times=2/suction=strong/water=3, Flur@room`. Ohne Optionen gelten die aktuelle Saug- und
Wasserstufe, einmal.

Hinweis: `times` wird vom Roboter (Mova V50 Ultra) übernommen. `suction` und `water` werden zwar gesendet, der
Roboter wendet sie nach bisherigen Tests aber vermutlich nicht an, sondern nutzt die gespeicherten Raum- bzw.
Roboter-Einstellungen. Saug- und Wasserstufe daher besser über die Kanäle `suction` und `water-level` oder die
Raum-Einstellungen in der App setzen.

Die Karte wird etwa alle 30 Minuten und kurz nach Ende einer Reinigung neu geladen. Ein `REFRESH` auf `rooms`,
`room-info` oder `map-image` lädt sie sofort neu.

Der Kanal `room-info` (nur lesen) liefert je Raum eine Zeile mit Modus, Saugstufe, Wasser, Wiederholungen,
Reihenfolge und letzter Reinigung, sortiert nach der Reinigungsreihenfolge der App.

## Einrichtung

1. Thing **Mova/Dreame Account** (Bridge) anlegen: Benutzername, Passwort, Account-Typ
   (Mova-App = `mova`, Dreamehome-App = `dreame`) und Region (`eu` zuerst probieren).
1. Sobald die Bridge ONLINE ist, steht im openHAB-Log (`log:tail`) pro Gerät eine Zeile
   `Mova/Dreame-Gerät gefunden: did=... model=... name=...`.
1. Thing **Mova/Dreame Saugroboter** an die Bridge hängen und die `did` eintragen.

Bei bestehenden Things ergänzt das Binding neue Kanäle beim Start selbst.

## Kanäle

| Kanal | Typ | Beschreibung |
| --- | --- | --- |
| state | String | sweeping, idle, paused, error, returning, charging, ... |
| status | Number | roher Status-Code (siid 4 / piid 1) |
| battery | Number:Dimensionless | Akkustand in % |
| charging | Switch | lädt |
| error | Number | Fehlercode, 0 = kein Fehler |
| error-text | String | Klartext zum Fehlercode (nur lesen; Texte aus der Tasshack-Integration) |
| last-cleaning | String | letzte Reinigung: Start, Dauer, Fläche, Ergebnis (nur lesen) |
| cleaning-history | String | die letzten 10 Reinigungen, eine Zeile je Lauf (nur lesen; wird alle 6 Stunden und kurz nach Ende einer Reinigung aus der Cloud geladen) |
| room-settings | String | Raum-Einstellungen schreiben (nur Befehl). Syntax: `Küche@times=2/suction=strong/water=2/mode=sweeping_and_mopping/order=1, 9@times=1`. Raum per Name oder Nummer; `times` 1–3, `suction` quiet/standard/strong/turbo, `mode` sweeping/mopping/sweeping_and_mopping, `water` 1–3, `order` Position. Nicht während einer Reinigung. |
| clean-zone | String | Zonenreinigung (nur Befehl): `x1,y1,x2,y2[@times=1-3/suction=quiet..turbo/water=1-3]`, mehrere Zonen mit `;` getrennt. Koordinaten in Karteneinheiten (mm) wie in der Karte. |
| clean-spot | String | Punktreinigung (nur Befehl, ca. 1,5 m² um den Punkt): `x,y[@times=1-3/suction=quiet..turbo/water=1-3]`, mehrere Punkte mit `;` getrennt. Koordinaten in mm; Punkte außerhalb der Karte werden abgelehnt. |
| room-zones | String | Eine Zeile je Raum mit umschließendem Rechteck (`Zone x1,y1,x2,y2`) und einem sicher befahrbaren Punkt nahe der Raummitte („Mitte“) in mm, zur Verwendung mit clean-zone und clean-spot (nur lesen). Bei L-förmigen Räumen kann das Rechteck Teile anderer Räume enthalten. |
| robot-position | String | Position des Roboters (`x,y` in mm) mit Raum, Station und Kartenstand (nur lesen). Während Reinigung und Rückfahrt wird die Karte jede Minute neu geladen, sonst alle 30 Minuten. |
| go-to | String | Zu einem Punkt fahren und dort stehen bleiben (nur Befehl): `x,y` in mm. Nur auf Modellen mit Kamera; Punkte außerhalb der Karte werden abgelehnt. |
| remote-control | String | Fernsteuerung, ein Schritt je Befehl (nur Befehl): `forward`, `backward`, `left`, `right`, `stop` oder `Drehung,Geschwindigkeit` (-360..360, -600..600). |
| room-edit | String | Räume bearbeiten (nur Befehl, nicht während der Reinigung): `Raum@name=Neuer Name/visible=on\|off/floor=none\|wood\|tile/mop=A\|B\|C`, mehrere Räume mit `;` getrennt. Beispiel: `Technikraum@name=Haustechnik/floor=tile`. Es werden nur tatsächlich geänderte Räume gesendet. |
| current-room | String | Raum, in dem der Roboter laut letzter Karte steht (nur lesen). |
| room-progress | String | Erledigte, übersprungene und offene Räume des laufenden Auftrags (nur lesen). |
| obstacles | String | Erkannte Hindernisse, eine Zeile je Hindernis mit Nummer, Art und Position (nur lesen). |
| obstacle-ignore | String | Hindernis ignorieren (nur Befehl, nicht während der Reinigung): `Nummer=ignore` oder `Nummer=restore`. |
| map-command | String | Karten-Befehl (nur Befehl, nicht während der Reinigung): `rename:Neuer Name` benennt die aktuelle Karte um, `backup` sichert sie in der Cloud. |
| shortcuts | String | Liste der Shortcuts (Nummer, Name, "läuft"), wird mit der Karte geladen (nur lesen). |
| shortcut | String | Shortcut-Befehl (nur Befehl): `start:Nummer oder Name` (ohne Präfix wird gestartet), `rename:Nummer=Neuer Name`, `delete:Nummer`. Ändern und Löschen nicht während einer Reinigung. |
| command | String | start, pause, stop, dock, locate. `start` setzt eine pausierte Moppwäsche oder Rückfahrt zur Station fort, `start` und `dock` auch eine pausierte Rückfahrt zum Absaugen (wie Tasshack 2.0.3). |
| suction | String | quiet, standard, strong, turbo (lesen und schreiben) |
| volume | Number:Dimensionless | Lautstärke 0 bis 100 % (lesen und schreiben) |
| dnd | Switch | Nicht stören aktiv (nur lesen) |
| cleaning-mode-raw | Number | gepackter Rohwert des Reinigungsmodus (Diagnose) |
| water-raw | Number | Rohwert der Wasserstufe (Diagnose) |
| cleaning-time | Number:Time | Dauer der aktuellen/letzten Reinigung |
| cleaned-area | Number:Area | Fläche der aktuellen/letzten Reinigung |
| total-cleaning-time | Number:Time | gesamte Reinigungsdauer |
| total-cleaned-area | Number:Area | gesamte gereinigte Fläche |
| cleaning-count | Number | Anzahl Reinigungen |
| main-brush-left | Number:Dimensionless | Hauptbürste, Rest in % |
| side-brush-left | Number:Dimensionless | Seitenbürste, Rest in % |
| filter-left | Number:Dimensionless | Filter, Rest in % |
| sensor-left | Number:Dimensionless | Sensoren, Rest in % |
| wheel-left | Number:Dimensionless | Räder, Rest in % |
| wheel-time-left | Number:Time | Räder, Restzeit in Stunden |
| scale-inhibitor-left | Number:Dimensionless | Kalkschutz, Rest in % |
| scale-inhibitor-time-left | Number:Time | Kalkschutz, Restzeit in Stunden |
| washboard-left | Number:Dimensionless | Waschbrett-Reinigung, Rest in % (Kanal erscheint nur, wenn das Gerät den Wert liefert) |
| washboard-time-left | Number:Time | Waschbrett-Reinigung, Restzeit in Stunden (ebenso) |
| clean-rooms | String | Raum-Nummern oder -Namen, z. B. `1,2` (je einmal, aktuelle Saugstufe) |

### Teppich- und Bodeneinstellungen

Die Kennungen stammen aus Tasshack/dreame-vacuum v2.0.1. Welche davon die V50 Ultra wirklich
unterstützt, zeigt der Betrieb: Nicht unterstützte Einstellungen bleiben leer (NULL) und werden
nicht geschrieben. Abgefragt wird alle 20 Sekunden (Thing-Parameter `settingsInterval`, mindestens 10) und kurz nach jeder Änderung.

| Kanal | Typ | Werte |
| --- | --- | --- |
| carpet-cleaning | String | avoidance (Meiden), adaptation (Saugen, Mopp-Pad anheben), remove_mop, adaptation_without_route, vacuum_and_mop, cross (überqueren), ignore |
| carpet-boost | Switch | Teppich-Boost |
| carpets-first | Switch | Zuerst Teppiche reinigen |
| side-brush-carpet-rotate | Switch | Seitenbürste rotiert auf Teppich |
| intensive-carpet-cleaning | Switch | Intensive Teppichreinigung |
| auto-lds | String | off (Aus), security (Sichere Abdeckung), extreme (Extreme Abdeckung) |
| low-lying-frequency | String | weekly (Niedrige Häufigkeit), daily (Hohe Häufigkeit) |
| collision-avoidance | Switch | Kollisionsvermeidungsmodus |
| auto-mount-mop | Switch | Mopp-Pads automatisch demontieren/montieren |
| floor-direction-cleaning | Switch | Reinigung in Bodenrichtung |
| dynamic-obstacle-cleaning | Switch | Dynamische Reinigung bei Hindernissen |
| obstacle-avoidance | Switch | Intelligente Hindernisvermeidung (Voraussetzung für die beiden nächsten Kanäle) |
| intelligent-stain-cleaning | Switch | Intelligente Fleckenentfernung |
| large-particles | Switch | Erkennung großer Schmutzpartikel |
| obstacle-crossing | Switch | Synchronisierte Hindernisüberquerung |
| clearance-leg-crossing | Switch | Hürdenartige Hindernisüberquerung (Zuordnung vermutet) |
| active-suspension-crossing | Switch | Aktive Federung beim Überwinden |
| lift-chassis-on-carpet | Switch | Chassis auf Teppich anheben |
| close-roller-cover-on-carpet | Switch | Rollenabdeckung auf Teppich schließen |
| side-reach | Switch | Seitenbürste ausfahren (Side Reach) |
| mop-extend | Switch | Mopp-Pad ausfahren (Mop Extend) |
| mop-extend-frequency | String | standard, intelligent, high |
| gap-cleaning-extension | Switch | Spalten-Reinigung erweitern |
| mopping-under-furniture | Switch | Intensive Reinigung für Möbelfüße |

### Basisstation

Gleiche Technik und gleiche Vorbehalte wie oben. Die Werte von `auto-empty-mode` gelten für Modelle mit
"intelligentem" Entleeren und können bei älteren Stationen anders belegt sein.

| Kanal | Typ | Beschreibung |
| --- | --- | --- |
| dnd-disable-auto-empty | Switch | Entleeren im Nicht-stören-Modus aus |
| auto-drying | Switch | Automatisches Trocknen |
| smart-drying | Switch | Intelligentes Trocknen |
| silent-drying | Switch | Leises Trocknen |
| dust-bag-drying | Switch | Staubbeutel trocknen |
| self-clean | Switch | Selbstreinigung der Station |
| water-electrolysis | Switch | Wasser-Elektrolyse |
| mopping-with-detergent | Switch | Wischen mit Reinigungsmittel |
| smart-mop-washing | Switch | Intelligentes Wischpad-Waschen |
| hot-washing | Switch | Heißwaschen |
| uv-sterilization | Switch | UV-Desinfektion |
| ultra-clean-mode | Switch | Ultra-Reinigungsmodus |
| drying-time | String | Trocknungsdauer im Modus Schnell (1h, 2h, 3h, 4h) |
| resume-cleaning | Switch | Reinigung nach dem Aufladen fortsetzen |
| pet-detection | Switch | Erkennung von Haustieren |
| obstacle-pictures | Switch | Bilder der Hindernisse |
| fill-light | Switch | Fülllicht |
| mop-washing-with-detergent | Switch | Moppwaschen mit Reinigungsmittel |
| child-lock | Switch | Kindersicherung (Tasten an Station und Roboter) |
| auto-empty-mode | String | Automatisches Entleeren (off, ...) |
| mop-wash-level | String | Wischpad-Waschstufe (water_saving, daily = "Standard" in der App, deep) |
| scraper-frequency | String | Häufigkeit Abstreifer (off, ...) |
| water-temperature | String | Wassertemperatur (normal, ...) |
| self-clean-frequency | String | Selbstreinigung Häufigkeit (by_area, ...) |
| mopping-type | String | Wischart (daily, ...) |

Ist die intelligente Hindernisvermeidung aus, schaltet die App Fleckenentfernung und dynamische Reinigung
ebenfalls ab. Das Binding lehnt das Einschalten dieser beiden Kanäle dann mit einer Warnung im Log ab.

### Kartenbild

| Kanal | Typ | Beschreibung |
| --- | --- | --- |
| map-image | Image | gezeichnete Karte als PNG im hellen Design wie in der App (transparenter Hintergrund, Nummer-Bubble mit Reinigungsreihenfolge, Raumname, Teppiche gepunktet, Hindernisse, Station und Roboter) |
| map-image-dark | Image | dieselbe Karte im dunklen Design (wird nur gezeichnet, wenn ein Item verknüpft ist) |

In einer Main-UI-Seite folgt das Bild automatisch dem openHAB-Design, z. B. in einer `oh-image-card`:
`item: =themeOptions.dark === 'dark' ? 'Robbi_MapDark' : 'Robbi_Map'`.

Das Bild wird bei jedem Laden der Karte neu gezeichnet (alle 30 Minuten oder über `map-refresh`). Ist es gedreht
oder gespiegelt, helfen die erweiterten Thing-Parameter `mapRotation` (0, 90, 180, 270) und `mapFlip`. Beschriftet wird standardmäßig mit dem Raumnamen (`mapLabels`: `name`, `number`, `both`). Die Bildgröße lässt sich mit `mapSize` (Kantenlänge in Pixeln, Standard 600) ändern. Standardmäßig wird das Bild von oben nach unten gespiegelt (`mapFlip` an), damit es wie in der App aussieht.
Mit dem erweiterten Thing-Parameter `showHiddenRooms` werden in der App ausgeblendete Räume grau mitgezeichnet.
Die Karte wird auf den gezeichneten Bereich zugeschnitten. Die Position von Roboter und Station kommt aus der Karte und ist ein Momentbild.

### Reinigungsmodus und Wasserstufe

| Kanal | Typ | Beschreibung |
| --- | --- | --- |
| cleaning-mode | String | sweeping, mopping, sweeping_and_mopping, mopping_after_sweeping (lesen und schreiben) |
| charge-limit | Number | Ladelimit 80 bis 100 Prozent (lesen und schreiben, Property 28/47) |
| off-peak-charging | Switch | Niedertarif-Laden ein/aus (Property 3/3) |
| off-peak-start, off-peak-end | String | Beginn und Ende des Niedertarifs als HH:mm |
| water-level | Number | Wasserstufe Mopp 1 bis 32 (lesen und schreiben, Property 28/1) |

Der Reinigungsmodus steckt im gepackten Wert 4/23. Beim Schreiben werden nur die untersten zwei Bit
geändert, der Rest bleibt erhalten. Die Belegung stammt vom Modell mit Mopp-Pad-Anheben (wie beim V50 Ultra);
bei anderen Modellen kann sie abweichen. Der Maximalwert der Wasserstufe kann je nach Einstellung der
Waschhäufigkeit kleiner sein (z. B. 15).

| cleangenius | String | off, routine_cleaning, deep_cleaning (Auto-Einstellung `SmartHost`) |
| cleangenius-mode | String | vacuum_and_mop, mop_after_vacuum (Property 28/5) |

CleanGenius ist die KI-gesteuerte Reinigung der App und wirkt nur bei eingesetztem Mopp-Pad. In der App
sperrt sie viele andere Einstellungen während einer Reinigung; das Binding schreibt trotzdem, das Gerät
kann den Wert aber ablehnen. Bitte erst testen.

### Stationssensoren (nur lesen)

| Kanal | Typ | Werte |
| --- | --- | --- |
| clean-water-tank | String | installed, not_installed, low_water, checking |
| dirty-water-tank | String | installed, not_installed_or_full |
| dust-bag | String | installed, not_installed, check |
| detergent-status | String | installed, disabled, low_detergent |
| base-status | String | idle, washing, drying, returning, paused, clean_add_water, returning_to_drain, returning_for_dry_mop |
| dust-bag-drying-status | String | idle, drying, paused |
| drainage-status | String | idle, draining, draining_success, draining_failed |
| auto-empty-status | String | idle, active, not_performed, returning |
| dust-collection | String | not_available, available, over_use, never |
| drying-progress | Number:Dimensionless | Trocknungsfortschritt in % |
| mop-in-station | Switch | Mopp steckt in der Station |

### Basisstation-Befehle und Zurücksetzen

| Kanal | Typ | Befehle |
| --- | --- | --- |
| base-command | String | wash_mop, pause_wash, resume_wash, dry_mop, stop_dry_mop, dry_dust_bag, stop_dry_dust_bag, drain_dirty_water, empty_clean_tank, empty_dust |
| reset-consumable | String | main_brush, side_brush, filter, sensor, wheel, scale_inhibitor, washboard |

`reset-consumable` setzt den Zähler nach dem Wechsel auf 100 % zurück. Nicht benötigte Teile (z. B. das
Mopp-Pad) gibt es bei diesem Modell nicht. Alle Befehle sind experimentell und nur nach Tasshack/dreame-vacuum
v2.0.1 übernommen; bitte einzeln testen.

## Beispieldateien

Fertige Beispieldateien liegen in [doc/examples](doc/examples): [Things](doc/examples/movavacuum.things), [Items für alle Channels](doc/examples/movavacuum.items), [Regeln](doc/examples/movavacuum.rules) (Raumauswahl, Alexa) und eine [Main-UI-Seite](doc/examples/vacuum-page.yaml), deren Karte dem hellen/dunklen Design folgt.

## Fehlersuche

Log-Level erhöhen: `log:set DEBUG org.openhab.binding.movavacuum`.
Die Rohwerte der ersten Abfragen werden einmal ins Log geschrieben.

- Bridge bleibt OFFLINE mit Verbindungs-/TLS-Fehler oder HTTP-Fehler vor dem Login:
  Die Vorlage baut ihre TLS-Verbindung selbst auf. Ob die Cloud den Standard-TLS-Client von
  Java akzeptiert, hat sich bisher bei Mova/eu bewährt.
- "Login abgelehnt": Account-Typ und Region prüfen.
- Geräteliste leer: andere Region probieren.
- Kanal bleibt leer: Das Gerät unterstützt die Property evtl. nicht (Einträge mit Fehlercode werden übersprungen).
