package dev.mcpuzzle.paper.resourcepack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Correlates status events to packs sent by this plugin, not other server content. */
final class ResourcePackRequestTracker {
    private final Set<UUID> sent = ConcurrentHashMap.newKeySet();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    void joined(UUID player) { sent.remove(player); loaded.remove(player); }
    void sent(UUID player) { sent.add(player); loaded.remove(player); }
    boolean succeeded(UUID player) {
        if (!sent.remove(player)) return false;
        loaded.add(player); return true;
    }
    boolean failed(UUID player) {
        if (!sent.remove(player)) return false;
        loaded.remove(player); return true;
    }
    boolean loaded(UUID player) { return loaded.contains(player); }
    void left(UUID player) { sent.remove(player); loaded.remove(player); }
    void clear() { sent.clear(); loaded.clear(); }
}
