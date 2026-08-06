package dev.mcpuzzle.paper.containment;

import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Objects;

public final class CommandContainmentListener implements Listener {
    private final InstanceRuntimeRegistry registry;
    private final CommandContainmentPolicy policy;

    public CommandContainmentListener(InstanceRuntimeRegistry registry, CommandContainmentPolicy policy) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isContained(player) || policy.isAllowed(event.getMessage(), player.isOp())) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage("§c미궁 안에서는 이 명령어를 사용할 수 없습니다.");
    }

    @EventHandler
    public void onCommandList(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (isContained(player)) {
            event.getCommands().removeIf(label -> !policy.isVisibleLabel(label, player.isOp()));
        }
    }

    private boolean isContained(Player player) {
        return registry.sessionOfPlayer(player.getUniqueId()).isPresent()
                || registry.sessionOfWorld(player.getWorld().getName()).isPresent();
    }
}
