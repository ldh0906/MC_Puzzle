package dev.mcpuzzle.core.domain;

import java.util.Objects;
import java.util.Optional;

public final class PartyChange {
    private final Party party;
    private final PartyFailure failure;

    private PartyChange(Party party, PartyFailure failure) {
        this.party = party;
        this.failure = failure;
    }

    public static PartyChange success(Party party) {
        return new PartyChange(Objects.requireNonNull(party, "party"), null);
    }

    public static PartyChange failure(Party original, PartyFailure failure) {
        return new PartyChange(
                Objects.requireNonNull(original, "original"),
                Objects.requireNonNull(failure, "failure")
        );
    }

    public boolean succeeded() {
        return failure == null;
    }

    public Party party() {
        return party;
    }

    public Optional<PartyFailure> failure() {
        return Optional.ofNullable(failure);
    }
}
