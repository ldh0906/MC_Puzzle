package dev.mcpuzzle.core.application.party;

import dev.mcpuzzle.core.domain.PartyRoster;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PartyView(
        PartyId id,
        UUID leaderId,
        List<UUID> members,
        Set<UUID> pendingInvites,
        PartyLifecycle lifecycle
) {
    public PartyView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(leaderId, "leaderId");
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        pendingInvites = Set.copyOf(Objects.requireNonNull(pendingInvites, "pendingInvites"));
        Objects.requireNonNull(lifecycle, "lifecycle");
    }

    public PartyRoster roster() {
        return new PartyRoster(leaderId, members);
    }
}
