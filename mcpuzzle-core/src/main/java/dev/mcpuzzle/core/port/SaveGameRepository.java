package dev.mcpuzzle.core.port;

import dev.mcpuzzle.core.domain.SaveGame;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

public interface SaveGameRepository {
    CompletionStage<Void> upsert(SaveGame saveGame);

    CompletionStage<Optional<SaveGame>> find(UUID ownerId, String mazeId, int slotNumber, Instant now);

    CompletionStage<List<SaveGame>> listVisible(UUID ownerId, String mazeId, Instant now);

    CompletionStage<Boolean> delete(UUID ownerId, String mazeId, int slotNumber);

    /** Authorization is deliberately handled by the application layer. */
    CompletionStage<Boolean> transferOwnership(
            UUID currentOwnerId,
            String mazeId,
            int slotNumber,
            UUID newOwnerId
    );

    CompletionStage<Integer> purgeExpired(Instant now);
}
