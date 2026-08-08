package dev.mcpuzzle.paper.listener;

import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;
import dev.mcpuzzle.paper.runtime.MazeRuntimeService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.Material;

import java.util.Objects;
import org.bukkit.plugin.Plugin;

public final class MazeGameplayListener implements Listener {
    private final MazeRuntimeService runtime;
    private final InstanceRuntimeRegistry registry;
    private final Plugin plugin;

    public MazeGameplayListener(Plugin plugin, MazeRuntimeService runtime, InstanceRuntimeRegistry registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event.getFrom(), event.getTo())) return;
        if (registry.sessionOfPlayer(event.getPlayer().getUniqueId()).isPresent()) runtime.onMove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (registry.sessionOfPlayer(event.getPlayer().getUniqueId()).isEmpty()) return;
        Material held = event.getItem() == null ? Material.AIR : event.getItem().getType();
        if (allowsMazeBook(event.getAction(), held)) {
            runtime.recordActivity(event.getPlayer());
            return;
        }
        event.setCancelled(true);
        if (event.getClickedBlock() == null) { runtime.recordActivity(event.getPlayer()); return; }
        boolean destructive = event.getAction() == Action.LEFT_CLICK_BLOCK;
        runtime.onBlockInteraction(event.getPlayer(), event.getClickedBlock(), destructive);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (registry.sessionOfPlayer(event.getPlayer().getUniqueId()).isEmpty()) return;
        event.setCancelled(true);
        event.setDropItems(false);
        runtime.onBlockInteraction(event.getPlayer(), event.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (registry.sessionOfPlayer(event.getPlayer().getUniqueId()).isEmpty()) return;
        event.setCancelled(true);
        runtime.recordActivity(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (registry.sessionOfWorld(event.getEntity().getWorld().getName()).isPresent()) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (registry.sessionOfWorld(event.getEntity().getWorld().getName()).isPresent()) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        runtime.onDisconnect(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        runtime.respawnLocation(event.getPlayer()).ifPresent(location -> {
            event.setRespawnLocation(location);
            plugin.getServer().getScheduler().runTask(plugin, () -> runtime.onMazeDeath(event.getPlayer()));
        });
    }

    private boolean sameBlock(org.bukkit.Location a, org.bukkit.Location b) {
        return a.getWorld() == b.getWorld() && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    static boolean allowsMazeBook(Action action, Material held) {
        return action == Action.RIGHT_CLICK_AIR
                && (held == Material.WRITTEN_BOOK || held == Material.WRITABLE_BOOK);
    }
}
