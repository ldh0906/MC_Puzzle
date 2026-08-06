package dev.mcpuzzle.paper.thread;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class BukkitMainThreadGateway implements MainThreadGateway {
    private final Plugin plugin;

    public BukkitMainThreadGateway(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public <T> CompletionStage<T> call(Callable<T> task) {
        Objects.requireNonNull(task, "task");
        CompletableFuture<T> result = new CompletableFuture<>();
        Runnable invocation = () -> {
            try {
                result.complete(task.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        };
        if (Bukkit.isPrimaryThread()) {
            invocation.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, invocation);
        }
        return result;
    }
}
