package dev.mcpuzzle.paper.world;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedWorldVerifierTest {
    @Test
    void identifiesPhysicalPuzzleInputsWithoutMisclassifyingSigns() {
        assertTrue(GeneratedWorldVerifier.isPuzzleInput(Material.OAK_PRESSURE_PLATE));
        assertTrue(GeneratedWorldVerifier.isPuzzleInput(Material.LIGHT_WEIGHTED_PRESSURE_PLATE));
        assertTrue(GeneratedWorldVerifier.isPuzzleInput(Material.STONE_BUTTON));
        assertTrue(GeneratedWorldVerifier.isPuzzleInput(Material.OAK_BUTTON));
        assertTrue(GeneratedWorldVerifier.isPuzzleInput(Material.LEVER));
        assertFalse(GeneratedWorldVerifier.isPuzzleInput(Material.OAK_SIGN));
        assertFalse(GeneratedWorldVerifier.isPuzzleInput(Material.WRITTEN_BOOK));
    }
}
