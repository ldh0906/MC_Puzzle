package dev.mcpuzzle.core.mechanic;

import java.util.Objects;

public final class DestructibleTargetMechanic extends AbstractRoomMechanic {
    private final String targetId;
    private boolean destroyed;

    public DestructibleTargetMechanic(MechanicId id, RoomAttemptId attempt, String targetId) {
        super(id, MechanicType.DESTRUCTIBLE_TARGET, attempt);
        this.targetId = requireId(targetId);
    }

    @Override
    protected MechanicOutcome onEvent(MechanicEvent event) {
        if (!(event instanceof TargetDestroyed target) || !targetId.equals(target.targetId())) {
            return noChange("destructible_target.unknown");
        }
        destroyed = true;
        return complete("destructible_target.destroyed");
    }

    @Override
    protected void onReset() {
        destroyed = false;
    }

    public synchronized boolean destroyed() {
        return destroyed;
    }

    public record TargetDestroyed(String targetId) implements MechanicEvent {
        public TargetDestroyed {
            targetId = requireId(targetId);
        }
    }

    private static String requireId(String value) {
        Objects.requireNonNull(value, "targetId");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Target id must not be blank");
        }
        return trimmed;
    }
}
