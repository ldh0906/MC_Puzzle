package dev.mcpuzzle.core.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable result used for run history and leaderboard ranking. */
public record CompletedRun(
        SessionId runId,
        String mazeId,
        MapVersion mapVersion,
        PartyRoster roster,
        RunMetrics metrics,
        Instant completedAt
) {
    public CompletedRun {
        Objects.requireNonNull(runId, "runId");
        Objects.requireNonNull(mazeId, "mazeId");
        mazeId = mazeId.trim();
        if (mazeId.isEmpty()) {
            throw new IllegalArgumentException("Maze id must not be blank");
        }
        Objects.requireNonNull(mapVersion, "mapVersion");
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(completedAt, "completedAt");
    }

    public static CompletedRun from(PuzzleSessionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.state() != SessionState.COMPLETED) {
            throw new IllegalArgumentException("Run history accepts completed sessions only");
        }
        PuzzleSession.rehydrate(snapshot);
        return new CompletedRun(
                snapshot.id(),
                snapshot.mazeId(),
                snapshot.mapVersion(),
                snapshot.roster(),
                snapshot.metrics(),
                snapshot.capturedAt()
        );
    }
}
