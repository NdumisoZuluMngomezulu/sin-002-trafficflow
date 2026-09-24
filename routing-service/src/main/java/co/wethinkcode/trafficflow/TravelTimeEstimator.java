package co.wethinkcode.trafficflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns two validated intersections and a congestion level into an estimated travel
 * time.
 *
 * <p>There is no road network in this dataset — no coordinates, no distances, no edges —
 * so an honest estimate can only be built from what we do have: whether the endpoints
 * share a district, what signal type sits at each end, whether they're active, and how
 * congested the city is right now. The model is deliberately simple and entirely
 * explicit:
 *
 * <pre>
 *   estimate = (base + signal delay + inactive penalty) x congestion multiplier
 * </pre>
 *
 * <ul>
 *   <li><b>base</b> — {@value #SAME_DISTRICT_BASE_MINUTES} min within one district,
 *       {@value #CROSS_DISTRICT_BASE_MINUTES} min across two. An unknown district
 *       counts as a crossing: with no evidence the trip is short, the longer estimate
 *       is the safer one to give a driver.</li>
 *   <li><b>signal delay</b> — added per endpoint, by signal type. An unknown type gets
 *       the middle-of-the-road {@value #DEFAULT_SIGNAL_DELAY_MINUTES} min.</li>
 *   <li><b>inactive penalty</b> — {@value #INACTIVE_PENALTY_MINUTES} min per endpoint
 *       that is known to be out of service, since traffic has to work around it.</li>
 *   <li><b>congestion multiplier</b> — {@code 1 + level/8}, so free-flow leaves the
 *       estimate alone and gridlock doubles it.</li>
 * </ul>
 *
 * <p>Pure functions of their inputs, so the whole model is unit tested without a broker,
 * a server or a network.
 */
public final class TravelTimeEstimator {

    public static final double SAME_DISTRICT_BASE_MINUTES = 6.0;
    public static final double CROSS_DISTRICT_BASE_MINUTES = 12.0;
    public static final double INACTIVE_PENALTY_MINUTES = 3.0;
    public static final double DEFAULT_SIGNAL_DELAY_MINUTES = 1.0;

    private static final Map<String, Double> SIGNAL_DELAY_MINUTES = Map.of(
            "4-way", 1.5,
            "stop-sign", 1.0,
            "roundabout", 0.5,
            "pedestrian", 2.0);

    private TravelTimeEstimator() {
    }

    /**
     * @throws IllegalArgumentException if the congestion level is outside 0-8
     */
    public static RouteEstimate estimate(Intersection from, Intersection to, CongestionReading congestion) {
        int level = congestion.level();
        if (level < 0 || level > 8) {
            throw new IllegalArgumentException("congestion level must be between 0 and 8, got " + level);
        }

        List<String> warnings = new ArrayList<>();
        double base = baseMinutes(from, to, warnings);
        double signalDelay = signalDelay(from, warnings) + signalDelay(to, warnings);
        double inactivePenalty = inactivePenalty(from, warnings) + inactivePenalty(to, warnings);
        double multiplier = congestionMultiplier(level);
        double estimate = (base + signalDelay + inactivePenalty) * multiplier;

        return new RouteEstimate(from, to, level, congestion.source(),
                round(base), round(signalDelay), round(inactivePenalty), round(multiplier), round(estimate),
                List.copyOf(warnings));
    }

    /** {@code 1.0} at free-flow, {@code 2.0} at gridlock, linear in between. */
    public static double congestionMultiplier(int level) {
        return 1.0 + (level / 8.0);
    }

    private static double baseMinutes(Intersection from, Intersection to, List<String> warnings) {
        if (from.district() == null || to.district() == null) {
            warnings.add("district unknown for at least one endpoint — estimated as a cross-district trip");
            return CROSS_DISTRICT_BASE_MINUTES;
        }
        return from.district().equalsIgnoreCase(to.district())
                ? SAME_DISTRICT_BASE_MINUTES
                : CROSS_DISTRICT_BASE_MINUTES;
    }

    private static double signalDelay(Intersection intersection, List<String> warnings) {
        if (intersection.signalType() == null) {
            warnings.add(intersection.id() + " has no recorded signal type — used the default delay");
            return DEFAULT_SIGNAL_DELAY_MINUTES;
        }
        Double delay = SIGNAL_DELAY_MINUTES.get(intersection.signalType());
        if (delay == null) {
            warnings.add(intersection.id() + " has an unrecognised signal type \""
                    + intersection.signalType() + "\" — used the default delay");
            return DEFAULT_SIGNAL_DELAY_MINUTES;
        }
        return delay;
    }

    private static double inactivePenalty(Intersection intersection, List<String> warnings) {
        if (Boolean.FALSE.equals(intersection.active())) {
            warnings.add(intersection.id() + " is currently inactive — traffic has to route around it");
            return INACTIVE_PENALTY_MINUTES;
        }
        if (intersection.active() == null) {
            warnings.add(intersection.id() + " has no recorded active flag — assumed to be in service");
        }
        return 0.0;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
