package dev.mcpuzzle.core.mechanic;

import java.util.Objects;

public record MechanicOutcome(
        MechanicOutcomeType type,
        MechanicStatus status,
        String detailKey
) {
    public MechanicOutcome {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detailKey, "detailKey");
    }
}
