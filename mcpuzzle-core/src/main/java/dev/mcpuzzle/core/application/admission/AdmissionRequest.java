package dev.mcpuzzle.core.application.admission;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;

import java.time.Instant;
import java.util.Objects;

public record AdmissionRequest(SessionId sessionId, PartyRoster roster, Instant enqueuedAt) {
    public AdmissionRequest {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(enqueuedAt, "enqueuedAt");
    }
}
