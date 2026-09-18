package co.wethinkcode.trafficflow;

/**
 * A cleaned intersection record, as served by {@code GET /intersections}.
 *
 * <p>{@code district}, {@code signalType} and {@code active} are deliberately nullable:
 * a missing value in the legacy export stays an explicit {@code null} so downstream
 * services can tell "we don't know" apart from "it's false" or "it's empty".
 *
 * <p>Duplicated verbatim in intersection-service and routing-service — these are
 * independent Maven projects with no shared parent pom, same as {@code MqConfig}.
 */
public record Intersection(String id, String district, String signalType, Boolean active) {

    /**
     * Fills this record's null fields from {@code other}, keeping this record's own
     * values wherever it has them. Used to collapse duplicate rows for the same
     * real-world intersection without losing data from either row.
     */
    public Intersection fillGapsFrom(Intersection other) {
        return new Intersection(
                id,
                district != null ? district : other.district(),
                signalType != null ? signalType : other.signalType(),
                active != null ? active : other.active());
    }
}
