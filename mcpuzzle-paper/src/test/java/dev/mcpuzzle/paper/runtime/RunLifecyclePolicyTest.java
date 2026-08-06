package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.domain.SessionState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunLifecyclePolicyTest {
    private final RunLifecyclePolicy policy = new RunLifecyclePolicy();

    @Test
    void onlyActiveRunsMayTickAfterAfkTransitions() {
        assertTrue(policy.mayTickGameplay(SessionState.ACTIVE));
        assertFalse(policy.mayTickGameplay(SessionState.SUSPENDED));
        assertFalse(policy.mayTickGameplay(SessionState.COMPLETED));
    }

    @Test
    void shutdownDiscardsInterruptedRunsButPreservesExistingSuspension() {
        assertEquals(RunLifecyclePolicy.ShutdownAction.DISCARD_TRANSIENT, policy.shutdownAction(SessionState.ACTIVE));
        assertEquals(RunLifecyclePolicy.ShutdownAction.DISCARD_TRANSIENT, policy.shutdownAction(SessionState.PROVISIONING));
        assertEquals(RunLifecyclePolicy.ShutdownAction.PRESERVE_SUSPENDED, policy.shutdownAction(SessionState.SUSPENDED));
        assertEquals(RunLifecyclePolicy.ShutdownAction.FINALIZE_COMPLETED, policy.shutdownAction(SessionState.COMPLETED));
    }
}
