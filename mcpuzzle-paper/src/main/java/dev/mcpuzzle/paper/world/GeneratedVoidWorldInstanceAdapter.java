package dev.mcpuzzle.paper.world;

import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.port.WorldInstanceHandle;
import dev.mcpuzzle.core.port.WorldInstancePort;
import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;
import dev.mcpuzzle.paper.map.MapPack;
import dev.mcpuzzle.paper.thread.MainThreadGateway;
import net.kyori.adventure.util.TriState;
import org.bukkit.Chunk;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.Plugin;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class GeneratedVoidWorldInstanceAdapter implements WorldInstancePort {
    private final Server server;
    private final MainThreadGateway mainThread;
    private final InstanceRuntimeRegistry registry;
    private final WorldInstanceFileStore files;
    private final GeneratedRoomBuilder builder;
    private final Executor filesExecutor;
    private final ScheduledExecutorService retries;
    private final Map<String, MapPack> mapPacks;

    public GeneratedVoidWorldInstanceAdapter(
            Plugin plugin,
            Server server,
            MainThreadGateway mainThread,
            InstanceRuntimeRegistry registry,
            Path worldContainer,
            Executor filesExecutor,
            ScheduledExecutorService retries,
            Map<String, MapPack> mapPacks
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.files = new WorldInstanceFileStore(worldContainer.resolve("mcpuzzle-templates"), worldContainer, Clock.systemUTC());
        this.builder = new GeneratedRoomBuilder(Objects.requireNonNull(plugin, "plugin"));
        this.filesExecutor = Objects.requireNonNull(filesExecutor, "filesExecutor");
        this.retries = Objects.requireNonNull(retries, "retries");
        this.mapPacks = Map.copyOf(Objects.requireNonNull(mapPacks, "mapPacks"));
    }

    @Override
    public CompletionStage<WorldInstanceHandle> provision(SessionId sessionId, String mazeId,
                                                           MapVersion mapVersion, PartyRoster roster) {
        MapPack mapPack = mapPacks.get(mazeId);
        if (mapPack == null || !mapPack.mapVersion().equals(mapVersion)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Loaded map does not match admission request"));
        }
        String name = files.worldName(sessionId);
        return mainThread.call(() -> createWorld(name, mapPack))
                .thenCompose(world -> CompletableFuture
                        .runAsync(() -> mark(sessionId, mazeId, mapVersion, roster), filesExecutor)
                        .thenApply(ignored -> world))
                .thenCompose(world -> preloadBuildChunks(world, mapPack))
                .thenCompose(world -> mainThread.call(() -> builder.build(world, mapPack))
                        .thenCompose(build -> build)
                        .thenApply(ignored -> world))
                .thenCompose(world -> mainThread.call(() -> {
                    registry.registerWorld(sessionId, name);
                    return new WorldInstanceHandle(name);
                }))
                .exceptionallyCompose(failure -> cleanupFailedCreation(sessionId, name, unwrap(failure)));
    }

    @Override
    public CompletionStage<Void> release(SessionId sessionId, WorldInstanceHandle instance) {
        return mainThread.call(() -> {
            World world = server.getWorld(instance.instanceName());
            if (world == null) return true;
            world.getEntities().forEach(entity -> {
                if (!(entity instanceof org.bukkit.entity.Player)) entity.remove();
            });
            return server.unloadWorld(world, false);
        }).thenCompose(unloaded -> {
            if (!unloaded) return CompletableFuture.failedFuture(
                    new IllegalStateException("Paper refused to unload generated instance " + instance.instanceName()));
            return CompletableFuture.runAsync(() -> delete(sessionId, instance.instanceName()), filesExecutor)
                    .thenRun(() -> registry.unregisterWorld(sessionId, instance.instanceName()));
        });
    }

    public CompletionStage<Void> releaseWithRetry(SessionId sessionId, WorldInstanceHandle instance) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        releaseAttempt(sessionId, instance, 3, result);
        return result;
    }

    public CompletionStage<List<OrphanedWorldInstance>> discoverOrphans() {
        return CompletableFuture.supplyAsync(() -> {
            try { return files.discoverOrphans(registry.activeWorldNames()); }
            catch (Exception failure) { throw new CompletionException(failure); }
        }, filesExecutor);
    }

    public CompletionStage<Void> cleanupOrphan(OrphanedWorldInstance orphan) {
        return mainThread.call(() -> {
            World world = server.getWorld(orphan.worldName());
            return world == null || server.unloadWorld(world, false);
        }).thenCompose(unloaded -> {
            if (!unloaded) return CompletableFuture.failedFuture(new IllegalStateException("고아 월드를 언로드할 수 없습니다: " + orphan.worldName()));
            return CompletableFuture.runAsync(() -> {
                try { files.deleteMarkedInstance(orphan.sessionId(), orphan.worldName()); }
                catch (Exception failure) { throw new CompletionException(failure); }
            }, filesExecutor);
        });
    }

    private World createWorld(String name, MapPack mapPack) {
        if (server.getWorld(name) != null || java.nio.file.Files.exists(server.getWorldContainer().toPath().resolve(name))) {
            throw new IllegalStateException("Instance world already exists: " + name);
        }
        MapPack.Position firstSpawn = mapPack.room(1).spawn();
        WorldCreator creator = new WorldCreator(name)
                .environment(World.Environment.NORMAL)
                .generator(new VoidGenerator(firstSpawn))
                .generateStructures(false)
                .keepSpawnLoaded(TriState.FALSE);
        World world = server.createWorld(creator);
        if (world == null) throw new IllegalStateException("Paper refused to create generated world " + name);
        world.setAutoSave(false);
        world.setKeepSpawnInMemory(false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setTime(18000L);
        return world;
    }

    /**
     * Paper can generate void chunks away from the spawn asynchronously. Preloading every
     * chunk touched by the complete room build prevents getBlockAt/setType from synchronously
     * waiting on chunk generation and freezing the server watchdog.
     */
    private CompletionStage<World> preloadBuildChunks(World world, MapPack mapPack) {
        return mainThread.call(() -> {
            List<CompletableFuture<Chunk>> loads = new ArrayList<>();
            for (ChunkCoordinate chunk : buildChunks(mapPack)) {
                loads.add(world.getChunkAtAsync(chunk.x(), chunk.z(), true));
            }
            CompletableFuture<?>[] futures = loads.toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(futures).thenApply(ignored -> world);
        }).thenCompose(stage -> stage);
    }

    private Set<ChunkCoordinate> buildChunks(MapPack mapPack) {
        Set<ChunkCoordinate> chunks = new LinkedHashSet<>();
        for (MapPack.RoomDefinition room : mapPack.rooms()) {
            MapPack.Bounds bounds = room.buildBounds();
            int minChunkX = Math.floorDiv(floor(bounds.min().x()), 16);
            int maxChunkX = Math.floorDiv(floor(bounds.max().x()), 16);
            int minChunkZ = Math.floorDiv(floor(bounds.min().z()), 16);
            int maxChunkZ = Math.floorDiv(floor(bounds.max().z()), 16);
            for (int x = minChunkX; x <= maxChunkX; x++) {
                for (int z = minChunkZ; z <= maxChunkZ; z++) {
                    chunks.add(new ChunkCoordinate(x, z));
                }
            }
        }
        return chunks;
    }

    private void mark(SessionId id, String mazeId, MapVersion version, PartyRoster roster) {
        try { files.markGenerated(id, mazeId, version, roster); }
        catch (Exception failure) { throw new CompletionException(failure); }
    }

    private void delete(SessionId id, String name) {
        try { files.deleteMarkedInstance(id, name); }
        catch (Exception failure) { throw new CompletionException(failure); }
    }

    private CompletionStage<WorldInstanceHandle> cleanupFailedCreation(SessionId id, String name, Throwable failure) {
        return mainThread.call(() -> {
            World world = server.getWorld(name);
            return world == null || server.unloadWorld(world, false);
        }).thenCompose(unloaded -> {
            if (unloaded) {
                return CompletableFuture.runAsync(() -> {
                    try { files.deleteFailedGeneratedInstance(id, name); }
                    catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                }, filesExecutor);
            }
            return CompletableFuture.completedFuture(null);
        }).thenCompose(ignored -> CompletableFuture.failedFuture(failure));
    }

    private void releaseAttempt(SessionId id, WorldInstanceHandle handle, int remaining, CompletableFuture<Void> result) {
        release(id, handle).whenComplete((ignored, failure) -> {
            if (failure == null) result.complete(null);
            else if (remaining <= 1) result.completeExceptionally(unwrap(failure));
            else retries.schedule(() -> releaseAttempt(id, handle, remaining - 1, result),
                        Duration.ofSeconds(2).toMillis(), TimeUnit.MILLISECONDS);
        });
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private record ChunkCoordinate(int x, int z) {
    }

    private static final class VoidGenerator extends ChunkGenerator {
        private final MapPack.Position spawn;

        private VoidGenerator(MapPack.Position spawn) {
            this.spawn = Objects.requireNonNull(spawn, "spawn");
        }

        @Override
        public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
            return createChunkData(world);
        }

        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        }
    }
}
