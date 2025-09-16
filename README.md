
# GefahrenReader 🚒 – UN‑Nummern, Klassen & Gefahrenzahlen (Kemler)

Ein schnelles, offline nutzbares **Lern‑ und Nachschlage‑Tool**. 
Links navigierst du **nach Klasse** oder **nach UN‑Bereich**, rechts siehst du die Stoffliste mit **UN‑Nummer**, **Gefahrenzahl**, **Klasse** und **Bezeichnung** – per Klick / Doppelklick öffnet sich ein kompaktes **Detail‑Popup**.

![Hero](wall.png)

> Fokus: **einfach**, **übersichtlich**, **ohne Internet**

---

## ✨ Funktionen

- **Zwei Navigationspfade**
  - *Nach Klasse* → z. B. `1.1D`, `2.1`, `3`, `8`, `9` …
  - *Nach UN‑Bereich* → `0000–0099`, `0100–0199`, … `9000–9006`
- **Tabelle** mit: **UN‑Nummer**, **Gefahrenzahl (Kemler)**, **Klasse**, **Bezeichnung**, **Hinweis**
- **Details‑Popup** (Doppelklick/Schaltfläche) mit allen Infos
- **UN‑Suche** (Teil‑ und Volltreffer)
- **Dark‑Nimbus UI** mit Akzentfarbe **#00D2FF** (barrierearm, lesefreundlich)
- **Keinerlei externe Libraries** – reine Standard‑JDK‑Swing‑App

---

## 📦 Daten & Struktur

Lege die App neben einen `data/`‑Ordner (oder in denselben Ordner), der mind. Folgendes enthält:

```
data/
├─ index.json                 # Index aller Bereichsdateien
├─ gefahrenzahl.json          # ADR-valide Gefahrnummern inkl. X-Varianten
├─ 0000-0099.json             # Bereichsdateien …
├─ 0100-0199.json
├─ …
└─ 9000-9006.json
```

**Schema der Bereichsdateien** (Auszug):
```json
{
  "title": "UN-Nummern 2705–2799",
  "generated": "YYYY-MM-DD",
  "count": 76,
  "columns": ["un_number", "gefahrenzahl", "klasse", "bezeichnung"],
  "rows": [
    { "un_number": "2705", "gefahrenzahl": "30", "klasse": "3", "bezeichnung": "…" }
  ]
}
```

**`gefahrenzahl.json`** liefert die Kurzbeschreibungen zu Kemler‑Codes (z. B. `33 = leicht entzündbare Flüssigkeit`).  
**Optional**: `missing_gefahrenzahl.json` (Liste aller Fälle ohne Kemlerzahl für Qualitätskontrolle).

> ⚠️ **Datenquelle = JSON**: Wenn dort `gefahrenzahl` „–“ war, steht im JSON `null`. Das ist oft korrekt (z. B. Klasse 1, nicht ADR‑pflichtig, Beförderung verboten, Sonderfälle).

---

## 🛠️ Build & Start

Voraussetzung: **Java 17+**

```bash
# kompilieren
javac *.java

# starten
java GefahrenReaderApp
```

Beim Start sucht die App `./data/index.json` oder `./index.json`. Falls beides fehlt, wählst du den Ordner per Dialog.

---

## 🧭 Bedienung

1. **Links**: Navigation im **JTree** öffnen → *Klasse* oder *UN‑Bereich* wählen.  
2. **Rechts**: Stoffliste ansehen; **Doppelklick** oder **Details…** öffnet das Popup.  
3. **Suche**: UN‑Nummer unten eingeben → Liste wird gefiltert; **Reset** setzt zurück.

Tastentipps: `Enter` in der Suche startet die Filterung; Markierung + `Enter` entspricht Doppelklick (Details).

---

## 🧠 Hintergrundwissen (kurz & praxisnah)

### UN‑Nummern (Stoffnummern)
Vierstellige Identnummern für gefährliche Güter im Transport, herausgegeben durch das UN‑Expertenkomitee („Orange Book“). Sie dienen **weltweit** der eindeutigen Zuordnung auf Warntafeln, Dokumenten und in Einsatz‑/Notfallkarten. Historisch gewachsen seit Mitte des 20. Jahrhunderts – Ziel: **einheitliche** Kennzeichnung, **schnellere** Gefahreneinschätzung, **bessere** Einsatz‑Sicherheit.

### Gefahrgutklassen (1–9)
Einteilung nach **Hauptgefahr** (z. B. `1 = Explosivstoffe`, `2 = Gase`, `3 = entzündbare Flüssigkeiten`, `5.1 = oxidierend`, `6.1 = giftig`, `7 = radioaktiv`, `8 = ätzend`, `9 = verschiedene/umweltgefährdend`).  
Klasse 1 nutzt zusätzlich **Unterklassen** (1.1–1.6) und **Verträglichkeitsgruppen** (A–S), z. B. `1.1D`.

### Gefahrenzahl / Kemler‑Zahl
Obere Zahl auf der **orangefarbenen Warntafel**: **2–3 Ziffern**, beschreibt Art/Intensität der Gefahr.  
Beispiele: `30 = entzündbare Flüssigkeit (23–61 °C)`, `33 = leicht entzündbare Flüssigkeit (< 23 °C)`, `80 = ätzend`, `90 = verschiedene/umweltgefährdend`.

<div style="border:1px solid #444; padding:10px; border-radius:8px; background:#1e1f25; color:#e6e6e6; margin:1em 0;">
  <b>INFO‑BOX: „X – Wasser nix“</b><br/>
  Ein vorangestelltes <b>X</b> zeigt eine <i>gefährliche Reaktion mit Wasser</i> an &rarr; <b>kein Wasser einsetzen</b>.
  <ul style="margin:6px 0 0 1.2em;">
    <li><code>33 / 1203</code> (Benzin): <b>ohne X</b> – brennbar, aber keine Wasser‑Reaktionsgefahr.</li>
    <li><code>X423 / 1402</code> (Calciumcarbid): <b>mit X</b> – Wasser → Acetylenbildung (entzündbar).</li>
  </ul>
</div>

**Warntafel‑Kombination:** oben **Gefahrenzahl**, unten **UN‑Nummer** (z. B. **33 / 1203**).

---

## ⚖️ Lizenz & Haftung

Beispiel‑/Schulungssoftware, **ohne Gewähr**. Nutzung auf eigene Verantwortung.  
Im Einsatz stets die **amtlichen Vorschriften** und **Einsatzbefehle** beachten.

---

<small>Stand: 2025-09-16</small>
