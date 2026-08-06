package dev.mcpuzzle.core.mechanic;

import dev.mcpuzzle.core.domain.SessionId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechanicStateMachinesTest {
    @Test
    void latchingPressurePadsResetFullyAndIgnoreStaleAttemptEvents() {
        RoomAttemptId first = attempt(0);
        RoomAttemptId second = attempt(first.sessionId(), 1);
        LatchingPressurePadsMechanic mechanic = new LatchingPressurePadsMechanic(
                new MechanicId("pads"),
                first,
                List.of("a", "b")
        );

        assertEquals(MechanicOutcomeType.PROGRESSED, mechanic.handle(
                first,
                new LatchingPressurePadsMechanic.PadPressed("a")
        ).type());
        assertEquals(MechanicOutcomeType.NO_CHANGE, mechanic.handle(
                first,
                new LatchingPressurePadsMechanic.PadPressed("a")
        ).type());
        assertEquals(MechanicOutcomeType.RESET, mechanic.reset(second).type());
        assertTrue(mechanic.latchedPads().isEmpty());
        assertEquals(MechanicOutcomeType.IGNORED_STALE_ATTEMPT, mechanic.handle(
                first,
                new LatchingPressurePadsMechanic.PadPressed("b")
        ).type());
        mechanic.handle(second, new LatchingPressurePadsMechanic.PadPressed("a"));
        assertEquals(MechanicOutcomeType.COMPLETED, mechanic.handle(
                second,
                new LatchingPressurePadsMechanic.PadPressed("b")
        ).type());
        assertEquals(MechanicOutcomeType.ALREADY_TERMINAL, mechanic.handle(
                second,
                new LatchingPressurePadsMechanic.PadPressed("b")
        ).type());
    }

    @Test
    void numericKeypadSupportsClearCorrectSubmitAndWrongWholeAttemptFailure() {
        RoomAttemptId first = attempt(0);
        RoomAttemptId second = attempt(first.sessionId(), 1);
        NumericKeypadMechanic keypad = new NumericKeypadMechanic(
                new MechanicId("keypad"),
                first,
                "37"
        );

        keypad.handle(first, new NumericKeypadMechanic.DigitPressed(3));
        assertEquals("3", keypad.buffer());
        keypad.handle(first, new NumericKeypadMechanic.ClearPressed());
        assertEquals("", keypad.buffer());
        keypad.handle(first, new NumericKeypadMechanic.DigitPressed(9));
        assertEquals(MechanicOutcomeType.FAILED, keypad.handle(
                first,
                new NumericKeypadMechanic.SubmitPressed()
        ).type());
        assertEquals("", keypad.buffer());
        assertEquals(MechanicOutcomeType.ALREADY_TERMINAL, keypad.handle(
                first,
                new NumericKeypadMechanic.SubmitPressed()
        ).type());

        keypad.reset(second);
        assertEquals(MechanicOutcomeType.IGNORED_STALE_ATTEMPT, keypad.handle(
                first,
                new NumericKeypadMechanic.DigitPressed(3)
        ).type());
        keypad.handle(second, new NumericKeypadMechanic.DigitPressed(3));
        keypad.handle(second, new NumericKeypadMechanic.DigitPressed(7));
        assertEquals(MechanicOutcomeType.COMPLETED, keypad.handle(
                second,
                new NumericKeypadMechanic.SubmitPressed()
        ).type());
    }

    @Test
    void escortRequiresSevenOrderedGatedCheckpointsThenDestination() {
        RoomAttemptId first = attempt(0);
        RoomAttemptId second = attempt(first.sessionId(), 1);
        List<String> checkpoints = List.of("c1", "c2", "c3", "c4", "c5", "c6", "c7");
        ProximityEscortMechanic escort = new ProximityEscortMechanic(
                new MechanicId("escort"),
                first,
                checkpoints,
                "destination"
        );

        assertEquals(MechanicOutcomeType.NO_CHANGE, escort.handle(
                first,
                new ProximityEscortMechanic.EntityReached("destination", true)
        ).type());
        escort.handle(first, new ProximityEscortMechanic.EntityReached("c1", true));
        assertEquals(MechanicOutcomeType.FAILED, escort.handle(
                first,
                new ProximityEscortMechanic.EntityReached("c2", false)
        ).type());

        escort.reset(second);
        assertEquals(0, escort.checkpointsPassed());
        for (String checkpoint : checkpoints) {
            assertEquals(MechanicOutcomeType.PROGRESSED, escort.handle(
                    second,
                    new ProximityEscortMechanic.EntityReached(checkpoint, true)
            ).type());
        }
        assertEquals(7, escort.checkpointsPassed());
        assertEquals(MechanicOutcomeType.COMPLETED, escort.handle(
                second,
                new ProximityEscortMechanic.EntityReached("destination", true)
        ).type());
    }

    @Test
    void dynamicPartyPadsActivateExactlyOneToFourRosterIndicesAndTarget() {
        for (int partySize = 1; partySize <= 4; partySize++) {
            RoomAttemptId attempt = attempt(0);
            DynamicPartyPadsAndTargetMechanic mechanic = new DynamicPartyPadsAndTargetMechanic(
                    new MechanicId("dynamic-" + partySize),
                    attempt,
                    partySize
            );
            assertEquals(MechanicOutcomeType.NO_CHANGE, mechanic.handle(
                    attempt,
                    new DynamicPartyPadsAndTargetMechanic.RosterPadPressed(partySize)
            ).type());
            mechanic.handle(attempt, new DynamicPartyPadsAndTargetMechanic.TargetDestroyed());
            for (int rosterIndex = 0; rosterIndex < partySize; rosterIndex++) {
                MechanicOutcome result = mechanic.handle(
                        attempt,
                        new DynamicPartyPadsAndTargetMechanic.RosterPadPressed(rosterIndex)
                );
                assertEquals(
                        rosterIndex == partySize - 1
                                ? MechanicOutcomeType.COMPLETED
                                : MechanicOutcomeType.PROGRESSED,
                        result.type()
                );
            }
            assertEquals(partySize, mechanic.latchedRosterPads().size());
            assertTrue(mechanic.targetDestroyed());
        }
    }

    @Test
    void destructibleTargetIsIdempotentAndResetRestoresIt() {
        RoomAttemptId first = attempt(0);
        RoomAttemptId second = attempt(first.sessionId(), 1);
        DestructibleTargetMechanic target = new DestructibleTargetMechanic(
                new MechanicId("target"),
                first,
                "core"
        );

        assertEquals(MechanicOutcomeType.NO_CHANGE, target.handle(
                first,
                new DestructibleTargetMechanic.TargetDestroyed("other")
        ).type());
        assertEquals(MechanicOutcomeType.COMPLETED, target.handle(
                first,
                new DestructibleTargetMechanic.TargetDestroyed("core")
        ).type());
        assertTrue(target.destroyed());
        target.reset(second);
        assertFalse(target.destroyed());
    }

    private static RoomAttemptId attempt(long revision) {
        return attempt(SessionId.random(), revision);
    }

    private static RoomAttemptId attempt(SessionId sessionId, long revision) {
        return new RoomAttemptId(sessionId, 1, revision);
    }
}
