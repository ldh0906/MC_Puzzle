package dev.mcpuzzle.core.application.admission;

import java.util.Objects;
import java.util.Optional;

public record EnqueueResult(Optional<AdmissionError> error, AdmissionBatch batch) {
    public EnqueueResult {
        Objects.requireNonNull(error, "error");
        Objects.requireNonNull(batch, "batch");
    }

    public static EnqueueResult success(AdmissionBatch batch) {
        return new EnqueueResult(Optional.empty(), batch);
    }

    public static EnqueueResult failure(AdmissionError error) {
        return new EnqueueResult(Optional.of(error), AdmissionBatch.empty());
    }

    public boolean succeeded() {
        return error.isEmpty();
    }
}
