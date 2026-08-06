package dev.mcpuzzle.paper.containment;

import dev.mcpuzzle.core.domain.SessionId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainmentPolicyTest {
    @Test
    void separatesLobbyAndEveryInstanceEvenForOperatorsByDefault() {
        ContainmentPolicy policy = new ContainmentPolicy();
        SessionId first = SessionId.random();
        SessionId second = SessionId.random();

        assertTrue(policy.canShare(Optional.empty(), false, Optional.empty(), false));
        assertTrue(policy.canShare(Optional.of(first), false, Optional.of(first), false));
        assertFalse(policy.canShare(Optional.of(first), false, Optional.of(second), false));
        assertFalse(policy.canShare(Optional.of(first), true, Optional.empty(), false));
        assertFalse(policy.canUseInstanceResource(Optional.empty(), true, Optional.of(first)));
    }

    @Test
    void optionalOperatorBypassIsExplicit() {
        ContainmentPolicy policy = new ContainmentPolicy(true);
        assertTrue(policy.canShare(Optional.of(SessionId.random()), true, Optional.empty(), false));
        assertTrue(policy.canUseInstanceResource(Optional.empty(), true, Optional.of(SessionId.random())));
    }
}
