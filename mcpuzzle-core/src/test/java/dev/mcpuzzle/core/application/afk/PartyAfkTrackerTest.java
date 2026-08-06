package dev.mcpuzzle.core.application.afk;

import dev.mcpuzzle.core.domain.PartyRoster;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartyAfkTrackerTest {
    @Test
    void warnsOnceAtEightAndNineThenSuspendsAtTen() {
        UUID leader = UUID.randomUUID();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T00:00:00Z"));
        PartyAfkTracker tracker = new PartyAfkTracker(
                new PartyRoster(leader, List.of(leader)),
                clock
        );
        tracker.resumeActivePlay();

        clock.advance(Duration.ofMinutes(8));
        assertEquals(List.of(AfkSignal.WARNING_EIGHT_MINUTES), tracker.tick().signals());
        assertTrue(tracker.tick().signals().isEmpty());
        clock.advance(Duration.ofMinutes(1));
        assertEquals(List.of(AfkSignal.WARNING_NINE_MINUTES), tracker.tick().signals());
        clock.advance(Duration.ofMinutes(1));
        assertEquals(List.of(AfkSignal.SUSPEND_TEN_MINUTES), tracker.tick().signals());
        assertFalse(tracker.isActive());
        assertTrue(tracker.tick().signals().isEmpty());
    }

    @Test
    void anyMeaningfulMemberActivityResetsWarningCycle() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T00:00:00Z"));
        PartyAfkTracker tracker = new PartyAfkTracker(
                new PartyRoster(leader, List.of(leader, member)),
                clock
        );
        tracker.resumeActivePlay();
        clock.advance(Duration.ofMinutes(9));
        assertEquals(
                List.of(AfkSignal.WARNING_EIGHT_MINUTES, AfkSignal.WARNING_NINE_MINUTES),
                tracker.tick().signals()
        );

        assertTrue(tracker.recordMeaningfulActivity(member));
        clock.advance(Duration.ofMinutes(8));
        assertEquals(List.of(AfkSignal.WARNING_EIGHT_MINUTES), tracker.tick().signals());
        assertFalse(tracker.recordMeaningfulActivity(UUID.randomUUID()));
    }

    @Test
    void pausedTimeDoesNotProduceSignalsAndResumeStartsFreshCycle() {
        UUID leader = UUID.randomUUID();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-05T00:00:00Z"));
        PartyAfkTracker tracker = new PartyAfkTracker(
                new PartyRoster(leader, List.of(leader)),
                clock
        );

        clock.advance(Duration.ofHours(1));
        assertTrue(tracker.tick().signals().isEmpty());
        tracker.resumeActivePlay();
        clock.advance(Duration.ofMinutes(7));
        tracker.pauseActivePlay();
        clock.advance(Duration.ofHours(2));
        assertTrue(tracker.tick().signals().isEmpty());
        tracker.resumeActivePlay();
        clock.advance(Duration.ofMinutes(8));
        assertEquals(List.of(AfkSignal.WARNING_EIGHT_MINUTES), tracker.tick().signals());
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
