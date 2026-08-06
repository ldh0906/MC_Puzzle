package dev.mcpuzzle.core.domain;

import java.time.Instant;
import java.util.Objects;

public record SessionCompletion(
        SessionId sessionId,
        String mazeId,
        MapVersion mapVersion,
        PartyRoster roster,
        RunMetrics metrics,
        Instant completedAt
) {
    public SessionCompletion {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(mazeId, "mazeId");
        Objects.requireNonNull(mapVersion, "mapVersion");
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(completedAt, "completedAt");
    }
}
