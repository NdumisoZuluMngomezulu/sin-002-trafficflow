package co.wethinkcode.trafficflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the cleaning pass did to the legacy export, served by {@code GET /report}.
 *
 * <p>The point of this class is that nothing gets dropped quietly: every row the
 * cleaner refused to keep, and every field it had to null out, shows up here.
 */
public class CleaningReport {

    private int rowsRead;
    private int blankRowsSkipped;
    private int rowsWithoutId;
    private int duplicateRowsMerged;
    private int unreadableFlags;
    private int cleanedRecords;
    private final Map<String, Integer> missingFields = new LinkedHashMap<>();
    private final List<String> unmappedColumns = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    void countRow() {
        rowsRead++;
    }

    void countBlankRow(int lineNumber) {
        blankRowsSkipped++;
        notes.add("line " + lineNumber + ": skipped, every field was blank");
    }

    void countRowWithoutId(int lineNumber) {
        rowsWithoutId++;
        notes.add("line " + lineNumber + ": dropped, no usable intersection id to key it on");
    }

    void countDuplicate(int lineNumber, String id) {
        duplicateRowsMerged++;
        notes.add("line " + lineNumber + ": duplicate of " + id + ", merged into the earlier record");
    }

    void countUnreadableFlag(int lineNumber, String raw) {
        unreadableFlags++;
        notes.add("line " + lineNumber + ": active flag \"" + raw + "\" not recognisable, kept as null");
    }

    void countConflict(int lineNumber, String id, String field, String kept, String discarded) {
        notes.add("line " + lineNumber + ": " + id + " disagrees on " + field
                + " (kept \"" + kept + "\", discarded \"" + discarded + "\")");
    }

    void addUnmappedColumn(String header) {
        unmappedColumns.add(header);
    }

    void summarise(List<Intersection> cleaned) {
        cleanedRecords = cleaned.size();
        missingFields.put("district", (int) cleaned.stream().filter(i -> i.district() == null).count());
        missingFields.put("signalType", (int) cleaned.stream().filter(i -> i.signalType() == null).count());
        missingFields.put("active", (int) cleaned.stream().filter(i -> i.active() == null).count());
    }

    public int getRowsRead() {
        return rowsRead;
    }

    public int getBlankRowsSkipped() {
        return blankRowsSkipped;
    }

    public int getRowsWithoutId() {
        return rowsWithoutId;
    }

    public int getDuplicateRowsMerged() {
        return duplicateRowsMerged;
    }

    public int getUnreadableFlags() {
        return unreadableFlags;
    }

    public int getCleanedRecords() {
        return cleanedRecords;
    }

    public Map<String, Integer> getMissingFields() {
        return missingFields;
    }

    public List<String> getUnmappedColumns() {
        return unmappedColumns;
    }

    public List<String> getNotes() {
        return notes;
    }
}
