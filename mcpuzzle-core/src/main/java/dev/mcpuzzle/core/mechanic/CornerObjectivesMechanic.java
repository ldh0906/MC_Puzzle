package dev.mcpuzzle.core.mechanic;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class CornerObjectivesMechanic extends AbstractRoomMechanic {
    public static final int REQUIRED_CORNERS = 4;

    private final Set<String> requiredObjectives;
    private final Set<String> activatedObjectives = new LinkedHashSet<>();

    public CornerObjectivesMechanic(
            MechanicId id,
            RoomAttemptId attempt,
            Collection<String> requiredObjectives
    ) {
        super(id, MechanicType.CORNER_OBJECTIVES, attempt);
        Objects.requireNonNull(requiredObjectives, "requiredObjectives");
        Set<String> copy = new LinkedHashSet<>();
        for (String objective : requiredObjectives) {
            String normalized = requireId(objective);
            if (!copy.add(normalized)) {
                throw new IllegalArgumentException("Corner objective ids must be unique");
            }
        }
        if (copy.size() != REQUIRED_CORNERS) {
            throw new IllegalArgumentException("Corner mechanic requires exactly four objectives");
        }
        this.requiredObjectives = Set.copyOf(copy);
    }

    @Override
    protected MechanicOutcome onEvent(MechanicEvent event) {
        if (!(event instanceof ObjectiveActivated activated)
                || !requiredObjectives.contains(activated.objectiveId())) {
            return noChange("corner_objective.unknown");
        }
        if (!activatedObjectives.add(activated.objectiveId())) {
            return noChange("corner_objective.already_activated");
        }
        return activatedObjectives.containsAll(requiredObjectives)
                ? complete("corner_objective.all_activated")
                : progressed("corner_objective.activated");
    }

    @Override
    protected void onReset() {
        activatedObjectives.clear();
    }

    public synchronized Set<String> activatedObjectives() {
        return Set.copyOf(activatedObjectives);
    }

    public record ObjectiveActivated(String objectiveId) implements MechanicEvent {
        public ObjectiveActivated {
            objectiveId = requireId(objectiveId);
        }
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "objectiveId");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Objective id must not be blank");
        }
        return trimmed;
    }
}
