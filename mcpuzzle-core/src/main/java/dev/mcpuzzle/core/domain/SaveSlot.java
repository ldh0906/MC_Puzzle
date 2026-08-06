package dev.mcpuzzle.core.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SaveSlot(
        int number,
        UUID ownerId,
        String mazeId,
        MapVersion mapVersion,
        PartyRoster roster,
        Checkpoint checkpoint,
        Instant updatedAt
) {
    public static final int MIN_NUMBER = 1;
    public static final int MAX_NUMBER = 3;
    public static final Duration RETENTION = Duration.ofDays(7);

    public SaveSlot {
        if (number < MIN_NUMBER || number > MAX_NUMBER) {
            throw new IllegalArgumentException("Save slot number must be between 1 and 3");
        }
        Objects.requireNonNull(ownerId, "ownerId");
        mazeId = requireText(mazeId, "mazeId");
        Objects.requireNonNull(mapVersion, "mapVersion");
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!roster.contains(ownerId)) {
            throw new IllegalArgumentException("Save owner must belong to the original roster");
        }
    }

    /**
     * Returns a new slot with a different owner. Authorization for this operation
     * belongs to the application service; the domain keeps the original roster intact.
     */
    public SaveSlot transferOwnership(UUID newOwnerId) {
        return new SaveSlot(
                number,
                Objects.requireNonNull(newOwnerId, "newOwnerId"),
                mazeId,
                mapVersion,
                roster,
                checkpoint,
                updatedAt
        );
    }

    public Instant expiresAt() {
        return updatedAt.plus(RETENTION);
    }

    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(expiresAt());
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
