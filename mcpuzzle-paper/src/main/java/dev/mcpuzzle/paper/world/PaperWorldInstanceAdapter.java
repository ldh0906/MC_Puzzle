package dev.mcpuzzle.paper.world;

import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.port.WorldInstanceHandle;
import dev.mcpuzzle.core.port.WorldInstancePort;
import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PaperWorldInstanceAdapter implements WorldInstancePort {
    private final WorldInstanceFileStore files;
    private final WorldLifecycleGateway worlds;
    private final InstanceRuntimeRegistry registry;
    private final Executor fileExecutor;
    private final ScheduledExecutorService retryScheduler;

    public PaperWorldInstanceAdapter(
            Path templatesRoot,
            Path worldContainer,
            WorldLifecycleGateway worlds,
            InstanceRuntimeRegistry registry,
            Executor fileExecutor,
            ScheduledExecutorService retryScheduler
    ) {
        this(templatesRoot, worldContainer, worlds, registry, fileExecutor, retryScheduler, Clock.systemUTC());
    }

    PaperWorldInstanceAdapter(
            Path templatesRoot,
            Path worldContainer,
            WorldLifecycleGateway worlds,
            InstanceRuntimeRegistry registry,
            Executor fileExecutor,
            ScheduledExecutorService retryScheduler,
            Clock clock
    ) {
        this.files = new WorldInstanceFileStore(templatesRoot, worldContainer, clock);
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.fileExecutor = Objects.requireNonNull(fileExecutor, "fileExecutor");
        this.retryScheduler = Objects.requireNonNull(retryScheduler, "retryScheduler");
    }

    @Override
    public CompletionStage<WorldInstanceHandle> provision(
            SessionId sessionId,
            String mazeId,
            MapVersion mapVersion,
            PartyRoster roster
    ) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(mapVersion, "mapVersion");
        Objects.requireNonNull(roster, "roster");
        String worldName = files.worldName(sessionId);
        return CompletableFuture.supplyAsync(() -> {
            try {
                files.provision(sessionId, mazeId, mapVersion, roster);
                return worldName;
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, fileExecutor).thenCompose(createdName -> worlds.load(createdName)
                .thenApply(ignored -> {
                    registry.registerWorld(sessionId, createdName);
                    return new WorldInstanceHandle(createdName);
                })
                .exceptionallyCompose(failure -> deleteAfterFailedLoad(sessionId, createdName, failure)));
    }

    @Override
    public CompletionStage<Void> release(SessionId sessionId, WorldInstanceHandle instance) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(instance, "instance");
        return worlds.unload(instance.instanceName(), false).thenCompose(unloaded -> {
            if (!unloaded) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Paper refused to unload instance world " + instance.instanceName()));
            }
            return CompletableFuture.runAsync(() -> {
                try {
                    files.deleteMarkedInstance(sessionId, instance.instanceName());
                    registry.unregisterWorld(sessionId, instance.instanceName());
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            }, fileExecutor);
        });
    }

    public CompletionStage<Void> releaseWithRetry(
            SessionId sessionId,
            WorldInstanceHandle instance,
            int maximumAttempts,
            Duration delay
    ) {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("maximumAttempts must be positive");
        }
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        attemptRelease(sessionId, instance, maximumAttempts, delay, result);
        return result;
    }

    public CompletionStage<List<OrphanedWorldInstance>> discoverOrphans() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return files.discoverOrphans(registry.activeWorldNames());
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, fileExecutor);
    }

    public CompletionStage<Void> cleanupOrphan(OrphanedWorldInstance orphan) {
        Objects.requireNonNull(orphan, "orphan");
        return worlds.unload(orphan.worldName(), false).thenCompose(unloaded -> {
            if (!unloaded) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Paper refused to unload orphan world " + orphan.worldName()));
            }
            return CompletableFuture.runAsync(() -> {
                try {
                    files.deleteMarkedInstance(orphan.sessionId(), orphan.worldName());
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            }, fileExecutor);
        });
    }

    public CompletionStage<Void> cleanupOrphanWithRetry(
            OrphanedWorldInstance orphan,
            int maximumAttempts,
            Duration delay
    ) {
        if (maximumAttempts < 1) {
            throw new IllegalArgumentException("maximumAttempts must be positive");
        }
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must not be negative");
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        attemptOrphanCleanup(orphan, maximumAttempts, delay, result);
        return result;
    }

    private CompletionStage<WorldInstanceHandle> deleteAfterFailedLoad(
            SessionId sessionId,
            String worldName,
            Throwable originalFailure
    ) {
        return CompletableFuture.<Void>runAsync(() -> {
            try {
                files.deleteMarkedInstance(sessionId, worldName);
            } catch (Exception cleanupFailure) {
                originalFailure.addSuppressed(cleanupFailure);
            }
        }, fileExecutor).thenCompose(ignored -> CompletableFuture.failedFuture(unwrap(originalFailure)));
    }

    private void attemptRelease(
            SessionId sessionId,
            WorldInstanceHandle instance,
            int attemptsRemaining,
            Duration delay,
            CompletableFuture<Void> result
    ) {
        release(sessionId, instance).whenComplete((ignored, failure) -> {
            if (failure == null) {
                result.complete(null);
            } else if (attemptsRemaining == 1) {
                result.completeExceptionally(unwrap(failure));
            } else {
                retryScheduler.schedule(
                        () -> attemptRelease(sessionId, instance, attemptsRemaining - 1, delay, result),
                        delay.toMillis(),
                        TimeUnit.MILLISECONDS
                );
            }
        });
    }

    private void attemptOrphanCleanup(
            OrphanedWorldInstance orphan,
            int attemptsRemaining,
            Duration delay,
            CompletableFuture<Void> result
    ) {
        cleanupOrphan(orphan).whenComplete((ignored, failure) -> {
            if (failure == null) {
                result.complete(null);
            } else if (attemptsRemaining == 1) {
                result.completeExceptionally(unwrap(failure));
            } else {
                retryScheduler.schedule(
                        () -> attemptOrphanCleanup(orphan, attemptsRemaining - 1, delay, result),
                        delay.toMillis(),
                        TimeUnit.MILLISECONDS
                );
            }
        });
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }
}
