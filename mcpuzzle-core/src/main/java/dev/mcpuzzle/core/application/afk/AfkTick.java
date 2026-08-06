package dev.mcpuzzle.core.application.afk;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public record AfkTick(Duration idleFor, List<AfkSignal> signals) {
    public AfkTick {
        Objects.requireNonNull(idleFor, "idleFor");
        signals = List.copyOf(Objects.requireNonNull(signals, "signals"));
    }

    public static AfkTick inactive() {
        return new AfkTick(Duration.ZERO, List.of());
    }
}
