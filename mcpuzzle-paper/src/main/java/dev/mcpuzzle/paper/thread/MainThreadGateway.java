package dev.mcpuzzle.paper.thread;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

/** Explicit boundary for operations that are only legal on the Paper main thread. */
public interface MainThreadGateway {
    <T> CompletionStage<T> call(Callable<T> task);
}
