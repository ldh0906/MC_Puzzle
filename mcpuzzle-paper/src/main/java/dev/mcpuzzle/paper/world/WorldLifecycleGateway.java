package dev.mcpuzzle.paper.world;

import java.util.concurrent.CompletionStage;

public interface WorldLifecycleGateway {
    CompletionStage<Void> load(String worldName);

    CompletionStage<Boolean> unload(String worldName, boolean save);
}
