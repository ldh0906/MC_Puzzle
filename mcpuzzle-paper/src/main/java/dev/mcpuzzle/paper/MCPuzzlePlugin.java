package dev.mcpuzzle.paper;

import dev.mcpuzzle.core.application.admission.InstanceAdmissionQueue;
import dev.mcpuzzle.core.application.party.PartyService;
import dev.mcpuzzle.paper.adapter.persistence.SQLitePersistence;
import dev.mcpuzzle.paper.authoring.AuthoringWandService;
import dev.mcpuzzle.paper.command.MazeCommand;
import dev.mcpuzzle.paper.config.MCPuzzleConfig;
import dev.mcpuzzle.paper.containment.CommandContainmentListener;
import dev.mcpuzzle.paper.containment.CommandContainmentPolicy;
import dev.mcpuzzle.paper.containment.ContainmentPolicy;
import dev.mcpuzzle.paper.containment.DamageContainmentListener;
import dev.mcpuzzle.paper.containment.ItemContainmentListener;
import dev.mcpuzzle.paper.containment.PortalContainmentListener;
import dev.mcpuzzle.paper.containment.TeleportPermitRegistry;
import dev.mcpuzzle.paper.containment.VisibilityContainmentListener;
import dev.mcpuzzle.paper.containment.VisibilityIsolationService;
import dev.mcpuzzle.paper.containment.WorldBoundaryContainmentListener;
import dev.mcpuzzle.paper.gui.MazeMenu;
import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;
import dev.mcpuzzle.paper.isolation.DurablePlayerSnapshotStore;
import dev.mcpuzzle.paper.isolation.PaperPlayerIsolationAdapter;
import dev.mcpuzzle.paper.isolation.PendingSnapshotRestoreListener;
import dev.mcpuzzle.paper.listener.MazeGameplayListener;
import dev.mcpuzzle.paper.map.JsoncMapPackLoader;
import dev.mcpuzzle.paper.map.MapPack;
import dev.mcpuzzle.paper.map.MapPackLoadException;
import dev.mcpuzzle.paper.map.MapPackRegistry;
import dev.mcpuzzle.paper.resourcepack.ResourcePackGate;
import dev.mcpuzzle.paper.resourcepack.LocalResourcePackServer;
import dev.mcpuzzle.paper.runtime.MazeRuntimeService;
import dev.mcpuzzle.paper.runtime.PluginReadiness;
import dev.mcpuzzle.paper.thread.BukkitMainThreadGateway;
import dev.mcpuzzle.paper.world.GeneratedVoidWorldInstanceAdapter;
import dev.mcpuzzle.paper.world.GeneratedWorldVerifier;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MCPuzzlePlugin extends JavaPlugin {
    private final PluginReadiness readiness = new PluginReadiness();
    private ExecutorService filesExecutor;
    private ScheduledExecutorService retryExecutor;
    private SQLitePersistence persistence;
    private PaperPlayerIsolationAdapter isolation;
    private GeneratedVoidWorldInstanceAdapter worlds;
    private GeneratedWorldVerifier worldVerifier;
    private MazeRuntimeService runtime;
    private MapPackRegistry mapRegistry;
    private ResourcePackGate resourcePacks;
    private LocalResourcePackServer localResourcePackServer;
    private MCPuzzleConfig configuration;
    private BukkitTask tickTask;
    private volatile boolean shuttingDown;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // This is the versioned, plugin-owned active pack. Overwrite it on upgrade so an
        // older extraction cannot silently keep the server on obsolete room content.
        saveResource("map-packs/a-to-z-archive-20/map.jsonc", true);
        saveResource("map-packs/schema/map-pack.schema.json", true);
        saveResource("resource-pack/MCPuzzle-1.0.0.zip", true);
        try {
            configuration = MCPuzzleConfig.load(getConfig());
            mapRegistry = new MapPackRegistry(new JsoncMapPackLoader(),
                    getDataFolder().toPath().resolve("map-packs/a-to-z-archive-20/map.jsonc"));
            MapPack map = mapRegistry.reload();
            bootstrap(map);
        } catch (Exception failure) {
            readiness.degraded("초기 설정/맵 오류: " + rootMessage(failure));
            getLogger().severe(readiness.detail());
            registerStartingCommand();
        }
    }

    private void bootstrap(MapPack map) {
        filesExecutor = Executors.newFixedThreadPool(2, namedFactory("mcpuzzle-files"));
        retryExecutor = Executors.newSingleThreadScheduledExecutor(namedFactory("mcpuzzle-world-retry"));
        InstanceRuntimeRegistry ownership = new InstanceRuntimeRegistry();
        TeleportPermitRegistry teleportPermits = new TeleportPermitRegistry();
        ContainmentPolicy containment = new ContainmentPolicy(configuration.operatorBypass());
        VisibilityIsolationService visibility = new VisibilityIsolationService(this, getServer(), ownership, containment);
        BukkitMainThreadGateway mainThread = new BukkitMainThreadGateway(this);
        isolation = new PaperPlayerIsolationAdapter(getServer(), mainThread, ownership, teleportPermits,
                configuration.lobby(), new DurablePlayerSnapshotStore(getDataFolder().toPath().resolve("player-snapshots")),
                filesExecutor);
        worlds = new GeneratedVoidWorldInstanceAdapter(this, getServer(), mainThread, ownership,
                getServer().getWorldContainer().toPath(), filesExecutor, retryExecutor, map);
        localResourcePackServer = new LocalResourcePackServer(this);
        try {
            localResourcePackServer.start(configuration.resourcePack());
        } catch (Exception failure) {
            throw new IllegalStateException("로컬 리소스 팩 서버 시작 실패: " + rootMessage(failure), failure);
        }
        resourcePacks = new ResourcePackGate(this, configuration.resourcePack());
        registerPlatformListeners(ownership, teleportPermits, containment, visibility);
        registerStartingCommand();

        Path database = getDataFolder().toPath().resolve("mcpuzzle.db");
        CompletionStage<SQLitePersistence> opening = SQLitePersistence.open(database);
        opening.thenCompose(opened -> {
            persistence = opened;
            CompletionStage<?> recovery = opened.recoverAfterRestart().thenAccept(report -> getLogger().info(
                    "시작 복구: 대기 삭제 " + report.discardedTransientAdmissions().size()
                            + ", 실행 삭제 " + report.discardedInterruptedRuns().size()
                            + ", 중단 세이브 유지 " + report.retainedSuspended().size()));
            CompletionStage<?> expiry = opened.purgeExpired(Instant.now()).thenAccept(count -> {
                if (count > 0) getLogger().info("만료 세이브 " + count + "개를 정리했습니다.");
            });
            CompletionStage<?> snapshots = isolation.loadPendingSnapshots().thenAccept(report -> {
                if (report.pendingSnapshots() > 0) getLogger().warning("복원 대기 플레이어 스냅샷: " + report.pendingSnapshots());
                report.warnings().forEach(getLogger()::warning);
            });
            CompletionStage<?> orphans = worlds.discoverOrphans().thenCompose(found -> {
                List<CompletionStage<Void>> cleanup = found.stream().map(orphan -> worlds.cleanupOrphan(orphan)
                        .exceptionally(failure -> { getLogger().warning("고아 월드 정리 실패 " + orphan.worldName() + ": " + rootMessage(failure)); return null; })).toList();
                return CompletableFuture.allOf(cleanup.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new));
            });
            return CompletableFuture.allOf(recovery.toCompletableFuture(), expiry.toCompletableFuture(),
                    snapshots.toCompletableFuture(), orphans.toCompletableFuture()).thenApply(ignored -> opened);
        }).whenComplete((opened, failure) -> {
            if (shuttingDown) {
                if (opened != null) opened.closeAsync();
                return;
            }
            onMain(() -> {
                if (failure != null) {
                    readiness.degraded("데이터 복구 실패: " + rootMessage(failure));
                    getLogger().severe(readiness.detail());
                    return;
                }
                finishBootstrap(map, ownership, teleportPermits, visibility);
            });
        });
    }

    private void finishBootstrap(MapPack map, InstanceRuntimeRegistry ownership,
                                 TeleportPermitRegistry teleportPermits, VisibilityIsolationService visibility) {
        runtime = new MazeRuntimeService(this, Clock.systemUTC(), readiness, map, new PartyService(),
                new InstanceAdmissionQueue(configuration.maxActiveWorlds()), persistence, worlds, isolation,
                resourcePacks, teleportPermits, visibility);
        resourcePacks.setFailureHandler(runtime::onDisconnect);
        AuthoringWandService authoring = new AuthoringWandService(this);
        MazeMenu menu = new MazeMenu(this, runtime);
        worldVerifier = new GeneratedWorldVerifier(getServer(), new BukkitMainThreadGateway(this), worlds, map);
        MazeCommand command = new MazeCommand(runtime, menu, readiness, authoring,
                this::reloadFromCommand, this::verifyWorldFromCommand);
        PluginCommand maze = requireMazeCommand();
        maze.setExecutor(command); maze.setTabCompleter(command);
        register(menu, authoring, new MazeGameplayListener(this, runtime, ownership));
        tickTask = getServer().getScheduler().runTaskTimer(this, runtime::tick, 20L, 1L);
        for (var player : getServer().getOnlinePlayers()) isolation.restorePendingPlayer(player.getUniqueId());
        if (configuration.resourcePack().configured()) {
            readiness.ready();
            getLogger().info("MCPuzzle 준비 완료: " + map.displayName() + " / " + map.rooms().size() + "개 방");
            getLogger().info("MCPuzzle READY maze=" + map.mazeId()
                    + " version=" + map.mapVersion() + " rooms=" + map.rooms().size());
        } else {
            readiness.degraded("필수 리소스 팩 설정 필요: " + configuration.resourcePack().problem().orElse("알 수 없음"));
            getLogger().warning(readiness.detail());
        }
    }

    private void registerPlatformListeners(InstanceRuntimeRegistry ownership, TeleportPermitRegistry permits,
                                           ContainmentPolicy containment, VisibilityIsolationService visibility) {
        register(resourcePacks,
                new PendingSnapshotRestoreListener(this, isolation),
                new WorldBoundaryContainmentListener(ownership, containment, permits),
                new PortalContainmentListener(ownership, containment),
                new DamageContainmentListener(ownership, containment),
                new ItemContainmentListener(this, ownership, containment),
                new CommandContainmentListener(ownership,
                        new CommandContainmentPolicy(configuration.allowedInstanceCommands(), configuration.operatorBypass())),
                new VisibilityContainmentListener(this, visibility));
    }

    private void reloadFromCommand(CommandSender sender) {
        try {
            reloadConfig();
            MCPuzzleConfig replacement = MCPuzzleConfig.load(getConfig());
            MapPack validated = mapRegistry.reload();
            if (!replacement.resourcePack().localHost().equals(configuration.resourcePack().localHost())) {
                throw new IllegalArgumentException("local-host 변경은 서버를 재시작해야 적용됩니다.");
            }
            configuration = replacement;
            resourcePacks.update(replacement.resourcePack());
            if (replacement.resourcePack().configured()) readiness.ready();
            else readiness.degraded("필수 리소스 팩 설정 필요: " + replacement.resourcePack().problem().orElse("알 수 없음"));
            sender.sendMessage("§a설정과 맵 팩 검증을 완료했습니다.");
            sender.sendMessage("§e월드 생성/인스턴스 수 변경은 안전을 위해 다음 서버 재시작부터 적용됩니다.");
            getLogger().info("리로드 검증 성공: " + validated.mazeId() + " " + validated.mapVersion());
        } catch (Exception failure) {
            sender.sendMessage("§c리로드 거부: " + rootMessage(failure));
            getLogger().warning("리로드 중 기존 정상 레지스트리를 유지했습니다: " + rootMessage(failure));
        }
    }

    private void verifyWorldFromCommand(CommandSender sender) {
        if (worldVerifier == null || !readiness.acceptsEntry()) {
            sender.sendMessage("§c플러그인이 READY 상태가 아닙니다.");
            return;
        }
        sender.sendMessage("§e임시 20방 월드를 생성하고 검사한 뒤 삭제합니다.");
        getLogger().info("MCPuzzle WORLD_VERIFY START");
        worldVerifier.verify().whenComplete((report, failure) -> onMain(() -> {
            if (failure != null) {
                getLogger().severe("MCPuzzle WORLD_VERIFY FAIL reason=" + rootMessage(failure));
                sender.sendMessage("§c월드 검증 실패: " + rootMessage(failure));
                return;
            }
            getLogger().info("MCPuzzle WORLD_VERIFY PASS rooms=" + report.rooms()
                    + " visualBlocks=" + report.visualBlocks()
                    + " scanned=" + report.scannedInputLayerBlocks()
                    + " signs=" + report.signs());
            sender.sendMessage("§a20방 생성·블록·표지판·금지 입력·정리 검증을 통과했습니다.");
        }));
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        readiness.stopping();
        if (tickTask != null) tickTask.cancel();
        if (localResourcePackServer != null) localResourcePackServer.close();
        try {
            if (isolation != null) isolation.restoreAllOnlineForDisableBlocking();
        } catch (RuntimeException failure) {
            getLogger().severe("종료 중 플레이어 복원 실패(내구 스냅샷 유지): " + rootMessage(failure));
        }
        try {
            CompletionStage<Void> runtimeClose = runtime == null ? CompletableFuture.completedFuture(null) : runtime.shutdown();
            CompletionStage<Void> databaseClose = runtimeClose.thenCompose(ignored -> persistence == null
                    ? CompletableFuture.completedFuture(null) : persistence.closeAsync());
            databaseClose.toCompletableFuture().get(20, TimeUnit.SECONDS);
        } catch (Exception failure) {
            getLogger().severe("종료 정리 시간 초과/실패: " + rootMessage(failure));
            if (persistence != null) {
                try { persistence.close(); } catch (RuntimeException closeFailure) { getLogger().severe(rootMessage(closeFailure)); }
            }
        } finally {
            shutdown(filesExecutor);
            shutdown(retryExecutor);
        }
        getLogger().info("MCPuzzle 종료 정리를 마쳤습니다.");
    }

    private void registerStartingCommand() {
        PluginCommand maze = requireMazeCommand();
        maze.setExecutor((sender, command, label, args) -> {
            sender.sendMessage("§6[MCPuzzle] §f" + readiness.state() + " §7- " + readiness.detail()); return true;
        });
    }

    private PluginCommand requireMazeCommand() {
        return Objects.requireNonNull(getCommand("maze"), "Command 'maze' is missing from plugin.yml");
    }

    private void register(Listener... listeners) {
        for (Listener listener : listeners) getServer().getPluginManager().registerEvents(listener, this);
    }

    private void onMain(Runnable action) {
        if (getServer().isPrimaryThread()) action.run();
        else getServer().getScheduler().runTask(this, action);
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> { Thread thread = new Thread(task, prefix + "-" + sequence.incrementAndGet()); thread.setDaemon(false); return thread; };
    }

    private void shutdown(java.util.concurrent.ExecutorService executor) {
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); executor.shutdownNow(); }
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return Objects.toString(current.getMessage(), current.getClass().getSimpleName());
    }
}
