package dev.mcpuzzle.core.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class Party {
    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 4;

    private final UUID leaderId;
    private final List<UUID> members;
    private final boolean rosterLocked;

    private Party(UUID leaderId, List<UUID> members, boolean rosterLocked) {
        this.leaderId = Objects.requireNonNull(leaderId, "leaderId");
        Objects.requireNonNull(members, "members");
        Set<UUID> uniqueMembers = new LinkedHashSet<>(members);
        if (uniqueMembers.size() != members.size()) {
            throw new IllegalArgumentException("Party members must be unique");
        }
        if (!uniqueMembers.contains(leaderId)) {
            throw new IllegalArgumentException("Party leader must be a member");
        }
        if (uniqueMembers.size() < MIN_SIZE || uniqueMembers.size() > MAX_SIZE) {
            throw new IllegalArgumentException("Party size must be between 1 and 4");
        }
        this.members = List.copyOf(uniqueMembers);
        this.rosterLocked = rosterLocked;
    }

    public static Party create(UUID leaderId) {
        return new Party(leaderId, List.of(leaderId), false);
    }

    public static Party of(UUID leaderId, List<UUID> members) {
        return new Party(leaderId, members, false);
    }

    public UUID leaderId() {
        return leaderId;
    }

    public List<UUID> members() {
        return members;
    }

    public boolean rosterLocked() {
        return rosterLocked;
    }

    public boolean contains(UUID playerId) {
        return members.contains(playerId);
    }

    public PartyChange addMember(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (rosterLocked) {
            return PartyChange.failure(this, PartyFailure.ROSTER_LOCKED);
        }
        if (members.contains(playerId)) {
            return PartyChange.failure(this, PartyFailure.MEMBER_ALREADY_PRESENT);
        }
        if (members.size() >= MAX_SIZE) {
            return PartyChange.failure(this, PartyFailure.PARTY_FULL);
        }
        List<UUID> updated = new ArrayList<>(members);
        updated.add(playerId);
        return PartyChange.success(new Party(leaderId, updated, false));
    }

    public PartyChange removeMember(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (rosterLocked) {
            return PartyChange.failure(this, PartyFailure.ROSTER_LOCKED);
        }
        if (leaderId.equals(playerId)) {
            return PartyChange.failure(this, PartyFailure.LEADER_CANNOT_LEAVE);
        }
        if (!members.contains(playerId)) {
            return PartyChange.failure(this, PartyFailure.MEMBER_NOT_PRESENT);
        }
        List<UUID> updated = new ArrayList<>(members);
        updated.remove(playerId);
        return PartyChange.success(new Party(leaderId, updated, false));
    }

    public Party lockRoster() {
        return rosterLocked ? this : new Party(leaderId, members, true);
    }

    public PartyRoster toRoster() {
        return new PartyRoster(leaderId, members);
    }
}
