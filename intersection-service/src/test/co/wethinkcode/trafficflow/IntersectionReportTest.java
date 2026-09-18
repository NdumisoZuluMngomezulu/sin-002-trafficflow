package co.wethinkcode.trafficflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntersectionRepositoryTest {

    private static final IntersectionRepository REPO = new IntersectionRepository(List.of(
            new Intersection("INT-1005", "Downtown", "roundabout", true),
            new Intersection("INT-1001", "Downtown", "4-way", true),
            new Intersection("INT-1009", "Westside", "4-way", false),
            new Intersection("INT-1015", null, "4-way", true)));

    @ParameterizedTest
    @ValueSource(strings = {"INT-1005", "int-1005", " int 1005 ", "INT1005", "int_1005"})
    void looksUpAnIntersectionHoweverTheCallerSpelledIt(String id) {
        assertTrue(REPO.find(id).isPresent());
        assertEquals("INT-1005", REPO.find(id).get().id());
    }

    @Test
    void unknownOrMissingIdsAreEmptyNotAnError() {
        assertTrue(REPO.find("INT-9999").isEmpty());
        assertTrue(REPO.find(null).isEmpty());
    }

    @Test
    void listsEverythingSortedById() {
        assertEquals(List.of("INT-1001", "INT-1005", "INT-1009", "INT-1015"),
                REPO.all().stream().map(Intersection::id).toList());
    }

    @Test
    void listsDistrictsWithoutTheUnknownOnes() {
        assertEquals(List.of("Downtown", "Westside"), REPO.districts());
    }

    @Test
    void summarisesADistrictCaseInsensitively() {
        assertEquals(new DistrictSummary("Downtown", 2, 2), REPO.findDistrict("downtown").orElseThrow());
        assertEquals(new DistrictSummary("Westside", 1, 0), REPO.findDistrict("WESTSIDE").orElseThrow());
    }

    @Test
    void unknownDistrictIsEmpty() {
        assertTrue(REPO.findDistrict("Nowhere").isEmpty());
        assertTrue(REPO.findDistrict(null).isEmpty());
    }

    @Test
    void filtersByDistrictAndActiveIndependently() {
        assertEquals(2, REPO.filter("Downtown", null).size());
        assertEquals(3, REPO.filter(null, true).size());
        assertEquals(1, REPO.filter("Westside", false).size());
        assertEquals(4, REPO.filter(null, null).size());
    }

    @Test
    void anEmptyRepositoryIsUsableRatherThanNull() {
        assertTrue(IntersectionRepository.empty().isEmpty());
        assertEquals(List.of(), IntersectionRepository.empty().all());
    }
}
