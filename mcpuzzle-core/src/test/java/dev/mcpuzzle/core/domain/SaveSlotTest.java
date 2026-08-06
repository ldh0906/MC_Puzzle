package dev.mcpuzzle.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SaveSlotTest {
    @Test
    void slotRangeAndSevenDayExpiryAreEnforced() {
        UUID leader = UUID.randomUUID();
        Instant savedAt = Instant.parse("2026-08-05T00:00:00Z");
        PartyRoster roster = new PartyRoster(leader, List.of(leader));
        Checkpoint checkpoint = new Checkpoint(4, 5, savedAt);

        SaveSlot slot = new SaveSlot(
                1,
                leader,
                "fifty-rooms",
                new MapVersion("1"),
                roster,
                checkpoint,
                savedAt
        );

        assertFalse(slot.isExpiredAt(slot.expiresAt().minusMillis(1)));
        assertTrue(slot.isExpiredAt(slot.expiresAt()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SaveSlot(4, leader, "maze", new MapVersion("1"), roster, checkpoint, savedAt)
        );
    }

    @Test
    void ownershipTransferDoesNotChangeOriginalRosterOrLeader() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        Instant savedAt = Instant.parse("2026-08-05T00:00:00Z");
        PartyRoster originalRoster = new PartyRoster(leader, List.of(leader, member));
        SaveSlot original = new SaveSlot(
                2,
                leader,
                "fifty-rooms",
                new MapVersion("1"),
                originalRoster,
                new Checkpoint(4, 5, savedAt),
                savedAt
        );

        SaveSlot transferred = original.transferOwnership(member);

        assertEquals(leader, original.ownerId());
        assertEquals(member, transferred.ownerId());
        assertSame(originalRoster, transferred.roster());
        assertEquals(leader, transferred.roster().leaderId());
        assertEquals(List.of(leader, member), transferred.roster().members());
        assertEquals(savedAt, transferred.updatedAt());
        assertThrows(IllegalArgumentException.class, () -> original.transferOwnership(UUID.randomUUID()));
    }
}
