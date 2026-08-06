package dev.mcpuzzle.paper.world;

import dev.mcpuzzle.core.domain.SessionId;

import java.nio.file.Path;
import java.time.Instant;

public record OrphanedWorldInstance(SessionId sessionId, String worldName, Path directory, Instant createdAt) {
}
