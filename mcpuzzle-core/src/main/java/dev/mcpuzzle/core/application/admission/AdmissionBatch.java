package dev.mcpuzzle.core.application.admission;

import java.util.List;
import java.util.Objects;

public record AdmissionBatch(
        List<AdmissionRequest> admitted,
        List<CancelledAdmission> cancelled
) {
    private static final AdmissionBatch EMPTY = new AdmissionBatch(List.of(), List.of());

    public AdmissionBatch {
        admitted = List.copyOf(Objects.requireNonNull(admitted, "admitted"));
        cancelled = List.copyOf(Objects.requireNonNull(cancelled, "cancelled"));
    }

    public static AdmissionBatch empty() {
        return EMPTY;
    }
}
