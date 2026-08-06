package dev.mcpuzzle.paper.containment;

import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Objects;
import java.util.Optional;

public final class DamageContainmentListener implements Listener {
    private final InstanceRuntimeRegistry registry;
    private final ContainmentPolicy policy;

    public DamageContainmentListener(InstanceRuntimeRegistry registry, ContainmentPolicy policy) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = attackingPlayer(event.getDamager());
        Player victim = event.getEntity() instanceof Player player ? player : null;
        Optional<SessionId> attackerSession = attacker == null
                ? registry.sessionOfWorld(event.getDamager().getWorld().getName())
                : registry.sessionOfPlayer(attacker.getUniqueId());
        Optional<SessionId> victimSession = victim == null
                ? registry.sessionOfWorld(event.getEntity().getWorld().getName())
                : registry.sessionOfPlayer(victim.getUniqueId());
        if (attackerSession.isEmpty() && victimSession.isEmpty()) {
            return;
        }
        if (!policy.canShare(attackerSession, attacker != null && attacker.isOp(),
                victimSession, victim != null && victim.isOp())) {
            event.setCancelled(true);
        }
    }

    private Player attackingPlayer(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof Player player ? player : null;
        }
        return null;
    }
}
