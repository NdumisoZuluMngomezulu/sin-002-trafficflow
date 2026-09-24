package co.wethinkcode.trafficflow;

import java.util.List;

/**
 * An estimated travel time, with its working shown.
 *
 * <p>The component parts are in the response rather than just the total so a caller can
 * see why an estimate came out the way it did — which matters when the inputs are as
 * patchy as a cleaned legacy export.
 */
public record RouteEstimate(
        Intersection from,
        Intersection to,
        int congestionLevel,
        String congestionSource,
        double baseMinutes,
        double signalDelayMinutes,
        double inactivePenaltyMinutes,
        double congestionMultiplier,
        double estimatedMinutes,
        List<String> warnings) {
}
