package dev.mcpuzzle.core.domain;

import java.util.List;
import java.util.Objects;

/**
 * Result of restart recovery. Waiting/queued admissions and interrupted
 * provisioning/active runs are transient and discarded; suspended saves survive.
 */
public record StartupRecoveryReport(
        List<SessionId> discardedTransientAdmissions,
        List<SessionId> discardedInterruptedRuns,
        List<SessionId> retainedSuspended
) {
    public StartupRecoveryReport {
        Objects.requireNonNull(discardedTransientAdmissions, "discardedTransientAdmissions");
        Objects.requireNonNull(discardedInterruptedRuns, "discardedInterruptedRuns");
        Objects.requireNonNull(retainedSuspended, "retainedSuspended");
        discardedTransientAdmissions = List.copyOf(discardedTransientAdmissions);
        discardedInterruptedRuns = List.copyOf(discardedInterruptedRuns);
        retainedSuspended = List.copyOf(retainedSuspended);
    }
}
