package dev.mcpuzzle.core.mechanic;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Expiring UUID-owned lock used to keep party members from interleaving one input attempt. */
public final class InputOperatorLock {
    private UUID owner;
    private Instant expiresAt = Instant.EPOCH;

    public synchronized boolean acquire(UUID actor, Instant now, Duration duration) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(duration, "duration");
        if (duration.isNegative() || duration.isZero()) throw new IllegalArgumentException("duration must be positive");
        if (owner == null || !now.isBefore(expiresAt) || owner.equals(actor)) {
            owner = actor;
            expiresAt = now.plus(duration);
            return true;
        }
        return false;
    }

    public synchronized Optional<UUID> owner(Instant now) {
        Objects.requireNonNull(now, "now");
        if (owner != null && !now.isBefore(expiresAt)) clear();
        return Optional.ofNullable(owner);
    }

    public synchronized void release(UUID actor) {
        if (Objects.equals(owner, actor)) clear();
    }

    public synchronized void clear() {
        owner = null;
        expiresAt = Instant.EPOCH;
    }
}
