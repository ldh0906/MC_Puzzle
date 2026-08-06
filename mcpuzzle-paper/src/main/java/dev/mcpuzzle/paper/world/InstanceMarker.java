package dev.mcpuzzle.paper.world;

import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

record InstanceMarker(
        SessionId sessionId,
        String mazeId,
        MapVersion mapVersion,
        PartyRoster roster,
        Instant createdAt
) {
    Properties toProperties() {
        Properties properties = new Properties();
        properties.setProperty("format", "1");
        properties.setProperty("session-id", sessionId.toString());
        properties.setProperty("maze-id", mazeId);
        properties.setProperty("map-version", mapVersion.toString());
        properties.setProperty("leader-id", roster.leaderId().toString());
        properties.setProperty("members", roster.members().stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse(""));
        properties.setProperty("created-at", createdAt.toString());
        return properties;
    }

    static InstanceMarker fromProperties(Properties properties) {
        if (!"1".equals(properties.getProperty("format"))) {
            throw new IllegalArgumentException("Unsupported instance marker format");
        }
        SessionId sessionId = new SessionId(UUID.fromString(required(properties, "session-id")));
        String mazeId = required(properties, "maze-id");
        MapVersion version = new MapVersion(required(properties, "map-version"));
        UUID leaderId = UUID.fromString(required(properties, "leader-id"));
        String memberValue = required(properties, "members");
        List<UUID> members = Arrays.stream(memberValue.split(","))
                .filter(value -> !value.isBlank())
                .map(UUID::fromString)
                .toList();
        return new InstanceMarker(sessionId, mazeId, version, new PartyRoster(leaderId, members),
                Instant.parse(required(properties, "created-at")));
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing instance marker property: " + key);
        }
        return value;
    }
}
