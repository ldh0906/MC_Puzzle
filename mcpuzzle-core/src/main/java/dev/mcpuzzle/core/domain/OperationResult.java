package dev.mcpuzzle.core.domain;

import java.util.Objects;
import java.util.Optional;

public final class OperationResult<E extends Enum<E>> {
    private static final OperationResult<?> SUCCESS = new OperationResult<>(null);

    private final E failure;

    private OperationResult(E failure) {
        this.failure = failure;
    }

    @SuppressWarnings("unchecked")
    public static <E extends Enum<E>> OperationResult<E> success() {
        return (OperationResult<E>) SUCCESS;
    }

    public static <E extends Enum<E>> OperationResult<E> failure(E failure) {
        return new OperationResult<>(Objects.requireNonNull(failure, "failure"));
    }

    public boolean succeeded() {
        return failure == null;
    }

    public Optional<E> failure() {
        return Optional.ofNullable(failure);
    }
}
