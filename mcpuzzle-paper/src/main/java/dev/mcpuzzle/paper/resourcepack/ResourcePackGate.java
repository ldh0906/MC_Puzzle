package dev.mcpuzzle.paper.resourcepack;

import dev.mcpuzzle.paper.config.MCPuzzleConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ResourcePackGate implements Listener {
    private final Plugin plugin;
    private volatile MCPuzzleConfig.ResourcePack configuration;
    private final ResourcePackRequestTracker requests = new ResourcePackRequestTracker();
    private volatile Consumer<UUID> failureHandler = ignored -> { };

    public ResourcePackGate(Plugin plugin, MCPuzzleConfig.ResourcePack configuration) {
        this.plugin = plugin;
        this.configuration = configuration;
    }

    public void update(MCPuzzleConfig.ResourcePack replacement) {
        configuration = replacement;
        requests.clear();
        plugin.getServer().getOnlinePlayers().forEach(this::send);
    }

    public void setFailureHandler(Consumer<UUID> handler) {
        this.failureHandler = java.util.Objects.requireNonNull(handler, "handler");
    }

    public boolean canEnter(UUID playerId) {
        MCPuzzleConfig.ResourcePack current = configuration;
        return !current.required() || current.configured() && requests.loaded(playerId);
    }

    public String denialReason(UUID playerId) {
        MCPuzzleConfig.ResourcePack current = configuration;
        if (current.problem().isPresent()) return "§c미궁 리소스 팩 설정 오류: " + current.problem().get();
        if (!canEnter(playerId)) return "§e필수 리소스 팩 로드가 끝난 뒤 다시 시도하세요.";
        return "";
    }

    public boolean configured() { return configuration.configured(); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        requests.joined(event.getPlayer().getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> send(event.getPlayer()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        requests.left(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                if (!requests.succeeded(event.getPlayer().getUniqueId())) return;
                event.getPlayer().sendMessage("§a미궁 리소스 팩을 불러왔습니다.");
            }
            case DECLINED, FAILED_DOWNLOAD -> {
                if (!requests.failed(event.getPlayer().getUniqueId())) return;
                event.getPlayer().sendMessage("§c필수 리소스 팩이 없어 미궁에 입장할 수 없습니다.");
                failureHandler.accept(event.getPlayer().getUniqueId());
            }
            default -> { }
        }
    }

    private void send(Player player) {
        MCPuzzleConfig.ResourcePack current = configuration;
        if (!current.required() || !current.configured()) return;
        requests.sent(player.getUniqueId());
        player.setResourcePack(current.url().orElseThrow(), current.sha1().orElseThrow(), true);
    }
}
