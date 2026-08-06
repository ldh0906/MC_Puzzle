package dev.mcpuzzle.core.port;

import java.util.Objects;

public record WorldInstanceHandle(String instanceName) {
    public WorldInstanceHandle {
        Objects.requireNonNull(instanceName, "instanceName");
        instanceName = instanceName.trim();
        if (instanceName.isEmpty()) {
            throw new IllegalArgumentException("Instance name must not be blank");
        }
    }
}
