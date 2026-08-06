package dev.mcpuzzle.core.application.party;

import java.util.Objects;
import java.util.Optional;

public final class PartyServiceResult {
    private final PartyView party;
    private final PartyServiceError error;

    private PartyServiceResult(PartyView party, PartyServiceError error) {
        this.party = party;
        this.error = error;
    }

    public static PartyServiceResult success(PartyView party) {
        return new PartyServiceResult(Objects.requireNonNull(party, "party"), null);
    }

    public static PartyServiceResult failure(PartyServiceError error) {
        return new PartyServiceResult(null, Objects.requireNonNull(error, "error"));
    }

    public boolean succeeded() {
        return error == null;
    }

    public Optional<PartyView> party() {
        return Optional.ofNullable(party);
    }

    public Optional<PartyServiceError> error() {
        return Optional.ofNullable(error);
    }
}
