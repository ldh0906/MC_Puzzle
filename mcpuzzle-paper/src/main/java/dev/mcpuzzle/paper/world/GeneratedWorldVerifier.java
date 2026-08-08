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
import org.bukkit.block.Sign;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Rotatable;
import org.bukkit.block.sign.Side;

import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private final List<MapPack> maps;
    private final AtomicBoolean running = new AtomicBoolean();

    public GeneratedWorldVerifier(Server server, MainThreadGateway mainThread,
                                  GeneratedVoidWorldInstanceAdapter worlds, Collection<MapPack> maps) {
        this.server = Objects.requireNonNull(server, "server");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.maps = List.copyOf(Objects.requireNonNull(maps, "maps"));
        if (this.maps.isEmpty()) throw new IllegalArgumentException("At least one map is required");
    }

    public CompletionStage<Report> verify() {
        if (!running.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("world verification is already running"));
        }
        CompletionStage<Report> result = verifyNext(0, new Report(0, 0, 0, 0));
        return result.whenComplete((ignored, failure) -> running.set(false));
    }

    private CompletionStage<Report> verifyNext(int index, Report aggregate) {
        if (index >= maps.size()) return CompletableFuture.completedFuture(aggregate);
        return verifyMap(maps.get(index)).thenCompose(report -> verifyNext(index + 1, add(aggregate, report)));
    }

    private CompletionStage<Report> verifyMap(MapPack map) {
        SessionId sessionId = SessionId.random();
        UUID verifier = UUID.randomUUID();
        PartyRoster roster = new PartyRoster(verifier, List.of(verifier));
        return worlds.provision(sessionId, map.mazeId(), map.mapVersion(), roster)
                .thenCompose(handle -> inspectAndRelease(sessionId, handle, map));
    }

    private CompletionStage<Report> inspectAndRelease(SessionId sessionId, WorldInstanceHandle handle,
                                                       MapPack map) {
        CompletableFuture<Report> result = new CompletableFuture<>();
        mainThread.call(() -> inspect(handle, map)).whenComplete((report, inspectionFailure) ->
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
        return result;
    }

    private Report inspect(WorldInstanceHandle handle, MapPack map) {
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
            requireStandingSign(world, spawnX, floor(room.spawn().y()), spawnZ + 2,
                    BlockFace.NORTH, List.of(
                            "§1[방 " + room.sequence() + "]",
                            shorten(room.title()),
                            "§0/maze hint"
                    ), room.id() + " sign");
            signs++;

            MapPack.VisualBlueprint visual = room.visual().orElseThrow(
                    () -> new IllegalStateException(room.id() + " has no floor diagram"));
            Map<BlockPosition, Material> structureBlocks = expectedStructure(room);
            int originX = floor(visual.origin().x());
            int originY = floor(visual.origin().y());
            int originZ = floor(visual.origin().z());
            for (int row = 0; row < visual.height(); row++) {
                for (int column = 0; column < visual.width(); column++) {
                    int tile = visual.cells().get(row * visual.width() + column);
                    Material expected = material(visual.palette().get(tile));
                    for (int dx = 0; dx < visual.scale(); dx++) {
                        for (int dz = 0; dz < visual.scale(); dz++) {
                            int x = originX + column * visual.scale() + dx;
                            int z = originZ + row * visual.scale() + dz;
                            BlockPosition point = new BlockPosition(x, originY, z);
                            if (!structureBlocks.containsKey(point) && !overriddenByMechanicOrSign(room, point)) {
                                requireBlock(world, x, originY, z, expected,
                                        room.id() + " visual " + row + "," + column);
                                visualBlocks++;
                            }
                        }
                    }
                }
            }

            for (Map.Entry<BlockPosition, Material> entry : structureBlocks.entrySet()) {
                if (!overriddenByMechanicOrSign(room, entry.getKey())) {
                    BlockPosition point = entry.getKey();
                    requireBlock(world, point.x(), point.y(), point.z(), entry.getValue(), room.id() + " structure");
                }
            }
            for (MapPack.MechanicDefinition mechanic : room.mechanics()) verifyMechanic(world, room, mechanic);
            if (room.structure().isPresent()) {
                for (MapPack.StructureSign structureSign : room.structure().orElseThrow().signs()) {
                    MapPack.Position point = structureSign.position();
                    requireStandingSign(world, floor(point.x()), floor(point.y()), floor(point.z()),
                            BlockFace.valueOf(structureSign.facing()), structureSign.lines(),
                            room.id() + " structure sign");
                    signs++;
                }
            }

            for (int x = floor(bounds.min().x()); x <= floor(bounds.max().x()); x++) {
                for (int z = floor(bounds.min().z()); z <= floor(bounds.max().z()); z++) {
                    for (int y = floorY; y <= floorY + 2; y++) {
                        scanned++;
                    }
                }
            }
        }
        return new Report(map.rooms().size(), visualBlocks, scanned, signs);
    }

    private static Report add(Report left, Report right) {
        return new Report(left.rooms() + right.rooms(), left.visualBlocks() + right.visualBlocks(),
                left.scannedInputLayerBlocks() + right.scannedInputLayerBlocks(), left.signs() + right.signs());
    }

    static boolean isPuzzleInput(Material material) {
        String name = material.name();
        return name.endsWith("_PRESSURE_PLATE") || name.endsWith("_BUTTON") || name.equals("LEVER");
    }

    private static Map<BlockPosition, Material> expectedStructure(MapPack.RoomDefinition room) {
        Map<BlockPosition, Material> expected = new LinkedHashMap<>();
        if (room.structure().isEmpty()) return expected;
        MapPack.StructureBlueprint structure = room.structure().orElseThrow();
        for (MapPack.StructureBlock block : structure.blocks()) {
            MapPack.Position point = block.position();
            expected.put(new BlockPosition(floor(point.x()), floor(point.y()), floor(point.z())), material(block.material()));
        }
        for (MapPack.StructureCuboid cuboid : structure.cuboids()) {
            MapPack.Bounds bounds = cuboid.bounds();
            Material material = material(cuboid.material());
            for (int x = floor(bounds.min().x()); x <= floor(bounds.max().x()); x++) {
                for (int y = floor(bounds.min().y()); y <= floor(bounds.max().y()); y++) {
                    for (int z = floor(bounds.min().z()); z <= floor(bounds.max().z()); z++) {
                        expected.put(new BlockPosition(x, y, z), material);
                    }
                }
            }
        }
        return expected;
    }

    private static void verifyMechanic(World world, MapPack.RoomDefinition room, MapPack.MechanicDefinition mechanic) {
        if (mechanic instanceof MapPack.OrderedInput ordered) {
            ordered.controls().forEach(control -> verifyControl(world, room.id(), control));
            ordered.clearButton().ifPresent(point -> verifyButton(world, room.id(), point, Material.POLISHED_BLACKSTONE));
        } else if (mechanic instanceof MapPack.ChoiceInput choice) {
            choice.controls().forEach(control -> verifyControl(world, room.id(), control));
        } else if (mechanic instanceof MapPack.ToggleInput toggle) {
            toggle.controls().forEach(control -> {
                verifyControl(world, room.id(), control);
                control.indicator().ifPresent(point -> requireBlock(world, floor(point.x()), floor(point.y()), floor(point.z()),
                        Material.RED_STAINED_GLASS, room.id() + " toggle indicator"));
            });
            verifyButton(world, room.id(), toggle.submitButton(), Material.EMERALD_BLOCK);
            verifyButton(world, room.id(), toggle.clearButton(), Material.REDSTONE_BLOCK);
        }
    }

    private static void verifyControl(World world, String roomId, MapPack.Control control) {
        MapPack.Position point = control.position();
        int x = floor(point.x());
        int y = floor(point.y());
        int z = floor(point.z());
        if (control.activation().equals("STEP")) {
            requireBlock(world, x, y - 1, z, material(control.material()), roomId + " control base " + control.id());
            requireBlock(world, x, y, z, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, roomId + " step control " + control.id());
        } else {
            verifyButton(world, roomId, point, material(control.material()));
        }
    }

    private static void verifyButton(World world, String roomId, MapPack.Position point, Material support) {
        int x = floor(point.x());
        int y = floor(point.y());
        int z = floor(point.z());
        requireBlock(world, x, y, z, Material.STONE_BUTTON, roomId + " button");
        requireBlock(world, x, y, z + 1, support, roomId + " button support");
    }

    private static boolean overriddenByMechanicOrSign(MapPack.RoomDefinition room, BlockPosition point) {
        if (point.equals(blockPosition(room.spawn().x(), room.spawn().y(), room.spawn().z() + 2))) return true;
        if (room.structure().isPresent() && room.structure().orElseThrow().signs().stream()
                .map(MapPack.StructureSign::position).map(GeneratedWorldVerifier::blockPosition).anyMatch(point::equals)) return true;
        for (MapPack.MechanicDefinition mechanic : room.mechanics()) {
            List<MapPack.Control> controls = mechanic instanceof MapPack.OrderedInput ordered ? ordered.controls()
                    : mechanic instanceof MapPack.ChoiceInput choice ? choice.controls()
                    : mechanic instanceof MapPack.ToggleInput toggle ? toggle.controls() : List.of();
            for (MapPack.Control control : controls) {
                BlockPosition controlPoint = blockPosition(control.position());
                if (point.equals(controlPoint)) return true;
                if (control.activation().equals("STEP") && point.equals(new BlockPosition(
                        controlPoint.x(), controlPoint.y() - 1, controlPoint.z()))) return true;
                if (control.activation().equals("CLICK") && point.equals(new BlockPosition(
                        controlPoint.x(), controlPoint.y(), controlPoint.z() + 1))) return true;
                if (control.indicator().map(GeneratedWorldVerifier::blockPosition).filter(point::equals).isPresent()) return true;
            }
            if (mechanic instanceof MapPack.OrderedInput ordered && ordered.clearButton().isPresent()
                    && isButtonPosition(point, ordered.clearButton().orElseThrow())) return true;
            if (mechanic instanceof MapPack.ToggleInput toggle
                    && (isButtonPosition(point, toggle.submitButton()) || isButtonPosition(point, toggle.clearButton()))) return true;
        }
        return false;
    }

    private static boolean isButtonPosition(BlockPosition point, MapPack.Position button) {
        BlockPosition position = blockPosition(button);
        return point.equals(position) || point.equals(new BlockPosition(position.x(), position.y(), position.z() + 1));
    }

    private static BlockPosition blockPosition(MapPack.Position position) {
        return blockPosition(position.x(), position.y(), position.z());
    }

    private static BlockPosition blockPosition(double x, double y, double z) {
        return new BlockPosition(floor(x), floor(y), floor(z));
    }

    private record BlockPosition(int x, int y, int z) { }

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

    private static void requireStandingSign(World world, int x, int y, int z,
                                            BlockFace expectedFacing, List<String> expectedLines, String label) {
        requireBlock(world, x, y, z, Material.OAK_SIGN, label);
        if (!(world.getBlockAt(x, y, z).getBlockData() instanceof Rotatable rotation)) {
            throw new IllegalStateException(label + " is not rotatable at " + x + "," + y + "," + z);
        }
        if (rotation.getRotation() != expectedFacing) {
            throw new IllegalStateException(label + " expected facing " + expectedFacing + " but found "
                    + rotation.getRotation() + " at " + x + "," + y + "," + z);
        }
        if (!(world.getBlockAt(x, y, z).getState() instanceof Sign sign)) {
            throw new IllegalStateException(label + " has no sign state at " + x + "," + y + "," + z);
        }
        for (Side side : Side.values()) {
            if (!sign.getSide(side).isGlowingText()) {
                throw new IllegalStateException(label + " " + side + " text is not glowing at "
                        + x + "," + y + "," + z);
            }
            for (int index = 0; index < 4; index++) {
                String expected = index < expectedLines.size() ? expectedLines.get(index) : "";
                String actual = sign.getSide(side).getLine(index);
                if (!actual.equals(expected)) {
                    throw new IllegalStateException(label + " " + side + " line " + index
                            + " expected '" + expected + "' but found '" + actual + "'");
                }
            }
        }
        if (!sign.isWaxed()) {
            throw new IllegalStateException(label + " is not waxed at " + x + "," + y + "," + z);
        }
    }

    private static String shorten(String value) {
        return value.length() <= 15 ? value : value.substring(0, 15);
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
