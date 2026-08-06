package dev.mcpuzzle.paper.containment;

import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ItemContainmentListener implements Listener {
    private final InstanceRuntimeRegistry registry;
    private final ContainmentPolicy policy;
    private final NamespacedKey ownerKey;

    public ItemContainmentListener(Plugin plugin, InstanceRuntimeRegistry registry, ContainmentPolicy policy) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.ownerKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "instance_session");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Optional<SessionId> playerSession = registry.sessionOfPlayer(player.getUniqueId());
        Optional<SessionId> worldSession = registry.sessionOfWorld(player.getWorld().getName());
        if (!policy.canUseInstanceResource(playerSession, player.isOp(), worldSession)) {
            event.setCancelled(true);
            return;
        }
        playerSession.ifPresent(sessionId -> mark(event.getItemDrop(), sessionId));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        registry.sessionOfWorld(event.getLocation().getWorld().getName())
                .ifPresent(sessionId -> mark(event.getEntity(), sessionId));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Optional<SessionId> itemSession = markedSession(event.getItem())
                .or(() -> registry.sessionOfWorld(event.getItem().getWorld().getName()));
        Optional<SessionId> playerSession = registry.sessionOfPlayer(player.getUniqueId());
        if (!policy.canUseInstanceResource(playerSession, player.isOp(), itemSession)) {
            event.setCancelled(true);
        }
    }

    private void mark(Item item, SessionId sessionId) {
        item.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, sessionId.toString());
    }

    private Optional<SessionId> markedSession(Item item) {
        String value = item.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SessionId(UUID.fromString(value)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
