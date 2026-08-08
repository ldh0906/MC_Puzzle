package dev.mcpuzzle.paper.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MazeCommandParsingTest {
    @Test
    void acceptsConfiguredRangeEdges() {
        assertEquals(1, MazeCommand.integer("1", 1, 3, "슬롯"));
        assertEquals(3, MazeCommand.integer("3", 1, 3, "슬롯"));
    }

    @Test
    void rejectsNonNumericAndOutOfRangeArguments() {
        assertThrows(IllegalArgumentException.class, () -> MazeCommand.integer("x", 1, 3, "슬롯"));
        assertThrows(IllegalArgumentException.class, () -> MazeCommand.integer("4", 1, 3, "슬롯"));
    }

    @Test
    void exposesInvitationResponseCommandsAtTheRootAndPartyLevels() {
        assertTrue(MazeCommand.ROOT.contains("accept"));
        assertTrue(MazeCommand.ROOT.contains("deny"));
        assertTrue(MazeCommand.PARTY.contains("accept"));
        assertTrue(MazeCommand.PARTY.contains("deny"));
    }
}
