package dev.mcpuzzle.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuzzleSessionTest {
    private static final Instant START = Instant.parse("2026-08-05T00:00:00Z");

    @Test
    void followsExplicitHappyPathAndRejectsInvalidTransitions() {
        Fixture fixture = fixture(2, 3);
        PuzzleSession session = fixture.session();

        assertEquals(SessionState.WAITING, session.state());
        assertFailure(session.beginProvisioning(), SessionFailure.INVALID_STATE);
        assertSuccess(session.queue(fixture.leader()));
        assertEquals(SessionState.QUEUED, session.state());
        assertSuccess(session.beginProvisioning());
        assertEquals(SessionState.PROVISIONING, session.state());
        assertSuccess(session.activate(START));
        assertEquals(SessionState.ACTIVE, session.state());
        assertFailure(session.activate(START), SessionFailure.INVALID_STATE);

        assertSuccess(session.completeCurrentRoom(START.plusSeconds(10)));
        assertSuccess(session.completeCurrentRoom(START.plusSeconds(20)));
        assertSuccess(session.completeCurrentRoom(START.plusSeconds(30)));
        assertEquals(SessionState.COMPLETED, session.state());
        assertTrue(session.checkpoint().isEmpty());
        assertSuccess(session.beginCleanup());
        assertEquals(SessionState.CLEANUP, session.state());
    }

    @Test
    void queueLocksRosterAndOnlyLeaderCanControlPartyOrSuspend() {
        Fixture fixture = fixture(2, 5);
        PuzzleSession session = fixture.session();
        UUID member = fixture.members().get(1);

        assertFailure(session.addPartyMember(member, UUID.randomUUID()), SessionFailure.NOT_LEADER);
        assertFailure(session.queue(member), SessionFailure.NOT_LEADER);
        assertSuccess(session.queue(fixture.leader()));
        assertTrue(session.party().rosterLocked());
        assertFailure(
                session.addPartyMember(fixture.leader(), UUID.randomUUID()),
                SessionFailure.ROSTER_LOCKED
        );
        assertSuccess(session.beginProvisioning());
        assertSuccess(session.activate(START));
        assertFailure(session.requestSuspend(member, START.plusSeconds(30)), SessionFailure.NOT_LEADER);
        assertSuccess(session.requestSuspend(fixture.leader(), START.plusSeconds(30)));
        assertEquals(SessionState.SUSPENDED, session.state());
        assertEquals(SuspendReason.LEADER_REQUEST, session.lastSuspendReason().orElseThrow());
    }

    @Test
    void anyMemberDisconnectSuspendsWholeActiveSession() {
        Fixture fixture = fixture(4, 5);
        PuzzleSession session = activate(fixture);

        assertSuccess(session.memberDisconnected(fixture.members().get(3), START.plusSeconds(45)));

        assertEquals(SessionState.SUSPENDED, session.state());
        assertEquals(SuspendReason.MEMBER_DISCONNECTED, session.lastSuspendReason().orElseThrow());
        assertEquals(Duration.ofSeconds(45), session.metricsAt(START.plusSeconds(500)).activePlayTime());
    }

    @Test
    void queuedOrProvisioningMemberLossCancelsAdmissionWithoutCreatingSave() {
        Fixture queuedFixture = fixture(2, 5);
        assertSuccess(queuedFixture.session().queue(queuedFixture.leader()));
        assertSuccess(queuedFixture.session().memberDisconnected(
                queuedFixture.members().get(1),
                START
        ));
        assertEquals(SessionState.ABANDONED, queuedFixture.session().state());
        assertEquals(
                AbandonReason.QUEUE_MEMBER_UNAVAILABLE,
                queuedFixture.session().abandonReason().orElseThrow()
        );
        assertTrue(queuedFixture.session().lastSuspendReason().isEmpty());
        assertFalse(queuedFixture.session().shouldDiscardAfterRestart());

        Fixture provisioningFixture = fixture(2, 5);
        assertSuccess(provisioningFixture.session().queue(provisioningFixture.leader()));
        assertSuccess(provisioningFixture.session().beginProvisioning());
        assertSuccess(provisioningFixture.session().cancelAdmission(
                provisioningFixture.members().get(1)
        ));
        assertEquals(SessionState.ABANDONED, provisioningFixture.session().state());
        assertEquals(
                AbandonReason.PROVISIONING_MEMBER_UNAVAILABLE,
                provisioningFixture.session().abandonReason().orElseThrow()
        );
        assertTrue(provisioningFixture.session().checkpoint().isEmpty());
    }

    @Test
    void afkSuspendsAtExactlyTenMinutesButNotBefore() {
        Fixture fixture = fixture(1, 5);
        PuzzleSession session = activate(fixture);

        assertFailure(
                session.suspendIfAfk(START.plus(PuzzleSession.AFK_TIMEOUT).minusMillis(1)),
                SessionFailure.AFK_THRESHOLD_NOT_REACHED
        );
        assertSuccess(session.suspendIfAfk(START.plus(PuzzleSession.AFK_TIMEOUT)));

        assertEquals(SessionState.SUSPENDED, session.state());
        assertEquals(SuspendReason.PARTY_IDLE, session.lastSuspendReason().orElseThrow());
        assertEquals(PuzzleSession.AFK_TIMEOUT, session.metricsAt(START.plusSeconds(900)).activePlayTime());
    }

    @Test
    void activityRestartsAfkWindow() {
        Fixture fixture = fixture(1, 5);
        PuzzleSession session = activate(fixture);

        assertSuccess(session.recordActivity(START.plus(Duration.ofMinutes(9))));
        assertFailure(
                session.suspendIfAfk(START.plus(Duration.ofMinutes(10))),
                SessionFailure.AFK_THRESHOLD_NOT_REACHED
        );
        assertSuccess(session.suspendIfAfk(START.plus(Duration.ofMinutes(19))));
    }

    @Test
    void activePlayTimePausesWhileSuspendedAndResumesWithoutCountingProvisioning() {
        Fixture fixture = fixture(2, 2);
        PuzzleSession session = activate(fixture);

        assertSuccess(session.requestSuspend(fixture.leader(), START.plus(Duration.ofMinutes(3))));
        assertEquals(
                Duration.ofMinutes(3),
                session.metricsAt(START.plus(Duration.ofHours(1))).activePlayTime()
        );

        assertSuccess(session.queueForResume(fixture.leader(), session.party().toRoster()));
        assertSuccess(session.beginProvisioning());
        assertSuccess(session.activate(START.plus(Duration.ofMinutes(10))));
        assertSuccess(session.completeCurrentRoom(START.plus(Duration.ofMinutes(12))));
        assertSuccess(session.requestSuspend(fixture.leader(), START.plus(Duration.ofMinutes(12))));

        assertEquals(Duration.ofMinutes(5), session.metricsAt(START.plus(Duration.ofHours(2))).activePlayTime());
    }

    @Test
    void resumeRequiresExactOriginalRoster() {
        Fixture fixture = fixture(2, 3);
        PuzzleSession session = activate(fixture);
        assertSuccess(session.requestSuspend(fixture.leader(), START.plusSeconds(1)));

        PartyRoster wrong = new PartyRoster(fixture.leader(), List.of(fixture.leader()));
        assertFailure(
                session.queueForResume(fixture.leader(), wrong),
                SessionFailure.ROSTER_MISMATCH
        );
        assertEquals(SessionState.SUSPENDED, session.state());
    }

    @Test
    void roomFailureResetsAttemptAndRoomCompletionCreatesCheckpoint() {
        Fixture fixture = fixture(1, 5);
        PuzzleSession session = activate(fixture);

        assertSuccess(session.failCurrentRoom(START.plusSeconds(5)));
        assertEquals(1, session.currentRoom());
        assertEquals(1, session.roomAttemptRevision());
        assertEquals(1, session.metricsAt(START.plusSeconds(5)).failures());
        assertTrue(session.checkpoint().isEmpty());

        assertSuccess(session.completeCurrentRoom(START.plusSeconds(8)));
        assertEquals(2, session.currentRoom());
        assertEquals(2, session.roomAttemptRevision());
        Checkpoint checkpoint = session.checkpoint().orElseThrow();
        assertEquals(1, checkpoint.completedRoom());
        assertEquals(2, checkpoint.nextRoom());
        assertEquals(START.plusSeconds(8), checkpoint.savedAt());
    }

    @Test
    void hintMetricsCountUniqueTiersPerRoomInsteadOfRepeatedViews() {
        Fixture fixture = fixture(1, 2);
        PuzzleSession session = activate(fixture);

        assertSuccess(session.unlockHint(1, START.plusSeconds(1)));
        assertSuccess(session.unlockHint(1, START.plusSeconds(2)));
        assertSuccess(session.unlockHint(2, START.plusSeconds(3)));
        assertEquals(2, session.metricsAt(START.plusSeconds(3)).hintsUsed());
        assertEquals(2, session.hintProgress().totalUnlocked());
        assertTrue(session.hintProgress().isUnlocked(1, 1));
        assertTrue(session.hintProgress().isUnlocked(1, 2));

        assertSuccess(session.completeCurrentRoom(START.plusSeconds(4)));
        assertSuccess(session.unlockHint(1, START.plusSeconds(5)));
        assertEquals(3, session.metricsAt(START.plusSeconds(5)).hintsUsed());
        assertTrue(session.hintProgress().isUnlocked(2, 1));
    }

    @Test
    void restartDiscardsOnlyActiveAndProvisioningProgress() {
        Fixture provisioningFixture = fixture(1, 2);
        assertSuccess(provisioningFixture.session().queue(provisioningFixture.leader()));
        assertSuccess(provisioningFixture.session().beginProvisioning());
        assertTrue(provisioningFixture.session().shouldDiscardAfterRestart());

        Fixture activeFixture = fixture(1, 2);
        PuzzleSession active = activate(activeFixture);
        assertTrue(active.shouldDiscardAfterRestart());
        assertSuccess(active.requestSuspend(activeFixture.leader(), START.plusSeconds(3)));
        assertFalse(active.shouldDiscardAfterRestart());
        assertEquals(SessionState.SUSPENDED, active.state());
    }

    @Test
    void onlyLeaderCanAbandon() {
        Fixture fixture = fixture(2, 2);
        PuzzleSession session = activate(fixture);

        assertFailure(
                session.abandon(fixture.members().get(1), START.plusSeconds(2)),
                SessionFailure.NOT_LEADER
        );
        assertSuccess(session.abandon(fixture.leader(), START.plusSeconds(2)));
        assertEquals(SessionState.ABANDONED, session.state());
    }

    private static PuzzleSession activate(Fixture fixture) {
        assertSuccess(fixture.session().queue(fixture.leader()));
        assertSuccess(fixture.session().beginProvisioning());
        assertSuccess(fixture.session().activate(START));
        return fixture.session();
    }

    private static Fixture fixture(int partySize, int roomCount) {
        UUID leader = UUID.randomUUID();
        Party party = Party.create(leader);
        for (int index = 1; index < partySize; index++) {
            party = party.addMember(UUID.randomUUID()).party();
        }
        PuzzleSession session = PuzzleSession.create(
                SessionId.random(),
                "fifty-rooms",
                new MapVersion("1.0.0"),
                party,
                roomCount
        );
        return new Fixture(session, leader, party.members());
    }

    private static void assertSuccess(OperationResult<SessionFailure> result) {
        assertTrue(result.succeeded(), () -> "Expected success but got " + result.failure());
    }

    private static void assertFailure(
            OperationResult<SessionFailure> result,
            SessionFailure expected
    ) {
        assertFalse(result.succeeded());
        assertEquals(expected, result.failure().orElseThrow());
    }

    private record Fixture(PuzzleSession session, UUID leader, List<UUID> members) {
    }
}
