package dev.mcpuzzle.core.domain;

import java.time.Instant;
import java.util.Objects;

public record Checkpoint(int completedRoom, int nextRoom, Instant savedAt) {
    public Checkpoint {
        if (completedRoom < 0) {
            throw new IllegalArgumentException("Completed room must not be negative");
        }
        if (nextRoom != completedRoom + 1) {
            throw new IllegalArgumentException("Next room must immediately follow completed room");
        }
        Objects.requireNonNull(savedAt, "savedAt");
    }

    public static Checkpoint initial(Instant savedAt) {
        return new Checkpoint(0, 1, savedAt);
    }
}
