package dev.mcpuzzle.core.application.admission;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record AvailabilityCheck(AvailabilityStatus status, Optional<UUID> unavailableMember) {
    public AvailabilityCheck {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(unavailableMember, "unavailableMember");
        if ((status == AvailabilityStatus.ELIGIBLE) == unavailableMember.isPresent()) {
            throw new IllegalArgumentException("Only unavailable results identify a member");
        }
    }

    public static AvailabilityCheck eligible() {
        return new AvailabilityCheck(AvailabilityStatus.ELIGIBLE, Optional.empty());
    }

    public static AvailabilityCheck unavailable(AvailabilityStatus status, UUID memberId) {
        if (status == AvailabilityStatus.ELIGIBLE) {
            throw new IllegalArgumentException("Eligible status cannot be unavailable");
        }
        return new AvailabilityCheck(status, Optional.of(Objects.requireNonNull(memberId, "memberId")));
    }

    public boolean isEligible() {
        return status == AvailabilityStatus.ELIGIBLE;
    }
}
