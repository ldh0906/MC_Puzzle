package dev.mcpuzzle.paper.world;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.port.WorldInstanceHandle;
import dev.mcpuzzle.paper.map.MapPack;
import dev.mcpuzzle.paper.thread.MainThreadGateway;
import org.bukkit.GameRule;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

/** Builds, inspects, unloads, and deletes a disposable instance for operator diagnostics. */
public final class GeneratedWorldVerifier {
    public record Report(int rooms, int visualBlocks, int scannedInputLayerBlocks, int signs) { }

    private final Server server;
    private final MainThreadGateway mainThread;
    private final GeneratedVoidWorldInstanceAdapter worlds;
    private final MapPack map;
    private final AtomicBoolean running = new AtomicBoolean();

    public GeneratedWorldVerifier(Server server, MainThreadGateway mainThread,
                                  GeneratedVoidWorldInstanceAdapter worlds, MapPack map) {
        this.server = Objects.requireNonNull(server, "server");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.map = Objects.requireNonNull(map, "map");
    }

    public CompletionStage<Report> verify() {
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("world verification is already running"));
        }
        SessionId sessionId = SessionId.random();
        UUID verifier = UUID.randomUUID();
        PartyRoster roster = new PartyRoster(verifier, List.of(verifier));
        CompletableFuture<Report> result = new CompletableFuture<>();

        worlds.provision(sessionId, map.mazeId(), map.mapVersion(), roster)
                .whenComplete((handle, provisionFailure) -> {
                    if (provisionFailure != null) {
                        result.completeExceptionally(unwrap(provisionFailure));
                        return;
                    }
                    inspectAndRelease(sessionId, handle, result);
                });
        return result.whenComplete((ignored, failure) -> running.set(false));
    }

    private void inspectAndRelease(SessionId sessionId, WorldInstanceHandle handle,
                                   CompletableFuture<Report> result) {
        mainThread.call(() -> inspect(handle)).whenComplete((report, inspectionFailure) ->
                worlds.releaseWithRetry(sessionId, handle).whenComplete((ignored, releaseFailure) -> {
                    Throwable failure = inspectionFailure == null ? null : unwrap(inspectionFailure);
                    if (releaseFailure != null) {
                        Throwable release = unwrap(releaseFailure);
                        if (failure == null) failure = release;
                        else failure.addSuppressed(release);
                    }
                    if (failure == null) result.complete(report);
                    else result.completeExceptionally(failure);
                }));
    }

    private Report inspect(WorldInstanceHandle handle) {
        World world = server.getWorld(handle.instanceName());
        if (world == null) throw new IllegalStateException("generated verification world is not loaded");
        if (world.isAutoSave()) throw new IllegalStateException("temporary world autosave must be disabled");
        if (!Boolean.FALSE.equals(world.getGameRuleValue(GameRule.DO_MOB_SPAWNING))) {
            throw new IllegalStateException("mob spawning must be disabled");
        }

        int visualBlocks = 0;
        int scanned = 0;
        int signs = 0;
        for (MapPack.RoomDefinition room : map.rooms()) {
            MapPack.Bounds bounds = room.buildBounds();
            int floorY = map.world().floorY();
            int spawnX = floor(room.spawn().x());
            int spawnZ = floor(room.spawn().z());
            requireNotAir(world, spawnX, floorY, spawnZ, room.id() + " floor");
            requireBlock(world, floor(bounds.min().x()), floorY + 1, spawnZ,
                    material(map.world().wallMaterial()), room.id() + " wall");
            requireBlock(world, spawnX, floor(bounds.max().y()), spawnZ,
                    material(map.world().ceilingMaterial()), room.id() + " ceiling");
            requireBlock(world, spawnX, floor(room.spawn().y()), spawnZ + 2,
                    Material.OAK_SIGN, room.id() + " sign");
            signs++;

            MapPack.VisualBlueprint visual = room.visual().orElseThrow(
                    () -> new IllegalStateException(room.id() + " has no floor diagram"));
            int originX = floor(visual.origin().x());
            int originY = floor(visual.origin().y());
            int originZ = floor(visual.origin().z());
            for (int row = 0; row < visual.height(); row++) {
                for (int column = 0; column < visual.width(); column++) {
                    int tile = visual.cells().get(row * visual.width() + column);
                    Material expected = material(visual.palette().get(tile));
                    for (int dx = 0; dx < visual.scale(); dx++) {
                        for (int dz = 0; dz < visual.scale(); dz++) {
                            requireBlock(world, originX + column * visual.scale() + dx, originY,
                                    originZ + row * visual.scale() + dz, expected,
                                    room.id() + " visual " + row + "," + column);
                            visualBlocks++;
                        }
                    }
                }
            }

            for (int x = floor(bounds.min().x()); x <= floor(bounds.max().x()); x++) {
                for (int z = floor(bounds.min().z()); z <= floor(bounds.max().z()); z++) {
                    for (int y = floorY; y <= floorY + 2; y++) {
                        Material type = world.getBlockAt(x, y, z).getType();
                        if (isForbiddenInput(type)) {
                            throw new IllegalStateException(room.id() + " generated forbidden input block "
                                    + type + " at " + x + "," + y + "," + z);
                        }
                        scanned++;
                    }
                }
            }
        }
        return new Report(map.rooms().size(), visualBlocks, scanned, signs);
    }

    static boolean isForbiddenInput(Material material) {
        String name = material.name();
        return name.endsWith("_PRESSURE_PLATE") || name.endsWith("_BUTTON") || name.equals("LEVER");
    }

    private static void requireNotAir(World world, int x, int y, int z, String label) {
        if (world.getBlockAt(x, y, z).getType().isAir()) {
            throw new IllegalStateException(label + " is air at " + x + "," + y + "," + z);
        }
    }

    private static void requireBlock(World world, int x, int y, int z, Material expected, String label) {
        Material actual = world.getBlockAt(x, y, z).getType();
        if (actual != expected) {
            throw new IllegalStateException(label + " expected " + expected + " but found " + actual
                    + " at " + x + "," + y + "," + z);
        }
    }

    private static Material material(String name) {
        return Objects.requireNonNull(Material.matchMaterial(name), "validated material disappeared: " + name);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }
}
