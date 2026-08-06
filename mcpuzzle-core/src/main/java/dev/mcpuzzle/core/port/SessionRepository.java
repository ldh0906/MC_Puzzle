package dev.mcpuzzle.core.port;

import dev.mcpuzzle.core.domain.PuzzleSessionSnapshot;
import dev.mcpuzzle.core.domain.SessionId;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SessionRepository {
    CompletionStage<Void> save(PuzzleSessionSnapshot session);

    CompletionStage<Optional<PuzzleSessionSnapshot>> findById(SessionId sessionId);

    CompletionStage<Collection<PuzzleSessionSnapshot>> findByMember(UUID playerId);

    CompletionStage<Collection<PuzzleSessionSnapshot>> findUnfinished();

    CompletionStage<Void> delete(SessionId sessionId);
}
