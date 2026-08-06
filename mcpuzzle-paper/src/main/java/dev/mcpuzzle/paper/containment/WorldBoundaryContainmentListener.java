package dev.mcpuzzle.paper.containment;

import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Objects;
import java.util.Optional;

public final class WorldBoundaryContainmentListener implements Listener {
    private final InstanceRuntimeRegistry registry;
    private final ContainmentPolicy policy;
    private final TeleportPermitRegistry permits;

    public WorldBoundaryContainmentListener(
            InstanceRuntimeRegistry registry,
            ContainmentPolicy policy,
            TeleportPermitRegistry permits
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.permits = Objects.requireNonNull(permits, "permits");
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        if (permits.isPermitted(player.getUniqueId()) || policy.operatorBypassEnabled() && player.isOp()) {
            return;
        }
        Location destination = event.getTo();
        if (destination == null || destination.getWorld() == null) {
            event.setCancelled(true);
            return;
        }
        Optional<SessionId> playerSession = registry.sessionOfPlayer(player.getUniqueId())
                .or(() -> registry.sessionOfWorld(event.getFrom().getWorld().getName()));
        Optional<SessionId> destinationSession = registry.sessionOfWorld(destination.getWorld().getName());
        if (!playerSession.equals(destinationSession)) {
            event.setCancelled(true);
        }
    }
}
