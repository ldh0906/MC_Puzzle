package dev.mcpuzzle.core.application.admission;

import java.util.Objects;
import java.util.Optional;

public record ReleaseResult(
        boolean released,
        Optional<AdmissionError> error,
        AdmissionBatch batch
) {
    public ReleaseResult {
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(batch, "batch");
    }

    public static ReleaseResult success(AdmissionBatch batch) {
        return new ReleaseResult(true, Optional.empty(), batch);
    }

    public static ReleaseResult failure(AdmissionError error) {
        return new ReleaseResult(false, Optional.of(error), AdmissionBatch.empty());
    }
}
