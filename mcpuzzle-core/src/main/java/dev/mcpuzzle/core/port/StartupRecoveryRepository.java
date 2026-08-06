package dev.mcpuzzle.core.port;

import dev.mcpuzzle.core.domain.StartupRecoveryReport;

import java.util.concurrent.CompletionStage;

public interface StartupRecoveryRepository {
    CompletionStage<StartupRecoveryReport> recoverAfterRestart();
}
