package dev.mcpuzzle.paper.containment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeleportPermitRegistryTest {
    @Test
    void nestedPermitsRemainActiveUntilOutermostTeleportCompletes() {
        TeleportPermitRegistry permits = new TeleportPermitRegistry();
        UUID playerId = UUID.randomUUID();

        assertFalse(permits.isPermitted(playerId));
        assertTrue(permits.runPermitted(playerId, () -> {
            assertTrue(permits.isPermitted(playerId));
            return permits.runPermitted(playerId, () -> {
                assertTrue(permits.isPermitted(playerId));
                return true;
            }) && permits.isPermitted(playerId);
        }));
        assertFalse(permits.isPermitted(playerId));
    }
}
