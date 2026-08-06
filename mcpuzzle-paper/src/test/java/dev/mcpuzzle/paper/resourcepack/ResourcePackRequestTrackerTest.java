package dev.mcpuzzle.paper.resourcepack;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackRequestTrackerTest {
    @Test
    void ignoresStatusesForPacksSentByOtherPlugins() {
        ResourcePackRequestTracker tracker = new ResourcePackRequestTracker();
        UUID player = UUID.randomUUID();
        assertFalse(tracker.succeeded(player));
        assertFalse(tracker.failed(player));
        assertFalse(tracker.loaded(player));
    }

    @Test
    void acceptsExactlyOneStatusForOurRequestAndClearsAcrossJoinQuit() {
        ResourcePackRequestTracker tracker = new ResourcePackRequestTracker();
        UUID player = UUID.randomUUID();
        tracker.sent(player);
        assertTrue(tracker.succeeded(player));
        assertTrue(tracker.loaded(player));
        assertFalse(tracker.failed(player));
        tracker.joined(player);
        assertFalse(tracker.loaded(player));
        tracker.sent(player);
        assertTrue(tracker.failed(player));
        tracker.left(player);
        assertFalse(tracker.loaded(player));
    }
}
