package dev.mcpuzzle.paper.instance;

import dev.mcpuzzle.core.domain.SessionId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceRuntimeRegistryTest {
    @Test
    void preventsWorldAndPlayerOwnershipCollisions() {
        InstanceRuntimeRegistry registry = new InstanceRuntimeRegistry();
        SessionId first = SessionId.random();
        SessionId second = SessionId.random();
        UUID playerId = UUID.randomUUID();

        registry.registerWorld(first, "mcpuzzle_first");
        registry.attachPlayer(first, playerId);

        assertThrows(IllegalStateException.class, () -> registry.registerWorld(second, "mcpuzzle_first"));
        assertThrows(IllegalStateException.class, () -> registry.attachPlayer(second, playerId));
        assertEquals(first, registry.sessionOfPlayer(playerId).orElseThrow());
        assertTrue(registry.activeWorldNames().contains("mcpuzzle_first"));
    }
}
