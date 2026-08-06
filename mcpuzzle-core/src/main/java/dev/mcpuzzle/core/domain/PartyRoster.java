package dev.mcpuzzle.core.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PartyRoster(UUID leaderId, List<UUID> members) {
    public PartyRoster {
        Objects.requireNonNull(leaderId, "leaderId");
        Objects.requireNonNull(members, "members");
        Set<UUID> uniqueMembers = new LinkedHashSet<>(members);
        if (uniqueMembers.size() != members.size()) {
            throw new IllegalArgumentException("Roster members must be unique");
        }
        if (!uniqueMembers.contains(leaderId)) {
            throw new IllegalArgumentException("Roster leader must be a member");
        }
        if (uniqueMembers.size() < Party.MIN_SIZE || uniqueMembers.size() > Party.MAX_SIZE) {
            throw new IllegalArgumentException("Roster size must be between 1 and 4");
        }
        members = List.copyOf(uniqueMembers);
    }

    public boolean contains(UUID playerId) {
        return members.contains(playerId);
    }

    public int size() {
        return members.size();
    }
}
