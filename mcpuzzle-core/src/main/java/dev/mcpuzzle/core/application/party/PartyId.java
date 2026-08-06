package dev.mcpuzzle.core.application.party;

import java.util.Objects;
import java.util.UUID;

public record PartyId(UUID value) {
    public PartyId {
        Objects.requireNonNull(value, "value");
    }

    public static PartyId random() {
        return new PartyId(UUID.randomUUID());
    }
}
