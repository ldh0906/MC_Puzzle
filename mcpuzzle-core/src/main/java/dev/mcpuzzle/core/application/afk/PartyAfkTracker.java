package dev.mcpuzzle.core.application.afk;

import dev.mcpuzzle.core.domain.PartyRoster;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Thread-safe party-wide AFK timer that only advances while gameplay is active. */
public final class PartyAfkTracker {
    public static final Duration EIGHT_MINUTES = Duration.ofMinutes(8);
    public static final Duration NINE_MINUTES = Duration.ofMinutes(9);
    public static final Duration TEN_MINUTES = Duration.ofMinutes(10);

    private final PartyRoster roster;
    private final Clock clock;

    private boolean active;
    private Instant lastMeaningfulActivity;
    private boolean warnedEight;
    private boolean warnedNine;

    public PartyAfkTracker(PartyRoster roster, Clock clock) {
        this.roster = Objects.requireNonNull(roster, "roster");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public synchronized void resumeActivePlay() {
        active = true;
        resetCycle(clock.instant());
    }

    public synchronized void pauseActivePlay() {
        active = false;
    }

    public synchronized boolean recordMeaningfulActivity(UUID memberId) {
        Objects.requireNonNull(memberId, "memberId");
        if (!active || !roster.contains(memberId)) {
            return false;
        }
        resetCycle(clock.instant());
        return true;
    }

    public synchronized AfkTick tick() {
        if (!active) {
            return AfkTick.inactive();
        }
        Instant now = clock.instant();
        if (now.isBefore(lastMeaningfulActivity)) {
            throw new IllegalStateException("Clock moved backwards during an AFK cycle");
        }
        Duration idle = Duration.between(lastMeaningfulActivity, now);
        List<AfkSignal> signals = new ArrayList<>(3);
        if (!warnedEight && idle.compareTo(EIGHT_MINUTES) >= 0) {
            warnedEight = true;
            signals.add(AfkSignal.WARNING_EIGHT_MINUTES);
        }
        if (!warnedNine && idle.compareTo(NINE_MINUTES) >= 0) {
            warnedNine = true;
            signals.add(AfkSignal.WARNING_NINE_MINUTES);
        }
        if (idle.compareTo(TEN_MINUTES) >= 0) {
            active = false;
            signals.add(AfkSignal.SUSPEND_TEN_MINUTES);
        }
        return new AfkTick(idle, signals);
    }

    public synchronized boolean isActive() {
        return active;
    }

    private void resetCycle(Instant at) {
        lastMeaningfulActivity = at;
        warnedEight = false;
        warnedNine = false;
    }
}
