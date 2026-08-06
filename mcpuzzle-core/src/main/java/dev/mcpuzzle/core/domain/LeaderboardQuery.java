package dev.mcpuzzle.core.domain;

import java.util.Objects;

public record LeaderboardQuery(String mazeId, MapVersion mapVersion, int partySize, int limit) {
    public LeaderboardQuery {
        Objects.requireNonNull(mazeId, "mazeId");
        mazeId = mazeId.trim();
        if (mazeId.isEmpty()) {
            throw new IllegalArgumentException("Maze id must not be blank");
        }
        Objects.requireNonNull(mapVersion, "mapVersion");
        if (partySize < Party.MIN_SIZE || partySize > Party.MAX_SIZE) {
            throw new IllegalArgumentException("Party size must be between 1 and 4");
        }
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Leaderboard limit must be between 1 and 100");
        }
    }
}
