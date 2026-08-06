package dev.mcpuzzle.core.domain;

import java.time.Duration;
import java.util.Objects;

public record RunMetrics(Duration activePlayTime, int failures, int hintsUsed) {
    public RunMetrics {
        Objects.requireNonNull(activePlayTime, "activePlayTime");
        if (activePlayTime.isNegative()) {
            throw new IllegalArgumentException("Active play time must not be negative");
        }
        if (failures < 0 || hintsUsed < 0) {
            throw new IllegalArgumentException("Metrics counters must not be negative");
        }
    }

    public static RunMetrics empty() {
        return new RunMetrics(Duration.ZERO, 0, 0);
    }

    public RunMetrics addActiveTime(Duration elapsed) {
        Objects.requireNonNull(elapsed, "elapsed");
        if (elapsed.isNegative()) {
            throw new IllegalArgumentException("Elapsed time must not be negative");
        }
        return new RunMetrics(activePlayTime.plus(elapsed), failures, hintsUsed);
    }

    public RunMetrics recordFailure() {
        return new RunMetrics(activePlayTime, failures + 1, hintsUsed);
    }

    public RunMetrics recordHint() {
        return new RunMetrics(activePlayTime, failures, hintsUsed + 1);
    }
}
