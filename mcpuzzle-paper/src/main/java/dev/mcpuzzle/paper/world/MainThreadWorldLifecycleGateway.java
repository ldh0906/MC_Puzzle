package dev.mcpuzzle.paper.world;

import dev.mcpuzzle.paper.thread.MainThreadGateway;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

public final class MainThreadWorldLifecycleGateway implements WorldLifecycleGateway {
    private final Server server;
    private final MainThreadGateway mainThread;

    public MainThreadWorldLifecycleGateway(Server server, MainThreadGateway mainThread) {
        this.server = Objects.requireNonNull(server, "server");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
    }

    @Override
    public CompletionStage<Void> load(String worldName) {
        return mainThread.call(() -> {
            World world = server.createWorld(new WorldCreator(worldName));
            if (world == null) {
                throw new IllegalStateException("Paper refused to load instance world " + worldName);
            }
            world.setAutoSave(false);
            return null;
        });
    }

    @Override
    public CompletionStage<Boolean> unload(String worldName, boolean save) {
        return mainThread.call(() -> {
            World world = server.getWorld(worldName);
            return world == null || server.unloadWorld(world, save);
        });
    }
}
