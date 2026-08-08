package dev.mcpuzzle.paper.isolation;

import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.port.PlayerIsolationPort;
import dev.mcpuzzle.core.port.WorldInstanceHandle;
import dev.mcpuzzle.paper.containment.TeleportPermitRegistry;
import dev.mcpuzzle.paper.instance.InstanceRuntimeRegistry;
import dev.mcpuzzle.paper.thread.MainThreadGateway;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class PaperPlayerIsolationAdapter implements PlayerIsolationPort {
    private final Server server;
    private final MainThreadGateway mainThread;
    private final InstanceRuntimeRegistry registry;
    private final TeleportPermitRegistry teleportPermits;
    private final LobbyDestinationResolver lobbyDestination;
    private final DurablePlayerSnapshotStore durableStore;
    private final Executor snapshotExecutor;
    private final PlayerSnapshotCodec codec = new PlayerSnapshotCodec();
    private final Map<SessionId, Map<UUID, PlayerSnapshot>> snapshotsBySession = new HashMap<>();
    private final Set<SnapshotKey> restoredAwaitingDeletion = new HashSet<>();
    private final Set<UUID> blockedPlayers = ConcurrentHashMap.newKeySet();
    private boolean recoveryLoaded;

    public PaperPlayerIsolationAdapter(
            Server server,
            MainThreadGateway mainThread,
            InstanceRuntimeRegistry registry,
            TeleportPermitRegistry teleportPermits,
            LobbyDestinationResolver lobbyDestination,
            DurablePlayerSnapshotStore durableStore,
            Executor snapshotExecutor
    ) {
        this.server = Objects.requireNonNull(server, "server");
        this.mainThread = Objects.requireNonNull(mainThread, "mainThread");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.teleportPermits = Objects.requireNonNull(teleportPermits, "teleportPermits");
        this.lobbyDestination = Objects.requireNonNull(lobbyDestination, "lobbyDestination");
        this.durableStore = Objects.requireNonNull(durableStore, "durableStore");
        this.snapshotExecutor = Objects.requireNonNull(snapshotExecutor, "snapshotExecutor");
    }

    /**
     * Loads crash-surviving snapshots. Integration must await this before accepting maze joins and should
     * invoke {@link #restorePendingPlayer(UUID)} from a join listener before exposing the player to the lobby.
     */
    public CompletionStage<SnapshotRecoveryReport> loadPendingSnapshots() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return durableStore.loadAll();
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }, snapshotExecutor).thenCompose(loadResult -> mainThread.call(() -> prepareRecovery(loadResult)))
                .thenCompose(work -> CompletableFuture.runAsync(() -> quarantine(work.quarantine()), snapshotExecutor)
                        .thenApply(ignored -> work.report()));
    }

    @Override
    public CompletionStage<Void> captureAndEnter(
            SessionId sessionId,
            PartyRoster roster,
            WorldInstanceHandle instance
    ) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(instance, "instance");
        return mainThread.call(() -> prepareCapture(sessionId, roster, instance))
                .thenCompose(plan -> CompletableFuture.runAsync(() -> persist(plan.encoded()), snapshotExecutor)
                        .thenCompose(ignored -> mainThread.<Void>call(() -> {
                            enterCaptured(plan);
                            return null;
                        }))
                        .exceptionallyCompose(failure -> cleanupAfterFailedEntry(plan, failure)));
    }

    @Override
    public CompletionStage<Void> restoreToLobby(SessionId sessionId, PartyRoster roster) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(roster, "roster");
        return mainThread.call(() -> {
            Map<UUID, PlayerSnapshot> snapshots = snapshotsBySession.get(sessionId);
            if (snapshots == null) {
                return RestorePlan.empty();
            }
            if (!Set.copyOf(roster.members()).containsAll(snapshots.keySet())) {
                throw new IllegalArgumentException("Restore roster does not contain every pending party member");
            }
            return restoreOnline(sessionId, List.copyOf(snapshots.keySet()));
        }).thenCompose(this::finishRestore);
    }

    /** Restores an offline player's retained snapshot when a join listener observes them again. */
    public CompletionStage<Boolean> restorePendingPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return mainThread.call(() -> {
            SessionId sessionId = registry.sessionOfPlayer(playerId).orElse(null);
            if (sessionId == null) {
                return RestorePlan.empty();
            }
            Map<UUID, PlayerSnapshot> snapshots = snapshotsBySession.get(sessionId);
            if (snapshots == null || !snapshots.containsKey(playerId)) {
                return RestorePlan.empty();
            }
            return restoreOnline(sessionId, List.of(playerId));
        }).thenCompose(plan -> finishRestore(plan).thenApply(ignored -> !plan.restored().isEmpty()));
    }

    /**
     * Best-effort disable hook. Online players are restored; offline or failed players remain durably pending.
     */
    public CompletionStage<Void> restoreAllOnlineForDisable() {
        return mainThread.call(() -> {
            List<SnapshotKey> all = snapshotsBySession.entrySet().stream()
                    .flatMap(entry -> entry.getValue().keySet().stream()
                            .map(playerId -> new SnapshotKey(entry.getKey(), playerId)))
                    .toList();
            return restoreKeys(all);
        }).thenCompose(this::finishRestore);
    }

    /**
     * Deterministic main-thread shutdown path. Player mutations happen first and
     * the small durable-file cleanup is completed inline so plugin disable cannot
     * deadlock waiting for a scheduler callback that will never run.
     */
    public void restoreAllOnlineForDisableBlocking() {
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Disable restoration must run on the Paper main thread");
        }
        List<SnapshotKey> all = snapshotsBySession.entrySet().stream()
                .flatMap(entry -> entry.getValue().keySet().stream().map(playerId -> new SnapshotKey(entry.getKey(), playerId)))
                .toList();
        RestorePlan plan = restoreKeys(all);
        if (!plan.restored().isEmpty()) {
            delete(plan.restored());
            for (SnapshotKey key : plan.restored()) {
                Map<UUID, PlayerSnapshot> snapshots = snapshotsBySession.get(key.sessionId());
                if (snapshots != null) {
                    snapshots.remove(key.playerId());
                    if (snapshots.isEmpty()) snapshotsBySession.remove(key.sessionId());
                }
                restoredAwaitingDeletion.remove(key);
                registry.detachPlayer(key.sessionId(), key.playerId());
            }
        }
        if (plan.failure() != null) throw plan.failure();
    }

    public boolean isRecoveryBlocked(UUID playerId) {
        return blockedPlayers.contains(playerId);
    }

    public Set<UUID> blockedPlayers() {
        return Set.copyOf(blockedPlayers);
    }

    /** Returns the configured main world's current spawn, with the primary world spawn as fallback. */
    public Optional<Location> lobbySpawn() {
        Location configured = lobbyDestination.resolve(server).filter(this::isSafe).orElse(null);
        if (configured != null) {
            return Optional.of(configured);
        }
        List<World> worlds = server.getWorlds();
        if (worlds.isEmpty()) {
            return Optional.empty();
        }
        Location fallback = worlds.get(0).getSpawnLocation();
        return isSafe(fallback) ? Optional.of(fallback) : Optional.empty();
    }

    private CapturePlan prepareCapture(SessionId sessionId, PartyRoster roster, WorldInstanceHandle instance)
            throws IOException {
        if (!recoveryLoaded) {
            throw new IllegalStateException("Durable snapshots must be loaded before accepting maze entries");
        }
        if (snapshotsBySession.containsKey(sessionId)) {
            throw new IllegalStateException("Session players were already isolated: " + sessionId);
        }
        World instanceWorld = server.getWorld(instance.instanceName());
        if (instanceWorld == null) {
            throw new IllegalStateException("Instance world is not loaded: " + instance.instanceName());
        }
        if (!registry.sessionOfWorld(instance.instanceName()).filter(sessionId::equals).isPresent()) {
            throw new IllegalStateException("Instance registry ownership mismatch: " + instance.instanceName());
        }
        List<Player> players = requireOnline(roster);
        Map<UUID, PlayerSnapshot> snapshots = new LinkedHashMap<>();
        Map<SnapshotKey, byte[]> encoded = new LinkedHashMap<>();
        for (Player player : players) {
            UUID playerId = player.getUniqueId();
            if (blockedPlayers.contains(playerId)) {
                throw new IllegalStateException("Player has a quarantined snapshot requiring operator recovery: " + playerId);
            }
            if (registry.sessionOfPlayer(playerId).isPresent()) {
                throw new IllegalStateException("Player already has a pending or active instance snapshot: " + playerId);
            }
            PlayerSnapshot snapshot = PlayerSnapshot.capture(player);
            snapshots.put(playerId, snapshot);
            encoded.put(new SnapshotKey(sessionId, playerId), codec.encode(snapshot));
        }
        return new CapturePlan(sessionId, instanceWorld.getSpawnLocation(), players, snapshots, encoded);
    }

    private void enterCaptured(CapturePlan plan) {
        List<Player> touched = new ArrayList<>();
        RuntimeException failure = null;
        try {
            for (Player player : plan.players()) {
                touched.add(player);
                enterPuzzle(player, plan.entrance());
            }
            for (UUID playerId : plan.snapshots().keySet()) {
                registry.attachPlayer(plan.sessionId(), playerId);
            }
            snapshotsBySession.put(plan.sessionId(), new LinkedHashMap<>(plan.snapshots()));
            return;
        } catch (RuntimeException enterFailure) {
            failure = enterFailure;
        }

        for (int index = touched.size() - 1; index >= 0; index--) {
            Player player = touched.get(index);
            try {
                restoreOriginal(player, plan.snapshots().get(player.getUniqueId()));
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }
        plan.snapshots().keySet().forEach(playerId -> registry.detachPlayer(plan.sessionId(), playerId));
        boolean rollbackComplete = failure.getSuppressed().length == 0;
        if (!rollbackComplete) {
            snapshotsBySession.put(plan.sessionId(), new LinkedHashMap<>(plan.snapshots()));
            for (UUID playerId : plan.snapshots().keySet()) {
                try {
                    registry.attachPlayer(plan.sessionId(), playerId);
                } catch (RuntimeException attachFailure) {
                    failure.addSuppressed(attachFailure);
                    blockedPlayers.add(playerId);
                }
            }
        }
        throw new EntryFailure("Party entry failed", failure, rollbackComplete);
    }

    private CompletionStage<Void> cleanupAfterFailedEntry(CapturePlan plan, Throwable stageFailure) {
        Throwable failure = unwrap(stageFailure);
        if (failure instanceof EntryFailure entryFailure && entryFailure.rollbackComplete()) {
            return CompletableFuture.runAsync(() -> delete(plan.encoded().keySet()), snapshotExecutor)
                    .thenCompose(ignored -> CompletableFuture.failedFuture(entryFailure.getCause()));
        }
        // If the failure happened after persistence but before a provably complete rollback, retain snapshots.
        return CompletableFuture.failedFuture(failure);
    }

    private RestorePlan restoreOnline(SessionId sessionId, List<UUID> playerIds) {
        return restoreKeys(playerIds.stream().map(playerId -> new SnapshotKey(sessionId, playerId)).toList());
    }

    private RestorePlan restoreKeys(List<SnapshotKey> keys) {
        List<SnapshotKey> restored = new ArrayList<>();
        RuntimeException aggregate = null;
        for (SnapshotKey key : keys) {
            Map<UUID, PlayerSnapshot> snapshots = snapshotsBySession.get(key.sessionId());
            PlayerSnapshot snapshot = snapshots == null ? null : snapshots.get(key.playerId());
            Player player = server.getPlayer(key.playerId());
            if (snapshot == null || player == null || !player.isOnline()) {
                continue;
            }
            if (restoredAwaitingDeletion.contains(key)) {
                restored.add(key);
                continue;
            }
            try {
                restoreToLobby(player, snapshot);
                restoredAwaitingDeletion.add(key);
                restored.add(key);
            } catch (RuntimeException failure) {
                if (aggregate == null) {
                    aggregate = new IllegalStateException("One or more players could not be restored");
                }
                aggregate.addSuppressed(failure);
            }
        }
        return new RestorePlan(restored, aggregate);
    }

    private CompletionStage<Void> finishRestore(RestorePlan plan) {
        if (plan.restored().isEmpty()) {
            return plan.failure() == null
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.failedFuture(plan.failure());
        }
        return CompletableFuture.runAsync(() -> delete(plan.restored()), snapshotExecutor)
                .thenCompose(ignored -> mainThread.call(() -> {
                    for (SnapshotKey key : plan.restored()) {
                        Map<UUID, PlayerSnapshot> snapshots = snapshotsBySession.get(key.sessionId());
                        if (snapshots != null) {
                            snapshots.remove(key.playerId());
                            if (snapshots.isEmpty()) {
                                snapshotsBySession.remove(key.sessionId());
                            }
                        }
                        restoredAwaitingDeletion.remove(key);
                        registry.detachPlayer(key.sessionId(), key.playerId());
                    }
                    if (plan.failure() != null) {
                        throw plan.failure();
                    }
                    return null;
                }));
    }

    private RecoveryWork prepareRecovery(SnapshotLoadResult loadResult) {
        if (recoveryLoaded) {
            throw new IllegalStateException("Pending snapshots were already loaded");
        }
        recoveryLoaded = true;
        List<String> warnings = new ArrayList<>();
        List<SnapshotKey> quarantine = new ArrayList<>();
        Map<UUID, SnapshotKey> acceptedPlayers = new HashMap<>();
        for (SnapshotCorruption corruption : loadResult.corruptions()) {
            corruption.playerId().ifPresent(blockedPlayers::add);
            warnings.add(corruption.reason() + " [" + corruption.quarantinedFile() + "]");
        }
        for (Map.Entry<SnapshotKey, byte[]> entry : loadResult.snapshots().entrySet()) {
            SnapshotKey key = entry.getKey();
            PlayerSnapshot snapshot;
            try {
                snapshot = codec.decode(entry.getValue());
            } catch (IOException corruptPayload) {
                blockedPlayers.add(key.playerId());
                quarantine.add(key);
                warnings.add("Player snapshot payload is corrupt for " + key.playerId() + ": " + corruptPayload.getMessage());
                continue;
            }
            SnapshotKey previous = acceptedPlayers.putIfAbsent(key.playerId(), key);
            if (previous != null) {
                blockedPlayers.add(key.playerId());
                quarantine.add(previous);
                quarantine.add(key);
                removeRecovered(previous);
                warnings.add("Multiple pending snapshots exist for player " + key.playerId());
                continue;
            }
            try {
                registry.attachPlayer(key.sessionId(), key.playerId());
                snapshotsBySession.computeIfAbsent(key.sessionId(), ignored -> new LinkedHashMap<>())
                        .put(key.playerId(), snapshot);
            } catch (RuntimeException ownershipFailure) {
                blockedPlayers.add(key.playerId());
                quarantine.add(key);
                warnings.add("Could not reserve recovered player " + key.playerId() + ": " + ownershipFailure.getMessage());
            }
        }
        int pending = snapshotsBySession.values().stream().mapToInt(Map::size).sum();
        return new RecoveryWork(new SnapshotRecoveryReport(pending, blockedPlayers, warnings),
                List.copyOf(new LinkedHashSet<>(quarantine)));
    }

    private void removeRecovered(SnapshotKey key) {
        Map<UUID, PlayerSnapshot> snapshots = snapshotsBySession.get(key.sessionId());
        if (snapshots != null) {
            snapshots.remove(key.playerId());
            if (snapshots.isEmpty()) {
                snapshotsBySession.remove(key.sessionId());
            }
        }
        registry.detachPlayer(key.sessionId(), key.playerId());
    }

    private void quarantine(List<SnapshotKey> keys) {
        for (SnapshotKey key : keys) {
            try {
                durableStore.quarantine(key, "Bukkit snapshot payload failed validation");
            } catch (IOException failure) {
                throw new CompletionException(failure);
            }
        }
    }

    private void persist(Map<SnapshotKey, byte[]> encoded) {
        try {
            durableStore.saveBatch(encoded);
        } catch (IOException failure) {
            throw new CompletionException(failure);
        }
    }

    private void delete(Iterable<SnapshotKey> keys) {
        try {
            durableStore.deleteBatch(keys);
        } catch (IOException failure) {
            throw new CompletionException(failure);
        }
    }

    private List<Player> requireOnline(PartyRoster roster) {
        List<Player> players = new ArrayList<>(roster.size());
        for (UUID playerId : roster.members()) {
            Player player = server.getPlayer(playerId);
            if (player == null || !player.isOnline() || player.isDead()) {
                throw new IllegalStateException("Every party member must be online and alive: " + playerId);
            }
            players.add(player);
        }
        return players;
    }

    private void enterPuzzle(Player player, Location entrance) {
        player.getActivePotionEffects().stream().map(PotionEffect::getType).toList()
                .forEach(player::removePotionEffect);
        player.getInventory().clear();
        player.getInventory().setItem(1, new ItemStack(Material.WRITABLE_BOOK));
        player.getInventory().setArmorContents(new ItemStack[4]);
        player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        player.setExhaustion(0.0F);
        player.setFoodLevel(20);
        player.setSaturation(5.0F);
        player.setExp(0.0F);
        player.setLevel(0);
        player.setTotalExperience(0);
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0D : maxHealth.getValue());
        boolean teleported = teleportPermits.runPermitted(player.getUniqueId(), () -> player.teleport(entrance));
        if (!teleported) {
            throw new IllegalStateException("Could not teleport player into puzzle: " + player.getUniqueId());
        }
    }

    private void restoreOriginal(Player player, PlayerSnapshot snapshot) {
        Location original = snapshot.originalLocation(server);
        if (!isSafe(original)
                || !teleportPermits.runPermitted(player.getUniqueId(), () -> player.teleport(original))) {
            throw new IllegalStateException("Could not roll player back to their original location: " + player.getUniqueId());
        }
        snapshot.restoreState(player);
    }

    private void restoreToLobby(Player player, PlayerSnapshot snapshot) {
        Location spawn = lobbySpawn().orElse(null);
        if (spawn != null
                && teleportPermits.runPermitted(player.getUniqueId(), () -> player.teleport(spawn))) {
            snapshot.restoreState(player);
            return;
        }
        Location original = snapshot.originalLocation(server);
        if (isSafe(original)
                && teleportPermits.runPermitted(player.getUniqueId(), () -> player.teleport(original))) {
            snapshot.restoreState(player);
            return;
        }
        throw new IllegalStateException("No safe lobby fallback is available for " + player.getUniqueId());
    }

    private boolean isSafe(Location location) {
        return location != null && location.getWorld() != null
                && Double.isFinite(location.getX())
                && Double.isFinite(location.getY())
                && Double.isFinite(location.getZ());
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private record CapturePlan(
            SessionId sessionId,
            Location entrance,
            List<Player> players,
            Map<UUID, PlayerSnapshot> snapshots,
            Map<SnapshotKey, byte[]> encoded
    ) {
    }

    private record RestorePlan(List<SnapshotKey> restored, RuntimeException failure) {
        private RestorePlan {
            restored = List.copyOf(restored);
        }

        static RestorePlan empty() {
            return new RestorePlan(List.of(), null);
        }
    }

    private record RecoveryWork(SnapshotRecoveryReport report, List<SnapshotKey> quarantine) {
    }

    private static final class EntryFailure extends RuntimeException {
        private final boolean rollbackComplete;

        private EntryFailure(String message, Throwable cause, boolean rollbackComplete) {
            super(message, cause);
            this.rollbackComplete = rollbackComplete;
        }

        boolean rollbackComplete() {
            return rollbackComplete;
        }
    }
}
