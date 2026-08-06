package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.domain.SessionState;

import java.util.Objects;

/** Pure terminal policy shared by the tick loop and deterministic shutdown planning. */
public final class RunLifecyclePolicy {
    public enum ShutdownAction { DISCARD_TRANSIENT, PRESERVE_SUSPENDED, FINALIZE_COMPLETED, NO_DURABLE_ACTION }

    public boolean mayTickGameplay(SessionState state) {
        return Objects.requireNonNull(state, "state") == SessionState.ACTIVE;
    }

    public ShutdownAction shutdownAction(SessionState state) {
        return switch (Objects.requireNonNull(state, "state")) {
            case WAITING, QUEUED, PROVISIONING, ACTIVE -> ShutdownAction.DISCARD_TRANSIENT;
            case SUSPENDED -> ShutdownAction.PRESERVE_SUSPENDED;
            case COMPLETED -> ShutdownAction.FINALIZE_COMPLETED;
            case ABANDONED, CLEANUP -> ShutdownAction.NO_DURABLE_ACTION;
        };
    }
}
