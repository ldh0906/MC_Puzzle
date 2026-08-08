package dev.mcpuzzle.core.mechanic;

import dev.mcpuzzle.core.domain.SessionId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    @Test
    void clueRegionsOnlyProgressOncePerRegionAndIgnoreStaleAttempts() {
        RoomAttemptId first = attempt(0);
        RoomAttemptId second = attempt(first.sessionId(), 1);
        ClueRegionsMechanic clues = new ClueRegionsMechanic(
                new MechanicId("clues"), first, List.of("red", "blue"));

        assertEquals(MechanicOutcomeType.PROGRESSED,
                clues.handle(first, new ClueRegionsMechanic.RegionEntered("red")).type());
        assertEquals(MechanicOutcomeType.NO_CHANGE,
                clues.handle(first, new ClueRegionsMechanic.RegionEntered("red")).type());
        assertEquals(Set.of("red"), clues.discoveredRegions());
        clues.reset(second);
        assertEquals(MechanicOutcomeType.IGNORED_STALE_ATTEMPT,
                clues.handle(first, new ClueRegionsMechanic.RegionEntered("blue")).type());
        clues.handle(second, new ClueRegionsMechanic.RegionEntered("red"));
        assertEquals(MechanicOutcomeType.COMPLETED,
                clues.handle(second, new ClueRegionsMechanic.RegionEntered("blue")).type());
    }

    @Test
    void orderedInputWrongStepClearsOnlyItsBufferAndAllowsRepeatedControls() {
        RoomAttemptId attempt = attempt(0);
        OrderedInputMechanic ordered = new OrderedInputMechanic(
                new MechanicId("ordered"), attempt, List.of("h", "h", "o"));

        ordered.handle(attempt, new OrderedInputMechanic.ControlEntered("h"));
        assertEquals(1, ordered.cursor());
        assertEquals(MechanicOutcomeType.NO_CHANGE,
                ordered.handle(attempt, new OrderedInputMechanic.ControlEntered("o")).type());
        assertEquals(0, ordered.cursor());
        ordered.handle(attempt, new OrderedInputMechanic.ControlEntered("h"));
        ordered.handle(attempt, new OrderedInputMechanic.ControlEntered("h"));
        assertEquals(MechanicOutcomeType.COMPLETED,
                ordered.handle(attempt, new OrderedInputMechanic.ControlEntered("o")).type());
    }

    @Test
    void choiceAndToggleWrongInputsSoftResetWithoutFailingTheRoom() {
        RoomAttemptId attempt = attempt(0);
        ChoiceInputMechanic choice = new ChoiceInputMechanic(new MechanicId("choice"), attempt, "zeus");
        assertEquals(MechanicOutcomeType.NO_CHANGE,
                choice.handle(attempt, new ChoiceInputMechanic.ChoiceSelected("hades")).type());
        assertEquals(MechanicStatus.ACTIVE, choice.status());
        assertEquals(MechanicOutcomeType.COMPLETED,
                choice.handle(attempt, new ChoiceInputMechanic.ChoiceSelected("zeus")).type());

        ToggleInputMechanic toggle = new ToggleInputMechanic(
                new MechanicId("toggle"), attempt, List.of("a", "b", "c"), List.of("a", "c"), 2);
        toggle.handle(attempt, new ToggleInputMechanic.TogglePressed("a"));
        assertEquals(MechanicOutcomeType.NO_CHANGE,
                toggle.handle(attempt, new ToggleInputMechanic.SubmitPressed()).type());
        assertTrue(toggle.selected().isEmpty());
        toggle.handle(attempt, new ToggleInputMechanic.TogglePressed("a"));
        toggle.handle(attempt, new ToggleInputMechanic.TogglePressed("c"));
        assertEquals(MechanicOutcomeType.COMPLETED,
                toggle.handle(attempt, new ToggleInputMechanic.SubmitPressed()).type());
    }

    @Test
    void operatorLockRefreshesExpiresAndReleasesByOwner() {
        InputOperatorLock lock = new InputOperatorLock();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-06T00:00:00Z");

        assertTrue(lock.acquire(first, now, Duration.ofSeconds(10)));
        assertFalse(lock.acquire(second, now.plusSeconds(9), Duration.ofSeconds(10)));
        assertTrue(lock.acquire(first, now.plusSeconds(9), Duration.ofSeconds(10)));
        assertFalse(lock.acquire(second, now.plusSeconds(18), Duration.ofSeconds(10)));
        assertTrue(lock.acquire(second, now.plusSeconds(19), Duration.ofSeconds(10)));
        lock.release(first);
        assertEquals(second, lock.owner(now.plusSeconds(20)).orElseThrow());
        lock.release(second);
        assertTrue(lock.owner(now.plusSeconds(20)).isEmpty());
    }

    private static RoomAttemptId attempt(long revision) {
        return attempt(SessionId.random(), revision);
    }

    private static RoomAttemptId attempt(SessionId sessionId, long revision) {
        return new RoomAttemptId(sessionId, 1, revision);
    }
}
