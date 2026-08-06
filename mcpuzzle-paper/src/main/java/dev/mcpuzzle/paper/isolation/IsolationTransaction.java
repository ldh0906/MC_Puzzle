package dev.mcpuzzle.paper.isolation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class IsolationTransaction<K, S> {
    @FunctionalInterface
    interface Capture<K, S> {
        S capture(K key) throws Exception;
    }

    @FunctionalInterface
    interface Mutation<K, S> {
        void apply(K key, S snapshot) throws Exception;
    }

    Map<K, S> execute(List<K> keys, Capture<K, S> capture, Mutation<K, S> enter, Mutation<K, S> rollback)
            throws Exception {
        Objects.requireNonNull(keys, "keys");
        Map<K, S> snapshots = new LinkedHashMap<>();
        for (K key : keys) {
            S previous = snapshots.put(key, capture.capture(key));
            if (previous != null) {
                throw new IllegalArgumentException("Isolation transaction keys must be unique");
            }
        }

        List<K> touched = new ArrayList<>();
        try {
            for (Map.Entry<K, S> entry : snapshots.entrySet()) {
                // Include the current entry because a mutation may fail after partially changing state.
                touched.add(entry.getKey());
                enter.apply(entry.getKey(), entry.getValue());
            }
            return Map.copyOf(snapshots);
        } catch (Exception enterFailure) {
            for (int index = touched.size() - 1; index >= 0; index--) {
                K key = touched.get(index);
                try {
                    rollback.apply(key, snapshots.get(key));
                } catch (Exception rollbackFailure) {
                    enterFailure.addSuppressed(rollbackFailure);
                }
            }
            throw enterFailure;
        }
    }
}
