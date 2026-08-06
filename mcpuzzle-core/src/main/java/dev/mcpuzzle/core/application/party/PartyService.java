package dev.mcpuzzle.core.application.party;

import dev.mcpuzzle.core.domain.Party;
import dev.mcpuzzle.core.domain.PartyChange;
import dev.mcpuzzle.core.domain.PartyFailure;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Thread-safe in-memory party registry. One monitor protects party state and the
 * reverse player index so membership changes are atomic.
 */
public final class PartyService {
    private final Map<PartyId, PartyEntry> parties = new HashMap<>();
    private final Map<UUID, PartyId> partyByPlayer = new HashMap<>();

    public synchronized PartyServiceResult create(UUID leaderId) {
        Objects.requireNonNull(leaderId, "leaderId");
        if (partyByPlayer.containsKey(leaderId)) {
            return PartyServiceResult.failure(PartyServiceError.ALREADY_IN_PARTY_OR_RUN);
        }
        PartyId id = PartyId.random();
        PartyEntry entry = new PartyEntry(Party.create(leaderId));
        parties.put(id, entry);
        partyByPlayer.put(leaderId, id);
        return PartyServiceResult.success(view(id, entry));
    }

    public synchronized PartyServiceResult invite(UUID actorId, UUID targetId) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(targetId, "targetId");
        LocatedParty located = locateActor(actorId);
        if (located == null) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        }
        PartyEntry entry = located.entry();
        PartyServiceError leaderError = requireOpenLeader(actorId, entry);
        if (leaderError != null) {
            return PartyServiceResult.failure(leaderError);
        }
        if (entry.party.contains(targetId)) {
            return PartyServiceResult.failure(PartyServiceError.TARGET_ALREADY_MEMBER);
        }
        if (partyByPlayer.containsKey(targetId)) {
            return PartyServiceResult.failure(PartyServiceError.ALREADY_IN_PARTY_OR_RUN);
        }
        if (entry.party.members().size() >= Party.MAX_SIZE) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_FULL);
        }
        if (!entry.pendingInvites.add(targetId)) {
            return PartyServiceResult.failure(PartyServiceError.INVITE_ALREADY_PENDING);
        }
        return PartyServiceResult.success(view(located.id(), entry));
    }

    public synchronized PartyServiceResult accept(UUID playerId, PartyId partyId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(partyId, "partyId");
        PartyEntry entry = parties.get(partyId);
        if (entry == null) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        }
        if (!entry.pendingInvites.contains(playerId)) {
            return PartyServiceResult.failure(PartyServiceError.INVITE_NOT_FOUND);
        }
        if (entry.lifecycle != PartyLifecycle.OPEN || entry.party.rosterLocked()) {
            return PartyServiceResult.failure(PartyServiceError.ROSTER_LOCKED);
        }
        if (partyByPlayer.containsKey(playerId)) {
            return PartyServiceResult.failure(PartyServiceError.ALREADY_IN_PARTY_OR_RUN);
        }
        PartyChange change = entry.party.addMember(playerId);
        if (!change.succeeded()) {
            return PartyServiceResult.failure(mapPartyError(change.failure().orElseThrow()));
        }
        entry.party = change.party();
        entry.pendingInvites.remove(playerId);
        removeOtherInvites(playerId, partyId);
        partyByPlayer.put(playerId, partyId);
        return PartyServiceResult.success(view(partyId, entry));
    }

    public synchronized PartyServiceResult decline(UUID playerId, PartyId partyId) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(partyId, "partyId");
        PartyEntry entry = parties.get(partyId);
        if (entry == null) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        }
        if (!entry.pendingInvites.remove(playerId)) {
            return PartyServiceResult.failure(PartyServiceError.INVITE_NOT_FOUND);
        }
        return PartyServiceResult.success(view(partyId, entry));
    }

    public synchronized PartyServiceResult kick(UUID actorId, UUID targetId) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(targetId, "targetId");
        LocatedParty located = locateActor(actorId);
        if (located == null) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        }
        PartyEntry entry = located.entry();
        PartyServiceError leaderError = requireOpenLeader(actorId, entry);
        if (leaderError != null) {
            return PartyServiceResult.failure(leaderError);
        }
        if (entry.party.leaderId().equals(targetId)) {
            return PartyServiceResult.failure(PartyServiceError.CANNOT_KICK_LEADER);
        }
        if (!entry.party.contains(targetId)) {
            return PartyServiceResult.failure(PartyServiceError.TARGET_NOT_MEMBER);
        }
        PartyChange change = entry.party.removeMember(targetId);
        if (!change.succeeded()) {
            return PartyServiceResult.failure(mapPartyError(change.failure().orElseThrow()));
        }
        entry.party = change.party();
        partyByPlayer.remove(targetId);
        return PartyServiceResult.success(view(located.id(), entry));
    }

    public synchronized PartyServiceResult start(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        LocatedParty located = locateActor(actorId);
        if (located == null) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        }
        PartyEntry entry = located.entry();
        PartyServiceError leaderError = requireOpenLeader(actorId, entry);
        if (leaderError != null) {
            return PartyServiceResult.failure(leaderError);
        }
        entry.party = entry.party.lockRoster();
        entry.lifecycle = PartyLifecycle.QUEUED;
        entry.pendingInvites.clear();
        return PartyServiceResult.success(view(located.id(), entry));
    }

    /** Closes an OPEN party. Queued/running parties use their runtime cancellation flow. */
    public synchronized PartyServiceResult disband(UUID leaderId) {
        Objects.requireNonNull(leaderId, "leaderId");
        LocatedParty located = locateActor(leaderId);
        if (located == null) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        }
        PartyEntry entry = located.entry();
        if (!entry.party.leaderId().equals(leaderId)) {
            return PartyServiceResult.failure(PartyServiceError.NOT_LEADER);
        }
        if (entry.lifecycle != PartyLifecycle.OPEN || entry.party.rosterLocked()) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_OPEN);
        }
        return PartyServiceResult.success(close(located.id(), entry));
    }

    /** Removes a nonleader member before the roster is locked. */
    public synchronized PartyServiceResult leaveOpenParty(UUID memberId) {
        Objects.requireNonNull(memberId, "memberId");
        LocatedParty located = locateActor(memberId);
        if (located == null) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        }
        PartyEntry entry = located.entry();
        if (entry.lifecycle != PartyLifecycle.OPEN || entry.party.rosterLocked()) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_OPEN);
        }
        if (entry.party.leaderId().equals(memberId)) {
            return PartyServiceResult.failure(PartyServiceError.LEADER_MUST_DISBAND);
        }
        PartyChange change = entry.party.removeMember(memberId);
        if (!change.succeeded()) {
            return PartyServiceResult.failure(mapPartyError(change.failure().orElseThrow()));
        }
        entry.party = change.party();
        partyByPlayer.remove(memberId);
        removeAllInvites(memberId);
        return PartyServiceResult.success(view(located.id(), entry));
    }

    public synchronized PartyServiceResult markRunActive(PartyId partyId) {
        PartyEntry entry = parties.get(Objects.requireNonNull(partyId, "partyId"));
        if (entry == null) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        }
        if (entry.lifecycle != PartyLifecycle.QUEUED) {
            return PartyServiceResult.failure(PartyServiceError.INVALID_STATE);
        }
        entry.lifecycle = PartyLifecycle.IN_RUN;
        return PartyServiceResult.success(view(partyId, entry));
    }

    public synchronized PartyServiceResult leave(UUID actorId) {
        Objects.requireNonNull(actorId, "actorId");
        LocatedParty located = locateActor(actorId);
        if (located == null) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        }
        PartyEntry entry = located.entry();
        if (!entry.party.leaderId().equals(actorId)) {
            return PartyServiceResult.failure(PartyServiceError.NOT_LEADER);
        }
        PartyView closed = close(located.id(), entry);
        return PartyServiceResult.success(closed);
    }

    public synchronized PartyServiceResult completeRun(PartyId partyId) {
        PartyEntry entry = parties.get(Objects.requireNonNull(partyId, "partyId"));
        if (entry == null) {
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        }
        if (entry.lifecycle != PartyLifecycle.IN_RUN) {
            return PartyServiceResult.failure(PartyServiceError.INVALID_STATE);
        }
        return PartyServiceResult.success(close(partyId, entry));
    }

    public synchronized Optional<PartyView> findByPlayer(UUID playerId) {
        PartyId id = partyByPlayer.get(Objects.requireNonNull(playerId, "playerId"));
        if (id == null) {
            return Optional.empty();
        }
        PartyEntry entry = parties.get(id);
        return entry == null ? Optional.empty() : Optional.of(view(id, entry));
    }

    public synchronized Optional<PartyView> findById(PartyId partyId) {
        PartyEntry entry = parties.get(Objects.requireNonNull(partyId, "partyId"));
        return entry == null ? Optional.empty() : Optional.of(view(partyId, entry));
    }

    private LocatedParty locateActor(UUID actorId) {
        PartyId id = partyByPlayer.get(actorId);
        PartyEntry entry = id == null ? null : parties.get(id);
        return entry == null ? null : new LocatedParty(id, entry);
    }

    private static PartyServiceError requireOpenLeader(UUID actorId, PartyEntry entry) {
        if (!entry.party.leaderId().equals(actorId)) {
            return PartyServiceError.NOT_LEADER;
        }
        if (entry.lifecycle != PartyLifecycle.OPEN || entry.party.rosterLocked()) {
            return PartyServiceError.ROSTER_LOCKED;
        }
        return null;
    }

    private void removeOtherInvites(UUID playerId, PartyId acceptedPartyId) {
        parties.forEach((id, entry) -> {
            if (!id.equals(acceptedPartyId)) {
                entry.pendingInvites.remove(playerId);
            }
        });
    }

    private void removeAllInvites(UUID playerId) {
        parties.values().forEach(entry -> entry.pendingInvites.remove(playerId));
    }

    private PartyView close(PartyId id, PartyEntry entry) {
        entry.lifecycle = PartyLifecycle.CLOSED;
        entry.pendingInvites.clear();
        entry.party.members().forEach(partyByPlayer::remove);
        parties.remove(id);
        return view(id, entry);
    }

    private static PartyView view(PartyId id, PartyEntry entry) {
        return new PartyView(
                id,
                entry.party.leaderId(),
                entry.party.members(),
                entry.pendingInvites,
                entry.lifecycle
        );
    }

    private static PartyServiceError mapPartyError(PartyFailure failure) {
        return switch (failure) {
            case PARTY_FULL -> PartyServiceError.PARTY_FULL;
            case MEMBER_ALREADY_PRESENT -> PartyServiceError.TARGET_ALREADY_MEMBER;
            case MEMBER_NOT_PRESENT -> PartyServiceError.TARGET_NOT_MEMBER;
            case LEADER_CANNOT_LEAVE -> PartyServiceError.CANNOT_KICK_LEADER;
            case ROSTER_LOCKED -> PartyServiceError.ROSTER_LOCKED;
        };
    }

    private static final class PartyEntry {
        private Party party;
        private final Set<UUID> pendingInvites = new LinkedHashSet<>();
        private PartyLifecycle lifecycle = PartyLifecycle.OPEN;

        private PartyEntry(Party party) {
            this.party = party;
        }
    }

    private record LocatedParty(PartyId id, PartyEntry entry) {
    }
}
