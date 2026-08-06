package dev.mcpuzzle.core.mechanic;

import java.util.Objects;

/**
 * Monitor-protected base state machine. Subclasses mutate their local state only
 * from onEvent/onReset, which are always invoked while this monitor is held.
 */
public abstract class AbstractRoomMechanic implements RoomMechanic {
    private final MechanicId id;
    private final MechanicType type;
    private RoomAttemptId attempt;
    private MechanicStatus status = MechanicStatus.ACTIVE;

    protected AbstractRoomMechanic(MechanicId id, MechanicType type, RoomAttemptId attempt) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.attempt = Objects.requireNonNull(attempt, "attempt");
    }

    @Override
    public final MechanicId id() {
        return id;
    }

    @Override
    public final MechanicType type() {
        return type;
    }

    @Override
    public final synchronized RoomAttemptId attempt() {
        return attempt;
    }

    @Override
    public final synchronized MechanicStatus status() {
        return status;
    }

    @Override
    public final synchronized MechanicOutcome handle(
            RoomAttemptId eventAttempt,
            MechanicEvent event
    ) {
        Objects.requireNonNull(eventAttempt, "eventAttempt");
        Objects.requireNonNull(event, "event");
        if (!attempt.equals(eventAttempt)) {
            return outcome(MechanicOutcomeType.IGNORED_STALE_ATTEMPT, "mechanic.stale_attempt");
        }
        if (status != MechanicStatus.ACTIVE) {
            return outcome(MechanicOutcomeType.ALREADY_TERMINAL, "mechanic.already_terminal");
        }
        return Objects.requireNonNull(onEvent(event), "mechanic outcome");
    }

    @Override
    public final synchronized MechanicOutcome reset(RoomAttemptId newAttempt) {
        Objects.requireNonNull(newAttempt, "newAttempt");
        if (!newAttempt.isNewerAttemptOf(attempt)) {
            return outcome(MechanicOutcomeType.IGNORED_STALE_ATTEMPT, "mechanic.stale_reset");
        }
        attempt = newAttempt;
        status = MechanicStatus.ACTIVE;
        onReset();
        return outcome(MechanicOutcomeType.RESET, "mechanic.reset");
    }

    protected abstract MechanicOutcome onEvent(MechanicEvent event);

    protected abstract void onReset();

    protected final MechanicOutcome noChange(String detailKey) {
        return outcome(MechanicOutcomeType.NO_CHANGE, detailKey);
    }

    protected final MechanicOutcome progressed(String detailKey) {
        return outcome(MechanicOutcomeType.PROGRESSED, detailKey);
    }

    protected final MechanicOutcome complete(String detailKey) {
        status = MechanicStatus.COMPLETED;
        return outcome(MechanicOutcomeType.COMPLETED, detailKey);
    }

    protected final MechanicOutcome fail(String detailKey) {
        status = MechanicStatus.FAILED;
        return outcome(MechanicOutcomeType.FAILED, detailKey);
    }

    private MechanicOutcome outcome(MechanicOutcomeType type, String detailKey) {
        return new MechanicOutcome(type, status, detailKey);
    }
}
