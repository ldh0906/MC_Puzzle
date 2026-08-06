package dev.mcpuzzle.core.mechanic;

public enum RoomRuntimeOutcomeType {
    MECHANIC_NO_CHANGE,
    MECHANIC_PROGRESSED,
    ROOM_COMPLETED,
    ROOM_FAILED,
    RESET,
    UNKNOWN_MECHANIC,
    IGNORED_STALE_ATTEMPT,
    ALREADY_TERMINAL
}
