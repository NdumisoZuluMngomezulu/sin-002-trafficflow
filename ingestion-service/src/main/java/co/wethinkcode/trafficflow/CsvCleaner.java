package co.wethinkcode.trafficflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the raw rows of {@code intersections-legacy.csv} into clean, de-duplicated
 * {@link Intersection} records plus a {@link CleaningReport} of what had to be fixed.
 *
 * <p>Takes already-parsed rows rather than a file or a {@code Reader} so the cleaning
 * rules can be tested without touching the filesystem or the CSV library — see
 * {@link LegacyCsvSource} for the parsing half.
 */
public final class CsvCleaner {

    /**
     * Header spellings we recognise, mapped to our own field names. Keys are
     * {@link Normalizers#headerKey} form, so {@code "District "} and {@code "district_name"}
     * both land here.
     */
    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("intersectionid", "id"),
            Map.entry("id", "id"),
            Map.entry("intersection", "id"),
            Map.entry("intid", "id"),
            Map.entry("intersectionnumber", "id"),
            Map.entry("district", "district"),
            Map.entry("districtname", "district"),
            Map.entry("region", "district"),
            Map.entry("area", "district"),
            Map.entry("zone", "district"),
            Map.entry("signaltype", "signalType"),
            Map.entry("signal", "signalType"),
            Map.entry("type", "signalType"),
            Map.entry("activeflag", "active"),
            Map.entry("active", "active"),
            Map.entry("isactive", "active"),
            Map.entry("enabled", "active"),
            Map.entry("status", "active"));

    private CsvCleaner() {
    }

    /** The cleaned records, plus the story of how they got that way. */
    public record Result(List<Intersection> intersections, CleaningReport report) {
    }

    /**
     * @param rows the whole file as parsed cells, including the header row at index 0
     * @throws IllegalArgumentException if the file has no header or no recognisable id column
     */
    public static Result clean(List<String[]> rows) {
        CleaningReport report = new CleaningReport();
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("legacy export is empty — expected a header row");
        }

        Map<String, Integer> columns = mapColumns(rows.get(0), report);
        if (!columns.containsKey("id")) {
            throw new IllegalArgumentException(
                    "no intersection id column found in header: " + String.join(",", rows.get(0)));
        }

        Map<String, Intersection> byId = new LinkedHashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1; // 1-based, counting the header, so it matches an editor
            readRow(rows.get(i), columns, lineNumber, byId, report);
        }

        List<Intersection> cleaned = new ArrayList<>(byId.values());
        cleaned.sort(Comparator.comparing(Intersection::id));
        report.summarise(cleaned);
        return new Result(List.copyOf(cleaned), report);
    }

    private static void readRow(String[] row, Map<String, Integer> columns, int lineNumber,
                                Map<String, Intersection> byId, CleaningReport report) {
        report.countRow();
        if (isBlank(row)) {
            report.countBlankRow(lineNumber);
            return;
        }

        String id = Normalizers.intersectionId(cell(row, columns.get("id")));
        if (id == null) {
            report.countRowWithoutId(lineNumber);
            return;
        }

        String rawFlag = cell(row, columns.get("active"));
        Boolean active = Normalizers.bool(rawFlag);
        if (active == null && Normalizers.blankToNull(rawFlag) != null) {
            report.countUnreadableFlag(lineNumber, Normalizers.squish(rawFlag));
        }

        Intersection incoming = new Intersection(
                id,
                Normalizers.district(cell(row, columns.get("district"))),
                Normalizers.signalType(cell(row, columns.get("signalType"))),
                active);

        Intersection existing = byId.get(id);
        if (existing == null) {
            byId.put(id, incoming);
            return;
        }

        // Same real-world intersection under a different id spelling. Keep the first
        // record's values, fill its gaps from this one, and say so when they disagree.
        report.countDuplicate(lineNumber, id);
        reportConflicts(existing, incoming, lineNumber, report);
        byId.put(id, existing.fillGapsFrom(incoming));
    }

    private static void reportConflicts(Intersection kept, Intersection incoming, int lineNumber,
                                        CleaningReport report) {
        compare(kept.district(), incoming.district(), "district", kept.id(), lineNumber, report);
        compare(kept.signalType(), incoming.signalType(), "signalType", kept.id(), lineNumber, report);
        compare(stringOf(kept.active()), stringOf(incoming.active()), "active", kept.id(), lineNumber, report);
    }

    private static void compare(String kept, String incoming, String field, String id, int lineNumber,
                                CleaningReport report) {
        if (kept != null && incoming != null && !kept.equals(incoming)) {
            report.countConflict(lineNumber, id, field, kept, incoming);
        }
    }

    private static Map<String, Integer> mapColumns(String[] header, CleaningReport report) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            String field = HEADER_ALIASES.get(Normalizers.headerKey(header[i]));
            if (field == null) {
                report.addUnmappedColumn(Normalizers.squish(header[i]));
            } else {
                columns.putIfAbsent(field, i);
            }
        }
        return columns;
    }

    /** Reads a cell defensively: ragged rows and absent columns both read as empty. */
    private static String cell(String[] row, Integer index) {
        return (index == null || index >= row.length) ? "" : row[index];
    }

    private static boolean isBlank(String[] row) {
        for (String cell : row) {
            if (!Normalizers.squish(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static String stringOf(Boolean value) {
        return value == null ? null : value.toString();
    }
}
