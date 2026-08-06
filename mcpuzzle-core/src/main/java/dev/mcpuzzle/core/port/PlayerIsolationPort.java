package dev.mcpuzzle.core.port;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;

import java.util.concurrent.CompletionStage;

public interface PlayerIsolationPort {
    CompletionStage<Void> captureAndEnter(
            SessionId sessionId,
            PartyRoster roster,
            WorldInstanceHandle instance
    );

    CompletionStage<Void> restoreToLobby(SessionId sessionId, PartyRoster roster);
}
