package dev.mcpuzzle.paper.isolation;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public record SnapshotCorruption(Optional<UUID> playerId, Path quarantinedFile, String reason) {
}
