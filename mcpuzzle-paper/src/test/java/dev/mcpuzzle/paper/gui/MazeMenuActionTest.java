package dev.mcpuzzle.paper.gui;

import dev.mcpuzzle.core.domain.SessionState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void validatesTypedArgumentsWithoutThrowing() {
        UUID playerId = UUID.randomUUID();
        MazeMenuAction valid = MazeMenuAction.of(MazeMenuAction.Type.SAVE_RESUME, "3", playerId.toString());

        assertEquals(3, valid.integer(0, 1, 3).orElseThrow());
        assertEquals(playerId, valid.uuid(1).orElseThrow());
        assertFalse(MazeMenuAction.of(MazeMenuAction.Type.SAVE_RESUME, "x").integer(0, 1, 3).isPresent());
        assertFalse(MazeMenuAction.of(MazeMenuAction.Type.SAVE_RESUME, "4").integer(0, 1, 3).isPresent());
        assertTrue(MazeMenuAction.of(MazeMenuAction.Type.PARTY_INVITE, "not-a-uuid").uuid(0).isEmpty());
        assertTrue(MazeMenuAction.of(MazeMenuAction.Type.PAGE).uuid(0).isEmpty());
    }

    @Test
    void preservesTheRequestedActionInsideAConfirmation() {
        UUID owner = UUID.randomUUID();
        MazeMenuAction requested = MazeMenuAction.of(MazeMenuAction.Type.SAVE_DELETE, "2", owner.toString());

        MazeMenuAction confirmation = MazeMenuAction.confirmation(requested);

        assertEquals(requested, confirmation.confirmedAction().orElseThrow());
        assertTrue(MazeMenuAction.of(MazeMenuAction.Type.CONFIRM).confirmedAction().isEmpty());
        assertTrue(MazeMenuAction.of(MazeMenuAction.Type.CONFIRM, "NOT_A_TYPE").confirmedAction().isEmpty());
    }

    @Test
    void refusesToSubstituteAMinimumForMalformedNumbers() {
        MazeMenuAction malformed = MazeMenuAction.of(MazeMenuAction.Type.SAVE_DELETE, "not-a-slot");

        assertThrows(IllegalArgumentException.class, () -> malformed.requireInteger(0, 1, 3));
    }

    @Test
    void exposesRunActionsOnlyInStatesThatCanExecuteThem() {
        assertEquals(List.of(MazeMenuAction.Type.QUEUE_CANCEL), MazeMenu.dashboardActions(SessionState.QUEUED));
        assertEquals(List.of(MazeMenuAction.Type.ANSWER_PROMPT, MazeMenuAction.Type.RUN_LEAVE),
                MazeMenu.dashboardActions(SessionState.ACTIVE));
        assertEquals(List.of(MazeMenuAction.Type.RUN_LEAVE), MazeMenu.dashboardActions(SessionState.SUSPENDED));
        assertTrue(MazeMenu.dashboardActions(SessionState.PROVISIONING).isEmpty());
        assertTrue(MazeMenu.dashboardActions(SessionState.CLEANUP).isEmpty());
    }
}
