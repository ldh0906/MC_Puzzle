package dev.mcpuzzle.core.domain;

public enum SessionState {
    WAITING,
    QUEUED,
    PROVISIONING,
    ACTIVE,
    SUSPENDED,
    COMPLETED,
    ABANDONED,
    CLEANUP
}
