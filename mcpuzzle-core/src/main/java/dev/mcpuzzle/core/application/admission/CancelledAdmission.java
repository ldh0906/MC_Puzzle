package dev.mcpuzzle.core.application.admission;

import java.util.Objects;

public record CancelledAdmission(AdmissionRequest request, AvailabilityCheck reason) {
    public CancelledAdmission {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(reason, "reason");
        if (reason.isEligible()) {
            throw new IllegalArgumentException("Cancellation requires an unavailable reason");
        }
    }
}
