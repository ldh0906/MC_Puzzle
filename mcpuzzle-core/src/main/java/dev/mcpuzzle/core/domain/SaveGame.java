package dev.mcpuzzle.core.domain;

import java.util.Objects;

/** A resumable save slot together with its complete suspended session snapshot. */
public record SaveGame(SaveSlot slot, PuzzleSessionSnapshot snapshot) {
    public SaveGame {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.state() != SessionState.SUSPENDED) {
            throw new IllegalArgumentException("Only suspended sessions can be stored in a save slot");
        }
        if (!slot.mazeId().equals(snapshot.mazeId())
                || !slot.mapVersion().equals(snapshot.mapVersion())
                || !slot.roster().equals(snapshot.roster())) {
            throw new IllegalArgumentException("Save metadata must match its session snapshot");
        }
        if (snapshot.checkpoint().isEmpty() || !slot.checkpoint().equals(snapshot.checkpoint().orElseThrow())) {
            throw new IllegalArgumentException("Save slot checkpoint must match its session snapshot");
        }
        if (!slot.updatedAt().equals(snapshot.capturedAt())) {
            throw new IllegalArgumentException("Save timestamp must match the snapshot capture time");
        }
        PuzzleSession.rehydrate(snapshot);
    }

    public SaveGame transferOwnership(java.util.UUID newOwnerId) {
        return new SaveGame(slot.transferOwnership(newOwnerId), snapshot);
    }
}
