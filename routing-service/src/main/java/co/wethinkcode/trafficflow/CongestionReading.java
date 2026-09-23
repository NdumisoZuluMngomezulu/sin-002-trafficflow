package co.wethinkcode.trafficflow;

/**
 * A congestion level plus where it came from.
 *
 * <p>The {@code source} is carried through into the route response on purpose: it's how
 * you can see at a glance whether an estimate used a value pushed over the MQ topic
 * (stage 3) or one polled from congestion-service over HTTP (stage 2).
 */
public record CongestionReading(int level, String source, String observedAt) {
}
