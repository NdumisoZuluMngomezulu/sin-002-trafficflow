package co.wethinkcode.trafficflow;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Decides whether intersection-service is still alive, based on what has (and hasn't)
 * arrived on the heartbeat queue.
 *
 * <p>Deliberately free of JMS and HTTP: {@code now} is passed in rather than read from
 * the clock, so every transition — first contact, going quiet, coming back, a
 * dead-lettered beat — is unit tested without a broker or a sleeping test.
 *
 * <p>Three states, because "we haven't heard from it yet" is not the same as "it has
 * stopped answering". A watchdog that cried wolf the moment it started would be ignored.
 */
public class WatchdogState {

    /** How many alerts to keep; enough to see a flapping service, not a memory leak. */
    private static final int ALERT_HISTORY = 50;

    public enum Status {
        /** Started, but no heartbeat has arrived yet and the grace period hasn't run out. */
        WAITING,
        /** A heartbeat arrived recently enough. */
        HEALTHY,
        /** Heartbeats stopped, or one was dead-lettered. */
        ALERT
    }

    public record Alert(String at, String type, String detail) {
    }

    public record Snapshot(
            String status,
            String detail,
            String lastHeartbeatAt,
            Long secondsSinceLastHeartbeat,
            Long lastSequence,
            long timeoutSeconds,
            List<Alert> alerts) {
    }

    private final Duration timeout;
    private final Instant startedAt;
    private final Deque<Alert> alerts = new ArrayDeque<>();

    private Status status = Status.WAITING;
    private String detail = "waiting for the first heartbeat";
    private Instant lastHeartbeatAt;
    private Long lastSequence;

    public WatchdogState(Duration timeout, Instant startedAt) {
        this.timeout = timeout;
        this.startedAt = startedAt;
    }

    /** Records a heartbeat, and notes the recovery if we'd given up on the service. */
    public synchronized void heartbeat(Instant at, Long sequence, Integer intersections) {
        Status previous = status;
        lastHeartbeatAt = at;
        lastSequence = sequence;
        status = Status.HEALTHY;
        detail = "heartbeat " + sequence + " received"
                + (intersections == null ? "" : " (" + intersections + " intersections)");

        if (previous == Status.ALERT) {
            record(at, "RECOVERED", "intersection-service is beating again at sequence " + sequence);
        } else if (previous == Status.WAITING) {
            record(at, "FIRST_CONTACT", "first heartbeat from intersection-service");
        }
    }

    /**
     * Records a dead-lettered heartbeat. The broker only dead-letters a beat that nobody
     * consumed before it expired, which means it is already too stale to be reassuring.
     */
    public synchronized void deadLetter(Instant at, String messageBody) {
        status = Status.ALERT;
        detail = "a heartbeat was dead-lettered";
        record(at, "DEAD_LETTER", "expired heartbeat found on the dead-letter queue: " + messageBody);
    }

    /**
     * Checks the clock and raises an alert if the service has gone quiet.
     *
     * @return true if this call changed the status to {@link Status#ALERT}
     */
    public synchronized boolean evaluate(Instant now) {
        if (status == Status.ALERT) {
            return false;
        }
        Instant since = lastHeartbeatAt != null ? lastHeartbeatAt : startedAt;
        if (Duration.between(since, now).compareTo(timeout) <= 0) {
            return false;
        }

        status = Status.ALERT;
        if (lastHeartbeatAt == null) {
            detail = "no heartbeat has ever arrived";
            record(now, "NO_CONTACT", "nothing heard from intersection-service since this watchdog started");
        } else {
            long seconds = Duration.between(lastHeartbeatAt, now).getSeconds();
            detail = "no heartbeat for " + seconds + "s";
            record(now, "MISSED_HEARTBEAT",
                    "intersection-service has not beaten for " + seconds + "s (timeout "
                            + timeout.getSeconds() + "s) — routes can no longer be validated");
        }
        return true;
    }

    public synchronized Status status() {
        return status;
    }

    public synchronized Snapshot snapshot(Instant now) {
        return new Snapshot(
                status.name(),
                detail,
                lastHeartbeatAt == null ? null : lastHeartbeatAt.toString(),
                lastHeartbeatAt == null ? null : Duration.between(lastHeartbeatAt, now).getSeconds(),
                lastSequence,
                timeout.getSeconds(),
                List.copyOf(alerts));
    }

    public synchronized List<Alert> alerts() {
        return List.copyOf(alerts);
    }

    private void record(Instant at, String type, String message) {
        alerts.addFirst(new Alert(at.toString(), type, message));
        while (alerts.size() > ALERT_HISTORY) {
            alerts.removeLast();
        }
    }
}
