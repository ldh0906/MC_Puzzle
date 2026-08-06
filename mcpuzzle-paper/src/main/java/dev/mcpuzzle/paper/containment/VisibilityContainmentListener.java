package dev.mcpuzzle.paper.containment;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public final class VisibilityContainmentListener implements Listener {
    private final Plugin plugin;
    private final VisibilityIsolationService visibility;

    public VisibilityContainmentListener(Plugin plugin, VisibilityIsolationService visibility) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> visibility.refresh(event.getPlayer()));
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> visibility.refresh(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, visibility::refreshAll);
    }
}
