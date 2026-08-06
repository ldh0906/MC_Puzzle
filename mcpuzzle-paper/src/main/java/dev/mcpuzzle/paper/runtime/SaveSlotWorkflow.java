package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.PuzzleSession;
import dev.mcpuzzle.core.domain.PuzzleSessionSnapshot;
import dev.mcpuzzle.core.domain.SaveGame;
import dev.mcpuzzle.core.domain.SaveSlot;
import dev.mcpuzzle.paper.adapter.persistence.SQLitePersistence;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Owns save-slot keying, expiry timestamps, checkpoint requirements and ownership operations. */
public final class SaveSlotWorkflow {
    private final SQLitePersistence persistence;
    private final Clock clock;
    private final String mazeId;
    private final MapVersion mapVersion;

    public SaveSlotWorkflow(SQLitePersistence persistence, Clock clock, String mazeId, MapVersion mapVersion) {
        this.persistence = persistence;
        this.clock = clock;
        this.mazeId = mazeId;
        this.mapVersion = mapVersion;
    }

    public CompletionStage<Optional<SaveGame>> find(UUID owner, int slot) {
        return persistence.find(owner, mazeId, slot, clock.instant());
    }

    public CompletionStage<List<SaveGame>> list(UUID owner) {
        return persistence.listVisible(owner, mazeId, clock.instant());
    }

    public CompletionStage<List<SaveGame>> listForPrincipal(UUID principal) {
        return persistence.listVisibleToPrincipal(principal, mazeId, clock.instant());
    }

    public CompletionStage<Boolean> delete(UUID owner, int slot) {
        return persistence.delete(owner, mazeId, slot);
    }

    public CompletionStage<Boolean> deleteAuthorized(UUID owner, int slot, UUID actor, boolean operator) {
        return persistence.deleteAuthorized(owner, mazeId, slot, actor, operator);
    }

    public CompletionStage<Boolean> transfer(UUID owner, int slot, UUID newOwner) {
        return persistence.transferOwnership(owner, mazeId, slot, newOwner);
    }

    public CompletionStage<Void> store(int slotNumber, UUID owner, PartyRoster roster, PuzzleSession session) {
        Instant now = clock.instant();
        PuzzleSessionSnapshot snapshot = session.snapshot(now);
        SaveSlot slot = new SaveSlot(slotNumber, owner, mazeId, mapVersion, roster,
                snapshot.checkpoint().orElseThrow(), now);
        return persistence.upsert(new SaveGame(slot, snapshot));
    }
}
