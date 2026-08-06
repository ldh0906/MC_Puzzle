package dev.mcpuzzle.paper.isolation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IsolationTransactionTest {
    @Test
    void rollsBackEveryPossiblyTouchedPlayerInReverseOrder() {
        IsolationTransaction<Integer, String> transaction = new IsolationTransaction<>();
        List<String> actions = new ArrayList<>();

        Exception failure = assertThrows(Exception.class, () -> transaction.execute(
                List.of(1, 2, 3),
                key -> "snapshot-" + key,
                (key, snapshot) -> {
                    actions.add("enter-" + key);
                    if (key == 2) {
                        throw new IllegalStateException("teleport failed");
                    }
                },
                (key, snapshot) -> actions.add("rollback-" + key + "-" + snapshot)
        ));

        assertEquals("teleport failed", failure.getMessage());
        assertEquals(List.of(
                "enter-1", "enter-2", "rollback-2-snapshot-2", "rollback-1-snapshot-1"
        ), actions);
    }

    @Test
    void returnsAllSnapshotsOnlyAfterEveryMutationSucceeds() throws Exception {
        IsolationTransaction<Integer, String> transaction = new IsolationTransaction<>();
        Map<Integer, String> snapshots = transaction.execute(
                List.of(1, 2), key -> "snapshot-" + key, (key, snapshot) -> { }, (key, snapshot) -> { }
        );
        assertEquals(Map.of(1, "snapshot-1", 2, "snapshot-2"), snapshots);
    }
}
