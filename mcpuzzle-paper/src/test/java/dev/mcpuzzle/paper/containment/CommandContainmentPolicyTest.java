package dev.mcpuzzle.paper.containment;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandContainmentPolicyTest {
    @Test
    void acceptsOnlyExplicitMazeLabelsAndCannotBeBypassedThroughNamespace() {
        CommandContainmentPolicy policy = CommandContainmentPolicy.mazeOnly();

        assertTrue(policy.isAllowed("/maze leave", false));
        assertTrue(policy.isAllowed("/mcpuzzle:maze status", false));
        assertTrue(policy.isAllowed("/미궁 나가기", false));
        assertFalse(policy.isAllowed("/minecraft:tp @s 0 100 0", true));
        assertFalse(policy.isAllowed("/home", false));
    }

    @Test
    void operatorBypassMustBeConfigured() {
        CommandContainmentPolicy policy = new CommandContainmentPolicy(Set.of("maze"), true);
        assertTrue(policy.isAllowed("/tp 0 100 0", true));
        assertFalse(policy.isAllowed("/tp 0 100 0", false));
    }
}
