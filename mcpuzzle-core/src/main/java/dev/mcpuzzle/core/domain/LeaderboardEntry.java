package dev.mcpuzzle.core.domain;

import java.util.Objects;

public record LeaderboardEntry(int rank, CompletedRun run) {
    public LeaderboardEntry {
        if (rank < 1) {
            throw new IllegalArgumentException("Rank must be positive");
        }
        Objects.requireNonNull(run, "run");
    }
}
