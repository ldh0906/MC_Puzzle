package dev.mcpuzzle.core.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyTest {
    @Test
    void partyAcceptsOneToFourUniqueMembers() {
        UUID leader = UUID.randomUUID();
        Party party = Party.create(leader);

        for (int index = 0; index < 3; index++) {
            PartyChange change = party.addMember(UUID.randomUUID());
            assertTrue(change.succeeded());
            party = change.party();
        }

        assertEquals(4, party.members().size());
        PartyChange fifth = party.addMember(UUID.randomUUID());
        assertFalse(fifth.succeeded());
        assertEquals(PartyFailure.PARTY_FULL, fifth.failure().orElseThrow());
    }

    @Test
    void invalidRosterShapesAreRejected() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> Party.of(leader, List.of(member)));
        assertThrows(
                IllegalArgumentException.class,
                () -> Party.of(leader, List.of(leader, member, member))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> Party.of(
                        leader,
                        List.of(leader, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), member)
                )
        );
    }

    @Test
    void lockedRosterCannotChangeAndExposesImmutableMembers() {
        UUID leader = UUID.randomUUID();
        Party party = Party.create(leader).lockRoster();

        PartyChange change = party.addMember(UUID.randomUUID());

        assertFalse(change.succeeded());
        assertEquals(PartyFailure.ROSTER_LOCKED, change.failure().orElseThrow());
        assertThrows(UnsupportedOperationException.class, () -> party.members().add(UUID.randomUUID()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> party.toRoster().members().add(UUID.randomUUID())
        );
    }
}
