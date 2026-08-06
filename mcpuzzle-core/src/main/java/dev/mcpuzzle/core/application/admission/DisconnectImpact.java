package dev.mcpuzzle.core.application.admission;

import java.util.Objects;
import java.util.Optional;

public record DisconnectImpact(
        Optional<AdmissionRequest> cancelledQueued,
        Optional<AdmissionRequest> activeSession
) {
    public DisconnectImpact {
        Objects.requireNonNull(cancelledQueued, "cancelledQueued");
        Objects.requireNonNull(activeSession, "activeSession");
    }
}
