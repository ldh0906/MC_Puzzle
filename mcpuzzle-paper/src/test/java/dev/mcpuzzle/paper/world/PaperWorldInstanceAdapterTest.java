package dev.mcpuzzle.paper.world;

import dev.mcpuzzle.core.domain.MapVersion;
import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.port.WorldInstanceHandle;
import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperWorldInstanceAdapterTest {
    @TempDir
    Path temporaryDirectory;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach
    void stopScheduler() {
        scheduler.shutdownNow();
    }

    @Test
    void provisionsFilteredCopyAndReleasesOnlyItsMarkedFolder() throws Exception {
        Path templates = temporaryDirectory.resolve("templates");
        Path worlds = temporaryDirectory.resolve("worlds");
        createTemplate(templates, "classic", "v1");
        RecordingWorlds lifecycle = new RecordingWorlds();
        InstanceRuntimeRegistry registry = new InstanceRuntimeRegistry();
        PaperWorldInstanceAdapter adapter = adapter(templates, worlds, lifecycle, registry);
        SessionId sessionId = SessionId.random();

        WorldInstanceHandle handle = adapter.provision(
                sessionId, "classic", new MapVersion("v1"), roster()
        ).toCompletableFuture().join();

        Path instance = worlds.resolve(handle.instanceName());
        assertTrue(Files.exists(instance.resolve("level.dat")));
        assertTrue(Files.exists(instance.resolve("region/r.0.0.mca")));
        assertTrue(Files.exists(instance.resolve(WorldInstanceFileStore.MARKER_FILE)));
        assertFalse(Files.exists(instance.resolve("uid.dat")));
        assertFalse(Files.exists(instance.resolve("session.lock")));
        assertFalse(Files.exists(instance.resolve("playerdata")));
        assertFalse(Files.exists(instance.resolve("stats")));
        assertEquals(List.of(handle.instanceName()), lifecycle.loaded);
        assertEquals(sessionId, registry.sessionOfWorld(handle.instanceName()).orElseThrow());

        adapter.release(sessionId, handle).toCompletableFuture().join();

        assertFalse(Files.exists(instance));
        assertEquals(List.of(handle.instanceName()), lifecycle.unloaded);
        assertTrue(registry.sessionOfWorld(handle.instanceName()).isEmpty());
    }

    @Test
    void rejectsPathTraversalBeforeCopying() throws Exception {
        Path templates = temporaryDirectory.resolve("templates");
        Path worlds = temporaryDirectory.resolve("worlds");
        Files.createDirectories(templates);
        Files.createDirectories(worlds);
        PaperWorldInstanceAdapter adapter = adapter(
                templates, worlds, new RecordingWorlds(), new InstanceRuntimeRegistry());

        assertThrows(Exception.class, () -> adapter.provision(
                SessionId.random(), "../outside", new MapVersion("v1"), roster()
        ).toCompletableFuture().join());
        assertEquals(0L, Files.list(worlds).count());
    }

    @Test
    void removesMarkedCopyWhenPaperLoadFails() throws Exception {
        Path templates = temporaryDirectory.resolve("templates");
        Path worlds = temporaryDirectory.resolve("worlds");
        createTemplate(templates, "classic", "v1");
        RecordingWorlds lifecycle = new RecordingWorlds();
        lifecycle.failLoad = true;
        PaperWorldInstanceAdapter adapter = adapter(
                templates, worlds, lifecycle, new InstanceRuntimeRegistry());

        assertThrows(Exception.class, () -> adapter.provision(
                SessionId.random(), "classic", new MapVersion("v1"), roster()
        ).toCompletableFuture().join());
        assertEquals(0L, Files.list(worlds).count());
    }

    @Test
    void startupDiscoveryReturnsOnlyValidUnregisteredMarkers() throws Exception {
        Path templates = temporaryDirectory.resolve("templates");
        Path worlds = temporaryDirectory.resolve("worlds");
        createTemplate(templates, "classic", "v1");
        InstanceRuntimeRegistry firstRegistry = new InstanceRuntimeRegistry();
        PaperWorldInstanceAdapter first = adapter(templates, worlds, new RecordingWorlds(), firstRegistry);
        SessionId sessionId = SessionId.random();
        WorldInstanceHandle handle = first.provision(
                sessionId, "classic", new MapVersion("v1"), roster()
        ).toCompletableFuture().join();
        Files.createDirectories(worlds.resolve("mcpuzzle_not_owned"));

        PaperWorldInstanceAdapter afterRestart = adapter(
                templates, worlds, new RecordingWorlds(), new InstanceRuntimeRegistry());
        List<OrphanedWorldInstance> orphans = afterRestart.discoverOrphans().toCompletableFuture().join();

        assertEquals(1, orphans.size());
        assertEquals(sessionId, orphans.get(0).sessionId());
        assertEquals(handle.instanceName(), orphans.get(0).worldName());
    }

    private PaperWorldInstanceAdapter adapter(
            Path templates,
            Path worlds,
            RecordingWorlds lifecycle,
            InstanceRuntimeRegistry registry
    ) {
        return new PaperWorldInstanceAdapter(
                templates,
                worlds,
                lifecycle,
                registry,
                Runnable::run,
                scheduler,
                Clock.fixed(Instant.parse("2026-08-05T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private void createTemplate(Path root, String maze, String version) throws IOException {
        Path template = root.resolve(maze).resolve(version);
        Files.createDirectories(template.resolve("region"));
        Files.createDirectories(template.resolve("playerdata"));
        Files.createDirectories(template.resolve("stats"));
        Files.createDirectories(template.resolve("advancements"));
        Files.writeString(template.resolve("level.dat"), "level");
        Files.writeString(template.resolve("region/r.0.0.mca"), "region");
        Files.writeString(template.resolve("uid.dat"), "uid");
        Files.writeString(template.resolve("session.lock"), "lock");
        Files.writeString(template.resolve("playerdata/player.dat"), "player");
        Files.writeString(template.resolve("stats/player.json"), "stats");
    }

    private PartyRoster roster() {
        UUID leader = UUID.randomUUID();
        return new PartyRoster(leader, List.of(leader));
    }

    private static final class RecordingWorlds implements WorldLifecycleGateway {
        private final List<String> loaded = new java.util.ArrayList<>();
        private final List<String> unloaded = new java.util.ArrayList<>();
        private boolean failLoad;

        @Override
        public CompletionStage<Void> load(String worldName) {
            loaded.add(worldName);
            return failLoad
                    ? CompletableFuture.failedFuture(new IllegalStateException("load failed"))
                    : CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Boolean> unload(String worldName, boolean save) {
            unloaded.add(worldName);
            return CompletableFuture.completedFuture(true);
        }
    }
}
