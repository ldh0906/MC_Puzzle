package dev.mcpuzzle.paper.instance;

import dev.mcpuzzle.core.domain.SessionId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime ownership index shared by independently registered platform adapters. */
public final class InstanceRuntimeRegistry {
    private final Map<SessionId, String> worldsBySession = new ConcurrentHashMap<>();
    private final Map<String, SessionId> sessionsByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, SessionId> sessionsByPlayer = new ConcurrentHashMap<>();

    public void registerWorld(SessionId sessionId, String worldName) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(worldName, "worldName");
        SessionId priorWorldOwner = sessionsByWorld.putIfAbsent(worldName, sessionId);
        if (priorWorldOwner != null && !priorWorldOwner.equals(sessionId)) {
            throw new IllegalStateException("Instance world is already owned: " + worldName);
        }
        String priorName = worldsBySession.putIfAbsent(sessionId, worldName);
        if (priorName != null && !priorName.equals(worldName)) {
            sessionsByWorld.remove(worldName, sessionId);
            throw new IllegalStateException("Session already owns an instance world: " + sessionId);
        }
    }

    public void unregisterWorld(SessionId sessionId, String worldName) {
        worldsBySession.remove(sessionId, worldName);
        sessionsByWorld.remove(worldName, sessionId);
    }

    public void attachPlayer(SessionId sessionId, UUID playerId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        SessionId previous = sessionsByPlayer.putIfAbsent(playerId, sessionId);
        if (previous != null && !previous.equals(sessionId)) {
            throw new IllegalStateException("Player already belongs to another instance: " + playerId);
        }
    }

    public void detachPlayer(SessionId sessionId, UUID playerId) {
        sessionsByPlayer.remove(playerId, sessionId);
    }

    public Optional<SessionId> sessionOfPlayer(UUID playerId) {
        return Optional.ofNullable(sessionsByPlayer.get(playerId));
    }

    public Optional<SessionId> sessionOfWorld(String worldName) {
        return Optional.ofNullable(sessionsByWorld.get(worldName));
    }

    public Optional<String> worldOf(SessionId sessionId) {
        return Optional.ofNullable(worldsBySession.get(sessionId));
    }

    public boolean sameInstance(UUID first, UUID second) {
        SessionId firstSession = sessionsByPlayer.get(first);
        return firstSession != null && firstSession.equals(sessionsByPlayer.get(second));
    }

    public Set<String> activeWorldNames() {
        return Set.copyOf(sessionsByWorld.keySet());
    }
}
