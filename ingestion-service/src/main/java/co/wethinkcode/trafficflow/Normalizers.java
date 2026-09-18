package co.wethinkcode.trafficflow;

import java.util.Map;
import java.util.Set;

/**
 * Field-level cleaning rules for {@code intersections-legacy.csv}.
 *
 * <p>Every method here is a pure function of its input, which is what makes the
 * messy-data rules cheap to unit test. {@link CsvCleaner} handles the row- and
 * file-level concerns (duplicates, reporting) on top of these.
 *
 * <p>The guiding rule throughout: normalize what we can recognise, and return
 * {@code null} for anything missing or unrecognisable rather than guessing a value.
 */
public final class Normalizers {

    /**
     * Values that mean "no data" in the legacy export. Compared case-insensitively
     * against the squished field value.
     */
    private static final Set<String> PLACEHOLDERS = Set.of(
            "", "-", "--", "?", "n/a", "n.a.", "na", "nan", "none", "null", "tbd", "unknown");

    private static final Set<String> TRUTHY = Set.of("y", "yes", "true", "t", "1", "on", "active");
    private static final Set<String> FALSEY = Set.of("n", "no", "false", "f", "0", "off", "inactive");

    /** Spelling/spacing variants of the same district, keyed by letters-only lowercase. */
    private static final Map<String, String> DISTRICT_ALIASES = Map.of(
            "downtown", "Downtown",
            "cbd", "Downtown",
            "centralbusinessdistrict", "Downtown",
            "midtown", "Midtown",
            "uptown", "Uptown",
            "eastside", "Eastside",
            "westside", "Westside",
            "northside", "Northside",
            "southside", "Southside");

    /** Spelling variants and synonyms of the same signal type, keyed by hyphenated lowercase. */
    private static final Map<String, String> SIGNAL_TYPE_ALIASES = Map.ofEntries(
            Map.entry("4-way", "4-way"),
            Map.entry("4way", "4-way"),
            Map.entry("four-way", "4-way"),
            Map.entry("fourway", "4-way"),
            Map.entry("4-way-signal", "4-way"),
            Map.entry("roundabout", "roundabout"),
            Map.entry("round-about", "roundabout"),
            Map.entry("traffic-circle", "roundabout"),
            Map.entry("circle", "roundabout"),
            Map.entry("rotary", "roundabout"),
            Map.entry("pedestrian", "pedestrian"),
            Map.entry("ped", "pedestrian"),
            Map.entry("pedestrian-crossing", "pedestrian"),
            Map.entry("crosswalk", "pedestrian"),
            Map.entry("stop-sign", "stop-sign"),
            Map.entry("stop", "stop-sign"),
            Map.entry("stopsign", "stop-sign"),
            Map.entry("stop-street", "stop-sign"));

    private Normalizers() {
    }

    /** Trims, and collapses any run of internal whitespace (including double spaces) to one space. */
    public static String squish(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.strip().replaceAll("\\s+", " ");
    }

    /**
     * Squishes a raw field and maps blanks and placeholder text ({@code N/A}, {@code TBD},
     * {@code unknown}, {@code -}, {@code NaN}, ...) to {@code null}.
     */
    public static String blankToNull(String raw) {
        String squished = squish(raw);
        return PLACEHOLDERS.contains(squished.toLowerCase()) ? null : squished;
    }

    /**
     * Canonicalises an intersection id so that {@code int-1005}, {@code INT 1005} and
     * {@code INT-1005} all key to the same real-world intersection.
     *
     * <p>Ids that don't look like {@code INT-<digits>} are kept, uppercased, rather than
     * discarded — an unrecognised id is still an identifier.
     */
    public static String intersectionId(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        String compact = value.replaceAll("[\\s_]", "").toUpperCase();
        return compact.matches("INT-?\\d+") ? "INT-" + compact.replaceAll("\\D", "") : compact;
    }

    /** Title-cases a district name and folds known spelling/spacing variants together. */
    public static String district(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        String alias = DISTRICT_ALIASES.get(value.toLowerCase().replaceAll("[^a-z]", ""));
        return alias != null ? alias : titleCase(value);
    }

    /**
     * Lower-cases and hyphenates a signal type, then folds known synonyms together
     * ({@code 4 Way} / {@code four-way} → {@code 4-way}).
     *
     * <p>An unrecognised type is returned normalised but otherwise untouched, so new
     * signal types in a future export flow through instead of silently becoming null.
     */
    public static String signalType(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        String hyphenated = value.toLowerCase().replaceAll("[\\s_]+", "-");
        return SIGNAL_TYPE_ALIASES.getOrDefault(hyphenated, hyphenated);
    }

    /**
     * Reads the many boolean spellings in the export ({@code Y}/{@code N}, {@code yes}/{@code no},
     * {@code 1}/{@code 0}, {@code true}/{@code FALSE}).
     *
     * @return {@code null} when the value is missing, a placeholder, or not a recognisable flag
     */
    public static Boolean bool(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return null;
        }
        String lower = value.toLowerCase();
        if (TRUTHY.contains(lower)) {
            return Boolean.TRUE;
        }
        if (FALSEY.contains(lower)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** Normalises a CSV header to a comparable key: lowercase, letters and digits only. */
    public static String headerKey(String raw) {
        return squish(raw).toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private static String titleCase(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean startOfWord = true;
        for (char c : value.toCharArray()) {
            if (startOfWord) {
                out.append(Character.toUpperCase(c));
            } else {
                out.append(Character.toLowerCase(c));
            }
            startOfWord = (c == ' ' || c == '-' || c == '\'');
        }
        return out.toString();
    }
}
