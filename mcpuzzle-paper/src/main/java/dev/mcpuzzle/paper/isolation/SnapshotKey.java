package dev.mcpuzzle.paper.isolation;

import dev.mcpuzzle.core.domain.SessionId;

import java.util.Objects;
import java.util.UUID;

public record SnapshotKey(SessionId sessionId, UUID playerId) {
    public SnapshotKey {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
    }
}
