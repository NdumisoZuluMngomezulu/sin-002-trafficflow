package co.wethinkcode.trafficflow;

/**
 * An intersection as returned by intersection-service.
 *
 * <p>Duplicated in ingestion-service and intersection-service — independent Maven
 * projects with no shared parent pom, same as {@code MqConfig}.
 */
public record Intersection(String id, String district, String signalType, Boolean active) {
}
