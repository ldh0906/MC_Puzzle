package dev.mcpuzzle.paper.config;

import dev.mcpuzzle.paper.isolation.ConfiguredLobbyDestination;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record MCPuzzleConfig(
        int maxActiveWorlds,
        int afkTimeoutMinutes,
        ConfiguredLobbyDestination lobby,
        boolean operatorBypass,
        Set<String> allowedInstanceCommands,
        ResourcePack resourcePack
) {
    public static MCPuzzleConfig load(FileConfiguration config) {
        Objects.requireNonNull(config, "config");
        int max = config.getInt("instances.max-active-worlds", 2);
        if (max < 1 || max > 16) throw new IllegalArgumentException("instances.max-active-worlds must be 1..16");
        int afk = config.getInt("instances.afk-timeout-minutes", 10);
        if (afk != 10) throw new IllegalArgumentException("MVP AFK timeout is fixed at 10 minutes");
        String world = config.getString("lobby.world", "world");
        ConfiguredLobbyDestination lobby = new ConfiguredLobbyDestination(world,
                config.getDouble("lobby.spawn.x", 0.5), config.getDouble("lobby.spawn.y", 80),
                config.getDouble("lobby.spawn.z", 0.5), (float) config.getDouble("lobby.spawn.yaw", 0),
                (float) config.getDouble("lobby.spawn.pitch", 0));
        boolean opBypass = config.getBoolean("containment.operator-bypass", false);
        Set<String> commands = Set.copyOf(config.getStringList("containment.allowed-commands"));
        if (commands.isEmpty()) commands = Set.of("maze", "미궁");
        boolean required = config.getBoolean("resource-pack.required", true);
        String url = Objects.requireNonNullElse(config.getString("resource-pack.url"), "").trim();
        String sha1 = Objects.requireNonNullElse(config.getString("resource-pack.sha1"), "").trim().toLowerCase();
        LocalResourcePackHost localHost = LocalResourcePackHost.validate(
                config.getBoolean("resource-pack.local-host.enabled", false),
                Objects.requireNonNullElse(config.getString("resource-pack.local-host.bind-address"), "127.0.0.1"),
                config.getInt("resource-pack.local-host.port", 8123),
                Objects.requireNonNullElse(config.getString("resource-pack.local-host.file"),
                        "resource-pack/MCPuzzle-1.0.0.zip"));
        return new MCPuzzleConfig(max, afk, lobby, opBypass, commands,
                ResourcePack.validate(required, url, sha1, localHost));
    }

    public record ResourcePack(boolean required, Optional<String> url, Optional<byte[]> sha1,
                               Optional<String> problem, LocalResourcePackHost localHost) {
        public ResourcePack {
            url = url.map(String::trim);
            sha1 = sha1.map(byte[]::clone);
            Objects.requireNonNull(problem, "problem");
            Objects.requireNonNull(localHost, "localHost");
        }
        @Override public Optional<byte[]> sha1() { return sha1.map(byte[]::clone); }

        static ResourcePack validate(boolean required, String url, String sha1, LocalResourcePackHost localHost) {
            if (!required) return new ResourcePack(false, Optional.empty(), Optional.empty(), Optional.empty(), localHost);
            if (localHost.problem().isPresent()) return invalid(localHost.problem().orElseThrow(), localHost);
            if (url.isBlank()) return invalid("resource-pack.url이 비어 있습니다.", localHost);
            try {
                URI parsed = URI.create(url);
                if (!Set.of("http", "https").contains(parsed.getScheme())) {
                    return invalid("리소스 팩 URL은 http(s)여야 합니다.", localHost);
                }
            } catch (IllegalArgumentException failure) {
                return invalid("리소스 팩 URL 형식이 잘못되었습니다.", localHost);
            }
            if (!sha1.matches("[0-9a-f]{40}")) {
                return invalid("resource-pack.sha1은 40자리 16진수여야 합니다.", localHost);
            }
            return new ResourcePack(true, Optional.of(url), Optional.of(HexFormat.of().parseHex(sha1)),
                    Optional.empty(), localHost);
        }

        private static ResourcePack invalid(String problem, LocalResourcePackHost localHost) {
            return new ResourcePack(true, Optional.empty(), Optional.empty(), Optional.of(problem), localHost);
        }

        public boolean configured() { return !required || problem.isEmpty(); }
    }

    public record LocalResourcePackHost(boolean enabled, String bindAddress, int port, String file,
                                        Optional<String> problem) {
        public LocalResourcePackHost {
            bindAddress = bindAddress.trim();
            file = file.trim().replace('\\', '/');
            Objects.requireNonNull(problem, "problem");
        }

        static LocalResourcePackHost validate(boolean enabled, String bindAddress, int port, String file) {
            if (!enabled) return new LocalResourcePackHost(false, bindAddress, port, file, Optional.empty());
            if (bindAddress.isBlank()) return invalid(bindAddress, port, file, "local-host.bind-address가 비어 있습니다.");
            if (port < 1024 || port > 65535) return invalid(bindAddress, port, file, "local-host.port는 1024..65535여야 합니다.");
            if (file.isBlank()) return invalid(bindAddress, port, file, "local-host.file이 비어 있습니다.");
            return new LocalResourcePackHost(true, bindAddress, port, file, Optional.empty());
        }

        private static LocalResourcePackHost invalid(String bindAddress, int port, String file, String problem) {
            return new LocalResourcePackHost(true, bindAddress, port, file, Optional.of(problem));
        }
    }
}
