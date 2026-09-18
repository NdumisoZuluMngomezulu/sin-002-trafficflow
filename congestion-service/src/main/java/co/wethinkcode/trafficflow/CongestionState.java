package co.wethinkcode.trafficflow;

/**
 * The city-wide congestion level and when it last moved.
 *
 * <p>{@code updatedAt} is an ISO-8601 string rather than an {@code Instant} so the JSON
 * shape doesn't depend on which Jackson modules happen to be registered — the same
 * value goes out over HTTP and over the MQ topic.
 */
public record CongestionState(int level, String label, String updatedAt) {
}
