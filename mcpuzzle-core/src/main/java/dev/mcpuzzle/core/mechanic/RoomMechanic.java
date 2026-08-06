package dev.mcpuzzle.core.mechanic;

public interface RoomMechanic {
    MechanicId id();

    MechanicType type();

    RoomAttemptId attempt();

    MechanicStatus status();

    MechanicOutcome handle(RoomAttemptId eventAttempt, MechanicEvent event);

    MechanicOutcome reset(RoomAttemptId newAttempt);
}
