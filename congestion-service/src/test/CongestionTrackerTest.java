package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CongestionTrackerTest {

    @Test
    void startsAtTheLevelItWasGiven() {
        CongestionTracker tracker = new CongestionTracker(3);
        assertEquals(3, tracker.current().level());
        assertEquals("moderate", tracker.current().label());
        assertNotNull(tracker.current().updatedAt());
    }

    @ParameterizedTest
    @CsvSource({"0,free-flow", "1,light", "2,light", "3,moderate", "4,moderate",
            "5,heavy", "6,heavy", "7,severe", "8,gridlock"})
    void labelsEveryLevelInRange(int level, String label) {
        assertEquals(label, CongestionTracker.labelFor(level));
    }

    @Test
    void rejectsLevelsOutsideTheRange() {
        CongestionTracker tracker = new CongestionTracker(3);
        assertThrows(IllegalArgumentException.class, () -> tracker.set(9));
        assertThrows(IllegalArgumentException.class, () -> tracker.set(-1));
        assertThrows(IllegalArgumentException.class, () -> new CongestionTracker(42));
    }

    @Test
    void clampsAdjustmentsInsteadOfRejectingThem() {
        CongestionTracker tracker = new CongestionTracker(8);
        assertEquals(8, tracker.adjust(1).level());
        tracker.set(0);
        assertEquals(0, tracker.adjust(-1).level());
    }

    @Test
    void notifiesListenersOnlyWhenTheLevelActuallyMoves() {
        CongestionTracker tracker = new CongestionTracker(3);
        List<Integer> published = new ArrayList<>();
        tracker.onChange(state -> published.add(state.level()));

        tracker.set(5);
        tracker.set(5);   // no change, so nothing to announce
        tracker.adjust(1);
        tracker.adjust(-2);

        assertEquals(List.of(5, 6, 4), published);
    }
}
