package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.port.WorldInstanceHandle;
import dev.mcpuzzle.paper.isolation.PaperPlayerIsolationAdapter;
import dev.mcpuzzle.paper.world.GeneratedVoidWorldInstanceAdapter;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Enforces the safety order: restore players first, then unload/delete their instance world. */
public final class InstanceCleanupService {
    private final PaperPlayerIsolationAdapter isolation;
    private final GeneratedVoidWorldInstanceAdapter worlds;

    public InstanceCleanupService(PaperPlayerIsolationAdapter isolation, GeneratedVoidWorldInstanceAdapter worlds) {
        this.isolation = Objects.requireNonNull(isolation, "isolation");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
    }

    public CompletionStage<Void> restoreThenRelease(SessionId sessionId, PartyRoster roster, WorldInstanceHandle handle) {
        return isolation.restoreToLobby(sessionId, roster).thenCompose(ignored -> releaseOnly(sessionId, handle));
    }

    public CompletionStage<Void> releaseOnly(SessionId sessionId, WorldInstanceHandle handle) {
        return handle == null ? CompletableFuture.completedFuture(null) : worlds.releaseWithRetry(sessionId, handle);
    }
}
