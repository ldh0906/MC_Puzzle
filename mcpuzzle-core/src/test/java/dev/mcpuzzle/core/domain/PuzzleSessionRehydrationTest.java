package dev.mcpuzzle.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuzzleSessionRehydrationTest {
    private static final Instant START = Instant.parse("2026-08-05T00:00:00Z");
    private static final Instant CAPTURED = START.plusSeconds(60);

    @Test
    void suspendedSaveRoundTripsEveryDurableField() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PuzzleSession original = PuzzleSession.create(
                SessionId.random(),
                "fifty-rooms",
                new MapVersion("1.2.3"),
                Party.of(leader, List.of(leader, member)),
                3
        );
        succeed(original.queue(leader));
        succeed(original.beginProvisioning());
        succeed(original.activate(START));
        succeed(original.unlockHint(1, START.plusSeconds(2)));
        succeed(original.completeCurrentRoom(START.plusSeconds(10)));
        succeed(original.unlockHint(2, START.plusSeconds(12)));
        succeed(original.failCurrentRoom(START.plusSeconds(15)));
        succeed(original.memberDisconnected(member, START.plusSeconds(20)));

        PuzzleSessionSnapshot persisted = original.snapshot(CAPTURED);
        PuzzleSession restored = PuzzleSession.rehydrate(persisted);

        assertEquals(persisted, restored.snapshot(CAPTURED));
        assertEquals(SessionState.SUSPENDED, restored.state());
        assertEquals(SuspendReason.MEMBER_DISCONNECTED, restored.lastSuspendReason().orElseThrow());
        assertEquals(2, restored.hintProgress().totalUnlocked());
        assertEquals(1, restored.metricsAt(CAPTURED).failures());
        assertEquals(2, restored.currentRoom());
        assertEquals(2, restored.roomAttemptRevision());
        assertTrue(restored.party().rosterLocked());
    }

    @Test
    void rejectsSuspendedSnapshotWithoutReason() {
        PuzzleSessionSnapshot valid = validSuspendedSnapshot();
        PuzzleSessionSnapshot invalid = copy(
                valid,
                valid.metrics(),
                valid.hintProgress(),
                valid.checkpoint(),
                Optional.empty(),
                valid.activeSince(),
                valid.lastActivityAt()
        );

        assertThrows(IllegalArgumentException.class, () -> PuzzleSession.rehydrate(invalid));
    }

    @Test
    void rejectsHintMetricMismatchAndInvalidCheckpoint() {
        PuzzleSessionSnapshot valid = validSuspendedSnapshot();
        PuzzleSessionSnapshot hintMismatch = copy(
                valid,
                valid.metrics().recordHint(),
                valid.hintProgress(),
                valid.checkpoint(),
                valid.lastSuspendReason(),
                valid.activeSince(),
                valid.lastActivityAt()
        );
        assertThrows(IllegalArgumentException.class, () -> PuzzleSession.rehydrate(hintMismatch));

        Checkpoint futureRoom = new Checkpoint(2, 3, START.plusSeconds(1));
        PuzzleSessionSnapshot badCheckpoint = copy(
                valid,
                valid.metrics(),
                valid.hintProgress(),
                Optional.of(futureRoom),
                valid.lastSuspendReason(),
                valid.activeSince(),
                valid.lastActivityAt()
        );
        assertThrows(IllegalArgumentException.class, () -> PuzzleSession.rehydrate(badCheckpoint));
    }

    @Test
    void activeSnapshotReanchorsClockWithoutDoubleCountingOnRestore() {
        UUID leader = UUID.randomUUID();
        PuzzleSession session = PuzzleSession.create(
                SessionId.random(),
                "maze",
                new MapVersion("1"),
                Party.create(leader),
                2
        );
        succeed(session.queue(leader));
        succeed(session.beginProvisioning());
        succeed(session.activate(START));

        PuzzleSession restored = PuzzleSession.rehydrate(session.snapshot(START.plusSeconds(10)));
        assertEquals(
                SessionFailure.LAST_ACTIVITY_IN_FUTURE,
                restored.recordActivity(START.plusSeconds(9)).failure().orElseThrow()
        );
        succeed(restored.requestSuspend(leader, START.plusSeconds(15)));

        assertEquals(15, restored.metricsAt(CAPTURED).activePlayTime().toSeconds());
    }

    private static PuzzleSessionSnapshot validSuspendedSnapshot() {
        UUID leader = UUID.randomUUID();
        PuzzleSession session = PuzzleSession.create(
                SessionId.random(),
                "maze",
                new MapVersion("1"),
                Party.create(leader),
                3
        );
        succeed(session.queue(leader));
        succeed(session.beginProvisioning());
        succeed(session.activate(START));
        succeed(session.requestSuspend(leader, START.plusSeconds(5)));
        return session.snapshot(CAPTURED);
    }

    private static PuzzleSessionSnapshot copy(
            PuzzleSessionSnapshot source,
            RunMetrics metrics,
            HintProgress hints,
            Optional<Checkpoint> checkpoint,
            Optional<SuspendReason> suspendReason,
            Optional<Instant> activeSince,
            Optional<Instant> lastActivityAt
    ) {
        return new PuzzleSessionSnapshot(
                source.id(),
                source.mazeId(),
                source.mapVersion(),
                source.state(),
                source.roster(),
                source.rosterLocked(),
                source.currentRoom(),
                source.roomCount(),
                source.roomAttemptRevision(),
                metrics,
                hints,
                checkpoint,
                activeSince,
                lastActivityAt,
                suspendReason,
                source.abandonReason(),
                source.capturedAt()
        );
    }

    private static void succeed(OperationResult<SessionFailure> result) {
        assertTrue(result.succeeded(), () -> "Unexpected failure: " + result.failure());
    }
}
