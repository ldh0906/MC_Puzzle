package dev.mcpuzzle.core.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record PuzzleSessionSnapshot(
        SessionId id,
        String mazeId,
        MapVersion mapVersion,
        SessionState state,
        PartyRoster roster,
        boolean rosterLocked,
        int currentRoom,
        int roomCount,
        long roomAttemptRevision,
        RunMetrics metrics,
        HintProgress hintProgress,
        Optional<Checkpoint> checkpoint,
        Optional<Instant> activeSince,
        Optional<Instant> lastActivityAt,
        Optional<SuspendReason> lastSuspendReason,
        Optional<AbandonReason> abandonReason,
        Instant capturedAt
) {
    public PuzzleSessionSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(mazeId, "mazeId");
        Objects.requireNonNull(mapVersion, "mapVersion");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(hintProgress, "hintProgress");
        Objects.requireNonNull(checkpoint, "checkpoint");
        Objects.requireNonNull(activeSince, "activeSince");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt");
        Objects.requireNonNull(lastSuspendReason, "lastSuspendReason");
        Objects.requireNonNull(abandonReason, "abandonReason");
        Objects.requireNonNull(capturedAt, "capturedAt");
    }
}
