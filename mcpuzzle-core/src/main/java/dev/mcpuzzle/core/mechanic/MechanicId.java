package dev.mcpuzzle.core.mechanic;

import java.util.Objects;

public record MechanicId(String value) {
    public MechanicId {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Mechanic id must not be blank");
        }
    }
}
