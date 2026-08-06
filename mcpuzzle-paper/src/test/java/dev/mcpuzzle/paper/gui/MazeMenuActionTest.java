package dev.mcpuzzle.paper.gui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MazeMenuActionTest {
    @Test
    void roundTripsActionsWithUuidSlotAndPageArguments() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        MazeMenuAction action = MazeMenuAction.of(
                MazeMenuAction.Type.ADMIN_TRANSFER,
                owner.toString(), "2", target.toString());

        assertEquals(action, MazeMenuAction.decode(action.encode()).orElseThrow());
    }

    @Test
    void rejectsUnknownMalformedAndDelimiterBearingActions() {
        assertTrue(MazeMenuAction.decode("missing").isEmpty());
        assertTrue(MazeMenuAction.decode("NOT_A_TYPE|1").isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> MazeMenuAction.of(MazeMenuAction.Type.MAIN, "bad|argument"));
    }
}
