package co.wethinkcode.trafficflow;

/**
 * An intersection as published by ingestion-service and served on from here.
 *
 * <p>Nullable fields are deliberate: a missing value in the legacy export stays an
 * explicit {@code null} so callers can tell "we don't know" apart from "false".
 *
 * <p>Duplicated in ingestion-service and routing-service — independent Maven projects
 * with no shared parent pom, same as {@code MqConfig}.
 */
public record Intersection(String id, String district, String signalType, Boolean active) {
}
