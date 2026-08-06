package dev.mcpuzzle.core.mechanic;

import dev.mcpuzzle.core.domain.SessionId;

import java.util.Objects;

public record RoomAttemptId(SessionId sessionId, int room, long revision) {
    public RoomAttemptId {
        Objects.requireNonNull(sessionId, "sessionId");
        if (room < 1 || revision < 0) {
            throw new IllegalArgumentException("Room attempt requires a positive room and non-negative revision");
        }
    }

    public boolean isNewerAttemptOf(RoomAttemptId other) {
        Objects.requireNonNull(other, "other");
        return sessionId.equals(other.sessionId) && room == other.room && revision > other.revision;
    }
}
