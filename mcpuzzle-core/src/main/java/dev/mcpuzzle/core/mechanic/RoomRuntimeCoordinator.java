package dev.mcpuzzle.core.mechanic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Thread-safe, exactly-once room result coordinator. */
public final class RoomRuntimeCoordinator {
    private final RoomCompletionPolicy policy;
    private final Map<MechanicId, RoomMechanic> mechanics;
    private RoomAttemptId attempt;
    private RoomRuntimeStatus status = RoomRuntimeStatus.ACTIVE;

    public RoomRuntimeCoordinator(
            RoomAttemptId attempt,
            RoomCompletionPolicy policy,
            List<? extends RoomMechanic> mechanics
    ) {
        this.attempt = Objects.requireNonNull(attempt, "attempt");
        this.policy = Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(mechanics, "mechanics");
        if (mechanics.isEmpty()) {
            throw new IllegalArgumentException("A room requires at least one mechanic");
        }
        Map<MechanicId, RoomMechanic> indexed = new LinkedHashMap<>();
        for (RoomMechanic mechanic : mechanics) {
            Objects.requireNonNull(mechanic, "mechanic");
            if (!attempt.equals(mechanic.attempt())) {
                throw new IllegalArgumentException("Every mechanic must belong to the room attempt");
            }
            if (indexed.putIfAbsent(mechanic.id(), mechanic) != null) {
                throw new IllegalArgumentException("Duplicate mechanic id: " + mechanic.id().value());
            }
        }
        this.mechanics = Map.copyOf(indexed);
    }

    public synchronized RoomRuntimeOutcome handle(
            MechanicId mechanicId,
            RoomAttemptId eventAttempt,
            MechanicEvent event
    ) {
        Objects.requireNonNull(mechanicId, "mechanicId");
        Objects.requireNonNull(eventAttempt, "eventAttempt");
        Objects.requireNonNull(event, "event");
        if (!attempt.equals(eventAttempt)) {
            return outcome(
                    RoomRuntimeOutcomeType.IGNORED_STALE_ATTEMPT,
                    Optional.of(mechanicId),
                    "room.stale_attempt"
            );
        }
        if (status != RoomRuntimeStatus.ACTIVE) {
            return outcome(
                    RoomRuntimeOutcomeType.ALREADY_TERMINAL,
                    Optional.of(mechanicId),
                    "room.already_terminal"
            );
        }
        RoomMechanic mechanic = mechanics.get(mechanicId);
        if (mechanic == null) {
            return outcome(
                    RoomRuntimeOutcomeType.UNKNOWN_MECHANIC,
                    Optional.of(mechanicId),
                    "room.unknown_mechanic"
            );
        }
        MechanicOutcome mechanicOutcome = mechanic.handle(eventAttempt, event);
        if (mechanicOutcome.status() == MechanicStatus.FAILED) {
            status = RoomRuntimeStatus.FAILED;
            return outcome(
                    RoomRuntimeOutcomeType.ROOM_FAILED,
                    Optional.of(mechanicId),
                    mechanicOutcome.detailKey()
            );
        }
        if (policy == RoomCompletionPolicy.ALL_MECHANICS
                && mechanics.values().stream().allMatch(value -> value.status() == MechanicStatus.COMPLETED)) {
            status = RoomRuntimeStatus.COMPLETED;
            return outcome(
                    RoomRuntimeOutcomeType.ROOM_COMPLETED,
                    Optional.of(mechanicId),
                    "room.all_mechanics_completed"
            );
        }
        RoomRuntimeOutcomeType type = mechanicOutcome.type() == MechanicOutcomeType.PROGRESSED
                || mechanicOutcome.type() == MechanicOutcomeType.COMPLETED
                ? RoomRuntimeOutcomeType.MECHANIC_PROGRESSED
                : RoomRuntimeOutcomeType.MECHANIC_NO_CHANGE;
        return outcome(type, Optional.of(mechanicId), mechanicOutcome.detailKey());
    }

    public synchronized RoomRuntimeOutcome reset(RoomAttemptId newAttempt) {
        Objects.requireNonNull(newAttempt, "newAttempt");
        if (!newAttempt.isNewerAttemptOf(attempt)) {
            return outcome(
                    RoomRuntimeOutcomeType.IGNORED_STALE_ATTEMPT,
                    Optional.empty(),
                    "room.stale_reset"
            );
        }
        if (mechanics.values().stream().anyMatch(mechanic -> !mechanic.attempt().equals(attempt))) {
            throw new IllegalStateException("A mechanic was reset outside its room coordinator");
        }
        for (RoomMechanic mechanic : mechanics.values()) {
            MechanicOutcome reset = mechanic.reset(newAttempt);
            if (reset.type() != MechanicOutcomeType.RESET) {
                throw new IllegalStateException("Mechanic rejected a coordinator-approved reset");
            }
        }
        attempt = newAttempt;
        status = RoomRuntimeStatus.ACTIVE;
        return outcome(RoomRuntimeOutcomeType.RESET, Optional.empty(), "room.reset");
    }

    public synchronized RoomRuntimeStatus status() {
        return status;
    }

    public synchronized RoomAttemptId attempt() {
        return attempt;
    }

    private RoomRuntimeOutcome outcome(
            RoomRuntimeOutcomeType type,
            Optional<MechanicId> mechanicId,
            String detailKey
    ) {
        return new RoomRuntimeOutcome(type, status, mechanicId, detailKey);
    }
}
