package dev.mcpuzzle.paper.containment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Main-thread scoped one-operation permits for intentional instance entry and exit teleports. */
public final class TeleportPermitRegistry {
    private final Map<UUID, Integer> permitDepth = new HashMap<>();

    public boolean runPermitted(UUID playerId, BooleanSupplier teleport) {
        permitDepth.merge(playerId, 1, Integer::sum);
        try {
            return teleport.getAsBoolean();
        } finally {
            permitDepth.computeIfPresent(playerId, (ignored, depth) -> depth == 1 ? null : depth - 1);
        }
    }

    public boolean isPermitted(UUID playerId) {
        return permitDepth.containsKey(playerId);
    }
}
