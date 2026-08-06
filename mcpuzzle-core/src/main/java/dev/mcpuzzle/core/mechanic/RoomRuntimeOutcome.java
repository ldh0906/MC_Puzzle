package dev.mcpuzzle.core.mechanic;

import java.util.Objects;
import java.util.Optional;

public record RoomRuntimeOutcome(
        RoomRuntimeOutcomeType type,
        RoomRuntimeStatus status,
        Optional<MechanicId> mechanicId,
        String detailKey
) {
    public RoomRuntimeOutcome {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(mechanicId, "mechanicId");
        Objects.requireNonNull(detailKey, "detailKey");
    }
}
