package dev.mcpuzzle.core.port;

import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;

import java.util.concurrent.CompletionStage;

public interface WorldInstancePort {
    CompletionStage<WorldInstanceHandle> provision(
            SessionId sessionId,
            String mazeId,
            MapVersion mapVersion,
            PartyRoster roster
    );

    CompletionStage<Void> release(SessionId sessionId, WorldInstanceHandle instance);
}
