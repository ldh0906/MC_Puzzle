package dev.mcpuzzle.paper.adapter.persistence;

import dev.mcpuzzle.core.domain.AbandonReason;
import dev.mcpuzzle.core.domain.Checkpoint;
import dev.mcpuzzle.core.domain.CompletedRun;
import dev.mcpuzzle.core.domain.HintProgress;
import dev.mcpuzzle.core.domain.LeaderboardEntry;
import dev.mcpuzzle.core.domain.LeaderboardQuery;
import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.PuzzleSessionSnapshot;
import dev.mcpuzzle.core.domain.RunMetrics;
import dev.mcpuzzle.core.domain.SaveGame;
import dev.mcpuzzle.core.domain.SaveSlot;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.domain.SessionState;
import dev.mcpuzzle.core.domain.StartupRecoveryReport;
import dev.mcpuzzle.core.domain.SuspendReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLitePersistenceTest {
    private static final String MAZE = "starcraft-50";
    private static final MapVersion VERSION = new MapVersion("1.0.0");
    private static final Instant BASE = Instant.parse("2026-08-05T12:34:56.123456789Z");

    @TempDir
    Path temporaryDirectory;

    private SQLitePersistence persistence;

    @AfterEach
    void closePersistence() {
        if (persistence != null) {
            persistence.close();
        }
    }

    @Test
    void roundTripsCompleteSuspendedSnapshotWithoutLosingPrecisionOrRosterOrder() {
        persistence = open("roundtrip.db");
        UUID leader = uuid(1);
        List<UUID> members = List.of(leader, uuid(2), uuid(3));
        PuzzleSessionSnapshot snapshot = suspendedSnapshot(
                sessionId(10),
                members,
                2,
                Duration.ofSeconds(83, 987_654_321),
                4,
                Map.of(1, Set.of(1, 3)),
                BASE
        );

        join(persistence.save(snapshot));

        PuzzleSessionSnapshot restored = join(persistence.findById(snapshot.id())).orElseThrow();
        assertEquals(snapshot, restored);
        assertEquals(members, restored.roster().members());
        assertEquals(leader, restored.roster().leaderId());
        assertTrue(restored.rosterLocked());
        assertEquals(Optional.of(SuspendReason.MEMBER_DISCONNECTED), restored.lastSuspendReason());
    }

    @Test
    void roundTripsActiveClockAnchorsAndAbandonReason() {
        persistence = open("runtime-fields.db");
        PuzzleSessionSnapshot active = stateSnapshot(sessionId(11), SessionState.ACTIVE);
        PuzzleSessionSnapshot abandoned = abandonedSnapshot(sessionId(12));

        join(persistence.save(active));
        join(persistence.save(abandoned));

        assertEquals(active, join(persistence.findById(active.id())).orElseThrow());
        assertEquals(abandoned, join(persistence.findById(abandoned.id())).orElseThrow());
        assertEquals(Optional.of(AbandonReason.LEADER_REQUEST),
                join(persistence.findById(abandoned.id())).orElseThrow().abandonReason());
    }

    @Test
    void saveSlotsAreScopedToOwnerAndMazeLimitedToThreeAndAtomicallyReplaceable() {
        persistence = open("slots.db");
        UUID owner = uuid(1);
        UUID otherOwner = uuid(9);
        List<UUID> roster = List.of(owner, uuid(2));

        for (int slot = 1; slot <= 3; slot++) {
            join(persistence.upsert(save(slot, owner, MAZE, roster, sessionId(100 + slot), BASE.plusSeconds(slot))));
        }
        join(persistence.upsert(save(1, otherOwner, MAZE, List.of(otherOwner), sessionId(200), BASE)));
        join(persistence.upsert(save(1, owner, "other-maze", roster, sessionId(201), BASE)));

        List<SaveGame> visible = join(persistence.listVisible(owner, MAZE, BASE));
        assertEquals(List.of(1, 2, 3), visible.stream().map(game -> game.slot().number()).toList());
        assertEquals(1, join(persistence.listVisible(otherOwner, MAZE, BASE)).size());
        assertThrows(IllegalArgumentException.class, () -> persistence.find(owner, MAZE, 4, BASE));

        SaveGame replacement = save(2, owner, MAZE, roster, sessionId(999), BASE.plusSeconds(50));
        join(persistence.upsert(replacement));
        SaveGame restored = join(persistence.find(owner, MAZE, 2, BASE.plusSeconds(51))).orElseThrow();
        assertEquals(replacement, restored);
        assertEquals(3, join(persistence.listVisible(owner, MAZE, BASE.plusSeconds(51))).size());
    }

    @Test
    void exactOriginalRosterAndLeaderSurviveOwnershipTransfer() {
        persistence = open("transfer.db");
        UUID leader = uuid(1);
        UUID second = uuid(2);
        UUID third = uuid(3);
        List<UUID> roster = List.of(leader, third, second);
        SaveGame original = save(1, leader, MAZE, roster, sessionId(300), BASE);
        join(persistence.upsert(original));

        assertTrue(join(persistence.transferOwnership(leader, MAZE, 1, second)));
        assertTrue(join(persistence.find(leader, MAZE, 1, BASE)).isEmpty());
        SaveGame transferred = join(persistence.find(second, MAZE, 1, BASE)).orElseThrow();

        assertEquals(second, transferred.slot().ownerId());
        assertEquals(leader, transferred.snapshot().roster().leaderId());
        assertEquals(roster, transferred.snapshot().roster().members());
        assertEquals(original.snapshot(), transferred.snapshot());
    }

    @Test
    void conflictingOwnershipTransferRollsBackParentAndChildRows() {
        persistence = open("rollback.db");
        UUID leader = uuid(1);
        UUID member = uuid(2);
        List<UUID> roster = List.of(leader, member);
        SaveGame source = save(1, leader, MAZE, roster, sessionId(400), BASE);
        SaveGame occupiedTarget = save(1, member, MAZE, roster, sessionId(401), BASE.plusSeconds(1));
        join(persistence.upsert(source));
        join(persistence.upsert(occupiedTarget));

        assertThrows(CompletionException.class,
                () -> join(persistence.transferOwnership(leader, MAZE, 1, member)));

        assertEquals(source, join(persistence.find(leader, MAZE, 1, BASE)).orElseThrow());
        assertEquals(occupiedTarget, join(persistence.find(member, MAZE, 1, BASE)).orElseThrow());
    }

    @Test
    void expiryIsExclusiveAndPurgeUsesTheExactSevenDayBoundary() {
        persistence = open("expiry.db");
        UUID owner = uuid(1);
        SaveGame save = save(1, owner, MAZE, List.of(owner), sessionId(500), BASE);
        join(persistence.upsert(save));
        Instant expiry = BASE.plus(Duration.ofDays(7));

        assertTrue(join(persistence.find(owner, MAZE, 1, expiry.minusNanos(1))).isPresent());
        assertTrue(join(persistence.find(owner, MAZE, 1, expiry)).isEmpty());
        assertEquals(0, join(persistence.purgeExpired(expiry.minusNanos(1))));
        assertEquals(1, join(persistence.purgeExpired(expiry)));
        assertTrue(join(persistence.find(owner, MAZE, 1, BASE)).isEmpty());
    }

    @Test
    void purgeIncompatibleVersionsDeletesOnlyObsoleteProgressForKnownMazes() {
        persistence = open("version-purge.db");
        UUID owner = uuid(1);
        MapVersion obsoleteVersion = new MapVersion("0.9.0");
        SaveGame compatible = save(1, owner, MAZE, List.of(owner), sessionId(510), BASE);
        SaveGame incompatible = withVersion(
                save(2, owner, MAZE, List.of(owner), sessionId(511), BASE), obsoleteVersion);
        SaveGame unknownMaze = withVersion(
                save(1, owner, "retired-maze", List.of(owner), sessionId(512), BASE), obsoleteVersion);
        for (SaveGame game : List.of(compatible, incompatible, unknownMaze)) {
            join(persistence.save(game.snapshot()));
            join(persistence.upsert(game));
        }
        CompletedRun oldHistory = run(513, MAZE, obsoleteVersion,
                new PartyRoster(owner, List.of(owner)), Duration.ofSeconds(5), BASE, 0, 0);
        join(persistence.record(oldHistory));

        assertEquals(1, join(persistence.purgeIncompatibleVersions(Map.of(MAZE, VERSION))));

        assertTrue(join(persistence.find(owner, MAZE, 1, BASE)).isPresent());
        assertTrue(join(persistence.find(owner, MAZE, 2, BASE)).isEmpty());
        assertTrue(join(persistence.find(owner, "retired-maze", 1, BASE)).isPresent());
        assertTrue(join(persistence.findById(compatible.snapshot().id())).isPresent());
        assertTrue(join(persistence.findById(incompatible.snapshot().id())).isEmpty());
        assertTrue(join(persistence.findById(unknownMaze.snapshot().id())).isPresent());
        assertEquals(oldHistory, join(persistence.find(oldHistory.runId())).orElseThrow());
    }

    @Test
    void restartRecoveryDiscardsTransientAndInterruptedSessionsButRetainsSuspended() {
        persistence = open("recovery.db");
        PuzzleSessionSnapshot waiting = stateSnapshot(sessionId(601), SessionState.WAITING);
        PuzzleSessionSnapshot queued = stateSnapshot(sessionId(602), SessionState.QUEUED);
        PuzzleSessionSnapshot provisioning = stateSnapshot(sessionId(603), SessionState.PROVISIONING);
        PuzzleSessionSnapshot active = stateSnapshot(sessionId(604), SessionState.ACTIVE);
        PuzzleSessionSnapshot suspended = stateSnapshot(sessionId(605), SessionState.SUSPENDED);
        for (PuzzleSessionSnapshot snapshot : List.of(waiting, queued, provisioning, active, suspended)) {
            join(persistence.save(snapshot));
        }

        StartupRecoveryReport report = join(persistence.recoverAfterRestart());

        assertEquals(List.of(waiting.id(), queued.id()), report.discardedTransientAdmissions());
        assertEquals(List.of(provisioning.id(), active.id()), report.discardedInterruptedRuns());
        assertEquals(List.of(suspended.id()), report.retainedSuspended());
        for (PuzzleSessionSnapshot discarded : List.of(waiting, queued, provisioning, active)) {
            assertTrue(join(persistence.findById(discarded.id())).isEmpty());
        }
        assertEquals(suspended, join(persistence.findById(suspended.id())).orElseThrow());
    }

    @Test
    void completedRunLeaderboardOrdersByActiveTimeThenStableCompletionAndIdAndFilters() {
        persistence = open("history.db");
        UUID leader = uuid(1);
        PartyRoster solo = new PartyRoster(leader, List.of(leader));
        PartyRoster duo = new PartyRoster(leader, List.of(leader, uuid(2)));
        CompletedRun slow = run(703, MAZE, VERSION, solo, Duration.ofSeconds(20), BASE, 1, 0);
        CompletedRun earlyTie = run(702, MAZE, VERSION, solo, Duration.ofSeconds(10), BASE, 2, 1);
        CompletedRun lateTie = run(701, MAZE, VERSION, solo, Duration.ofSeconds(10), BASE.plusNanos(1), 0, 0);
        CompletedRun otherMap = run(704, "other", VERSION, solo, Duration.ofSeconds(1), BASE, 0, 0);
        CompletedRun otherVersion = run(705, MAZE, new MapVersion("2"), solo, Duration.ofSeconds(1), BASE, 0, 0);
        CompletedRun otherSize = run(706, MAZE, VERSION, duo, Duration.ofSeconds(1), BASE, 0, 0);
        for (CompletedRun run : List.of(slow, earlyTie, lateTie, otherMap, otherVersion, otherSize)) {
            join(persistence.record(run));
        }

        List<LeaderboardEntry> board = join(persistence.leaderboard(
                new LeaderboardQuery(MAZE, VERSION, 1, 10)));

        assertEquals(List.of(earlyTie.runId(), lateTie.runId(), slow.runId()),
                board.stream().map(entry -> entry.run().runId()).toList());
        assertEquals(List.of(1, 2, 3), board.stream().map(LeaderboardEntry::rank).toList());
        assertEquals(earlyTie, join(persistence.find(earlyTie.runId())).orElseThrow());
        assertEquals(2, board.get(0).run().metrics().failures());
        assertEquals(1, board.get(0).run().metrics().hintsUsed());
    }

    @Test
    void concurrentCallersAreSerializedWithoutLostWrites() {
        persistence = open("concurrency.db");
        UUID commonMember = uuid(1);
        List<CompletableFuture<Void>> writes = new ArrayList<>();
        for (int index = 0; index < 64; index++) {
            PuzzleSessionSnapshot snapshot = suspendedSnapshot(
                    sessionId(800 + index),
                    List.of(commonMember),
                    1,
                    Duration.ofNanos(index),
                    0,
                    Map.of(),
                    BASE.plusNanos(index)
            );
            writes.add(persistence.save(snapshot).toCompletableFuture());
        }
        CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();

        Collection<PuzzleSessionSnapshot> stored = join(persistence.findByMember(commonMember));
        assertEquals(64, stored.size());
        assertEquals(64, stored.stream().map(PuzzleSessionSnapshot::id).distinct().count());
    }

    @Test
    void closeIsDeterministicAndRejectsFurtherWork() {
        persistence = open("close.db");
        SQLitePersistence closing = persistence;
        closing.close();
        persistence = null;

        CompletionException exception = assertThrows(CompletionException.class,
                () -> join(closing.findById(sessionId(900))));
        assertNotNull(exception.getCause());
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    void closeDrainsAlreadyAcceptedWritesBeforeClosingTheConnection() {
        Path database = temporaryDirectory.resolve("drain-close.db");
        persistence = join(SQLitePersistence.open(database));
        PuzzleSessionSnapshot snapshot = stateSnapshot(sessionId(901), SessionState.SUSPENDED);

        persistence.save(snapshot);
        persistence.close();
        persistence = join(SQLitePersistence.open(database));

        assertEquals(snapshot, join(persistence.findById(snapshot.id())).orElseThrow());
    }

    @Test
    void concurrentSubmitAndCloseNeverRunAnAcceptedOperationAfterConnectionClose() {
        persistence = open("close-race.db");
        SQLitePersistence closing = persistence;
        ExecutorService callers = Executors.newFixedThreadPool(33);
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<CompletionStage<Void>>> calls = new ArrayList<>();
        try {
            for (int index = 0; index < 32; index++) {
                int suffix = 1_000 + index;
                calls.add(CompletableFuture.supplyAsync(() -> {
                    await(start);
                    return closing.save(stateSnapshot(sessionId(suffix), SessionState.SUSPENDED));
                }, callers));
            }
            CompletableFuture<CompletionStage<Void>> closeCall = CompletableFuture.supplyAsync(() -> {
                await(start);
                return closing.closeAsync();
            }, callers);
            start.countDown();

            CompletionStage<Void> firstClose = closeCall.join();
            CompletionStage<Void> repeatedClose = closing.closeAsync();
            assertSame(firstClose, repeatedClose);

            for (CompletableFuture<CompletionStage<Void>> call : calls) {
                try {
                    join(call.join());
                } catch (CompletionException failure) {
                    assertTrue(failure.getCause() instanceof IllegalStateException
                                    || failure.getCause() instanceof RejectedExecutionException,
                            () -> "Unexpected post-close database failure: " + failure.getCause());
                }
            }
            join(firstClose);
            assertSame(firstClose, closing.closeAsync());
            persistence = null;
        } finally {
            callers.shutdownNow();
        }
    }

    private SQLitePersistence open(String filename) {
        return join(SQLitePersistence.open(temporaryDirectory.resolve(filename)));
    }

    private static SaveGame save(
            int number,
            UUID owner,
            String maze,
            List<UUID> roster,
            SessionId id,
            Instant capturedAt
    ) {
        PuzzleSessionSnapshot snapshot = suspendedSnapshot(
                id,
                roster,
                2,
                Duration.ofSeconds(12, 345),
                1,
                Map.of(1, Set.of(1)),
                capturedAt
        );
        if (!snapshot.mazeId().equals(maze)) {
            snapshot = new PuzzleSessionSnapshot(
                    snapshot.id(), maze, snapshot.mapVersion(), snapshot.state(), snapshot.roster(),
                    snapshot.rosterLocked(), snapshot.currentRoom(), snapshot.roomCount(),
                    snapshot.roomAttemptRevision(), snapshot.metrics(), snapshot.hintProgress(),
                    snapshot.checkpoint(), snapshot.activeSince(), snapshot.lastActivityAt(),
                    snapshot.lastSuspendReason(), snapshot.abandonReason(), snapshot.capturedAt()
            );
        }
        SaveSlot slot = new SaveSlot(
                number,
                owner,
                maze,
                snapshot.mapVersion(),
                snapshot.roster(),
                snapshot.checkpoint().orElseThrow(),
                capturedAt
        );
        return new SaveGame(slot, snapshot);
    }

    private static SaveGame withVersion(SaveGame source, MapVersion version) {
        PuzzleSessionSnapshot original = source.snapshot();
        PuzzleSessionSnapshot snapshot = new PuzzleSessionSnapshot(
                original.id(), original.mazeId(), version, original.state(), original.roster(),
                original.rosterLocked(), original.currentRoom(), original.roomCount(),
                original.roomAttemptRevision(), original.metrics(), original.hintProgress(),
                original.checkpoint(), original.activeSince(), original.lastActivityAt(),
                original.lastSuspendReason(), original.abandonReason(), original.capturedAt()
        );
        SaveSlot originalSlot = source.slot();
        SaveSlot slot = new SaveSlot(
                originalSlot.number(), originalSlot.ownerId(), originalSlot.mazeId(), version,
                originalSlot.roster(), originalSlot.checkpoint(), originalSlot.updatedAt()
        );
        return new SaveGame(slot, snapshot);
    }

    private static PuzzleSessionSnapshot suspendedSnapshot(
            SessionId id,
            List<UUID> members,
            int currentRoom,
            Duration activeTime,
            int failures,
            Map<Integer, Set<Integer>> hints,
            Instant capturedAt
    ) {
        UUID leader = members.get(0);
        HintProgress progress = new HintProgress(hints);
        Optional<Checkpoint> checkpoint = currentRoom == 1
                ? Optional.empty()
                : Optional.of(new Checkpoint(currentRoom - 1, currentRoom, capturedAt.minusSeconds(1)));
        return new PuzzleSessionSnapshot(
                id,
                MAZE,
                VERSION,
                SessionState.SUSPENDED,
                new PartyRoster(leader, members),
                true,
                currentRoom,
                5,
                7,
                new RunMetrics(activeTime, failures, progress.totalUnlocked()),
                progress,
                checkpoint,
                Optional.empty(),
                Optional.empty(),
                Optional.of(SuspendReason.MEMBER_DISCONNECTED),
                Optional.empty(),
                capturedAt
        );
    }

    private static PuzzleSessionSnapshot stateSnapshot(SessionId id, SessionState state) {
        UUID leader = uuid(1);
        boolean locked = state != SessionState.WAITING;
        Optional<Instant> activeSince = state == SessionState.ACTIVE
                ? Optional.of(BASE.minusSeconds(2)) : Optional.empty();
        Optional<Instant> lastActivity = state == SessionState.ACTIVE
                ? Optional.of(BASE.minusSeconds(1)) : Optional.empty();
        Optional<SuspendReason> suspendReason = state == SessionState.SUSPENDED
                ? Optional.of(SuspendReason.PARTY_IDLE) : Optional.empty();
        return new PuzzleSessionSnapshot(
                id,
                MAZE,
                VERSION,
                state,
                new PartyRoster(leader, List.of(leader)),
                locked,
                1,
                5,
                0,
                RunMetrics.empty(),
                HintProgress.empty(),
                Optional.empty(),
                activeSince,
                lastActivity,
                suspendReason,
                Optional.empty(),
                BASE
        );
    }

    private static PuzzleSessionSnapshot abandonedSnapshot(SessionId id) {
        UUID leader = uuid(1);
        return new PuzzleSessionSnapshot(
                id,
                MAZE,
                VERSION,
                SessionState.ABANDONED,
                new PartyRoster(leader, List.of(leader)),
                true,
                1,
                5,
                0,
                RunMetrics.empty(),
                HintProgress.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(AbandonReason.LEADER_REQUEST),
                BASE
        );
    }

    private static CompletedRun run(
            int id,
            String maze,
            MapVersion version,
            PartyRoster roster,
            Duration time,
            Instant completedAt,
            int failures,
            int hints
    ) {
        return new CompletedRun(
                sessionId(id),
                maze,
                version,
                roster,
                new RunMetrics(time, failures, hints),
                completedAt
        );
    }

    private static SessionId sessionId(int suffix) {
        return new SessionId(new UUID(0, suffix));
    }

    private static UUID uuid(int suffix) {
        return new UUID(1, suffix);
    }

    private static <T> T join(java.util.concurrent.CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating test callers", interrupted);
        }
    }
}
