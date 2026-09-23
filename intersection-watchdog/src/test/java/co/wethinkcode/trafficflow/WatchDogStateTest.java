package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The clock is an argument, not a dependency, so every transition is tested without a
 * broker and without a sleeping test.
 */
class WatchdogStateTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static WatchdogState watchdog() {
        return new WatchdogState(TIMEOUT, START);
    }

    @Test
    void doesNotCryWolfBeforeTheFirstHeartbeatIsEvenDue() {
        WatchdogState state = watchdog();

        assertEquals(WatchdogState.Status.WAITING, state.status());
        assertFalse(state.evaluate(START.plusSeconds(10)));
        assertEquals(WatchdogState.Status.WAITING, state.status());
    }

    @Test
    void alertsIfNoHeartbeatEverArrives() {
        WatchdogState state = watchdog();

        assertTrue(state.evaluate(START.plusSeconds(16)));
        assertEquals(WatchdogState.Status.ALERT, state.status());
        assertEquals("NO_CONTACT", state.alerts().get(0).type());
    }

    @Test
    void goesHealthyOnTheFirstHeartbeat() {
        WatchdogState state = watchdog();

        state.heartbeat(START.plusSeconds(1), 1L, 17);

        assertEquals(WatchdogState.Status.HEALTHY, state.status());
        assertEquals("FIRST_CONTACT", state.alerts().get(0).type());
    }

    @Test
    void steadyBeatingRaisesNothing() {
        WatchdogState state = watchdog();

        state.heartbeat(START.plusSeconds(1), 1L, 17);
        state.heartbeat(START.plusSeconds(6), 2L, 17);
        state.heartbeat(START.plusSeconds(11), 3L, 17);

        assertFalse(state.evaluate(START.plusSeconds(20)));
        assertEquals(1, state.alerts().size(), "only the first-contact entry");
    }

    @Test
    void alertsOnceTheBeatsStop() {
        WatchdogState state = watchdog();
        state.heartbeat(START.plusSeconds(6), 2L, 17);

        assertFalse(state.evaluate(START.plusSeconds(20)), "14s is still inside the timeout");
        assertTrue(state.evaluate(START.plusSeconds(22)), "16s is not");

        assertEquals(WatchdogState.Status.ALERT, state.status());
        assertEquals("MISSED_HEARTBEAT", state.alerts().get(0).type());
        assertTrue(state.alerts().get(0).detail().contains("16s"), state.alerts().get(0).detail());
    }

    @Test
    void raisesOneAlertPerOutageRatherThanOnePerCheck() {
        WatchdogState state = watchdog();
        state.heartbeat(START.plusSeconds(1), 1L, 17);
        state.evaluate(START.plusSeconds(30));

        assertFalse(state.evaluate(START.plusSeconds(40)));
        assertFalse(state.evaluate(START.plusSeconds(50)));
        assertEquals(2, state.alerts().size(), "first contact, then one missed-heartbeat alert");
    }

    @Test
    void recordsTheRecoveryWhenTheServiceComesBack() {
        WatchdogState state = watchdog();
        state.heartbeat(START.plusSeconds(1), 1L, 17);
        state.evaluate(START.plusSeconds(30));

        state.heartbeat(START.plusSeconds(31), 9L, 17);

        assertEquals(WatchdogState.Status.HEALTHY, state.status());
        assertEquals("RECOVERED", state.alerts().get(0).type());
    }

    @Test
    void aDeadLetteredHeartbeatAlertsImmediately() {
        WatchdogState state = watchdog();
        state.heartbeat(START.plusSeconds(1), 1L, 17);

        state.deadLetter(START.plusSeconds(2), "{\"service\":\"intersection-service\",\"sequence\":2}");

        assertEquals(WatchdogState.Status.ALERT, state.status());
        assertEquals("DEAD_LETTER", state.alerts().get(0).type());
    }

    @Test
    void snapshotReportsWhatAMonitorNeeds() {
        WatchdogState state = watchdog();
        WatchdogState.Snapshot cold = state.snapshot(START);

        assertEquals("WAITING", cold.status());
        assertNull(cold.lastHeartbeatAt());
        assertNull(cold.secondsSinceLastHeartbeat());
        assertEquals(15L, cold.timeoutSeconds());

        state.heartbeat(START.plusSeconds(5), 9L, 17);
        WatchdogState.Snapshot warm = state.snapshot(START.plusSeconds(8));

        assertEquals("HEALTHY", warm.status());
        assertEquals(3L, warm.secondsSinceLastHeartbeat());
        assertEquals(9L, warm.lastSequence());
        assertEquals("2026-01-01T00:00:05Z", warm.lastHeartbeatAt());
    }

    @Test
    void alertHistoryIsNewestFirstAndBounded() {
        WatchdogState state = new WatchdogState(Duration.ofSeconds(1), START);
        for (int i = 1; i <= 60; i++) {
            state.evaluate(START.plusSeconds(i * 10L));
            state.heartbeat(START.plusSeconds(i * 10L + 1), (long) i, 17);
        }

        assertEquals(50, state.alerts().size());
        assertTrue(state.alerts().get(0).at().compareTo(state.alerts().get(49).at()) > 0);
    }
}
