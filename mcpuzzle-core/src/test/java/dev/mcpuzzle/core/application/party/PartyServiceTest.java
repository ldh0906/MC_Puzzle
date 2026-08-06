package dev.mcpuzzle.core.application.party;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyServiceTest {
    @Test
    void createInviteAcceptDeclineAndKickRespectLeaderAuthority() {
        PartyService service = new PartyService();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID declined = UUID.randomUUID();
        PartyId id = success(service.create(leader)).id();

        assertError(service.invite(member, declined), PartyServiceError.PARTY_NOT_FOUND);
        success(service.invite(leader, member));
        success(service.invite(leader, declined));
        PartyView joined = success(service.accept(member, id));
        assertEquals(2, joined.members().size());
        success(service.decline(declined, id));
        assertError(service.kick(member, leader), PartyServiceError.NOT_LEADER);
        PartyView kicked = success(service.kick(leader, member));
        assertEquals(1, kicked.members().size());
        assertTrue(service.findByPlayer(member).isEmpty());
    }

    @Test
    void enforcesFourPlayerLimitAndSinglePartyOrRunOwnership() {
        PartyService service = new PartyService();
        UUID leader = UUID.randomUUID();
        PartyId id = success(service.create(leader)).id();
        UUID existingMember = null;
        for (int index = 0; index < 3; index++) {
            UUID member = UUID.randomUUID();
            success(service.invite(leader, member));
            success(service.accept(member, id));
            existingMember = member;
        }

        assertError(service.invite(leader, UUID.randomUUID()), PartyServiceError.PARTY_FULL);
        assertError(service.create(existingMember), PartyServiceError.ALREADY_IN_PARTY_OR_RUN);

        PartyView queued = success(service.start(leader));
        assertEquals(PartyLifecycle.QUEUED, queued.lifecycle());
        assertError(service.kick(leader, existingMember), PartyServiceError.ROSTER_LOCKED);
        assertError(service.invite(leader, UUID.randomUUID()), PartyServiceError.ROSTER_LOCKED);
        assertEquals(PartyLifecycle.IN_RUN, success(service.markRunActive(id)).lifecycle());
        assertError(service.create(existingMember), PartyServiceError.ALREADY_IN_PARTY_OR_RUN);
    }

    @Test
    void onlyLeaderMayStartOrLeaveAndCompletionReleasesEveryPlayer() {
        PartyService service = new PartyService();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyId id = success(service.create(leader)).id();
        success(service.invite(leader, member));
        success(service.accept(member, id));

        assertError(service.start(member), PartyServiceError.NOT_LEADER);
        assertError(service.leave(member), PartyServiceError.NOT_LEADER);
        success(service.start(leader));
        success(service.markRunActive(id));
        PartyView closed = success(service.completeRun(id));

        assertEquals(PartyLifecycle.CLOSED, closed.lifecycle());
        assertTrue(service.findByPlayer(leader).isEmpty());
        assertTrue(service.findByPlayer(member).isEmpty());
        assertFalse(service.findById(id).isPresent());
        assertTrue(service.create(member).succeeded());
    }

    @Test
    void nonleaderCanLeaveOpenPartyAndImmediatelyCreateAnotherParty() {
        PartyService service = new PartyService();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyId id = success(service.create(leader)).id();
        success(service.invite(leader, member));
        success(service.accept(member, id));

        assertError(service.disband(member), PartyServiceError.NOT_LEADER);
        PartyView remaining = success(service.leaveOpenParty(member));

        assertEquals(List.of(leader), remaining.members());
        assertTrue(service.findByPlayer(member).isEmpty());
        assertTrue(service.create(member).succeeded());
        assertError(service.leaveOpenParty(leader), PartyServiceError.LEADER_MUST_DISBAND);
    }

    @Test
    void disbandClearsMembershipReverseIndexAndOutstandingInvites() {
        PartyService service = new PartyService();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        PartyId id = success(service.create(leader)).id();
        success(service.invite(leader, member));
        success(service.accept(member, id));
        success(service.invite(leader, invited));

        PartyView closed = success(service.disband(leader));

        assertEquals(PartyLifecycle.CLOSED, closed.lifecycle());
        assertTrue(closed.pendingInvites().isEmpty());
        assertTrue(service.findByPlayer(leader).isEmpty());
        assertTrue(service.findByPlayer(member).isEmpty());
        assertTrue(service.findById(id).isEmpty());
        assertError(service.decline(invited, id), PartyServiceError.PARTY_NOT_FOUND);
        assertTrue(service.create(leader).succeeded());
        assertTrue(service.create(member).succeeded());
    }

    @Test
    void openPartyExitOperationsAreRejectedAfterQueueAndDuringRun() {
        PartyService service = new PartyService();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyId id = success(service.create(leader)).id();
        success(service.invite(leader, member));
        success(service.accept(member, id));
        success(service.start(leader));

        assertError(service.disband(leader), PartyServiceError.PARTY_NOT_OPEN);
        assertError(service.leaveOpenParty(member), PartyServiceError.PARTY_NOT_OPEN);
        success(service.markRunActive(id));
        assertError(service.disband(leader), PartyServiceError.PARTY_NOT_OPEN);
        assertError(service.leaveOpenParty(member), PartyServiceError.PARTY_NOT_OPEN);
        assertEquals(PartyLifecycle.IN_RUN, service.findById(id).orElseThrow().lifecycle());
    }

    private static PartyView success(PartyServiceResult result) {
        assertTrue(result.succeeded(), () -> "Unexpected party error: " + result.error());
        return result.party().orElseThrow();
    }

    private static void assertError(PartyServiceResult result, PartyServiceError error) {
        assertFalse(result.succeeded());
        assertEquals(error, result.error().orElseThrow());
    }
}
