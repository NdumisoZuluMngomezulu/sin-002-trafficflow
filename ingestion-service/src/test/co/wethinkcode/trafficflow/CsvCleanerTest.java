package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvCleanerTest {

    private static final String[] HEADER = {"intersection_id", "District ", "signal_type", "active_flag"};

    private static List<String[]> rows(String[]... dataRows) {
        List<String[]> all = new ArrayList<>();
        all.add(HEADER);
        all.addAll(List.of(dataRows));
        return all;
    }

    @Test
    void cleansTheWorkedExampleFromTheReadme() {
        CsvCleaner.Result result = CsvCleaner.clean(rows(
                new String[]{"INT-1001", " Downtown ", "4-way", "Y"},
                new String[]{"INT-1005", "Downtown", "Roundabout", "true"},
                new String[]{"int-1005", "downtown ", "ROUNDABOUT", "TRUE"},
                new String[]{"INT-1007", "Eastside", "", "1"},
                new String[]{"INT-1015", "", "4-way", "Y"}));

        assertEquals(List.of(
                new Intersection("INT-1001", "Downtown", "4-way", true),
                new Intersection("INT-1005", "Downtown", "roundabout", true),
                new Intersection("INT-1007", "Eastside", null, true),
                new Intersection("INT-1015", null, "4-way", true)),
                result.intersections());
    }

    @Test
    void collapsesDuplicatesKeepingTheFirstRecordAndFillingItsGaps() {
        CsvCleaner.Result result = CsvCleaner.clean(rows(
                new String[]{"INT-2001", "Downtown", "", "Y"},
                new String[]{"int 2001", "Midtown", "4-way", "N"}));

        assertEquals(1, result.intersections().size());
        // first row wins on conflicts, second row fills the blank signal type
        assertEquals(new Intersection("INT-2001", "Downtown", "4-way", true), result.intersections().get(0));
        assertEquals(1, result.report().getDuplicateRowsMerged());
    }

    @Test
    void reportsConflictingValuesInsteadOfSwallowingThem() {
        CsvCleaner.Result result = CsvCleaner.clean(rows(
                new String[]{"INT-2001", "Downtown", "4-way", "Y"},
                new String[]{"INT-2001", "Midtown", "4-way", "N"}));

        List<String> notes = result.report().getNotes();
        assertTrue(notes.stream().anyMatch(n -> n.contains("disagrees on district")), notes.toString());
        assertTrue(notes.stream().anyMatch(n -> n.contains("disagrees on active")), notes.toString());
    }

    @Test
    void keepsMissingValuesAsExplicitNulls() {
        CsvCleaner.Result result = CsvCleaner.clean(rows(
                new String[]{"INT-3001", "N/A", "unknown", "TBD"}));

        Intersection only = result.intersections().get(0);
        assertNull(only.district());
        assertNull(only.signalType());
        assertNull(only.active());
        assertEquals(Map.of("district", 1, "signalType", 1, "active", 1), result.report().getMissingFields());
    }

    @Test
    void dropsRowsWithNoUsableIdButSaysSoInTheReport() {
        CsvCleaner.Result result = CsvCleaner.clean(rows(
                new String[]{"", "Downtown", "4-way", "Y"},
                new String[]{"INT-3002", "Downtown", "4-way", "Y"}));

        assertEquals(1, result.intersections().size());
        assertEquals(1, result.report().getRowsWithoutId());
        assertTrue(result.report().getNotes().stream().anyMatch(n -> n.contains("no usable intersection id")));
    }

    @Test
    void skipsEntirelyBlankRows() {
        CsvCleaner.Result result = CsvCleaner.clean(rows(
                new String[]{"  ", " ", "", "  "},
                new String[]{"INT-3003", "Downtown", "4-way", "Y"}));

        assertEquals(1, result.intersections().size());
        assertEquals(1, result.report().getBlankRowsSkipped());
    }

    @Test
    void flagsUnreadableBooleansRatherThanGuessingAValue() {
        CsvCleaner.Result result = CsvCleaner.clean(rows(
                new String[]{"INT-3004", "Uptown", "4-way", "maybe"}));

        assertNull(result.intersections().get(0).active());
        assertEquals(1, result.report().getUnreadableFlags());
    }

    @Test
    void toleratesRaggedRowsThatStopShortOfTheHeader() {
        CsvCleaner.Result result = CsvCleaner.clean(rows(new String[]{"INT-3005", "Uptown"}));

        assertEquals(new Intersection("INT-3005", "Uptown", null, null), result.intersections().get(0));
    }

    @Test
    void recordsColumnsItDoesNotUnderstand() {
        List<String[]> input = new ArrayList<>();
        input.add(new String[]{"intersection_id", "District ", "signal_type", "active_flag", "legacy_notes"});
        input.add(new String[]{"INT-3006", "Uptown", "4-way", "Y", "migrated 2019"});

        assertEquals(List.of("legacy_notes"), CsvCleaner.clean(input).report().getUnmappedColumns());
    }

    @Test
    void sortsOutputById() {
        CsvCleaner.Result result = CsvCleaner.clean(rows(
                new String[]{"INT-4002", "Uptown", "4-way", "Y"},
                new String[]{"INT-4001", "Uptown", "4-way", "Y"}));

        assertEquals(List.of("INT-4001", "INT-4002"), result.intersections().stream().map(Intersection::id).toList());
    }

    @Test
    void refusesAFileWithNoIdColumn() {
        List<String[]> input = new ArrayList<>();
        input.add(new String[]{"District ", "signal_type"});
        input.add(new String[]{"Uptown", "4-way"});

        assertThrows(IllegalArgumentException.class, () -> CsvCleaner.clean(input));
    }

    @Test
    void refusesAnEmptyFile() {
        assertThrows(IllegalArgumentException.class, () -> CsvCleaner.clean(List.of()));
    }
}