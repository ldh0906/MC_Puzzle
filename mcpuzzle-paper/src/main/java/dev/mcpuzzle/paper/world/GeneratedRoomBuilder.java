package dev.mcpuzzle.paper.world;

import dev.mcpuzzle.paper.map.MapPack;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.Switch;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Main-thread only, deterministic vanilla-block builder for generated maze instances. */
public final class GeneratedRoomBuilder {
    private static final int BLOCKS_PER_TICK = 2_000;
    private final Plugin plugin;

    public GeneratedRoomBuilder(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * Applies large shells in bounded main-thread batches. All target chunks are already
     * preloaded by the world adapter, so this task never performs synchronous generation.
     */
    public CompletionStage<Void> build(World world, MapPack pack) {
        List<BlockPlacement> shell = new ArrayList<>();
        for (MapPack.RoomDefinition room : pack.rooms()) {
            planShell(shell, pack.world(), room);
            room.visual().ifPresent(visual -> planVisual(shell, visual));
        }
        CompletableFuture<Void> completion = new CompletableFuture<>();
        new BukkitRunnable() {
            private int cursor;

            @Override
            public void run() {
                try {
                    int end = Math.min(cursor + BLOCKS_PER_TICK, shell.size());
                    while (cursor < end) shell.get(cursor++).apply(world);
                    if (cursor < shell.size()) return;

                    for (MapPack.RoomDefinition room : pack.rooms()) {
                        room.mechanics().forEach(mechanic -> buildMechanic(world, mechanic));
                        placeSign(world, room);
                    }
                    MapPack.Position first = pack.room(1).spawn();
                    world.setSpawnLocation((int) first.x(), (int) first.y(), (int) first.z(), first.yaw());
                    completion.complete(null);
                    cancel();
                } catch (Throwable failure) {
                    completion.completeExceptionally(failure);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return completion;
    }

    private void planShell(List<BlockPlacement> plan, MapPack.WorldDefinition definition,
                           MapPack.RoomDefinition room) {
        MapPack.Bounds bounds = room.buildBounds();
        int minX = floor(bounds.min().x());
        int maxX = floor(bounds.max().x());
        int minY = floor(bounds.min().y());
        int maxY = floor(bounds.max().y());
        int minZ = floor(bounds.min().z());
        int maxZ = floor(bounds.max().z());
        Material floor = requireMaterial(definition.floorMaterial());
        Material wall = requireMaterial(definition.wallMaterial());
        Material ceiling = requireMaterial(definition.ceilingMaterial());
        Material light = requireMaterial(definition.lightMaterial());
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Material floorBlock = (x - minX) % 8 == 4 && (z - minZ) % 8 == 4 ? light : floor;
                plan.add(new BlockPlacement(x, definition.floorY(), z, floorBlock));
                plan.add(new BlockPlacement(x, maxY, z, ceiling));
            }
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                plan.add(new BlockPlacement(x, y, minZ, wall));
                plan.add(new BlockPlacement(x, y, maxZ, wall));
            }
            for (int z = minZ; z <= maxZ; z++) {
                plan.add(new BlockPlacement(minX, y, z, wall));
                plan.add(new BlockPlacement(maxX, y, z, wall));
            }
        }
    }

    private void planVisual(List<BlockPlacement> plan, MapPack.VisualBlueprint visual) {
        int originX = floor(visual.origin().x());
        int originY = floor(visual.origin().y());
        int originZ = floor(visual.origin().z());
        for (int row = 0; row < visual.height(); row++) {
            for (int column = 0; column < visual.width(); column++) {
                int tile = visual.cells().get(row * visual.width() + column);
                Material material = requireMaterial(visual.palette().get(tile));
                for (int dx = 0; dx < visual.scale(); dx++) {
                    for (int dz = 0; dz < visual.scale(); dz++) {
                        plan.add(new BlockPlacement(originX + column * visual.scale() + dx, originY,
                                originZ + row * visual.scale() + dz, material));
                    }
                }
            }
        }

        int maxX = originX + visual.width() * visual.scale() - 1;
        int maxZ = originZ + visual.height() * visual.scale() - 1;
        for (int x = originX + 3; x <= maxX; x += 8) {
            for (int z = originZ + 3; z <= maxZ; z += 8) {
                plan.add(new BlockPlacement(x, originY + 5, z, Material.LIGHT));
            }
        }
    }

    private void buildMechanic(World world, MapPack.MechanicDefinition mechanic) {
        if (mechanic instanceof MapPack.PadMechanic pads) {
            for (MapPack.Position pos : pads.pads()) {
                placePad(world, pos, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, Material.COPPER_BLOCK);
            }
            return;
        }
        if (mechanic instanceof MapPack.DestructibleTarget target) {
            set(world, target.position(), requireMaterial(target.material()));
            return;
        }
        if (mechanic instanceof MapPack.NumericKeypad keypad) {
            for (MapPack.DigitButton digit : keypad.digitButtons()) {
                placeButton(world, digit.position());
            }
            placeButton(world, keypad.submitButton());
            placeButton(world, keypad.clearButton());
            return;
        }
        if (mechanic instanceof MapPack.Escort escort) {
            for (MapPack.Position pos : escort.checkpoints()) {
                set(world, (int) pos.x(), (int) pos.y() - 1, (int) pos.z(), Material.YELLOW_CONCRETE);
            }
            MapPack.Position destination = escort.destination();
            set(world, (int) destination.x(), (int) destination.y() - 1, (int) destination.z(), Material.LIME_CONCRETE);
            return;
        }
        if (mechanic instanceof MapPack.DynamicPartyPads dynamic) {
            for (MapPack.IndexedPad pad : dynamic.playerPads()) {
                MapPack.Position pos = pad.position();
                placePad(world, pos, Material.HEAVY_WEIGHTED_PRESSURE_PLATE, Material.BLUE_CONCRETE);
            }
            set(world, dynamic.target().position(), requireMaterial(dynamic.target().material()));
        }
    }

    private void placePad(World world, MapPack.Position pos, Material plate, Material base) {
        int x = floor(pos.x());
        int y = floor(pos.y());
        int z = floor(pos.z());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) set(world, x + dx, y, z + dz, base);
        }
        set(world, x, y + 1, z, plate);
    }

    private void placeSign(World world, MapPack.RoomDefinition room) {
        MapPack.Position spawn = room.spawn();
        Block block = world.getBlockAt(floor(spawn.x()), floor(spawn.y()), floor(spawn.z()) + 2);
        block.setType(Material.OAK_SIGN, false);
        if (block.getState() instanceof Sign sign) {
            sign.setLine(0, "§1[방 " + room.sequence() + "]");
            sign.setLine(1, shorten(room.title()));
            sign.setLine(2, "§0/maze hint");
            sign.update(true, false);
        }
    }

    private void placeButton(World world, MapPack.Position pos) {
        Block support = world.getBlockAt((int) pos.x(), (int) pos.y(), (int) pos.z() + 1);
        support.setType(Material.POLISHED_BLACKSTONE, false);
        Block button = world.getBlockAt((int) pos.x(), (int) pos.y(), (int) pos.z());
        button.setType(Material.STONE_BUTTON, false);
        if (button.getBlockData() instanceof Switch data) {
            data.setFace(Switch.Face.WALL);
            data.setFacing(org.bukkit.block.BlockFace.NORTH);
            button.setBlockData(data, false);
        }
    }

    private static String shorten(String value) { return value.length() <= 15 ? value : value.substring(0, 15); }
    private static int floor(double value) { return (int) Math.floor(value); }
    private static Material requireMaterial(String value) {
        Material result = Material.matchMaterial(value.toUpperCase(Locale.ROOT));
        if (result == null) throw new IllegalArgumentException("Unknown material " + value);
        return result;
    }
    private static void set(World world, MapPack.Position pos, Material material) {
        set(world, (int) pos.x(), (int) pos.y(), (int) pos.z(), material);
    }
    private static void set(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }

    private record BlockPlacement(int x, int y, int z, Material material) {
        private BlockPlacement {
            Objects.requireNonNull(material, "material");
        }

        private void apply(World world) {
            world.getBlockAt(x, y, z).setType(material, false);
        }
    }
}
