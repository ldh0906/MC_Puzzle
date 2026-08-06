package dev.mcpuzzle.paper.containment;

import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;

import java.util.Objects;

public final class PortalContainmentListener implements Listener {
    private final InstanceRuntimeRegistry registry;
    private final ContainmentPolicy policy;

    public PortalContainmentListener(InstanceRuntimeRegistry registry, ContainmentPolicy policy) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        boolean instanceInvolved = registry.sessionOfPlayer(player.getUniqueId()).isPresent()
                || registry.sessionOfWorld(player.getWorld().getName()).isPresent()
                || event.getTo() != null && event.getTo().getWorld() != null
                && registry.sessionOfWorld(event.getTo().getWorld().getName()).isPresent();
        if (instanceInvolved && !(policy.operatorBypassEnabled() && player.isOp())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (registry.sessionOfWorld(event.getFrom().getWorld().getName()).isPresent()
                || event.getTo() != null && event.getTo().getWorld() != null
                && registry.sessionOfWorld(event.getTo().getWorld().getName()).isPresent()) {
            event.setCancelled(true);
        }
    }
}
