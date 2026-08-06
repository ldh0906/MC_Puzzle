package dev.mcpuzzle.paper.isolation;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

final class PlayerSnapshot {
    private final ItemStack[] storage;
    private final ItemStack[] armor;
    private final ItemStack offHand;
    private final int heldSlot;
    private final double health;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final float experience;
    private final int level;
    private final int totalExperience;
    private final GameMode gameMode;
    private final boolean allowFlight;
    private final boolean flying;
    private final int fireTicks;
    private final float fallDistance;
    private final List<PotionEffect> potionEffects;
    private final SavedLocation originalLocation;

    private PlayerSnapshot(Player player) {
        this.storage = cloneItems(player.getInventory().getStorageContents());
        this.armor = cloneItems(player.getInventory().getArmorContents());
        this.offHand = cloneItem(player.getInventory().getItemInOffHand());
        this.heldSlot = player.getInventory().getHeldItemSlot();
        this.health = player.getHealth();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exhaustion = player.getExhaustion();
        this.experience = player.getExp();
        this.level = player.getLevel();
        this.totalExperience = player.getTotalExperience();
        this.gameMode = player.getGameMode();
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
        this.fireTicks = player.getFireTicks();
        this.fallDistance = player.getFallDistance();
        this.potionEffects = List.copyOf(player.getActivePotionEffects());
        this.originalLocation = SavedLocation.capture(player.getLocation());
    }

    private PlayerSnapshot(
            ItemStack[] storage,
            ItemStack[] armor,
            ItemStack offHand,
            int heldSlot,
            double health,
            int foodLevel,
            float saturation,
            float exhaustion,
            float experience,
            int level,
            int totalExperience,
            GameMode gameMode,
            boolean allowFlight,
            boolean flying,
            int fireTicks,
            float fallDistance,
            List<PotionEffect> potionEffects,
            SavedLocation originalLocation
    ) {
        this.storage = cloneItems(storage);
        this.armor = cloneItems(armor);
        this.offHand = cloneItem(offHand);
        this.heldSlot = heldSlot;
        this.health = health;
        this.foodLevel = foodLevel;
        this.saturation = saturation;
        this.exhaustion = exhaustion;
        this.experience = experience;
        this.level = level;
        this.totalExperience = totalExperience;
        this.gameMode = gameMode;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.fireTicks = fireTicks;
        this.fallDistance = fallDistance;
        this.potionEffects = List.copyOf(potionEffects);
        this.originalLocation = originalLocation;
    }

    static PlayerSnapshot capture(Player player) {
        return new PlayerSnapshot(player);
    }

    Location originalLocation(Server server) {
        return originalLocation.resolve(server);
    }

    void restoreState(Player player) {
        clearPotionEffects(player);
        player.getInventory().clear();
        player.getInventory().setStorageContents(cloneItems(storage));
        player.getInventory().setArmorContents(cloneItems(armor));
        player.getInventory().setItemInOffHand(cloneItem(offHand));
        player.getInventory().setHeldItemSlot(heldSlot);
        player.setFoodLevel(foodLevel);
        player.setSaturation(saturation);
        player.setExhaustion(exhaustion);
        player.setExp(experience);
        player.setLevel(level);
        player.setTotalExperience(totalExperience);
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
        player.setFireTicks(fireTicks);
        player.setFallDistance(fallDistance);
        potionEffects.forEach(effect -> player.addPotionEffect(effect, true));
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double safeMaximum = maxHealth == null ? 20.0D : maxHealth.getValue();
        player.setHealth(Math.max(0.01D, Math.min(health, safeMaximum)));
    }

    void writeTo(BukkitObjectOutputStream output) throws IOException {
        output.writeInt(1);
        output.writeObject(storage);
        output.writeObject(armor);
        output.writeObject(offHand);
        output.writeInt(heldSlot);
        output.writeDouble(health);
        output.writeInt(foodLevel);
        output.writeFloat(saturation);
        output.writeFloat(exhaustion);
        output.writeFloat(experience);
        output.writeInt(level);
        output.writeInt(totalExperience);
        output.writeUTF(gameMode.name());
        output.writeBoolean(allowFlight);
        output.writeBoolean(flying);
        output.writeInt(fireTicks);
        output.writeFloat(fallDistance);
        output.writeObject(potionEffects);
        output.writeUTF(originalLocation.worldId().toString());
        output.writeUTF(originalLocation.worldName());
        output.writeDouble(originalLocation.x());
        output.writeDouble(originalLocation.y());
        output.writeDouble(originalLocation.z());
        output.writeFloat(originalLocation.yaw());
        output.writeFloat(originalLocation.pitch());
    }

    static PlayerSnapshot readFrom(BukkitObjectInputStream input) throws IOException, ClassNotFoundException {
        if (input.readInt() != 1) {
            throw new IOException("Unsupported player snapshot format");
        }
        ItemStack[] storage = requireType(input.readObject(), ItemStack[].class, "storage");
        ItemStack[] armor = requireType(input.readObject(), ItemStack[].class, "armor");
        ItemStack offHand = nullableType(input.readObject(), ItemStack.class, "offHand");
        int heldSlot = input.readInt();
        double health = input.readDouble();
        int foodLevel = input.readInt();
        float saturation = input.readFloat();
        float exhaustion = input.readFloat();
        float experience = input.readFloat();
        int level = input.readInt();
        int totalExperience = input.readInt();
        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(input.readUTF());
        } catch (IllegalArgumentException invalidMode) {
            throw new IOException("Unknown game mode in player snapshot", invalidMode);
        }
        boolean allowFlight = input.readBoolean();
        boolean flying = input.readBoolean();
        int fireTicks = input.readInt();
        float fallDistance = input.readFloat();
        Object effectsObject = input.readObject();
        if (!(effectsObject instanceof List<?> rawEffects)
                || rawEffects.stream().anyMatch(effect -> !(effect instanceof PotionEffect))) {
            throw new IOException("Invalid potion effect list in player snapshot");
        }
        List<PotionEffect> effects = rawEffects.stream().map(PotionEffect.class::cast).toList();
        SavedLocation originalLocation;
        try {
            originalLocation = new SavedLocation(
                    UUID.fromString(input.readUTF()), input.readUTF(), input.readDouble(), input.readDouble(),
                    input.readDouble(), input.readFloat(), input.readFloat());
        } catch (IllegalArgumentException invalidLocation) {
            throw new IOException("Invalid original location in player snapshot", invalidLocation);
        }
        if (heldSlot < 0 || heldSlot > 8 || health <= 0.0D || !Double.isFinite(health)
                || foodLevel < 0 || foodLevel > 20 || !Float.isFinite(saturation)
                || !Float.isFinite(exhaustion) || experience < 0.0F || experience > 1.0F
                || level < 0 || totalExperience < 0 || !Float.isFinite(fallDistance)) {
            throw new IOException("Player snapshot contains out-of-range values");
        }
        return new PlayerSnapshot(storage, armor, offHand, heldSlot, health, foodLevel, saturation, exhaustion,
                experience, level, totalExperience, gameMode, allowFlight, flying, fireTicks, fallDistance,
                effects, originalLocation);
    }

    private static <T> T requireType(Object value, Class<T> type, String field) throws IOException {
        if (!type.isInstance(value)) {
            throw new IOException("Invalid " + field + " in player snapshot");
        }
        return type.cast(value);
    }

    private static <T> T nullableType(Object value, Class<T> type, String field) throws IOException {
        if (value == null) {
            return null;
        }
        return requireType(value, type, field);
    }

    private static void clearPotionEffects(Player player) {
        Collection<PotionEffect> current = List.copyOf(player.getActivePotionEffects());
        current.forEach(effect -> player.removePotionEffect(effect.getType()));
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        return Arrays.stream(items).map(PlayerSnapshot::cloneItem).toArray(ItemStack[]::new);
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private record SavedLocation(
            UUID worldId,
            String worldName,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        private SavedLocation {
            if (worldId == null || worldName == null || worldName.isBlank()
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || !Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new IllegalArgumentException("Saved location is incomplete or non-finite");
            }
        }

        static SavedLocation capture(Location location) {
            World world = location.getWorld();
            if (world == null) {
                throw new IllegalStateException("Cannot snapshot a player in an unloaded world");
            }
            return new SavedLocation(world.getUID(), world.getName(), location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());
        }

        Location resolve(Server server) {
            World world = server.getWorld(worldId);
            if (world == null) {
                world = server.getWorld(worldName);
            }
            return new Location(world, x, y, z, yaw, pitch);
        }
    }
}
