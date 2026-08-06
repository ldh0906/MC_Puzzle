package dev.mcpuzzle.paper.listener;

import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MazeGameplayListenerTest {
    @Test
    void permitsOnlyAirRightClickForReopeningTheEvidenceBook() {
        assertTrue(MazeGameplayListener.allowsEvidenceBook(Action.RIGHT_CLICK_AIR, Material.WRITTEN_BOOK));
        assertFalse(MazeGameplayListener.allowsEvidenceBook(Action.RIGHT_CLICK_BLOCK, Material.WRITTEN_BOOK));
        assertFalse(MazeGameplayListener.allowsEvidenceBook(Action.LEFT_CLICK_AIR, Material.WRITTEN_BOOK));
        assertFalse(MazeGameplayListener.allowsEvidenceBook(Action.RIGHT_CLICK_AIR, Material.PAPER));
    }
}
