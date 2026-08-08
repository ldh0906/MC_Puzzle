package dev.mcpuzzle.core.mechanic;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Tracks party-wide discovery of environmental clue regions. */
public final class ClueRegionsMechanic extends AbstractRoomMechanic {
    private final Set<String> requiredRegions;
    private final Set<String> discoveredRegions = new LinkedHashSet<>();

    public ClueRegionsMechanic(MechanicId id, RoomAttemptId attempt, Collection<String> requiredRegions) {
        super(id, MechanicType.CLUE_REGIONS, attempt);
        Objects.requireNonNull(requiredRegions, "requiredRegions");
        LinkedHashSet<String> copy = new LinkedHashSet<>();
        for (String region : requiredRegions) {
            if (region == null || region.isBlank()) throw new IllegalArgumentException("Clue region id must not be blank");
            if (!copy.add(region)) throw new IllegalArgumentException("Duplicate clue region " + region);
        }
        if (copy.isEmpty()) throw new IllegalArgumentException("At least one clue region is required");
        this.requiredRegions = Set.copyOf(copy);
    }

    @Override
    protected MechanicOutcome onEvent(MechanicEvent event) {
        if (!(event instanceof RegionEntered entered) || !requiredRegions.contains(entered.regionId())) {
            return noChange("clue_regions.unknown_region");
        }
        if (!discoveredRegions.add(entered.regionId())) return noChange("clue_regions.revisited");
        return discoveredRegions.containsAll(requiredRegions)
                ? complete("clue_regions.completed")
                : progressed("clue_regions.discovered");
    }

    @Override
    protected void onReset() {
        discoveredRegions.clear();
    }

    public synchronized Set<String> discoveredRegions() {
        return Set.copyOf(discoveredRegions);
    }

    public record RegionEntered(String regionId) implements MechanicEvent {
        public RegionEntered { Objects.requireNonNull(regionId, "regionId"); }
    }
}
