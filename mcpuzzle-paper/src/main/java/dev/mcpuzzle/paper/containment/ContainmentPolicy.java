package dev.mcpuzzle.paper.containment;

import dev.mcpuzzle.core.domain.SessionId;

import java.util.Optional;

public final class ContainmentPolicy {
    private final boolean operatorBypass;

    public ContainmentPolicy() {
        this(false);
    }

    public ContainmentPolicy(boolean operatorBypass) {
        this.operatorBypass = operatorBypass;
    }

    public boolean canShare(
            Optional<SessionId> first,
            boolean firstOperator,
            Optional<SessionId> second,
            boolean secondOperator
    ) {
        if (operatorBypass && (firstOperator || secondOperator)) {
            return true;
        }
        return first.equals(second);
    }

    public boolean canUseInstanceResource(
            Optional<SessionId> playerSession,
            boolean operator,
            Optional<SessionId> resourceSession
    ) {
        if (operatorBypass && operator) {
            return true;
        }
        return playerSession.equals(resourceSession);
    }

    public boolean operatorBypassEnabled() {
        return operatorBypass;
    }
}
