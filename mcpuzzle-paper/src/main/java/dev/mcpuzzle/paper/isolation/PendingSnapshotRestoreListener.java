package dev.mcpuzzle.paper.isolation;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

/** Independently registrable startup-crash recovery listener. */
public final class PendingSnapshotRestoreListener implements Listener {
    private final Plugin plugin;
    private final PaperPlayerIsolationAdapter isolation;

    public PendingSnapshotRestoreListener(Plugin plugin, PaperPlayerIsolationAdapter isolation) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.isolation = Objects.requireNonNull(isolation, "isolation");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        isolation.restorePendingPlayer(event.getPlayer().getUniqueId()).whenComplete((restored, failure) -> {
            if (failure != null) {
                plugin.getLogger().severe("Could not restore durable player snapshot for "
                        + event.getPlayer().getUniqueId() + ": " + rootMessage(failure));
            } else if (Boolean.TRUE.equals(restored)) {
                plugin.getLogger().info("Restored durable player snapshot for " + event.getPlayer().getUniqueId());
            }
        });
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }
}
