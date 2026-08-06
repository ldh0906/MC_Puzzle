package dev.mcpuzzle.paper.resourcepack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.mcpuzzle.paper.config.MCPuzzleConfig;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalResourcePackServer implements AutoCloseable {
    private final Plugin plugin;
    private HttpServer server;
    private ExecutorService executor;

    public LocalResourcePackServer(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void start(MCPuzzleConfig.ResourcePack resourcePack) throws IOException {
        MCPuzzleConfig.LocalResourcePackHost hosting = resourcePack.localHost();
        if (!hosting.enabled()) return;
        if (!resourcePack.configured()) {
            throw new IllegalArgumentException(resourcePack.problem().orElse("리소스 팩 설정이 유효하지 않습니다."));
        }

        Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path packFile = dataFolder.resolve(hosting.file()).normalize();
        if (!packFile.startsWith(dataFolder)) {
            throw new IllegalArgumentException("local-host.file은 플러그인 데이터 폴더 안에 있어야 합니다.");
        }
        byte[] content = Files.readAllBytes(packFile);
        byte[] actualHash = sha1(content);
        if (!MessageDigest.isEqual(actualHash, resourcePack.sha1().orElseThrow())) {
            throw new IllegalArgumentException("로컬 리소스 팩 SHA-1이 config.yml과 일치하지 않습니다.");
        }

        URI publicUri = URI.create(resourcePack.url().orElseThrow());
        String route = publicUri.getPath();
        if (route == null || route.isBlank() || "/".equals(route)) {
            throw new IllegalArgumentException("로컬 리소스 팩 URL에는 ZIP 경로가 필요합니다.");
        }

        server = HttpServer.create(new InetSocketAddress(hosting.bindAddress(), hosting.port()), 0);
        server.createContext(route, exchange -> serve(exchange, content));
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mcpuzzle-resource-pack-http");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        plugin.getLogger().info("로컬 리소스 팩 제공 시작: " + resourcePack.url().orElseThrow());
    }

    private void serve(HttpExchange exchange, byte[] content) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            if ("HEAD".equals(method)) {
                exchange.getResponseHeaders().set("Content-Length", Integer.toString(content.length));
                exchange.sendResponseHeaders(200, -1);
                return;
            }
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
        }
    }

    private byte[] sha1(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(content);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JVM에 SHA-1 구현이 없습니다.", impossible);
        }
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
