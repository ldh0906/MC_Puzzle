package dev.mcpuzzle.paper.runtime;

import dev.mcpuzzle.core.application.admission.AdmissionBatch;
import dev.mcpuzzle.core.application.admission.AdmissionRequest;
import dev.mcpuzzle.core.application.admission.AvailabilityCheck;
import dev.mcpuzzle.core.application.admission.AvailabilityStatus;
import dev.mcpuzzle.core.application.admission.EnqueueResult;
import dev.mcpuzzle.core.application.admission.InstanceAdmissionQueue;
import dev.mcpuzzle.core.application.afk.AfkSignal;
import dev.mcpuzzle.core.application.afk.PartyAfkTracker;
import dev.mcpuzzle.core.application.hint.HintContextId;
import dev.mcpuzzle.core.application.hint.HintOutcome;
import dev.mcpuzzle.core.application.hint.HintOutcomeType;
import dev.mcpuzzle.core.application.hint.HintPolicy;
import dev.mcpuzzle.core.application.party.PartyId;
import dev.mcpuzzle.core.application.party.PartyService;
import dev.mcpuzzle.core.application.party.PartyServiceError;
import dev.mcpuzzle.core.application.party.PartyServiceResult;
import dev.mcpuzzle.core.application.party.PartyView;
import dev.mcpuzzle.core.domain.CompletedRun;
import dev.mcpuzzle.core.domain.LeaderboardEntry;
import dev.mcpuzzle.core.domain.LeaderboardQuery;
import dev.mcpuzzle.core.domain.OperationResult;
import dev.mcpuzzle.core.domain.Party;
import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.PuzzleSession;
import dev.mcpuzzle.core.domain.PuzzleSessionSnapshot;
import dev.mcpuzzle.core.domain.SaveGame;
import dev.mcpuzzle.core.domain.SaveSlot;
import dev.mcpuzzle.core.domain.SessionFailure;
import dev.mcpuzzle.core.domain.SessionId;
import dev.mcpuzzle.core.domain.SessionState;
import dev.mcpuzzle.core.port.WorldInstanceHandle;
import dev.mcpuzzle.paper.adapter.persistence.SQLitePersistence;
import dev.mcpuzzle.paper.containment.TeleportPermitRegistry;
import dev.mcpuzzle.paper.containment.VisibilityIsolationService;
import dev.mcpuzzle.paper.isolation.PaperPlayerIsolationAdapter;
import dev.mcpuzzle.paper.map.MapPack;
import dev.mcpuzzle.paper.presentation.RoomBriefingBookFactory;
import dev.mcpuzzle.paper.resourcepack.ResourcePackGate;
import dev.mcpuzzle.paper.world.GeneratedVoidWorldInstanceAdapter;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/** Main-thread application coordinator. All async completions hop back before touching Bukkit state. */
public final class MazeRuntimeService {
    private final Plugin plugin;
    private final Clock clock;
    private final PluginReadiness readiness;
    private final Map<String, MapPack> maps;
    private final PartyService parties;
    private final InstanceAdmissionQueue admissions;
    private final SQLitePersistence persistence;
    private final GeneratedVoidWorldInstanceAdapter worlds;
    private final PaperPlayerIsolationAdapter isolation;
    private final ResourcePackGate resourcePacks;
    private final TeleportPermitRegistry teleportPermits;
    private final VisibilityIsolationService visibility;
    private final HintPolicy hints = new HintPolicy();
    private final RoomBriefingBookFactory briefingBooks = new RoomBriefingBookFactory();
    private final Map<String, SaveSlotWorkflow> saveSlots;
    private final InstanceCleanupService cleanup;
    private final RunLifecyclePolicy lifecyclePolicy = new RunLifecyclePolicy();
    private final SaveAccessPolicy saveAccess = new SaveAccessPolicy();
    private final Map<SessionId, RunContext> runs = new HashMap<>();
    private final Map<UUID, SessionId> runByPlayer = new HashMap<>();
    private final Map<UUID, PendingOverwrite> pendingOverwrites = new HashMap<>();
    private boolean stopping;

    public MazeRuntimeService(Plugin plugin, Clock clock, PluginReadiness readiness, Map<String, MapPack> maps,
                              PartyService parties, InstanceAdmissionQueue admissions,
                              SQLitePersistence persistence, GeneratedVoidWorldInstanceAdapter worlds,
                              PaperPlayerIsolationAdapter isolation, ResourcePackGate resourcePacks,
                              TeleportPermitRegistry teleportPermits, VisibilityIsolationService visibility) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.maps = Map.copyOf(Objects.requireNonNull(maps, "maps"));
        if (this.maps.isEmpty()) throw new IllegalArgumentException("At least one maze is required");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.admissions = Objects.requireNonNull(admissions, "admissions");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.isolation = Objects.requireNonNull(isolation, "isolation");
        this.resourcePacks = Objects.requireNonNull(resourcePacks, "resourcePacks");
        this.teleportPermits = Objects.requireNonNull(teleportPermits, "teleportPermits");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.saveSlots = this.maps.values().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                MapPack::mazeId, pack -> new SaveSlotWorkflow(persistence, clock, pack.mazeId(), pack.mapVersion())));
        this.cleanup = new InstanceCleanupService(isolation, worlds);
    }

    public PartyServiceResult createParty(Player leader) {
        PartyServiceResult result = parties.create(leader.getUniqueId());
        tellResult(leader, result, "§a파티를 만들었습니다.");
        return result;
    }

    public PartyServiceResult invite(Player leader, Player target) {
        PartyServiceResult result = parties.invite(leader.getUniqueId(), target.getUniqueId());
        if (result.succeeded()) {
            PartyView party = result.party().orElseThrow();
            leader.sendMessage("§a" + target.getName() + "님을 초대했습니다.");
            target.sendMessage("§e" + leader.getName() + "님의 미궁 파티 초대: §f/maze accept " + leader.getName());
            target.sendMessage("§7거절: /maze deny " + leader.getName());
        } else tellResult(leader, result, "");
        return result;
    }

    public PartyServiceResult accept(Player player, Player leader) {
        Optional<PartyView> leaderParty = parties.findByPlayer(leader.getUniqueId());
        if (leaderParty.isEmpty()) {
            player.sendMessage("§c그 플레이어의 파티를 찾을 수 없습니다.");
            return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        }
        PartyServiceResult result = parties.accept(player.getUniqueId(), leaderParty.get().id());
        tellResult(player, result, "§a파티에 참가했습니다.");
        if (result.succeeded()) broadcast(result.party().orElseThrow().roster(), "§a" + player.getName() + "님이 파티에 참가했습니다.");
        return result;
    }

    public PartyServiceResult decline(Player player, Player leader) {
        Optional<PartyView> leaderParty = parties.findByPlayer(leader.getUniqueId());
        if (leaderParty.isEmpty()) return PartyServiceResult.failure(PartyServiceError.PARTY_NOT_FOUND);
        PartyServiceResult result = parties.decline(player.getUniqueId(), leaderParty.get().id());
        tellResult(player, result, "§7초대를 거절했습니다.");
        return result;
    }

    public PartyServiceResult kick(Player leader, Player target) {
        PartyServiceResult result = parties.kick(leader.getUniqueId(), target.getUniqueId());
        tellResult(leader, result, "§a파티에서 내보냈습니다.");
        if (result.succeeded()) target.sendMessage("§c미궁 파티에서 제외되었습니다.");
        return result;
    }

    public Optional<PartyView> party(UUID playerId) { return parties.findByPlayer(playerId); }

    public List<PartyView> invitations(UUID playerId) { return parties.findInvitations(playerId); }

    public void disbandParty(Player leader) {
        if (context(leader.getUniqueId()) != null) {
            leader.sendMessage("§c미궁 진행 중에는 /maze leave를 사용하세요."); return;
        }
        PartyView view = parties.findByPlayer(leader.getUniqueId()).orElse(null);
        if (view == null) { leader.sendMessage("§c파티가 없습니다."); return; }
        if (!view.leaderId().equals(leader.getUniqueId())) { leader.sendMessage("§c파티장만 파티를 해산할 수 있습니다."); return; }
        broadcast(view.roster(), "§e파티가 해산되었습니다.");
        PartyServiceResult result = parties.disband(leader.getUniqueId());
        if (!result.succeeded()) tellResult(leader, result, "");
    }

    public void leaveOpenParty(Player member) {
        if (context(member.getUniqueId()) != null) {
            member.sendMessage("§c미궁 진행 중에는 파티장이 /maze leave를 사용해야 합니다."); return;
        }
        PartyServiceResult result = parties.leaveOpenParty(member.getUniqueId());
        tellResult(member, result, "§a파티에서 나왔습니다.");
    }
    public Optional<RunView> run(UUID playerId) {
        RunContext value = context(playerId);
        return value == null ? Optional.empty() : Optional.of(view(value));
    }

    public List<MazeOption> mazes() {
        return List.of("midnight-easy", "midnight-normal", "midnight-hard").stream()
                .map(this::map)
                .map(pack -> new MazeOption(pack.mazeId(), pack.displayName(), pack.rooms().size()))
                .toList();
    }

    public void requestStart(Player actor, int slot) {
        requestStart(actor, "midnight-easy", slot, false);
    }

    public void requestStart(Player actor, int slot, boolean overwriteConfirmed) {
        requestStart(actor, "midnight-easy", slot, overwriteConfirmed);
    }

    public void requestStart(Player actor, String mazeId, int slot) {
        requestStart(actor, mazeId, slot, false);
    }

    public void requestStart(Player actor, String mazeId, int slot, boolean overwriteConfirmed) {
        MapPack selected = map(mazeId);
        if (!checkEntryPreconditions(actor, slot)) return;
        Instant now = clock.instant();
        actor.sendMessage("§7세이브 슬롯을 확인하는 중입니다...");
        saveSlots(mazeId).find(actor.getUniqueId(), slot).whenComplete((save, failure) -> onMain(() -> {
            if (failure != null) { asyncFailure(actor, "세이브 슬롯 확인", failure); return; }
            if (!checkEntryPreconditions(actor, slot)) return;
            PartyView current = parties.findByPlayer(actor.getUniqueId()).orElse(null);
            if (current == null || !current.leaderId().equals(actor.getUniqueId())) {
                actor.sendMessage("§c파티가 변경되어 시작을 취소했습니다."); return;
            }
            boolean confirmed = overwriteConfirmed || consumeOverwrite(actor.getUniqueId(), mazeId, slot);
            if (save.isPresent() && !confirmed) {
                pendingOverwrites.put(actor.getUniqueId(), new PendingOverwrite(mazeId, slot, now.plusSeconds(30)));
                actor.sendMessage("§e슬롯 " + slot + "의 기존 세이브를 덮어씁니다. 30초 안에 같은 명령을 다시 실행해 확인하세요.");
                return;
            }
            if (save.isPresent()) {
                saveSlots(mazeId).delete(actor.getUniqueId(), slot).whenComplete((deleted, deleteFailure) -> onMain(() -> {
                    if (deleteFailure != null || !Boolean.TRUE.equals(deleted)) {
                        asyncFailure(actor, "기존 세이브 덮어쓰기", deleteFailure == null
                                ? new IllegalStateException("세이브를 삭제하지 못했습니다") : deleteFailure);
                        return;
                    }
                    beginNewRun(actor, current, selected, slot);
                }));
            } else beginNewRun(actor, current, selected, slot);
        }));
    }

    public void requestResume(Player actor, int slot) {
        requestResume(actor, "midnight-easy", slot, actor.getUniqueId());
    }

    public void requestResume(Player actor, int slot, UUID ownerId) {
        requestResume(actor, "midnight-easy", slot, ownerId);
    }

    public void requestResume(Player actor, String mazeId, int slot, UUID ownerId) {
        map(mazeId);
        if (!checkResumePreconditions(actor, slot)) return;
        saveSlots(mazeId).find(ownerId, slot).whenComplete((save, failure) -> onMain(() -> {
            if (failure != null) { asyncFailure(actor, "세이브 읽기", failure); return; }
            if (!checkResumePreconditions(actor, slot)) return;
            if (save.isEmpty()) { actor.sendMessage("§c해당 슬롯에 유효한 세이브가 없습니다."); return; }
            if (!saveAccess.canManage(actor.getUniqueId(), actor.isOp(), save.get())) {
                actor.sendMessage("§c현재 소유자, 저장 당시 파티장 또는 OP만 재개할 수 있습니다."); return;
            }
            PartyView current = parties.findByPlayer(actor.getUniqueId()).orElse(null);
            if (current == null || !sameRoster(current.roster(), save.get().slot().roster())) {
                actor.sendMessage("§c원래 참가했던 플레이어 전원이 같은 파티에 있어야 재개할 수 있습니다."); return;
            }
            beginResume(actor, current, slot, save.get());
        }));
    }

    public void cancelQueue(Player actor) {
        RunContext ctx = context(actor.getUniqueId());
        if (ctx == null || ctx.session.state() != SessionState.QUEUED) {
            actor.sendMessage("§c대기 중인 입장이 없습니다."); return;
        }
        if (!ctx.roster.leaderId().equals(actor.getUniqueId())) {
            actor.sendMessage("§c파티장만 대기를 취소할 수 있습니다."); return;
        }
        admissions.cancelQueued(ctx.session.id());
        discard(ctx, "§e입장 대기를 취소했습니다.", false);
    }

    public void leave(Player actor) {
        RunContext ctx = context(actor.getUniqueId());
        if (ctx == null) { actor.sendMessage("§c참가 중인 미궁이 없습니다."); return; }
        if (!ctx.roster.leaderId().equals(actor.getUniqueId())) {
            actor.sendMessage("§c파티장만 미궁 전체 퇴장을 요청할 수 있습니다."); return;
        }
        if (ctx.session.state() == SessionState.QUEUED) { cancelQueue(actor); return; }
        if (ctx.session.state() == SessionState.SUSPENDED) {
            suspendOrDiscard(ctx, "§e중단된 진행 저장을 다시 시도합니다.");
            return;
        }
        if (ctx.session.state() != SessionState.ACTIVE) { actor.sendMessage("§e현재 퇴장 처리 중입니다."); return; }
        OperationResult<SessionFailure> result = ctx.session.requestSuspend(actor.getUniqueId(), clock.instant());
        if (!result.succeeded()) { actor.sendMessage("§c지금은 퇴장할 수 없습니다."); return; }
        suspendOrDiscard(ctx, "§e파티장이 미궁을 종료했습니다.");
    }

    public void onDisconnect(UUID playerId) {
        RunContext ctx = context(playerId);
        if (ctx == null) return;
        if (ctx.room != null) ctx.room.releaseOperator(playerId);
        var impact = admissions.disconnect(playerId);
        if (impact.cancelledQueued().isPresent() || ctx.session.state() == SessionState.QUEUED
                || ctx.session.state() == SessionState.PROVISIONING) {
            ctx.cancelled = true;
            ctx.session.cancelAdmission(playerId);
            discard(ctx, "§e파티원이 접속을 종료해 입장이 취소되었습니다.", true);
            return;
        }
        if (ctx.session.state() == SessionState.ACTIVE) {
            ctx.session.memberDisconnected(playerId, clock.instant());
            suspendOrDiscard(ctx, "§e파티원이 접속을 종료해 진행을 중단했습니다.");
        }
    }

    public void recordActivity(Player player) {
        RunContext ctx = context(player.getUniqueId());
        if (ctx == null || ctx.session.state() != SessionState.ACTIVE) return;
        Instant now = clock.instant();
        ctx.afk.recordMeaningfulActivity(player.getUniqueId());
        ctx.session.recordActivity(now);
    }

    public void onMove(Player player) {
        RunContext ctx = activeContext(player);
        if (ctx == null || ctx.room == null) return;
        recordActivity(player);
        applySignal(ctx, ctx.room.onMove(player));
    }

    public boolean onBlockInteraction(Player player, Block block, boolean destructive) {
        RunContext ctx = activeContext(player);
        if (ctx == null || ctx.room == null || !block.getWorld().getName().equals(ctx.handle.instanceName())) return false;
        recordActivity(player);
        PaperRoomRuntime.Signal signal = destructive ? ctx.room.onBreak(block.getLocation()) : ctx.room.onInteract(player, block.getLocation());
        if (destructive && signal != PaperRoomRuntime.Signal.NONE) block.setType(Material.AIR, false);
        applySignal(ctx, signal);
        return signal != PaperRoomRuntime.Signal.NONE;
    }

    public void numericAnswer(Player player, String answer) {
        submitAnswer(player, answer);
    }

    public void submitAnswer(Player player, String answer) {
        RunContext ctx = activeContext(player);
        if (ctx == null || ctx.room == null) { player.sendMessage("§c활성 미궁이 없습니다."); return; }
        recordActivity(player);
        PaperRoomRuntime.AnswerSubmission submission = ctx.room.submitAnswer(answer, clock.instant());
        switch (submission.status()) {
            case CORRECT -> {
                player.sendMessage("§a정답입니다.");
                applySignal(ctx, submission.signal());
            }
            case INCORRECT -> player.sendMessage("§c정답이 아닙니다. §7단서를 다시 연결해 보세요. 파티 입력 잠금: "
                    + submission.retryAfterSeconds() + "초");
            case COOLDOWN -> player.sendMessage("§e파티 입력 잠금 중입니다. " + submission.retryAfterSeconds() + "초 후 다시 제출하세요.");
            case INVALID_FORMAT -> player.sendMessage("§c정답에는 한글·영문·숫자 중 하나 이상이 필요합니다.");
            case PREREQUISITE -> player.sendMessage("§e먼저 방 안의 환경 단서와 장치를 모두 확인하세요.");
            case NOT_SUPPORTED -> player.sendMessage("§c현재 방에는 채팅 정답기가 없습니다.");
        }
    }

    public Optional<Location> respawnLocation(Player player) {
        RunContext ctx = activeContext(player);
        if (ctx == null || ctx.handle == null || ctx.room == null) return Optional.empty();
        return isolation.lobbySpawn();
    }

    public void onMazeDeath(Player player) {
        RunContext ctx = activeContext(player);
        if (ctx == null) return;
        OperationResult<SessionFailure> result = ctx.session.memberDied(player.getUniqueId(), clock.instant());
        if (!result.succeeded()) {
            plugin.getLogger().warning("사망한 미궁 참가자의 세션을 중단하지 못했습니다: "
                    + player.getUniqueId() + " (" + result.failure().orElse(null) + ")");
            return;
        }
        suspendOrDiscard(ctx, "§e" + player.getName() + "님이 사망하여 파티가 월드 스폰으로 나왔습니다.");
    }

    public void requestHint(Player player) {
        RunContext ctx = activeContext(player);
        if (ctx == null) { player.sendMessage("§c활성 미궁이 없습니다."); return; }
        HintOutcome outcome = hints.requestNext(hintContext(ctx), player.getUniqueId(), ctx.roster, ctx.session.hintProgress());
        if (outcome.type() == HintOutcomeType.REQUESTED_LEADER_CONFIRMATION) {
            broadcast(ctx.roster, "§e" + player.getName() + "님이 힌트 " + outcome.tier().orElseThrow() + "단계를 요청했습니다.");
            player(ctx.roster.leaderId()).ifPresent(leader -> leader.sendMessage("§f/maze hint confirm §7로 승인하세요."));
        } else sendHintOutcome(player, outcome);
    }

    public void confirmHint(Player leader, boolean approved) {
        RunContext ctx = activeContext(leader);
        if (ctx == null) { leader.sendMessage("§c활성 미궁이 없습니다."); return; }
        HintOutcome outcome = hints.confirm(hintContext(ctx), leader.getUniqueId(), ctx.roster,
                ctx.session.hintProgress(), approved);
        if (outcome.type() == HintOutcomeType.UNLOCKED) {
            int tier = outcome.tier().orElseThrow();
            ctx.session.unlockHint(tier, clock.instant());
            broadcast(ctx.roster, "§d힌트 " + tier + ": §f" + ctx.room.room().hints().get(tier - 1).text());
            persistSnapshot(ctx);
        } else sendHintOutcome(leader, outcome);
    }

    public void viewHint(Player player, int tier) {
        RunContext ctx = activeContext(player);
        if (ctx == null) { player.sendMessage("§c활성 미궁이 없습니다."); return; }
        HintOutcome outcome = hints.viewUnlocked(hintContext(ctx), player.getUniqueId(), ctx.roster,
                ctx.session.hintProgress(), tier);
        if (outcome.type() == HintOutcomeType.VIEWED_UNLOCKED) {
            broadcast(ctx.roster, "§d힌트 " + tier + ": §f" + ctx.room.room().hints().get(tier - 1).text());
        } else sendHintOutcome(player, outcome);
    }

    public void listSaves(Player player) {
        listSaves(player, "midnight-easy", player.getUniqueId());
    }

    public CompletionStage<List<SaveGame>> saves(Player viewer, UUID ownerId) {
        return saves(viewer, "midnight-easy", ownerId);
    }

    public CompletionStage<List<SaveGame>> saves(Player viewer, String mazeId, UUID ownerId) {
        return saveSlots(mazeId).listForViewer(viewer.getUniqueId(), viewer.isOp(), ownerId);
    }

    public void listSaves(Player viewer, UUID ownerId) {
        listSaves(viewer, "midnight-easy", ownerId);
    }

    public void listSaves(Player viewer, String mazeId, UUID ownerId) {
        MapPack selected = map(mazeId);
        saves(viewer, mazeId, ownerId).whenComplete((saves, failure) -> onMain(() -> {
            if (failure != null) { asyncFailure(viewer, "세이브 목록", failure); return; }
            viewer.sendMessage("§6[미궁 세이브: " + selected.displayName() + " / " + ownerId + "]");
            for (int slot = 1; slot <= 3; slot++) {
                int currentSlot = slot;
                List<SaveGame> matches = saves.stream().filter(save -> save.slot().number() == currentSlot).toList();
                if (matches.isEmpty()) viewer.sendMessage("§7" + slot + "번: 비어 있음");
                for (SaveGame save : matches) viewer.sendMessage("§a" + slot + "번: 방 " + save.snapshot().currentRoom() + ", "
                        + save.slot().roster().size() + "명, 소유자 " + save.slot().ownerId() + ", 만료 " + save.slot().expiresAt());
            }
        }));
    }

    public void deleteSave(Player actor, UUID ownerId, int slot) {
        deleteSave(actor, "midnight-easy", ownerId, slot);
    }

    public void deleteSave(Player actor, String mazeId, UUID ownerId, int slot) {
        UUID actorId = actor.getUniqueId();
        boolean operator = actor.isOp();
        saveSlots(mazeId).deleteAuthorized(ownerId, slot, actorId, operator).whenComplete((deleted, failure) -> onMain(() -> {
            if (failure != null) asyncFailure(actor, "세이브 삭제", failure);
            else actor.sendMessage(deleted ? "§a세이브를 삭제했습니다."
                    : "§c세이브가 없거나 삭제 권한이 없습니다.");
        }));
    }

    public void transferOwnership(Player operator, UUID ownerId, int slot, UUID newOwner) {
        transferOwnership(operator, "midnight-easy", ownerId, slot, newOwner);
    }

    public void transferOwnership(Player operator, String mazeId, UUID ownerId, int slot, UUID newOwner) {
        if (!operator.isOp()) { operator.sendMessage("§cOP만 소유권을 이전할 수 있습니다."); return; }
        saveSlots(mazeId).transfer(ownerId, slot, newOwner).whenComplete((changed, failure) -> onMain(() -> {
            if (failure != null) asyncFailure(operator, "소유권 이전", failure);
            else operator.sendMessage(changed ? "§a세이브 소유권을 이전했습니다." : "§c세이브를 찾지 못했거나 새 소유자가 원래 명단에 없습니다.");
        }));
    }

    public void leaderboard(Player player, int partySize) {
        leaderboard(player, "midnight-easy", partySize);
    }

    public void leaderboard(Player player, String mazeId, int partySize) {
        int size = Math.max(1, Math.min(4, partySize));
        leaderboardEntries(mazeId, size)
                .whenComplete((entries, failure) -> onMain(() -> {
                    if (failure != null) { asyncFailure(player, "순위표", failure); return; }
                    player.sendMessage("§6[" + size + "인 파티 순위표]");
                    if (entries.isEmpty()) player.sendMessage("§7아직 완주 기록이 없습니다.");
                    for (LeaderboardEntry entry : entries) player.sendMessage("§e#" + entry.rank() + " §f"
                            + formatDuration(entry.run().metrics().activePlayTime()) + " §7실패 "
                            + entry.run().metrics().failures() + " / 힌트 " + entry.run().metrics().hintsUsed());
                }));
    }

    public CompletionStage<List<LeaderboardEntry>> leaderboardEntries(int partySize) {
        return leaderboardEntries("midnight-easy", partySize);
    }

    public CompletionStage<List<LeaderboardEntry>> leaderboardEntries(String mazeId, int partySize) {
        MapPack selected = map(mazeId);
        int size = Math.max(1, Math.min(4, partySize));
        return persistence.leaderboard(new LeaderboardQuery(selected.mazeId(), selected.mapVersion(), size, 10));
    }

    public void tick() {
        if (stopping) return;
        for (RunContext ctx : List.copyOf(runs.values())) {
            if (!lifecyclePolicy.mayTickGameplay(ctx.session.state())) continue;
            var tick = ctx.afk.tick();
            for (AfkSignal signal : tick.signals()) {
                if (signal == AfkSignal.WARNING_EIGHT_MINUTES) broadcast(ctx.roster, "§e8분 동안 활동이 없습니다. 2분 뒤 자동 중단됩니다.");
                else if (signal == AfkSignal.WARNING_NINE_MINUTES) broadcast(ctx.roster, "§c9분 동안 활동이 없습니다. 1분 뒤 자동 중단됩니다.");
                else {
                    ctx.session.suspendIfAfk(clock.instant());
                    suspendOrDiscard(ctx, "§c10분간 활동이 없어 진행을 중단했습니다.");
                    break;
                }
            }
            if (!lifecyclePolicy.mayTickGameplay(ctx.session.state())) continue;
            if (ctx.room != null && ctx.handle != null) {
                World world = plugin.getServer().getWorld(ctx.handle.instanceName());
                if (world != null) applySignal(ctx, ctx.room.tickEscort(world, onlinePlayers(ctx.roster)));
            }
        }
    }

    public CompletionStage<Void> shutdown() {
        stopping = true;
        List<CompletionStage<Void>> work = new ArrayList<>();
        for (RunContext ctx : List.copyOf(runs.values())) {
            ctx.cancelled = true;
            ctx.afk.pauseActivePlay();
            if (ctx.room != null) ctx.room.cleanup();
            switch (lifecyclePolicy.shutdownAction(ctx.session.state())) {
                case DISCARD_TRANSIENT -> work.add(persistence.delete(ctx.session.id()));
                case PRESERVE_SUSPENDED -> {
                    if (ctx.session.checkpoint().isPresent()) work.add(save(ctx));
                    else work.add(persistence.delete(ctx.session.id()));
                }
                case FINALIZE_COMPLETED -> {
                    PuzzleSessionSnapshot completed = ctx.session.snapshot(clock.instant());
                    work.add(durableCompletion(ctx, CompletedRun.from(completed)));
                }
                case NO_DURABLE_ACTION -> { }
            }
            if (ctx.handle != null) work.add(cleanup.releaseOnly(ctx.session.id(), ctx.handle).exceptionally(failure -> null));
        }
        runs.clear();
        runByPlayer.clear();
        CompletableFuture<?>[] futures = work.stream().map(CompletionStage::toCompletableFuture).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    private void beginNewRun(Player actor, PartyView party, MapPack map, int slot) {
        PartyServiceResult locked = parties.start(actor.getUniqueId());
        if (!locked.succeeded()) { tellResult(actor, locked, ""); return; }
        PartyView view = locked.party().orElseThrow();
        SessionId id = SessionId.random();
        PuzzleSession session = PuzzleSession.create(id, map.mazeId(), map.mapVersion(),
                Party.of(view.leaderId(), view.members()), map.rooms().size());
        session.queue(actor.getUniqueId());
        RunContext ctx = new RunContext(view.id(), view.roster(), session, slot, clock, false, actor.getUniqueId());
        register(ctx);
        enqueue(ctx);
    }

    private void beginResume(Player actor, PartyView party, int slot, SaveGame save) {
        UUID savedLeader = save.snapshot().roster().leaderId();
        PartyServiceResult locked = parties.start(savedLeader);
        if (!locked.succeeded()) { tellResult(actor, locked, ""); return; }
        PuzzleSession session = PuzzleSession.rehydrate(save.snapshot());
        if (!session.queueForResume(savedLeader, party.roster()).succeeded()) {
            actor.sendMessage("§c세이브 명단을 검증하지 못했습니다."); return;
        }
        RunContext ctx = new RunContext(locked.party().orElseThrow().id(), party.roster(), session, slot, clock,
                true, save.slot().ownerId());
        register(ctx);
        enqueue(ctx);
    }

    private void enqueue(RunContext ctx) {
        persistSnapshot(ctx);
        EnqueueResult result = admissions.enqueueAndAdmit(new AdmissionRequest(ctx.session.id(), ctx.roster, clock.instant()), this::availability);
        if (!result.succeeded()) { discard(ctx, "§c이미 다른 미궁 입장에 속한 파티원이 있습니다.", false); return; }
        processBatch(result.batch());
        if (ctx.session.state() == SessionState.QUEUED) {
            broadcast(ctx.roster, admissions.waitingSnapshot().stream().anyMatch(r -> r.sessionId().equals(ctx.session.id()))
                    ? "§e인스턴스가 가득 찼습니다. FIFO 대기열에 등록했습니다."
                    : "§7미궁 인스턴스를 준비합니다...");
        }
    }

    private AvailabilityCheck availability(AdmissionRequest request) {
        for (UUID member : request.roster().members()) {
            Player player = plugin.getServer().getPlayer(member);
            if (player == null || !player.isOnline()) return AvailabilityCheck.unavailable(AvailabilityStatus.MEMBER_OFFLINE, member);
            if (player.isDead()) return AvailabilityCheck.unavailable(AvailabilityStatus.MEMBER_NOT_READY, member);
            if (!resourcePacks.canEnter(member)) return AvailabilityCheck.unavailable(AvailabilityStatus.MEMBER_NOT_READY, member);
            if (isolation.isRecoveryBlocked(member)) return AvailabilityCheck.unavailable(AvailabilityStatus.MEMBER_NOT_READY, member);
            if (!request.sessionId().equals(runByPlayer.get(member))) return AvailabilityCheck.unavailable(AvailabilityStatus.MEMBER_BUSY, member);
        }
        return AvailabilityCheck.eligible();
    }

    private void processBatch(AdmissionBatch batch) {
        batch.cancelled().forEach(cancelled -> {
            RunContext ctx = runs.get(cancelled.request().sessionId());
            if (ctx != null) discard(ctx, "§c입장 순간 파티원 상태가 달라 대기가 취소되었습니다.", true);
        });
        batch.admitted().forEach(request -> {
            RunContext ctx = runs.get(request.sessionId());
            if (ctx != null) provision(ctx);
        });
    }

    private void provision(RunContext ctx) {
        if (!ctx.session.beginProvisioning().succeeded()) { discard(ctx, "§c잘못된 인스턴스 상태입니다.", true); return; }
        broadcast(ctx.roster, "§7전용 미궁 공간과 방을 생성하는 중입니다. 로비에서 잠시 기다려 주세요...");
        persistSnapshot(ctx);
        MapPack map = map(ctx);
        worlds.provision(ctx.session.id(), map.mazeId(), map.mapVersion(), ctx.roster)
                .thenCompose(handle -> {
                    ctx.handle = handle;
                    if (ctx.cancelled) return worlds.releaseWithRetry(ctx.session.id(), handle)
                            .thenCompose(ignored -> CompletableFuture.failedFuture(new IllegalStateException("Admission cancelled")));
                    return isolation.captureAndEnter(ctx.session.id(), ctx.roster, handle).thenApply(ignored -> handle);
                }).whenComplete((handle, failure) -> onMain(() -> {
                    if (failure != null) { discard(ctx, "§c인스턴스 준비에 실패했습니다: " + rootMessage(failure), true); return; }
                    if (ctx.cancelled) { discard(ctx, "§e입장이 취소되었습니다.", true); return; }
                    activate(ctx);
                }));
    }

    private void activate(RunContext ctx) {
        Instant now = clock.instant();
        if (!ctx.session.activate(now).succeeded()) { discard(ctx, "§c세션 활성화에 실패했습니다.", true); return; }
        parties.markRunActive(ctx.partyId);
        ctx.afk.resumeActivePlay();
        ctx.room = new PaperRoomRuntime(ctx.session.id(), ctx.session.roomAttemptRevision(), map(ctx).room(ctx.session.currentRoom()), ctx.roster);
        teleportToRoom(ctx);
        broadcast(ctx.roster, "§6[방 " + ctx.session.currentRoom() + "] §f" + ctx.room.room().title());
        broadcast(ctx.roster, "§7" + ctx.room.room().intro());
        visibility.refreshAll();
        persistSnapshot(ctx);
    }

    private void applySignal(RunContext ctx, PaperRoomRuntime.Signal signal) {
        if (signal == PaperRoomRuntime.Signal.NONE) return;
        if (signal == PaperRoomRuntime.Signal.PROGRESSED) return;
        Instant now = clock.instant();
        if (signal == PaperRoomRuntime.Signal.ROOM_FAILED) {
            String message = ctx.room.room().failure();
            ctx.room.cleanup();
            ctx.session.failCurrentRoom(now);
            World world = plugin.getServer().getWorld(ctx.handle.instanceName());
            if (world != null) ctx.room.restoreBlocks(world);
            ctx.room = new PaperRoomRuntime(ctx.session.id(), ctx.session.roomAttemptRevision(),
                    map(ctx).room(ctx.session.currentRoom()), ctx.roster);
            teleportToRoom(ctx);
            broadcast(ctx.roster, "§c" + message);
            persistSnapshot(ctx);
            return;
        }
        String completion = ctx.room.room().completion();
        ctx.room.cleanup();
        ctx.session.completeCurrentRoom(now);
        broadcast(ctx.roster, "§a" + completion);
        if (ctx.session.state() == SessionState.COMPLETED) {
            complete(ctx, now);
            return;
        }
        ctx.room = new PaperRoomRuntime(ctx.session.id(), ctx.session.roomAttemptRevision(),
                map(ctx).room(ctx.session.currentRoom()), ctx.roster);
        teleportToRoom(ctx);
        broadcast(ctx.roster, "§6[방 " + ctx.session.currentRoom() + "] §f" + ctx.room.room().title());
        broadcast(ctx.roster, "§7" + ctx.room.room().intro());
        persistSnapshot(ctx);
    }

    private void complete(RunContext ctx, Instant now) {
        ctx.afk.pauseActivePlay();
        PuzzleSessionSnapshot snapshot = ctx.session.snapshot(now);
        CompletedRun run = CompletedRun.from(snapshot);
        for (Player player : onlinePlayers(ctx.roster)) {
            player.sendTitle("§6미궁 완주!", "§f" + formatDuration(run.metrics().activePlayTime()), 10, 80, 20);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
            Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().withColor(Color.LIME, Color.AQUA).trail(true).build());
            meta.setPower(1); firework.setFireworkMeta(meta);
        }
        finalizeCompletionWithRetry(ctx, run, 3);
    }

    private void suspendOrDiscard(RunContext ctx, String message) {
        ctx.afk.pauseActivePlay();
        if (ctx.session.checkpoint().isEmpty()) {
            broadcast(ctx.roster, message + " §7첫 방 체크포인트 전이므로 세이브는 생성되지 않습니다.");
            cleanupActive(ctx, "");
            return;
        }
        broadcast(ctx.roster, message + " §a슬롯 " + ctx.slot + "에 체크포인트를 저장합니다.");
        saveWithRetry(ctx, 3).whenComplete((ignored, failure) -> onMain(() -> {
            if (failure != null) {
                ctx.cleaning = false;
                broadcast(ctx.roster, "§c세이브 저장에 실패했습니다. 인스턴스를 유지합니다. 파티장이 /maze leave로 다시 시도하세요.");
                plugin.getLogger().severe("세이브 저장 실패, 인스턴스 유지: " + rootMessage(failure));
                return;
            }
            cleanupActive(ctx, "");
        }));
    }

    private CompletionStage<Void> save(RunContext ctx) {
        return saveSlots(ctx.session.mazeId()).store(ctx.slot, ctx.saveOwner, ctx.roster, ctx.session);
    }

    private CompletionStage<Void> saveWithRetry(RunContext ctx, int attempts) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        save(ctx).whenComplete((ignored, failure) -> {
            if (failure == null) result.complete(null);
            else if (attempts <= 1 || shuttingDown()) result.completeExceptionally(failure);
            else plugin.getServer().getScheduler().runTaskLater(plugin,
                        () -> saveWithRetry(ctx, attempts - 1).whenComplete((again, retryFailure) -> {
                            if (retryFailure == null) result.complete(null); else result.completeExceptionally(retryFailure);
                        }), 20L);
        });
        return result;
    }

    private boolean shuttingDown() { return stopping || !plugin.isEnabled(); }

    private void finalizeCompletionWithRetry(RunContext ctx, CompletedRun run, int attempts) {
        CompletionStage<Void> durable = durableCompletion(ctx, run);
        durable.whenComplete((ignored, failure) -> onMain(() -> {
            if (failure == null) {
                cleanupActive(ctx, "§a완주 기록을 저장하고 로비로 돌아왔습니다.");
                return;
            }
            if (stopping || !plugin.isEnabled()) {
                plugin.getLogger().severe("종료 전 완주 기록 저장 실패, 완료 스냅샷 유지 시도: " + rootMessage(failure));
                persistence.save(ctx.session.snapshot(clock.instant()));
                return;
            }
            long delay = attempts > 1 ? 20L : 600L;
            int nextAttempts = attempts > 1 ? attempts - 1 : 3;
            broadcast(ctx.roster, attempts > 1
                    ? "§e완주 기록 저장을 재시도합니다. 안전을 위해 인스턴스를 유지합니다."
                    : "§c완주 기록 DB 장애가 계속됩니다. 30초 뒤 다시 시도합니다.");
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> finalizeCompletionWithRetry(ctx, run, nextAttempts), delay);
        }));
    }

    private CompletionStage<Void> durableCompletion(RunContext ctx, CompletedRun run) {
        return persistence.record(run)
                .thenCompose(ignored -> ctx.resumed
                        ? saveSlots(ctx.session.mazeId()).delete(ctx.saveOwner, ctx.slot).thenApply(deleted -> null)
                        : CompletableFuture.completedFuture(null))
                .thenCompose(ignored -> persistence.delete(ctx.session.id()));
    }

    private void cleanupActive(RunContext ctx, String message) {
        if (ctx.cleaning) return;
        ctx.cleaning = true;
        if (ctx.room != null) ctx.room.cleanup();
        cleanup.restoreThenRelease(ctx.session.id(), ctx.roster, ctx.handle)
                .whenComplete((released, failure) -> onMain(() -> finishRelease(ctx, message, failure)));
    }

    private void finishRelease(RunContext ctx, String message, Throwable releaseFailure) {
        if (releaseFailure != null) {
            ctx.cleaning = false;
            plugin.getLogger().severe("인스턴스 정리 실패, 소유권/용량 유지: " + rootMessage(releaseFailure));
            broadcast(ctx.roster, "§c안전한 상태/월드 복원에 실패해 인스턴스를 유지합니다. OP 확인이 필요합니다.");
            return;
        }
        unregister(ctx);
        parties.completeRun(ctx.partyId);
        persistence.delete(ctx.session.id());
        var released = admissions.release(ctx.session.id(), this::availability);
        if (released.released()) processBatch(released.batch());
        if (!message.isBlank()) broadcast(ctx.roster, message);
        visibility.refreshAll();
    }

    private void discard(RunContext ctx, String message, boolean releaseCapacity) {
        if (ctx.cleaning) return;
        ctx.cleaning = true;
        broadcast(ctx.roster, message);
        if (ctx.room != null) ctx.room.cleanup();
        CompletionStage<Void> release = ctx.handle == null ? CompletableFuture.completedFuture(null)
                : cleanup.restoreThenRelease(ctx.session.id(), ctx.roster, ctx.handle);
        release.whenComplete((ignored, failure) -> onMain(() -> {
            if (failure != null) {
                ctx.cleaning = false;
                plugin.getLogger().severe("취소 인스턴스 정리 실패, 소유권/용량 유지: " + rootMessage(failure));
                return;
            }
            unregister(ctx);
            parties.leave(ctx.roster.leaderId());
            persistence.delete(ctx.session.id());
            if (releaseCapacity) {
                var result = admissions.release(ctx.session.id(), this::availability);
                if (result.released()) processBatch(result.batch());
            }
            visibility.refreshAll();
        }));
    }

    private void teleportToRoom(RunContext ctx) {
        World world = plugin.getServer().getWorld(ctx.handle.instanceName());
        if (world == null) return;
        MapPack.Position spawn = ctx.room.room().spawn();
        for (int index = 0; index < ctx.roster.members().size(); index++) {
            Player player = plugin.getServer().getPlayer(ctx.roster.members().get(index));
            if (player == null) continue;
            double offset = (index - (ctx.roster.size() - 1) / 2.0) * 1.5;
            Location target = new Location(world, spawn.x() + offset, spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
            teleportPermits.runPermitted(player.getUniqueId(), () -> player.teleport(target));
            var briefing = briefingBooks.create(ctx.room.room());
            player.getInventory().setItem(0, briefing);
            player.getInventory().setHeldItemSlot(0);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                RunContext current = activeContext(player);
                if (current == ctx && current.room == ctx.room) player.openBook(briefing);
            });
        }
    }

    private void register(RunContext ctx) {
        runs.put(ctx.session.id(), ctx);
        ctx.roster.members().forEach(member -> runByPlayer.put(member, ctx.session.id()));
    }

    private void unregister(RunContext ctx) {
        runs.remove(ctx.session.id());
        ctx.roster.members().forEach(member -> runByPlayer.remove(member, ctx.session.id()));
        hints.clearSession(ctx.session.id());
    }

    private RunContext context(UUID playerId) {
        SessionId id = runByPlayer.get(playerId);
        return id == null ? null : runs.get(id);
    }

    private MapPack map(String mazeId) {
        MapPack selected = maps.get(mazeId);
        if (selected == null) throw new IllegalArgumentException("알 수 없는 미궁입니다: " + mazeId);
        return selected;
    }

    private MapPack map(RunContext ctx) {
        MapPack selected = map(ctx.session.mazeId());
        if (!selected.mapVersion().equals(ctx.session.mapVersion())) {
            throw new IllegalStateException("세션과 현재 미궁 버전이 다릅니다: " + ctx.session.mazeId());
        }
        return selected;
    }

    private SaveSlotWorkflow saveSlots(String mazeId) {
        SaveSlotWorkflow workflow = saveSlots.get(mazeId);
        if (workflow == null) throw new IllegalArgumentException("알 수 없는 미궁입니다: " + mazeId);
        return workflow;
    }

    private RunContext activeContext(Player player) {
        RunContext ctx = context(player.getUniqueId());
        return ctx != null && ctx.session.state() == SessionState.ACTIVE ? ctx : null;
    }

    private void persistSnapshot(RunContext ctx) {
        try {
            persistence.save(ctx.session.snapshot(clock.instant())).whenComplete((ignored, failure) -> {
                if (failure != null) plugin.getLogger().severe("런타임 스냅샷 저장 실패: " + rootMessage(failure));
            });
        } catch (RuntimeException failure) {
            plugin.getLogger().severe("런타임 스냅샷 생성 실패: " + failure.getMessage());
        }
    }

    private boolean checkEntryPreconditions(Player actor, int slot) {
        if (!readiness.acceptsEntry() || stopping) { actor.sendMessage("§c미궁 입장 불가: " + readiness.detail()); return false; }
        if (slot < 1 || slot > 3) { actor.sendMessage("§c슬롯은 1~3이어야 합니다."); return false; }
        if (!resourcePacks.canEnter(actor.getUniqueId())) { actor.sendMessage(resourcePacks.denialReason(actor.getUniqueId())); return false; }
        PartyView party = parties.findByPlayer(actor.getUniqueId()).orElse(null);
        if (party == null) { actor.sendMessage("§c먼저 /maze party create로 파티를 만드세요."); return false; }
        if (!party.leaderId().equals(actor.getUniqueId())) { actor.sendMessage("§c파티장만 시작할 수 있습니다."); return false; }
        if (context(actor.getUniqueId()) != null) { actor.sendMessage("§c이미 미궁 입장 절차가 진행 중입니다."); return false; }
        for (UUID member : party.members()) {
            Player online = plugin.getServer().getPlayer(member);
            if (online == null || !online.isOnline()) { actor.sendMessage("§c모든 파티원이 온라인이어야 합니다."); return false; }
            if (!resourcePacks.canEnter(member)) { actor.sendMessage("§c모든 파티원이 필수 리소스 팩을 불러와야 합니다."); return false; }
            if (isolation.isRecoveryBlocked(member)) { actor.sendMessage("§c복구 격리된 플레이어가 있어 OP 확인이 필요합니다."); return false; }
        }
        return true;
    }

    private boolean checkResumePreconditions(Player actor, int slot) {
        if (!readiness.acceptsEntry() || stopping) { actor.sendMessage("§c미궁 입장 불가: " + readiness.detail()); return false; }
        if (slot < 1 || slot > 3) { actor.sendMessage("§c슬롯은 1~3이어야 합니다."); return false; }
        PartyView party = parties.findByPlayer(actor.getUniqueId()).orElse(null);
        if (party == null) { actor.sendMessage("§c먼저 원래 참가자 전원으로 파티를 구성하세요."); return false; }
        if (context(actor.getUniqueId()) != null) { actor.sendMessage("§c이미 미궁 입장 절차가 진행 중입니다."); return false; }
        for (UUID member : party.members()) {
            Player online = plugin.getServer().getPlayer(member);
            if (online == null || !online.isOnline()) { actor.sendMessage("§c모든 파티원이 온라인이어야 합니다."); return false; }
            if (!resourcePacks.canEnter(member)) { actor.sendMessage("§c모든 파티원이 필수 리소스 팩을 불러와야 합니다."); return false; }
            if (isolation.isRecoveryBlocked(member)) { actor.sendMessage("§c복구 격리된 플레이어가 있어 OP 확인이 필요합니다."); return false; }
        }
        return true;
    }

    private boolean consumeOverwrite(UUID leader, String mazeId, int slot) {
        PendingOverwrite pending = pendingOverwrites.remove(leader);
        return pending != null && pending.mazeId.equals(mazeId) && pending.slot == slot
                && clock.instant().isBefore(pending.expiresAt);
    }

    private HintContextId hintContext(RunContext ctx) {
        return new HintContextId(ctx.session.id(), ctx.session.currentRoom(), ctx.session.roomAttemptRevision());
    }

    private void sendHintOutcome(Player player, HintOutcome outcome) {
        String message = switch (outcome.type()) {
            case ALL_TIERS_UNLOCKED -> "§e모든 힌트가 이미 열렸습니다.";
            case ALREADY_PENDING -> "§e이미 파티장 승인을 기다리는 힌트가 있습니다.";
            case NOT_LEADER -> "§c파티장만 승인할 수 있습니다.";
            case NO_PENDING_REQUEST -> "§c승인 대기 중인 힌트가 없습니다.";
            case TIER_NOT_UNLOCKED -> "§c아직 열리지 않은 힌트입니다.";
            case DECLINED -> "§7힌트 요청을 거절했습니다.";
            default -> "§7힌트 요청 상태: " + outcome.type();
        };
        player.sendMessage(message);
    }

    private void tellResult(Player player, PartyServiceResult result, String success) {
        if (result.succeeded()) { if (!success.isBlank()) player.sendMessage(success); return; }
        player.sendMessage("§c" + partyError(result.error().orElseThrow()));
    }

    private String partyError(PartyServiceError error) {
        return switch (error) {
            case PARTY_NOT_FOUND -> "파티를 찾을 수 없습니다.";
            case NOT_LEADER -> "파티장만 할 수 있습니다.";
            case ALREADY_IN_PARTY_OR_RUN -> "이미 파티 또는 미궁에 속해 있습니다.";
            case PARTY_FULL -> "파티는 최대 4명입니다.";
            case INVITE_ALREADY_PENDING -> "이미 초대했습니다.";
            case INVITE_NOT_FOUND -> "초대를 찾을 수 없습니다.";
            case TARGET_ALREADY_MEMBER -> "이미 파티원입니다.";
            case TARGET_NOT_MEMBER -> "파티원이 아닙니다.";
            case CANNOT_KICK_LEADER -> "파티장은 내보낼 수 없습니다.";
            case ROSTER_LOCKED -> "입장 시작 후에는 명단을 바꿀 수 없습니다.";
            case INVALID_STATE -> "현재 파티 상태에서는 할 수 없습니다.";
            case PARTY_NOT_OPEN -> "열린 파티에서만 할 수 있습니다.";
            case LEADER_MUST_DISBAND -> "파티장은 leave 대신 disband를 사용해야 합니다.";
        };
    }

    private void broadcast(PartyRoster roster, String message) {
        roster.members().forEach(member -> player(member).ifPresent(player -> player.sendMessage(message)));
    }

    private Optional<Player> player(UUID id) { return Optional.ofNullable(plugin.getServer().getPlayer(id)); }
    private List<Player> onlinePlayers(PartyRoster roster) {
        return roster.members().stream().map(plugin.getServer()::getPlayer).filter(Objects::nonNull).filter(Player::isOnline).toList();
    }
    private void onMain(Runnable action) {
        if (stopping || !plugin.isEnabled()) return;
        if (plugin.getServer().isPrimaryThread()) action.run();
        else plugin.getServer().getScheduler().runTask(plugin, action);
    }
    private void asyncFailure(Player player, String operation, Throwable failure) {
        player.sendMessage("§c" + operation + " 실패: " + rootMessage(failure));
        plugin.getLogger().severe(operation + " 실패: " + rootMessage(failure));
    }
    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return Objects.toString(current.getMessage(), current.getClass().getSimpleName());
    }
    private static boolean sameRoster(PartyRoster a, PartyRoster b) {
        return a.leaderId().equals(b.leaderId()) && new HashSet<>(a.members()).equals(new HashSet<>(b.members()));
    }
    private static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        return "%02d:%02d:%02d".formatted(seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    public record MazeOption(String mazeId, String displayName, int roomCount) { }

    public record RunView(SessionId sessionId, String mazeId, String mazeName, SessionState state,
                          int room, int roomCount, int slot, PartyRoster roster, Set<Integer> unlockedHints) { }

    private RunView view(RunContext ctx) {
        int room = ctx.session.currentRoom();
        MapPack selected = map(ctx);
        return new RunView(ctx.session.id(), selected.mazeId(), selected.displayName(), ctx.session.state(),
                room, ctx.session.roomCount(), ctx.slot, ctx.roster,
                Set.copyOf(ctx.session.hintProgress().unlockedByRoom().getOrDefault(room, Set.of())));
    }

    private static final class RunContext {
        private final PartyId partyId;
        private final PartyRoster roster;
        private final PuzzleSession session;
        private final int slot;
        private final PartyAfkTracker afk;
        private final boolean resumed;
        private final UUID saveOwner;
        private WorldInstanceHandle handle;
        private PaperRoomRuntime room;
        private volatile boolean cancelled;
        private boolean cleaning;
        private RunContext(PartyId partyId, PartyRoster roster, PuzzleSession session, int slot, Clock clock,
                           boolean resumed, UUID saveOwner) {
            this.partyId = partyId; this.roster = roster; this.session = session; this.slot = slot;
            this.afk = new PartyAfkTracker(roster, clock);
            this.resumed = resumed; this.saveOwner = saveOwner;
        }
    }
    private record PendingOverwrite(String mazeId, int slot, Instant expiresAt) { }
}
