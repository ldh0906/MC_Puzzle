package dev.mcpuzzle.core.application.hint;

import dev.mcpuzzle.core.domain.SessionId;

import java.util.Objects;

public record HintContextId(SessionId sessionId, int room, long attemptRevision) {
    public HintContextId {
        Objects.requireNonNull(sessionId, "sessionId");
        if (room < 1 || attemptRevision < 0) {
            throw new IllegalArgumentException("Hint context requires a positive room and non-negative attempt");
        }
    }
}
