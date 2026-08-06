package dev.mcpuzzle.paper.isolation;

import java.util.List;
import java.util.Map;

public record SnapshotLoadResult(Map<SnapshotKey, byte[]> snapshots, List<SnapshotCorruption> corruptions) {
    public SnapshotLoadResult {
        snapshots = Map.copyOf(snapshots);
        corruptions = List.copyOf(corruptions);
    }
}
