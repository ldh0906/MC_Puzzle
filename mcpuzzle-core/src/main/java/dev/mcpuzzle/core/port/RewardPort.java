package dev.mcpuzzle.core.port;

import dev.mcpuzzle.core.domain.SessionCompletion;

import java.util.concurrent.CompletionStage;

public interface RewardPort {
    CompletionStage<Void> presentCompletion(SessionCompletion completion);
}
