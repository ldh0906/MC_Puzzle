package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.domain.Checkpoint;
import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.Party;
import dev.mcpuzzle.core.domain.PuzzleSession;
import dev.mcpuzzle.core.domain.SaveGame;
import dev.mcpuzzle.core.domain.SaveSlot;
import dev.mcpuzzle.core.domain.SessionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveAccessPolicyTest {
    @Test
    void ownerOriginalLeaderAndOperatorCanManageTransferredSave() {
        UUID leader = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        Instant start = Instant.parse("2026-08-05T00:00:00Z");
        MapVersion version = new MapVersion("1.0.0-mvp");
        PuzzleSession session = PuzzleSession.create(SessionId.random(), "a-to-z-archive-20", version,
                Party.of(leader, List.of(leader, owner)), 5);
        session.queue(leader); session.beginProvisioning(); session.activate(start);
        session.completeCurrentRoom(start.plusSeconds(1)); session.requestSuspend(leader, start.plusSeconds(2));
        var snapshot = session.snapshot(start.plusSeconds(3));
        SaveGame save = new SaveGame(new SaveSlot(1, owner, "a-to-z-archive-20", version, snapshot.roster(),
                snapshot.checkpoint().orElseThrow(), snapshot.capturedAt()), snapshot);

        SaveAccessPolicy policy = new SaveAccessPolicy();
        assertTrue(policy.canManage(owner, false, save));
        assertTrue(policy.canManage(leader, false, save));
        assertTrue(policy.canManage(stranger, true, save));
        assertFalse(policy.canManage(stranger, false, save));
    }
}
