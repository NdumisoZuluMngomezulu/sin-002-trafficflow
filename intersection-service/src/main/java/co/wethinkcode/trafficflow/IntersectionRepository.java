package co.wethinkcode.trafficflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The canonical intersection list, indexed for the two questions this service exists
 * to answer: "is this a real intersection?" and "is this a real district?".
 *
 * <p>Immutable. Reloading builds a new repository and swaps it in atomically, so a
 * request never sees a half-loaded list.
 */
public final class IntersectionRepository {

    private final Map<String, Intersection> byId;
    private final Map<String, List<Intersection>> byDistrict;
    private final Instant loadedAt;

    public IntersectionRepository(Collection<Intersection> intersections) {
        Map<String, Intersection> ids = new LinkedHashMap<>();
        for (Intersection intersection : intersections) {
            ids.put(canonicalId(intersection.id()), intersection);
        }
        this.byId = Map.copyOf(ids);

        // TreeMap with a case-insensitive comparator: district lookup shouldn't care
        // whether the caller typed "downtown" or "Downtown".
        Map<String, List<Intersection>> districts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Intersection intersection : ids.values()) {
            if (intersection.district() != null) {
                districts.computeIfAbsent(intersection.district(), d -> new ArrayList<>()).add(intersection);
            }
        }
        districts.replaceAll((district, members) -> List.copyOf(members));
        this.byDistrict = districts;
        this.loadedAt = Instant.now();
    }

    public static IntersectionRepository empty() {
        return new IntersectionRepository(List.of());
    }

    /** Looks an intersection up regardless of how the caller spelled its id. */
    public Optional<Intersection> find(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(canonicalId(id)));
    }

    public List<Intersection> all() {
        return byId.values().stream()
                .sorted(Comparator.comparing(Intersection::id))
                .toList();
    }

    /** Filters the list, ignoring either filter when it is null. */
    public List<Intersection> filter(String district, Boolean active) {
        return all().stream()
                .filter(i -> district == null || district.equalsIgnoreCase(String.valueOf(i.district())))
                .filter(i -> active == null || active.equals(i.active()))
                .toList();
    }

    public List<String> districts() {
        return List.copyOf(byDistrict.keySet());
    }

    public Optional<DistrictSummary> findDistrict(String name) {
        if (name == null) {
            return Optional.empty();
        }
        List<Intersection> members = byDistrict.get(name.strip());
        if (members == null) {
            return Optional.empty();
        }
        int active = (int) members.stream().filter(i -> Boolean.TRUE.equals(i.active())).count();
        return Optional.of(new DistrictSummary(members.get(0).district(), members.size(), active));
    }

    public int size() {
        return byId.size();
    }

    public boolean isEmpty() {
        return byId.isEmpty();
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    /**
     * Same id canonicalisation ingestion-service applies, so {@code int-1005},
     * {@code INT 1005} and {@code INT-1005} all resolve to the same record.
     */
    private static String canonicalId(String id) {
        String compact = id.strip().replaceAll("[\\s_]", "").toUpperCase();
        return compact.matches("INT-?\\d+") ? "INT-" + compact.replaceAll("\\D", "") : compact;
    }
}
