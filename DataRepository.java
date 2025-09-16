import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.table.AbstractTableModel;

/**
 * Lädt index.json, Bereichsdateien und gefahrenzahl.json.
 * Stellt Abfragen nach Klasse, Bereich und UN-Nummer bereit.
 */
class DataRepository {

    private final File dataRoot;
    private final List<IndexRange> ranges = new ArrayList<>();
    private final Map<String, List<Substance>> byClass = new HashMap<>();
    private final Map<String, List<Substance>> byRange = new HashMap<>();
    private final Map<String, List<Substance>> byUn = new HashMap<>();
    private final Map<String, String> hazardCodeToDesc = new HashMap<>();

    // Optionales Mapping für Sonderhinweise (falls missing_gefahrenzahl.json vorhanden)
    private final Map<String, String> hintByUn = new HashMap<>();

    public DataRepository(File dataRoot) throws IOException {
        if (dataRoot == null) throw new IOException("Kein Datenordner angegeben.");
        this.dataRoot = dataRoot;
        loadAll();
    }

    public File getDataRoot() { return dataRoot; }

    static File findDefaultDataRoot() {
        // bevorzugt ./data/index.json, dann ./index.json, sonst FileChooser
        Path p1 = Paths.get("data", "index.json");
        if (Files.exists(p1)) return p1.getParent().toFile();
        Path p2 = Paths.get("index.json");
        if (Files.exists(p2)) return p2.getParent().toFile();

        // fallback: Dialog
        File here = new File(".").getAbsoluteFile();
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser(here);
        fc.setDialogTitle("Ordner mit index.json auswählen");
        fc.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            return fc.getSelectedFile();
        }
        return here;
    }

    private void loadAll() throws IOException {
        Path indexPath = findIndexJson(dataRoot.toPath());
        if (indexPath == null) throw new IOException("index.json nicht gefunden.");
        Object idx = JSONMini.parse(Files.readString(indexPath, StandardCharsets.UTF_8));
        Map<String, Object> idxMap = asObj(idx);
        List<Object> rangesArr = asArr(idxMap.get("ranges"));
        for (Object o : rangesArr) {
            Map<String, Object> m = asObj(o);
            String label = str(m.get("range"));
            String file = str(m.get("file"));
            int count = (m.get("count") instanceof Number) ? ((Number)m.get("count")).intValue() : -1;
            ranges.add(new IndexRange(label, file, count));
        }
        // Load hazard number dict
        Path hz = indexPath.getParent().resolve("gefahrenzahl.json");
        if (Files.exists(hz)) {
            Object ho = JSONMini.parse(Files.readString(hz, StandardCharsets.UTF_8));
            Map<String, Object> hm = asObj(ho);
            List<Object> codes = asArr(hm.get("codes"));
            for (Object co : codes) {
                Map<String, Object> c = asObj(co);
                String code = str(c.get("code"));
                String desc = str(c.get("description"));
                hazardCodeToDesc.put(code, desc);
            }
        }
        // Optional hints
        Path missing = indexPath.getParent().resolve("missing_gefahrenzahl.json");
        if (Files.exists(missing)) {
            Object mo = JSONMini.parse(Files.readString(missing, StandardCharsets.UTF_8));
            Map<String, Object> mm = asObj(mo);
            List<Object> rows = asArr(mm.get("rows"));
            for (Object ro : rows) {
                Map<String, Object> r = asObj(ro);
                String un = str(r.get("un_number"));
                String hint = strOrNull(r.get("hint"));
                if (un != null && hint != null && !hint.isBlank()) {
                    hintByUn.put(un, hint);
                }
            }
        }

        // Load each range file
        for (IndexRange r : ranges) {
            Path rp = indexPath.getParent().resolve(r.fileName);
            if (!Files.exists(rp)) continue;
            Object ro = JSONMini.parse(Files.readString(rp, StandardCharsets.UTF_8));
            Map<String, Object> rm = asObj(ro);
            List<Object> rows = asArr(rm.get("rows"));
            List<Substance> list = new ArrayList<>();
            for (Object eo : rows) {
                Map<String, Object> e = asObj(eo);
                String un = str(e.get("un_number"));
                String gz = strOrNull(e.get("gefahrenzahl"));
                String kl = str(e.get("klasse"));
                String name = str(e.get("bezeichnung"));
                Substance s = new Substance(un, gz, kl, name, r.rangeLabel);
                list.add(s);

                byClass.putIfAbsent(kl, new ArrayList<Substance>());
                byClass.get(kl).add(s);
                byUn.putIfAbsent(un, new ArrayList<Substance>());
                byUn.get(un).add(s);
            }
            byRange.put(r.rangeLabel, list);
        }

        // Sort intern – ohne "unused parameter"-Warnung
        for (Map.Entry<String, List<Substance>> e : byClass.entrySet()) {
            e.setValue(sortSubs(e.getValue()));
        }
        for (Map.Entry<String, List<Substance>> e : byRange.entrySet()) {
            e.setValue(sortSubs(e.getValue()));
        }
        for (Map.Entry<String, List<Substance>> e : byUn.entrySet()) {
            e.setValue(sortSubs(e.getValue()));
        }
    }

    private Path findIndexJson(Path root) {
        Path p1 = root.resolve("index.json");
        if (Files.exists(p1)) return p1;
        Path p2 = root.resolve("data").resolve("index.json");
        if (Files.exists(p2)) return p2;
        return null;
    }

    private static List<Substance> sortSubs(List<Substance> v) {
        v.sort(Comparator.comparing((Substance s) -> s.unNumber)
                .thenComparing(s -> s.name));
        return v;
    }

    /* ----- Queries ----- */

    public List<IndexRange> getRanges() { return Collections.unmodifiableList(ranges); }

    public List<String> getAllClassesSorted() {
        List<String> cls = new ArrayList<>(byClass.keySet());
        cls.sort(new ClassCodeComparator());
        return cls;
    }

    public List<Substance> getByClass(String klass) {
        return byClass.getOrDefault(klass, Collections.emptyList());
    }

    public List<Substance> getByRange(String rangeLabel) {
        return byRange.getOrDefault(rangeLabel, Collections.emptyList());
    }

    public List<Substance> getByUN(String un) {
        return byUn.getOrDefault(un, Collections.emptyList());
    }

    public String getHazardDescription(String code) {
        if (code == null || code.isBlank()) return null;
        String exact = hazardCodeToDesc.get(code);
        if (exact != null) return exact;
        // sometimes X-prefix → try without X
        if (code.startsWith("X")) return hazardCodeToDesc.get(code.substring(1));
        return null;
    }

    public String deriveHint(Substance s) {
        // Prefer hint mapping from missing_gefahrenzahl.json
        if (hintByUn.containsKey(s.unNumber)) return hintByUn.get(s.unNumber);
        // Heuristics from name text
        final String nm = s.name.toLowerCase(Locale.ROOT);
        if (nm.contains("beförderung verboten")) return "Beförderung verboten";
        if (nm.contains("unterliegt nicht den vorschriften des adr") || nm.contains("not subject to adr"))
            return "Unterliegt nicht den Vorschriften des ADR";
        if (nm.contains("temperaturkontrolliert")) return "Temperaturkontrolliert transportieren";
        if (nm.contains("abfall")) return "Abfall/Sonderabfall – besondere Regelungen beachten";
        return null;
    }

    /* ----- helpers ----- */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObj(Object o) {
        return (Map<String, Object>) o;
    }
    @SuppressWarnings("unchecked")
    private static List<Object> asArr(Object o) {
        return (List<Object>) o;
    }
    private static String str(Object o) { return o == null ? "" : String.valueOf(o); }
    private static String strOrNull(Object o) { return (o == null || String.valueOf(o).equals("null")) ? null : String.valueOf(o); }
}

/* Models & Utils */

class IndexRange {
    final String rangeLabel;
    final String fileName;
    final int count;
    IndexRange(String label, String file, int count) {
        this.rangeLabel = label;
        this.fileName = file;
        this.count = count;
    }
}

class Substance {
    final String unNumber;
    final String hazardNumber; // Gefahrenzahl/Kemler
    final String klass;        // Gefahrgutklasse
    final String name;         // Bezeichnung
    final String rangeLabel;   // Bereich
    String hint;               // optional

    Substance(String un, String hz, String kl, String name, String range) {
        this.unNumber = un;
        this.hazardNumber = hz;
        this.klass = kl;
        this.name = name;
        this.rangeLabel = range;
    }
}

/** Sortiert Klassen wie 1, 1.1A, 2, 2.1, 8, 9 sinnvoll. */
class ClassCodeComparator implements Comparator<String> {
    @Override public int compare(String a, String b) {
        return Arrays.compare(parse(a), parse(b));
    }
    private int[] parse(String s) {
        // Mappe auf 3 Komponenten: Hauptklasse, Unterklasse (Zahl), Buchstabenwert
        // Beispiel: "1.1D" → [1,1,'D']; "8" → [8, -1, -1]
        int main = -1, sub = -1, letter = -1;
        try {
            String[] parts = s.split("\\.");
            main = Integer.parseInt(parts[0].replaceAll("\\D", ""));
            if (parts.length > 1) {
                String rest = parts[1];
                // Zahl am Anfang
                int i = 0;
                while (i < rest.length() && Character.isDigit(rest.charAt(i))) i++;
                if (i > 0) sub = Integer.parseInt(rest.substring(0, i));
                // Erster Buchstabe danach
                for (; i < rest.length(); i++) {
                    char c = rest.charAt(i);
                    if (Character.isLetter(c)) { letter = Character.toUpperCase(c); break; }
                }
            }
        } catch (Exception ignored) {}
        return new int[]{main, sub, letter};
    }
}

/** TableModel für die rechte Stoffliste. */
class SubstanceTableModel extends AbstractTableModel {
    private final String[] cols = {"UN-Nummer", "Gefahrenzahl", "Klasse", "Bezeichnung", "Hinweis"};
    private List<Substance> rows = new ArrayList<>();
    public void setRows(List<Substance> r) { this.rows = new ArrayList<>(r); fireTableDataChanged(); }
    public Substance getRow(int r) { return rows.get(r); }
    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int c) { return cols[c]; }
    @Override public Object getValueAt(int r, int c) {
        Substance s = rows.get(r);
        switch (c) {
            case 0: return s.unNumber;
            case 1: return (s.hazardNumber == null || s.hazardNumber.isEmpty()) ? "–" : s.hazardNumber;
            case 2: return s.klass;
            case 3: return s.name;
            case 4: return s.hint;
        }
        return "";
    }
}
