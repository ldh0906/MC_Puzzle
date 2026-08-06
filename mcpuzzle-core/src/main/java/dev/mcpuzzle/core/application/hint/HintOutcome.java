package dev.mcpuzzle.core.application.hint;

import dev.mcpuzzle.core.domain.HintProgress;

import java.util.Objects;
import java.util.OptionalInt;

public record HintOutcome(
        HintOutcomeType type,
        OptionalInt tier,
        HintProgress progress
) {
    public HintOutcome {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(progress, "progress");
    }

    public static HintOutcome of(HintOutcomeType type, int tier, HintProgress progress) {
        return new HintOutcome(type, OptionalInt.of(tier), progress);
    }

    public static HintOutcome withoutTier(HintOutcomeType type, HintProgress progress) {
        return new HintOutcome(type, OptionalInt.empty(), progress);
    }
}
