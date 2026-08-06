package dev.mcpuzzle.core.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Aggregate root for one party's maze run. All state changes pass through the
 * methods on this class so invalid transitions cannot be persisted accidentally.
 */
public final class PuzzleSession {
    public static final Duration AFK_TIMEOUT = Duration.ofMinutes(10);

    private final SessionId id;
    private final String mazeId;
    private final MapVersion mapVersion;
    private final int roomCount;

    private Party party;
    private SessionState state;
    private int currentRoom;
    private long roomAttemptRevision;
    private RunMetrics metrics;
    private HintProgress hintProgress;
    private Checkpoint checkpoint;
    private Instant activeSince;
    private Instant lastActivityAt;
    private SuspendReason lastSuspendReason;
    private AbandonReason abandonReason;

    private PuzzleSession(
            SessionId id,
            String mazeId,
            MapVersion mapVersion,
            Party party,
            int roomCount
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.mazeId = requireText(mazeId, "mazeId");
        this.mapVersion = Objects.requireNonNull(mapVersion, "mapVersion");
        this.party = Objects.requireNonNull(party, "party");
        if (roomCount < 1) {
            throw new IllegalArgumentException("A maze must contain at least one room");
        }
        this.roomCount = roomCount;
        this.state = SessionState.WAITING;
        this.currentRoom = 1;
        this.metrics = RunMetrics.empty();
        this.hintProgress = HintProgress.empty();
    }

    public static PuzzleSession create(
            SessionId id,
            String mazeId,
            MapVersion mapVersion,
            Party party,
            int roomCount
    ) {
        if (party.rosterLocked()) {
            throw new IllegalArgumentException("A new waiting session requires an unlocked party");
        }
        return new PuzzleSession(id, mazeId, mapVersion, party, roomCount);
    }

    /** Restores an aggregate only after validating all persisted state invariants. */
    public static PuzzleSession rehydrate(PuzzleSessionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        validateSnapshot(snapshot);

        Party restoredParty = Party.of(snapshot.roster().leaderId(), snapshot.roster().members());
        if (snapshot.rosterLocked()) {
            restoredParty = restoredParty.lockRoster();
        }
        PuzzleSession session = new PuzzleSession(
                snapshot.id(),
                snapshot.mazeId(),
                snapshot.mapVersion(),
                restoredParty,
                snapshot.roomCount()
        );
        session.state = snapshot.state();
        session.currentRoom = snapshot.currentRoom();
        session.roomAttemptRevision = snapshot.roomAttemptRevision();
        session.metrics = snapshot.metrics();
        session.hintProgress = snapshot.hintProgress();
        session.checkpoint = snapshot.checkpoint().orElse(null);
        session.activeSince = snapshot.activeSince().orElse(null);
        session.lastActivityAt = snapshot.lastActivityAt().orElse(null);
        session.lastSuspendReason = snapshot.lastSuspendReason().orElse(null);
        session.abandonReason = snapshot.abandonReason().orElse(null);
        return session;
    }

    public synchronized OperationResult<SessionFailure> addPartyMember(UUID actorId, UUID memberId) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(memberId, "memberId");
        if (state != SessionState.WAITING) {
            return OperationResult.failure(SessionFailure.ROSTER_LOCKED);
        }
        if (!party.leaderId().equals(actorId)) {
            return OperationResult.failure(SessionFailure.NOT_LEADER);
        }
        PartyChange change = party.addMember(memberId);
        if (!change.succeeded()) {
            return OperationResult.failure(mapPartyFailure(change.failure().orElseThrow()));
        }
        party = change.party();
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> removePartyMember(UUID actorId, UUID memberId) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(memberId, "memberId");
        if (state != SessionState.WAITING) {
            return OperationResult.failure(SessionFailure.ROSTER_LOCKED);
        }
        if (!party.leaderId().equals(actorId)) {
            return OperationResult.failure(SessionFailure.NOT_LEADER);
        }
        PartyChange change = party.removeMember(memberId);
        if (!change.succeeded()) {
            return OperationResult.failure(mapPartyFailure(change.failure().orElseThrow()));
        }
        party = change.party();
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> queue(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        if (state != SessionState.WAITING) {
            return OperationResult.failure(SessionFailure.INVALID_STATE);
        }
        if (!party.leaderId().equals(actorId)) {
            return OperationResult.failure(SessionFailure.NOT_LEADER);
        }
        party = party.lockRoster();
        state = SessionState.QUEUED;
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> queueForResume(
            UUID actorId,
            PartyRoster presentRoster
    ) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(presentRoster, "presentRoster");
        if (state != SessionState.SUSPENDED) {
            return OperationResult.failure(SessionFailure.INVALID_STATE);
        }
        if (!party.leaderId().equals(actorId)) {
            return OperationResult.failure(SessionFailure.NOT_LEADER);
        }
        if (!sameRoster(party.toRoster(), presentRoster)) {
            return OperationResult.failure(SessionFailure.ROSTER_MISMATCH);
        }
        state = SessionState.QUEUED;
        lastSuspendReason = null;
        abandonReason = null;
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> beginProvisioning() {
        if (state != SessionState.QUEUED) {
            return OperationResult.failure(SessionFailure.INVALID_STATE);
        }
        state = SessionState.PROVISIONING;
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> activate(Instant at) {
        Objects.requireNonNull(at, "at");
        if (state != SessionState.PROVISIONING) {
            return OperationResult.failure(SessionFailure.INVALID_STATE);
        }
        state = SessionState.ACTIVE;
        activeSince = at;
        lastActivityAt = at;
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> recordActivity(Instant at) {
        Objects.requireNonNull(at, "at");
        if (state != SessionState.ACTIVE) {
            return OperationResult.failure(SessionFailure.INVALID_STATE);
        }
        if (at.isBefore(activeSince) || at.isBefore(lastActivityAt)) {
            return OperationResult.failure(SessionFailure.LAST_ACTIVITY_IN_FUTURE);
        }
        lastActivityAt = at;
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> requestSuspend(UUID actorId, Instant at) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(at, "at");
        if (!party.leaderId().equals(actorId)) {
            return OperationResult.failure(SessionFailure.NOT_LEADER);
        }
        if (state != SessionState.ACTIVE) {
            return OperationResult.failure(SessionFailure.INVALID_STATE);
        }
        suspend(at, SuspendReason.LEADER_REQUEST);
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> memberDisconnected(UUID memberId, Instant at) {
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(at, "at");
        if (!party.contains(memberId)) {
            return OperationResult.failure(SessionFailure.NOT_MEMBER);
        }
        if (state == SessionState.SUSPENDED) {
            return OperationResult.success();
        }
        if (state == SessionState.QUEUED || state == SessionState.PROVISIONING) {
            return cancelAdmission(memberId);
        }
        if (state != SessionState.ACTIVE) {
            return OperationResult.failure(SessionFailure.INVALID_STATE);
        }
        suspend(at, SuspendReason.MEMBER_DISCONNECTED);
        return OperationResult.success();
    }

    /**
     * Cancels a queued/provisioning admission without producing a resumable save.
     * This is used when the exact roster is no longer simultaneously available.
     */
    public synchronized OperationResult<SessionFailure> cancelAdmission(UUID unavailableMemberId) {
        Objects.requireNonNull(unavailableMemberId, "unavailableMemberId");
        if (!party.contains(unavailableMemberId)) {
            return OperationResult.failure(SessionFailure.NOT_MEMBER);
        }
        if (state != SessionState.QUEUED && state != SessionState.PROVISIONING) {
            return OperationResult.failure(SessionFailure.INVALID_STATE);
        }
        abandonReason = state == SessionState.QUEUED
                ? AbandonReason.QUEUE_MEMBER_UNAVAILABLE
                : AbandonReason.PROVISIONING_MEMBER_UNAVAILABLE;
        state = SessionState.ABANDONED;
        lastSuspendReason = null;
        activeSince = null;
        lastActivityAt = null;
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> suspendIfAfk(Instant at) {
        Objects.requireNonNull(at, "at");
        if (state != SessionState.ACTIVE) {
            return OperationResult.failure(SessionFailure.INVALID_STATE);
        }
        if (at.isBefore(lastActivityAt)) {
            return OperationResult.failure(SessionFailure.LAST_ACTIVITY_IN_FUTURE);
        }
        if (Duration.between(lastActivityAt, at).compareTo(AFK_TIMEOUT) < 0) {
            return OperationResult.failure(SessionFailure.AFK_THRESHOLD_NOT_REACHED);
        }
        suspend(at, SuspendReason.PARTY_IDLE);
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> abandon(UUID actorId, Instant at) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(at, "at");
        if (!party.leaderId().equals(actorId)) {
            return OperationResult.failure(SessionFailure.NOT_LEADER);
        }
        if (state == SessionState.COMPLETED
                || state == SessionState.ABANDONED
                || state == SessionState.CLEANUP) {
            return OperationResult.failure(SessionFailure.INVALID_STATE);
        }
        commitActiveTime(at);
        state = SessionState.ABANDONED;
        lastSuspendReason = null;
        abandonReason = AbandonReason.LEADER_REQUEST;
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> failCurrentRoom(Instant at) {
        Objects.requireNonNull(at, "at");
        OperationResult<SessionFailure> activity = recordActivity(at);
        if (!activity.succeeded()) {
            return activity;
        }
        metrics = metrics.recordFailure();
        roomAttemptRevision++;
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> unlockHint(int tier, Instant at) {
        Objects.requireNonNull(at, "at");
        boolean alreadyUnlocked = hintProgress.isUnlocked(currentRoom, tier);
        OperationResult<SessionFailure> activity = recordActivity(at);
        if (!activity.succeeded()) {
            return activity;
        }
        if (!alreadyUnlocked) {
            hintProgress = hintProgress.unlock(currentRoom, tier);
            metrics = metrics.recordHint();
        }
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> useHint(int tier, Instant at) {
        return unlockHint(tier, at);
    }

    public synchronized OperationResult<SessionFailure> completeCurrentRoom(Instant at) {
        Objects.requireNonNull(at, "at");
        OperationResult<SessionFailure> activity = recordActivity(at);
        if (!activity.succeeded()) {
            return activity;
        }
        boolean finalRoom = currentRoom == roomCount;
        checkpoint = finalRoom ? null : new Checkpoint(currentRoom, currentRoom + 1, at);
        currentRoom++;
        roomAttemptRevision++;
        if (finalRoom) {
            commitActiveTime(at);
            state = SessionState.COMPLETED;
        }
        return OperationResult.success();
    }

    public synchronized OperationResult<SessionFailure> beginCleanup() {
        if (state != SessionState.COMPLETED && state != SessionState.ABANDONED) {
            return OperationResult.failure(SessionFailure.INVALID_STATE);
        }
        state = SessionState.CLEANUP;
        return OperationResult.success();
    }

    public synchronized PuzzleSessionSnapshot snapshot(Instant at) {
        Objects.requireNonNull(at, "at");
        RunMetrics snapshotMetrics = metricsAt(at);
        Optional<Instant> snapshotActiveSince = state == SessionState.ACTIVE
                ? Optional.of(at)
                : Optional.empty();
        PuzzleSessionSnapshot snapshot = new PuzzleSessionSnapshot(
                id,
                mazeId,
                mapVersion,
                state,
                party.toRoster(),
                party.rosterLocked(),
                currentRoom,
                roomCount,
                roomAttemptRevision,
                snapshotMetrics,
                hintProgress,
                Optional.ofNullable(checkpoint),
                snapshotActiveSince,
                Optional.ofNullable(lastActivityAt),
                Optional.ofNullable(lastSuspendReason),
                Optional.ofNullable(abandonReason),
                at
        );
        validateSnapshot(snapshot);
        return snapshot;
    }

    public synchronized SessionId id() {
        return id;
    }

    public synchronized String mazeId() {
        return mazeId;
    }

    public synchronized MapVersion mapVersion() {
        return mapVersion;
    }

    public synchronized Party party() {
        return party;
    }

    public synchronized SessionState state() {
        return state;
    }

    public synchronized int currentRoom() {
        return currentRoom;
    }

    public synchronized int roomCount() {
        return roomCount;
    }

    public synchronized long roomAttemptRevision() {
        return roomAttemptRevision;
    }

    public synchronized Optional<Checkpoint> checkpoint() {
        return Optional.ofNullable(checkpoint);
    }

    public synchronized Optional<SuspendReason> lastSuspendReason() {
        return Optional.ofNullable(lastSuspendReason);
    }

    public synchronized Optional<AbandonReason> abandonReason() {
        return Optional.ofNullable(abandonReason);
    }

    public synchronized HintProgress hintProgress() {
        return hintProgress;
    }

    public synchronized RunMetrics metricsAt(Instant at) {
        Objects.requireNonNull(at, "at");
        if (state != SessionState.ACTIVE) {
            return metrics;
        }
        if (at.isBefore(activeSince)) {
            throw new IllegalArgumentException("Metric time cannot be before activation");
        }
        if (lastActivityAt != null && at.isBefore(lastActivityAt)) {
            throw new IllegalArgumentException("Metric time cannot be before the last activity");
        }
        return metrics.addActiveTime(Duration.between(activeSince, at));
    }

    public synchronized boolean shouldDiscardAfterRestart() {
        return state == SessionState.ACTIVE || state == SessionState.PROVISIONING;
    }

    private void suspend(Instant at, SuspendReason reason) {
        commitActiveTime(at);
        state = SessionState.SUSPENDED;
        lastSuspendReason = reason;
        abandonReason = null;
    }

    private void commitActiveTime(Instant at) {
        if (activeSince == null) {
            return;
        }
        if (at.isBefore(activeSince)) {
            throw new IllegalArgumentException("Transition time cannot be before activation");
        }
        if (lastActivityAt != null && at.isBefore(lastActivityAt)) {
            throw new IllegalArgumentException("Transition time cannot be before the last activity");
        }
        metrics = metrics.addActiveTime(Duration.between(activeSince, at));
        activeSince = null;
        lastActivityAt = null;
    }

    private static boolean sameRoster(PartyRoster expected, PartyRoster actual) {
        return expected.leaderId().equals(actual.leaderId())
                && new HashSet<>(expected.members()).equals(new HashSet<>(actual.members()));
    }

    private static SessionFailure mapPartyFailure(PartyFailure failure) {
        return switch (failure) {
            case ROSTER_LOCKED -> SessionFailure.ROSTER_LOCKED;
            case PARTY_FULL -> SessionFailure.PARTY_FULL;
            case MEMBER_ALREADY_PRESENT -> SessionFailure.MEMBER_ALREADY_PRESENT;
            case MEMBER_NOT_PRESENT -> SessionFailure.NOT_MEMBER;
            case LEADER_CANNOT_LEAVE -> SessionFailure.LEADER_CANNOT_LEAVE;
        };
    }

    private static void validateSnapshot(PuzzleSessionSnapshot snapshot) {
        requireText(snapshot.mazeId(), "mazeId");
        if (snapshot.roomCount() < 1) {
            throw new IllegalArgumentException("Snapshot room count must be positive");
        }
        if (snapshot.roomAttemptRevision() < 0) {
            throw new IllegalArgumentException("Snapshot room attempt revision must not be negative");
        }

        boolean finishedPosition = snapshot.currentRoom() == snapshot.roomCount() + 1;
        if (snapshot.currentRoom() < 1 || snapshot.currentRoom() > snapshot.roomCount() + 1) {
            throw new IllegalArgumentException("Snapshot current room is outside the maze");
        }
        if (snapshot.state() == SessionState.COMPLETED && !finishedPosition) {
            throw new IllegalArgumentException("Completed snapshot must be beyond the final room");
        }
        if (snapshot.state() != SessionState.COMPLETED
                && snapshot.state() != SessionState.CLEANUP
                && finishedPosition) {
            throw new IllegalArgumentException("Only completed/cleanup snapshots may be beyond the final room");
        }

        if (snapshot.state() == SessionState.WAITING && snapshot.rosterLocked()) {
            throw new IllegalArgumentException("Waiting snapshot cannot have a locked roster");
        }
        if (requiresLockedRoster(snapshot.state()) && !snapshot.rosterLocked()) {
            throw new IllegalArgumentException("Snapshot state requires a locked roster");
        }

        snapshot.hintProgress().validateAgainstRoomCount(snapshot.roomCount());
        if (snapshot.hintProgress().unlockedByRoom().keySet().stream()
                .anyMatch(room -> room > Math.min(snapshot.currentRoom(), snapshot.roomCount()))) {
            throw new IllegalArgumentException("Hint progress references a future room");
        }
        if (snapshot.metrics().hintsUsed() != snapshot.hintProgress().totalUnlocked()) {
            throw new IllegalArgumentException("Hint metrics must match unique unlocked tiers");
        }

        validateCheckpoint(snapshot, finishedPosition);
        validateRuntimeTimes(snapshot);
        validateReasons(snapshot);

        if (snapshot.state() == SessionState.WAITING) {
            if (snapshot.currentRoom() != 1
                    || snapshot.roomAttemptRevision() != 0
                    || !snapshot.metrics().equals(RunMetrics.empty())
                    || snapshot.checkpoint().isPresent()
                    || snapshot.hintProgress().totalUnlocked() != 0) {
                throw new IllegalArgumentException("Waiting snapshot must be pristine");
            }
        }
    }

    private static void validateCheckpoint(PuzzleSessionSnapshot snapshot, boolean finishedPosition) {
        if (finishedPosition && snapshot.checkpoint().isPresent()) {
            throw new IllegalArgumentException("Final completion cannot retain a next-room checkpoint");
        }
        if (snapshot.currentRoom() == 1 && snapshot.checkpoint().isPresent()) {
            throw new IllegalArgumentException("First room cannot have a completed-room checkpoint");
        }
        if (!finishedPosition && snapshot.currentRoom() > 1 && snapshot.checkpoint().isEmpty()) {
            throw new IllegalArgumentException("Advanced room snapshot requires its checkpoint");
        }
        snapshot.checkpoint().ifPresent(value -> {
            if (value.completedRoom() >= snapshot.roomCount()
                    || value.nextRoom() != snapshot.currentRoom()) {
                throw new IllegalArgumentException("Checkpoint does not match the current room");
            }
            if (value.savedAt().isAfter(snapshot.capturedAt())) {
                throw new IllegalArgumentException("Checkpoint cannot be newer than its snapshot");
            }
        });
    }

    private static void validateRuntimeTimes(PuzzleSessionSnapshot snapshot) {
        boolean active = snapshot.state() == SessionState.ACTIVE;
        if (active != snapshot.activeSince().isPresent()
                || active != snapshot.lastActivityAt().isPresent()) {
            throw new IllegalArgumentException("Only active snapshots require runtime clock anchors");
        }
        snapshot.activeSince().ifPresent(value -> {
            if (value.isAfter(snapshot.capturedAt())) {
                throw new IllegalArgumentException("Active clock anchor cannot be in the future");
            }
        });
        snapshot.lastActivityAt().ifPresent(value -> {
            if (value.isAfter(snapshot.capturedAt())) {
                throw new IllegalArgumentException("Last activity cannot be in the future");
            }
        });
    }

    private static void validateReasons(PuzzleSessionSnapshot snapshot) {
        if (snapshot.state() == SessionState.SUSPENDED) {
            if (snapshot.lastSuspendReason().isEmpty() || snapshot.abandonReason().isPresent()) {
                throw new IllegalArgumentException("Suspended snapshot requires only a suspend reason");
            }
            return;
        }
        if (snapshot.state() == SessionState.ABANDONED) {
            if (snapshot.abandonReason().isEmpty() || snapshot.lastSuspendReason().isPresent()) {
                throw new IllegalArgumentException("Abandoned snapshot requires only an abandon reason");
            }
            return;
        }
        if (snapshot.lastSuspendReason().isPresent()) {
            throw new IllegalArgumentException("Non-suspended snapshot cannot retain a suspend reason");
        }
        if (snapshot.state() != SessionState.CLEANUP && snapshot.abandonReason().isPresent()) {
            throw new IllegalArgumentException("Non-abandoned snapshot cannot retain an abandon reason");
        }
    }

    private static boolean requiresLockedRoster(SessionState state) {
        return state == SessionState.QUEUED
                || state == SessionState.PROVISIONING
                || state == SessionState.ACTIVE
                || state == SessionState.SUSPENDED
                || state == SessionState.COMPLETED;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return trimmed;
    }
}
