package dev.mcpuzzle.paper.containment;

import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Must be invoked on the Paper main thread. */
public final class VisibilityIsolationService {
    private final Plugin plugin;
    private final Server server;
    private final InstanceRuntimeRegistry registry;
    private final ContainmentPolicy policy;

    public VisibilityIsolationService(
            Plugin plugin,
            Server server,
            InstanceRuntimeRegistry registry,
            ContainmentPolicy policy
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = Objects.requireNonNull(server, "server");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public void refreshAll() {
        List<Player> players = new ArrayList<>(server.getOnlinePlayers());
        for (int firstIndex = 0; firstIndex < players.size(); firstIndex++) {
            Player first = players.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < players.size(); secondIndex++) {
                applyPair(first, players.get(secondIndex));
            }
        }
    }

    public void refresh(Player player) {
        for (Player other : server.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(player.getUniqueId())) {
                applyPair(player, other);
            }
        }
    }

    private void applyPair(Player first, Player second) {
        boolean visible = policy.canShare(
                registry.sessionOfPlayer(first.getUniqueId()), first.isOp(),
                registry.sessionOfPlayer(second.getUniqueId()), second.isOp()
        );
        if (visible) {
            first.showPlayer(plugin, second);
            second.showPlayer(plugin, first);
        } else {
            first.hidePlayer(plugin, second);
            second.hidePlayer(plugin, first);
        }
    }
}
