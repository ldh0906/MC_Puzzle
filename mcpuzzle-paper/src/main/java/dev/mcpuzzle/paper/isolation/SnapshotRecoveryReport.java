package dev.mcpuzzle.paper.isolation;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SnapshotRecoveryReport(int pendingSnapshots, Set<UUID> blockedPlayers, List<String> warnings) {
    public SnapshotRecoveryReport {
        blockedPlayers = Set.copyOf(blockedPlayers);
        warnings = List.copyOf(warnings);
    }
}
