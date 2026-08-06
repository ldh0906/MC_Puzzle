package dev.mcpuzzle.core.mechanic;

import dev.mcpuzzle.core.domain.SessionId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomRuntimeCoordinatorTest {
    @Test
    void cornerObjectivesAndDestructibleTargetComposeUnderAllMechanics() {
        RoomAttemptId attempt = new RoomAttemptId(SessionId.random(), 3, 0);
        MechanicId cornersId = new MechanicId("corners");
        MechanicId targetId = new MechanicId("target");
        CornerObjectivesMechanic corners = new CornerObjectivesMechanic(
                cornersId,
                attempt,
                List.of("nw", "ne", "sw", "se")
        );
        DestructibleTargetMechanic target = new DestructibleTargetMechanic(
                targetId,
                attempt,
                "objective-target"
        );
        RoomRuntimeCoordinator coordinator = new RoomRuntimeCoordinator(
                attempt,
                RoomCompletionPolicy.ALL_MECHANICS,
                List.of(corners, target)
        );

        for (String corner : List.of("nw", "ne", "sw", "se")) {
            assertEquals(RoomRuntimeOutcomeType.MECHANIC_PROGRESSED, coordinator.handle(
                    cornersId,
                    attempt,
                    new CornerObjectivesMechanic.ObjectiveActivated(corner)
            ).type());
        }
        assertEquals(RoomRuntimeStatus.ACTIVE, coordinator.status());
        assertEquals(RoomRuntimeOutcomeType.ROOM_COMPLETED, coordinator.handle(
                targetId,
                attempt,
                new DestructibleTargetMechanic.TargetDestroyed("objective-target")
        ).type());
        assertEquals(RoomRuntimeOutcomeType.ALREADY_TERMINAL, coordinator.handle(
                targetId,
                attempt,
                new DestructibleTargetMechanic.TargetDestroyed("objective-target")
        ).type());
    }

    @Test
    void mechanicFailureEmitsWholeRoomFailureExactlyOnceThenResetClearsAll() {
        RoomAttemptId first = new RoomAttemptId(SessionId.random(), 8, 0);
        RoomAttemptId second = new RoomAttemptId(first.sessionId(), first.room(), 1);
        MechanicId keypadId = new MechanicId("keypad");
        MechanicId padId = new MechanicId("pad");
        NumericKeypadMechanic keypad = new NumericKeypadMechanic(keypadId, first, "37");
        LatchingPressurePadsMechanic pad = new LatchingPressurePadsMechanic(
                padId,
                first,
                List.of("only")
        );
        RoomRuntimeCoordinator coordinator = new RoomRuntimeCoordinator(
                first,
                RoomCompletionPolicy.ALL_MECHANICS,
                List.of(keypad, pad)
        );

        coordinator.handle(keypadId, first, new NumericKeypadMechanic.DigitPressed(9));
        assertEquals(RoomRuntimeOutcomeType.ROOM_FAILED, coordinator.handle(
                keypadId,
                first,
                new NumericKeypadMechanic.SubmitPressed()
        ).type());
        assertEquals(RoomRuntimeOutcomeType.ALREADY_TERMINAL, coordinator.handle(
                padId,
                first,
                new LatchingPressurePadsMechanic.PadPressed("only")
        ).type());

        assertEquals(RoomRuntimeOutcomeType.RESET, coordinator.reset(second).type());
        assertEquals("", keypad.buffer());
        assertEquals(RoomRuntimeOutcomeType.IGNORED_STALE_ATTEMPT, coordinator.handle(
                padId,
                first,
                new LatchingPressurePadsMechanic.PadPressed("only")
        ).type());
        coordinator.handle(padId, second, new LatchingPressurePadsMechanic.PadPressed("only"));
        coordinator.handle(keypadId, second, new NumericKeypadMechanic.DigitPressed(3));
        coordinator.handle(keypadId, second, new NumericKeypadMechanic.DigitPressed(7));
        assertEquals(RoomRuntimeOutcomeType.ROOM_COMPLETED, coordinator.handle(
                keypadId,
                second,
                new NumericKeypadMechanic.SubmitPressed()
        ).type());
    }

    @Test
    void concurrentTerminalEventsStillEmitRoomCompletionExactlyOnce() throws Exception {
        RoomAttemptId attempt = new RoomAttemptId(SessionId.random(), 1, 0);
        MechanicId padId = new MechanicId("pads");
        LatchingPressurePadsMechanic pads = new LatchingPressurePadsMechanic(
                padId,
                attempt,
                List.of("a", "b")
        );
        RoomRuntimeCoordinator coordinator = new RoomRuntimeCoordinator(
                attempt,
                RoomCompletionPolicy.ALL_MECHANICS,
                List.of(pads)
        );
        coordinator.handle(padId, attempt, new LatchingPressurePadsMechanic.PadPressed("a"));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<RoomRuntimeOutcome> left = executor.submit(() -> {
                start.await();
                return coordinator.handle(
                        padId,
                        attempt,
                        new LatchingPressurePadsMechanic.PadPressed("b")
                );
            });
            Future<RoomRuntimeOutcome> right = executor.submit(() -> {
                start.await();
                return coordinator.handle(
                        padId,
                        attempt,
                        new LatchingPressurePadsMechanic.PadPressed("b")
                );
            });
            start.countDown();
            List<RoomRuntimeOutcomeType> results = List.of(left.get().type(), right.get().type());
            assertEquals(1, results.stream()
                    .filter(type -> type == RoomRuntimeOutcomeType.ROOM_COMPLETED)
                    .count());
            assertEquals(1, results.stream()
                    .filter(type -> type == RoomRuntimeOutcomeType.ALREADY_TERMINAL)
                    .count());
        } finally {
            executor.shutdownNow();
        }
    }
}
