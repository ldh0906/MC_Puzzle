package dev.mcpuzzle.core.mechanic;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class ProximityEscortMechanic extends AbstractRoomMechanic {
    public static final int REQUIRED_CHECKPOINTS = 7;

    private final List<String> checkpoints;
    private final String destinationId;
    private int nextCheckpoint;

    public ProximityEscortMechanic(
            MechanicId id,
            RoomAttemptId attempt,
            List<String> checkpoints,
            String destinationId
    ) {
        super(id, MechanicType.PROXIMITY_ESCORT, attempt);
        Objects.requireNonNull(checkpoints, "checkpoints");
        if (checkpoints.size() != REQUIRED_CHECKPOINTS) {
            throw new IllegalArgumentException("Escort mechanic requires exactly seven checkpoints");
        }
        this.checkpoints = checkpoints.stream().map(ProximityEscortMechanic::requireId).toList();
        if (new HashSet<>(this.checkpoints).size() != REQUIRED_CHECKPOINTS) {
            throw new IllegalArgumentException("Escort checkpoint ids must be unique");
        }
        this.destinationId = requireId(destinationId);
        if (this.checkpoints.contains(this.destinationId)) {
            throw new IllegalArgumentException("Escort destination must differ from checkpoints");
        }
    }

    @Override
    protected MechanicOutcome onEvent(MechanicEvent event) {
        if (!(event instanceof EntityReached reached)) {
            return noChange("escort.unsupported_event");
        }
        int checkpointIndex = checkpoints.indexOf(reached.locationId());
        if (checkpointIndex >= 0) {
            if (!reached.gateOpen()) {
                return fail("escort.ungated_checkpoint_reached");
            }
            if (checkpointIndex != nextCheckpoint) {
                return noChange("escort.checkpoint_out_of_order");
            }
            nextCheckpoint++;
            return progressed("escort.checkpoint_reached");
        }
        if (destinationId.equals(reached.locationId())) {
            return nextCheckpoint == checkpoints.size()
                    ? complete("escort.destination_reached")
                    : noChange("escort.destination_locked");
        }
        return noChange("escort.unknown_location");
    }

    @Override
    protected void onReset() {
        nextCheckpoint = 0;
    }

    public synchronized int checkpointsPassed() {
        return nextCheckpoint;
    }

    public record EntityReached(String locationId, boolean gateOpen) implements MechanicEvent {
        public EntityReached {
            locationId = requireId(locationId);
        }
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "locationId");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Location id must not be blank");
        }
        return trimmed;
    }
}
