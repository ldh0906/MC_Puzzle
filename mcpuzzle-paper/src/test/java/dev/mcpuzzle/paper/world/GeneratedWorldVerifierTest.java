package dev.mcpuzzle.paper.world;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedWorldVerifierTest {
    @Test
    void detectsEveryForbiddenPhysicalQuizInputFamilyWithoutRejectingSigns() {
        assertTrue(GeneratedWorldVerifier.isForbiddenInput(Material.OAK_PRESSURE_PLATE));
        assertTrue(GeneratedWorldVerifier.isForbiddenInput(Material.LIGHT_WEIGHTED_PRESSURE_PLATE));
        assertTrue(GeneratedWorldVerifier.isForbiddenInput(Material.STONE_BUTTON));
        assertTrue(GeneratedWorldVerifier.isForbiddenInput(Material.OAK_BUTTON));
        assertTrue(GeneratedWorldVerifier.isForbiddenInput(Material.LEVER));
        assertFalse(GeneratedWorldVerifier.isForbiddenInput(Material.OAK_SIGN));
        assertFalse(GeneratedWorldVerifier.isForbiddenInput(Material.WRITTEN_BOOK));
    }
}
