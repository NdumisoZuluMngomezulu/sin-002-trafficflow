package co.wethinkcode.trafficflow;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Holds the city-wide congestion level (0-8) and tells anyone who's listening when it
 * moves.
 *
 * <p>The level lives in memory only: it's a live reading of current conditions, so
 * there's nothing worth surviving a restart. Listeners are how stage 3 publishes to the
 * MQ topic without this class needing to know that MQ exists.
 */
public class CongestionTracker {

    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 8;

    private final AtomicReference<CongestionState> state;
    private final List<Consumer<CongestionState>> listeners = new CopyOnWriteArrayList<>();

    public CongestionTracker(int initialLevel) {
        this.state = new AtomicReference<>(stateFor(requireValid(initialLevel)));
    }

    public CongestionState current() {
        return state.get();
    }

    /**
     * Sets an absolute level.
     *
     * @throws IllegalArgumentException if the level is outside 0-8
     */
    public CongestionState set(int level) {
        return apply(requireValid(level));
    }

    /** Nudges the level by {@code delta}, clamped to the 0-8 range rather than rejected. */
    public CongestionState adjust(int delta) {
        return apply(Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, current().level() + delta)));
    }

    /** Registers a listener fired only when the level actually changes. */
    public void onChange(Consumer<CongestionState> listener) {
        listeners.add(listener);
    }

    private CongestionState apply(int level) {
        CongestionState previous = state.get();
        if (previous.level() == level) {
            return previous; // no change, so nothing to announce
        }
        CongestionState updated = stateFor(level);
        state.set(updated);
        listeners.forEach(listener -> listener.accept(updated));
        return updated;
    }

    private static CongestionState stateFor(int level) {
        return new CongestionState(level, labelFor(level), Instant.now().toString());
    }

    /** A human-readable band, so callers don't have to remember what "6" feels like. */
    public static String labelFor(int level) {
        return switch (level) {
            case 0 -> "free-flow";
            case 1, 2 -> "light";
            case 3, 4 -> "moderate";
            case 5, 6 -> "heavy";
            case 7 -> "severe";
            case 8 -> "gridlock";
            default -> throw new IllegalArgumentException("congestion level out of range: " + level);
        };
    }

    private static int requireValid(int level) {
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException(
                    "congestion level must be between " + MIN_LEVEL + " and " + MAX_LEVEL + ", got " + level);
        }
        return level;
    }
}
