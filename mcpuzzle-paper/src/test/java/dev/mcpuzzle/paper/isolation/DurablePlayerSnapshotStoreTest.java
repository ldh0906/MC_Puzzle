package dev.mcpuzzle.paper.isolation;

import dev.mcpuzzle.core.domain.SessionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurablePlayerSnapshotStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsChecksummedSnapshotsAndDeletesThemAfterRestore() throws Exception {
        DurablePlayerSnapshotStore store = new DurablePlayerSnapshotStore(temporaryDirectory);
        SnapshotKey first = new SnapshotKey(SessionId.random(), UUID.randomUUID());
        SnapshotKey second = new SnapshotKey(SessionId.random(), UUID.randomUUID());
        byte[] firstPayload = new byte[]{1, 2, 3, 4};
        byte[] secondPayload = "serialized-bukkit-state".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        store.saveBatch(Map.of(first, firstPayload, second, secondPayload));
        SnapshotLoadResult loaded = store.loadAll();

        assertEquals(2, loaded.snapshots().size());
        assertArrayEquals(firstPayload, loaded.snapshots().get(first));
        assertArrayEquals(secondPayload, loaded.snapshots().get(second));
        assertTrue(loaded.corruptions().isEmpty());

        store.deleteBatch(loaded.snapshots().keySet());
        assertTrue(store.loadAll().snapshots().isEmpty());
    }

    @Test
    void corruptEnvelopeIsQuarantinedAndPlayerIdentityIsReportedFailClosed() throws Exception {
        DurablePlayerSnapshotStore store = new DurablePlayerSnapshotStore(temporaryDirectory);
        SnapshotKey key = new SnapshotKey(SessionId.random(), UUID.randomUUID());
        Path target = store.pathFor(key);
        Files.createDirectories(target.getParent());
        Files.write(target, new byte[]{0, 1, 2, 3, 4});

        SnapshotLoadResult loaded = store.loadAll();

        assertTrue(loaded.snapshots().isEmpty());
        assertEquals(1, loaded.corruptions().size());
        assertEquals(key.playerId(), loaded.corruptions().get(0).playerId().orElseThrow());
        assertFalse(Files.exists(target));
        assertTrue(Files.exists(loaded.corruptions().get(0).quarantinedFile()));
    }

    @Test
    void batchRefusesOverwriteBeforeWritingAnyNewSnapshot() throws Exception {
        DurablePlayerSnapshotStore store = new DurablePlayerSnapshotStore(temporaryDirectory);
        SessionId sessionId = SessionId.random();
        SnapshotKey existing = new SnapshotKey(sessionId, UUID.randomUUID());
        SnapshotKey newKey = new SnapshotKey(sessionId, UUID.randomUUID());
        store.saveBatch(Map.of(existing, new byte[]{9}));
        Map<SnapshotKey, byte[]> attempted = new LinkedHashMap<>();
        attempted.put(newKey, new byte[]{1});
        attempted.put(existing, new byte[]{2});

        assertThrows(Exception.class, () -> store.saveBatch(attempted));

        SnapshotLoadResult loaded = store.loadAll();
        assertEquals(1, loaded.snapshots().size());
        assertArrayEquals(new byte[]{9}, loaded.snapshots().get(existing));
        assertFalse(loaded.snapshots().containsKey(newKey));
    }
}
