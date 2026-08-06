package dev.mcpuzzle.core.domain;

import java.util.Objects;

public record MapVersion(String value) {
    public MapVersion {
        Objects.requireNonNull(value, "value");
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Map version must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
