package co.wethinkcode.trafficflow;

/** What this service knows about a district, served by {@code GET /districts/{name}}. */
public record DistrictSummary(String name, int intersectionCount, int activeCount) {
}
