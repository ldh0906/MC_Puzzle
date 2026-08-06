package dev.mcpuzzle.paper.gui;

import dev.mcpuzzle.core.application.party.PartyLifecycle;
import dev.mcpuzzle.core.application.party.PartyView;
import dev.mcpuzzle.core.domain.LeaderboardEntry;
import dev.mcpuzzle.core.domain.SaveGame;
import dev.mcpuzzle.core.domain.SessionState;
import dev.mcpuzzle.paper.authoring.AuthoringWandService;
import dev.mcpuzzle.paper.resourcepack.PuzzleItemModel;
import dev.mcpuzzle.paper.runtime.MazeRuntimeService;
import dev.mcpuzzle.paper.runtime.PluginReadiness;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MazeMenu implements Listener {
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MM/dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final MazeRuntimeService runtime;
    private final PluginReadiness readiness;
    private final AuthoringWandService authoring;
    private final Consumer<CommandSender> reloadAction;
    private final Consumer<CommandSender> verifyWorldAction;
    private final NamespacedKey actionKey;
    private final MazeMenuItems items;
    private final Map<UUID, Instant> answerPrompts = new ConcurrentHashMap<>();

    public MazeMenu(Plugin plugin, MazeRuntimeService runtime, PluginReadiness readiness,
                    AuthoringWandService authoring, Consumer<CommandSender> reloadAction,
                    Consumer<CommandSender> verifyWorldAction) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.authoring = Objects.requireNonNull(authoring, "authoring");
        this.reloadAction = Objects.requireNonNull(reloadAction, "reloadAction");
        this.verifyWorldAction = Objects.requireNonNull(verifyWorldAction, "verifyWorldAction");
        this.actionKey = new NamespacedKey(plugin, "menu_action");
        this.items = new MazeMenuItems(actionKey);
    }

    public void openMain(Player player) {
        MenuHolder holder = create(player, MenuType.MAIN, player.getUniqueId(), 0, 54, "§1✦ 심야의 미궁 ✦");
        Inventory inventory = holder.inventory;
        decorate(inventory);

        PartyView party = runtime.party(player.getUniqueId()).orElse(null);
        MazeRuntimeService.RunView run = runtime.run(player.getUniqueId()).orElse(null);
        List<String> statusLore = new ArrayList<>();
        statusLore.add(party == null ? "§7파티: §8없음" : "§7파티: §f" + party.members().size() + "명 · " + role(player, party));
        statusLore.add(run == null ? "§7미궁: §8대기 중" : "§7미궁: §f" + stateName(run.state()));
        if (run != null) {
            statusLore.add("§7진행: §d" + run.room() + "§7/§f" + run.roomCount() + "번 방");
            statusLore.add("§7슬롯: §b" + run.slot());
        }
        inventory.setItem(4, items.material(Material.NETHER_STAR, "§6§l현재 상태", MazeMenuAction.of(MazeMenuAction.Type.MAIN), statusLore));

        inventory.setItem(20, items.model(PuzzleItemModel.PARTY, party == null ? "§e파티 만들기" : "§e파티 관리",
                MazeMenuAction.of(MazeMenuAction.Type.PARTY), List.of(
                        party == null ? "§7함께 도전할 파티를 만듭니다." : "§7명단, 초대, 추방을 관리합니다.",
                        "", "§e클릭해서 열기")));
        inventory.setItem(22, items.model(PuzzleItemModel.SAVES, "§b세이브 · 미궁 입장",
                MazeMenuAction.of(MazeMenuAction.Type.SAVES), List.of("§7새 미궁 시작과 진행 재개", "§7슬롯 상세 정보를 확인합니다.", "", "§b클릭해서 열기")));
        inventory.setItem(24, items.model(PuzzleItemModel.HINT, "§d힌트 보관소",
                MazeMenuAction.of(MazeMenuAction.Type.HINTS), List.of("§7현재 방의 힌트를 요청하거나", "§7이미 열린 힌트를 다시 봅니다.", "", "§d클릭해서 열기")));
        inventory.setItem(30, items.material(Material.GOLDEN_HELMET, "§6명예의 전당",
                MazeMenuAction.of(MazeMenuAction.Type.LEADERBOARD, "1"), List.of("§71~4인 파티별 완주 순위", "", "§6클릭해서 열기")));

        int invitationCount = runtime.invitations(player.getUniqueId()).size();
        inventory.setItem(32, items.material(invitationCount == 0 ? Material.ENDER_PEARL : Material.ENDER_EYE,
                invitationCount == 0 ? "§7받은 파티 초대 없음" : "§a받은 파티 초대 §f" + invitationCount + "개",
                MazeMenuAction.of(MazeMenuAction.Type.PARTY_INVITATIONS, "0"), List.of("§7초대를 수락하거나 거절합니다.", "", "§a클릭해서 열기")));

        if (run == null) {
            inventory.setItem(40, items.model(PuzzleItemModel.START, "§a도전 준비",
                    MazeMenuAction.of(MazeMenuAction.Type.SAVES), List.of("§7파티를 구성하고 슬롯을 골라", "§7새 미궁을 시작합니다.", "", "§a클릭해서 슬롯 선택")));
        } else if (dashboardActions(run.state()).contains(MazeMenuAction.Type.QUEUE_CANCEL)) {
            inventory.setItem(40, items.material(Material.BARRIER, "§c입장 대기 취소",
                    MazeMenuAction.of(MazeMenuAction.Type.QUEUE_CANCEL), List.of("§7파티장만 취소할 수 있습니다.", "", "§c클릭 후 확인")));
        } else if (dashboardActions(run.state()).contains(MazeMenuAction.Type.ANSWER_PROMPT)) {
            inventory.setItem(39, items.material(Material.NAME_TAG, "§a정답 입력",
                    MazeMenuAction.of(MazeMenuAction.Type.ANSWER_PROMPT), List.of("§7명령어 없이 채팅으로 현재 방의", "§7정답을 한 번 제출합니다.", "", "§a클릭해서 입력 시작")));
            inventory.setItem(41, items.material(Material.OAK_DOOR, "§c미궁 저장 후 나가기",
                    MazeMenuAction.of(MazeMenuAction.Type.RUN_LEAVE), List.of("§7체크포인트 진행을 저장하고", "§7파티 전체가 로비로 돌아갑니다.", "", "§c클릭 후 확인")));
        } else if (dashboardActions(run.state()).contains(MazeMenuAction.Type.RUN_LEAVE)) {
            inventory.setItem(40, items.material(Material.OAK_DOOR, "§e저장·퇴장 다시 시도",
                    MazeMenuAction.of(MazeMenuAction.Type.RUN_LEAVE), List.of("§7중단된 진행의 저장과 정리를", "§7다시 요청합니다.", "", "§e클릭 후 확인")));
        } else {
            inventory.setItem(40, items.material(Material.CLOCK, "§7" + stateName(run.state()),
                    MazeMenuAction.of(MazeMenuAction.Type.MAIN), List.of("§8현재 단계가 끝날 때까지 기다려 주세요.")));
        }

        if (player.hasPermission("mcpuzzle.admin")) {
            inventory.setItem(47, items.material(Material.COMMAND_BLOCK, "§5관리자 허브",
                    MazeMenuAction.of(MazeMenuAction.Type.ADMIN), List.of("§7검증, 리로드, 제작 도구와", "§7플레이어 세이브를 관리합니다.")));
        }
        inventory.setItem(49, items.material(Material.BARRIER, "§c닫기", MazeMenuAction.of(MazeMenuAction.Type.CLOSE), List.of()));
        items.fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    public void openParty(Player player) {
        MenuHolder holder = create(player, MenuType.PARTY, player.getUniqueId(), 0, 54, "§1파티 관리");
        Inventory inventory = holder.inventory;
        decorate(inventory);
        PartyView party = runtime.party(player.getUniqueId()).orElse(null);

        if (party == null) {
            inventory.setItem(22, items.model(PuzzleItemModel.PARTY, "§a새 파티 만들기",
                    MazeMenuAction.of(MazeMenuAction.Type.PARTY_CREATE), List.of("§7파티장이 되어 최대 4명이", "§7함께 미궁에 도전합니다.", "", "§a클릭해서 만들기")));
            inventory.setItem(31, items.material(Material.ENDER_EYE, "§e받은 초대 확인",
                    MazeMenuAction.of(MazeMenuAction.Type.PARTY_INVITATIONS, "0"), List.of("§7다른 파티가 보낸 초대를 확인합니다.")));
        } else {
            OfflinePlayer leader = Bukkit.getOfflinePlayer(party.leaderId());
            inventory.setItem(4, items.playerHead(leader, "§6§l파티장 · " + displayName(leader),
                    MazeMenuAction.of(MazeMenuAction.Type.PARTY), List.of("§7상태: §f" + lifecycleName(party.lifecycle()), "§7인원: §f" + party.members().size() + "/4")));
            int memberSlot = 20;
            for (UUID memberId : party.members()) {
                OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
                boolean isLeader = memberId.equals(party.leaderId());
                MazeMenuAction action = !isLeader && player.getUniqueId().equals(party.leaderId())
                        ? MazeMenuAction.of(MazeMenuAction.Type.PARTY_KICK, memberId.toString())
                        : MazeMenuAction.of(MazeMenuAction.Type.PARTY);
                List<String> lore = new ArrayList<>();
                lore.add(isLeader ? "§6★ 파티장" : "§7파티원");
                lore.add(member.isOnline() ? "§a● 온라인" : "§8● 오프라인");
                if (!isLeader && player.getUniqueId().equals(party.leaderId())) lore.add("§c클릭해서 추방");
                inventory.setItem(memberSlot++, items.playerHead(member, "§f" + displayName(member), action, lore));
            }

            boolean leaderView = player.getUniqueId().equals(party.leaderId());
            if (leaderView && party.lifecycle() == PartyLifecycle.OPEN && party.members().size() < 4) {
                inventory.setItem(38, items.material(Material.LIME_DYE, "§a플레이어 초대",
                        MazeMenuAction.of(MazeMenuAction.Type.PARTY_INVITE_LIST, "0"), List.of("§7온라인 플레이어 목록을 엽니다.")));
            }
            if (leaderView) {
                inventory.setItem(42, items.material(Material.RED_DYE, "§c파티 해산",
                        MazeMenuAction.of(MazeMenuAction.Type.PARTY_DISBAND), List.of("§7모든 파티원을 내보냅니다.", "", "§c클릭 후 확인")));
            } else {
                inventory.setItem(42, items.material(Material.OAK_DOOR, "§c파티 나가기",
                        MazeMenuAction.of(MazeMenuAction.Type.PARTY_LEAVE), List.of("§7열린 파티에서 나갑니다.", "", "§c클릭 후 확인")));
            }
        }
        back(inventory, 49, MazeMenuAction.of(MazeMenuAction.Type.MAIN));
        items.fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    public void openInvitations(Player player, int page) {
        List<PartyView> invitations = runtime.invitations(player.getUniqueId());
        int safePage = normalizePage(page, invitations.size());
        MenuHolder holder = create(player, MenuType.INVITATIONS, player.getUniqueId(), safePage, 54, "§1받은 파티 초대");
        Inventory inventory = holder.inventory;
        decorate(inventory);
        if (invitations.isEmpty()) {
            inventory.setItem(22, items.material(Material.GRAY_DYE, "§7받은 초대가 없습니다", MazeMenuAction.of(MazeMenuAction.Type.PARTY), List.of("§8새 초대가 오면 여기에 표시됩니다.")));
        } else {
            int start = safePage * CONTENT_SLOTS.length;
            for (int index = start; index < Math.min(start + CONTENT_SLOTS.length, invitations.size()); index++) {
                PartyView invitation = invitations.get(index);
                OfflinePlayer leader = Bukkit.getOfflinePlayer(invitation.leaderId());
                inventory.setItem(CONTENT_SLOTS[index - start], items.playerHead(leader, "§e" + displayName(leader) + "님의 파티",
                        MazeMenuAction.of(MazeMenuAction.Type.PARTY_ACCEPT, invitation.leaderId().toString()),
                        List.of("§7현재 인원: §f" + invitation.members().size() + "/4", "", "§a좌클릭: 수락", "§c우클릭: 거절")));
            }
            pagination(inventory, safePage, invitations.size(), MazeMenuAction.Type.PARTY_INVITATIONS);
        }
        back(inventory, 49, MazeMenuAction.of(MazeMenuAction.Type.PARTY));
        items.fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    public void openInvitePlayers(Player player, int page) {
        List<? extends Player> candidates = Bukkit.getOnlinePlayers().stream()
                .filter(candidate -> !candidate.getUniqueId().equals(player.getUniqueId()))
                .filter(candidate -> runtime.party(candidate.getUniqueId()).isEmpty())
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int safePage = normalizePage(page, candidates.size());
        MenuHolder holder = create(player, MenuType.INVITE_PLAYERS, player.getUniqueId(), safePage, 54, "§1초대할 플레이어");
        Inventory inventory = holder.inventory;
        decorate(inventory);
        if (candidates.isEmpty()) {
            inventory.setItem(22, items.material(Material.GRAY_DYE, "§7초대 가능한 플레이어가 없습니다",
                    MazeMenuAction.of(MazeMenuAction.Type.PARTY), List.of("§8이미 파티에 속한 플레이어는 제외됩니다.")));
        } else {
            int start = safePage * CONTENT_SLOTS.length;
            for (int index = start; index < Math.min(start + CONTENT_SLOTS.length, candidates.size()); index++) {
                Player candidate = candidates.get(index);
                inventory.setItem(CONTENT_SLOTS[index - start], items.playerHead(candidate, "§a" + candidate.getName(),
                        MazeMenuAction.of(MazeMenuAction.Type.PARTY_INVITE, candidate.getUniqueId().toString()),
                        List.of("§7미궁 파티에 초대합니다.", "", "§a클릭해서 초대")));
            }
            pagination(inventory, safePage, candidates.size(), MazeMenuAction.Type.PARTY_INVITE_LIST);
        }
        back(inventory, 49, MazeMenuAction.of(MazeMenuAction.Type.PARTY));
        items.fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    public void openSaves(Player player) {
        openSaves(player, player.getUniqueId());
    }

    private void openSaves(Player player, UUID ownerId) {
        openSaves(player, ownerId, false);
    }

    private void openSaves(Player player, UUID ownerId, boolean administrator) {
        if (administrator && !requireAdmin(player)) return;
        MenuHolder holder = create(player, administrator ? MenuType.ADMIN_SAVES : MenuType.SAVES,
                ownerId, 0, 54, administrator ? "§5관리자 세이브 · " + displayName(Bukkit.getOfflinePlayer(ownerId)) : "§1미궁 세이브 슬롯");
        Inventory inventory = holder.inventory;
        decorate(inventory);
        MazeMenuAction refresh = administrator
                ? MazeMenuAction.of(MazeMenuAction.Type.ADMIN_SAVES, ownerId.toString())
                : MazeMenuAction.of(MazeMenuAction.Type.SAVES, ownerId.toString());
        inventory.setItem(22, items.material(Material.CLOCK, "§e세이브를 불러오는 중...", refresh, List.of("§7잠시만 기다려 주세요.")));
        back(inventory, 49, administrator
                ? MazeMenuAction.of(MazeMenuAction.Type.ADMIN_PLAYERS, "0")
                : MazeMenuAction.of(MazeMenuAction.Type.MAIN));
        items.fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inventory);

        runtime.saves(player, ownerId).whenComplete((saves, failure) -> onMain(() -> {
            if (!isCurrent(player, holder)) return;
            if (failure != null) {
                inventory.setItem(22, items.material(Material.BARRIER, "§c세이브를 불러오지 못했습니다",
                        refresh, List.of("§7" + rootMessage(failure), "", "§e클릭해서 다시 시도")));
                return;
            }
            renderSaves(player, holder, saves);
        }));
    }

    public void openHints(Player player) {
        MenuHolder holder = create(player, MenuType.HINTS, player.getUniqueId(), 0, 45, "§5미궁 힌트 보관소");
        Inventory inventory = holder.inventory;
        decorate(inventory);
        MazeRuntimeService.RunView run = runtime.run(player.getUniqueId()).orElse(null);
        if (run == null || run.state() != SessionState.ACTIVE) {
            inventory.setItem(22, items.material(Material.GRAY_DYE, "§7활성 미궁이 없습니다",
                    MazeMenuAction.of(MazeMenuAction.Type.MAIN), List.of("§8미궁에 입장하면 힌트를 사용할 수 있습니다.")));
        } else {
            inventory.setItem(4, items.material(Material.COMPASS, "§d현재 §f" + run.room() + "§d/§f" + run.roomCount() + "§d번 방",
                    MazeMenuAction.of(MazeMenuAction.Type.HINTS), List.of("§7열린 힌트: §f" + run.unlockedHints().size() + "/3")));
            inventory.setItem(11, items.model(PuzzleItemModel.HINT_REQUEST, "§d다음 힌트 요청",
                    MazeMenuAction.of(MazeMenuAction.Type.HINT_REQUEST), List.of("§7파티장의 승인을 받아", "§7다음 단계를 해제합니다.", "", "§d클릭해서 요청")));
            for (int tier = 1; tier <= 3; tier++) {
                boolean unlocked = run.unlockedHints().contains(tier);
                inventory.setItem(17 + tier * 2, items.model(hintViewModel(tier),
                        unlocked ? "§d힌트 " + tier + " 보기" : "§8잠긴 힌트 " + tier,
                        MazeMenuAction.of(MazeMenuAction.Type.HINT_VIEW, Integer.toString(tier)),
                        List.of(unlocked ? "§a이미 해제된 힌트입니다." : "§7먼저 이전 힌트를 요청하세요.", "", unlocked ? "§d클릭해서 보기" : "§8아직 볼 수 없습니다")));
            }
            inventory.setItem(31, items.model(PuzzleItemModel.APPROVE, "§a요청 승인",
                    MazeMenuAction.of(MazeMenuAction.Type.HINT_APPROVE), List.of("§7파티장 전용", "§a클릭해서 승인")));
            inventory.setItem(33, items.model(PuzzleItemModel.REJECT, "§c요청 거절",
                    MazeMenuAction.of(MazeMenuAction.Type.HINT_DECLINE), List.of("§7파티장 전용", "§c클릭해서 거절")));
        }
        back(inventory, 40, MazeMenuAction.of(MazeMenuAction.Type.MAIN));
        items.fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    public void openLeaderboard(Player player, int partySize) {
        int size = Math.max(1, Math.min(4, partySize));
        MenuHolder holder = create(player, MenuType.LEADERBOARD, player.getUniqueId(), size, 54, "§1명예의 전당 · " + size + "인");
        Inventory inventory = holder.inventory;
        decorate(inventory);
        for (int tab = 1; tab <= 4; tab++) {
            inventory.setItem(1 + tab * 2, items.material(tab == size ? Material.GOLD_INGOT : Material.IRON_INGOT,
                    (tab == size ? "§6§l" : "§7") + tab + "인 파티",
                    MazeMenuAction.of(MazeMenuAction.Type.LEADERBOARD, Integer.toString(tab)), List.of("§7클릭해서 순위 보기")));
        }
        inventory.setItem(22, items.material(Material.CLOCK, "§e순위를 불러오는 중...", MazeMenuAction.of(MazeMenuAction.Type.LEADERBOARD, Integer.toString(size)), List.of()));
        back(inventory, 49, MazeMenuAction.of(MazeMenuAction.Type.MAIN));
        items.fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inventory);

        runtime.leaderboardEntries(size).whenComplete((entries, failure) -> onMain(() -> {
            if (!isCurrent(player, holder)) return;
            inventory.setItem(22, items.decoration(Material.BLACK_STAINED_GLASS_PANE));
            if (failure != null) {
                inventory.setItem(22, items.material(Material.BARRIER, "§c순위를 불러오지 못했습니다",
                        MazeMenuAction.of(MazeMenuAction.Type.LEADERBOARD, Integer.toString(size)), List.of("§7" + rootMessage(failure))));
                return;
            }
            renderLeaderboard(inventory, entries);
        }));
    }

    public void openAdmin(Player player) {
        if (!requireAdmin(player)) return;
        MenuHolder holder = create(player, MenuType.ADMIN, player.getUniqueId(), 0, 54, "§5✦ 미궁 관리자 허브 ✦");
        Inventory inventory = holder.inventory;
        items.frame(inventory, Material.PURPLE_STAINED_GLASS_PANE);
        inventory.setItem(4, items.material(Material.BEACON, "§d플러그인 상태 · §f" + readiness.state(),
                MazeMenuAction.of(MazeMenuAction.Type.ADMIN), List.of("§7" + readiness.detail())));
        inventory.setItem(20, items.material(Material.REPEATER, "§e설정·맵 리로드",
                MazeMenuAction.of(MazeMenuAction.Type.ADMIN_RELOAD), List.of("§7현재 정상 레지스트리를 유지하며", "§7새 설정과 맵을 검증합니다.", "", "§e클릭 후 확인")));
        inventory.setItem(22, items.material(Material.STRUCTURE_BLOCK, "§b생성 월드 검증",
                MazeMenuAction.of(MazeMenuAction.Type.ADMIN_VERIFY_WORLD), List.of("§7임시 20방 월드를 생성·검사하고", "§7완료 후 자동으로 정리합니다.", "", "§b클릭해서 실행")));
        inventory.setItem(24, items.material(Material.BLAZE_ROD, "§6제작 완드 지급",
                MazeMenuAction.of(MazeMenuAction.Type.ADMIN_WAND), List.of("§7두 모서리를 선택하는 완드를 받습니다.")));
        inventory.setItem(30, items.material(Material.FILLED_MAP, "§a선택 영역 출력",
                MazeMenuAction.of(MazeMenuAction.Type.ADMIN_SELECTION), List.of("§7현재 선택 영역을 JSON으로 출력합니다.")));
        inventory.setItem(32, items.material(Material.ENDER_CHEST, "§d플레이어 세이브 관리",
                MazeMenuAction.of(MazeMenuAction.Type.ADMIN_PLAYERS, "0"), List.of("§7온라인 플레이어의 세이브를", "§7조회·삭제·이전합니다.")));
        back(inventory, 49, MazeMenuAction.of(MazeMenuAction.Type.MAIN));
        items.fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    private void openAdminPlayers(Player player, int page) {
        if (!requireAdmin(player)) return;
        List<? extends Player> players = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int safePage = normalizePage(page, players.size());
        MenuHolder holder = create(player, MenuType.ADMIN_PLAYERS, player.getUniqueId(), safePage, 54, "§5세이브 소유자 선택");
        Inventory inventory = holder.inventory;
        items.frame(inventory, Material.PURPLE_STAINED_GLASS_PANE);
        int start = safePage * CONTENT_SLOTS.length;
        for (int index = start; index < Math.min(start + CONTENT_SLOTS.length, players.size()); index++) {
            Player target = players.get(index);
            inventory.setItem(CONTENT_SLOTS[index - start], items.playerHead(target, "§d" + target.getName(),
                    MazeMenuAction.of(MazeMenuAction.Type.ADMIN_SAVES, target.getUniqueId().toString()),
                    List.of("§7이 플레이어가 소유한 세이브를 엽니다.")));
        }
        pagination(inventory, safePage, players.size(), MazeMenuAction.Type.ADMIN_PLAYERS);
        back(inventory, 49, MazeMenuAction.of(MazeMenuAction.Type.ADMIN));
        items.fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    private void openAdminTransfer(Player player, UUID ownerId, int slot) {
        if (!requireAdmin(player)) return;
        MenuHolder holder = create(player, MenuType.ADMIN_TRANSFER, ownerId, slot, 54, "§5새 세이브 소유자 선택");
        Inventory inventory = holder.inventory;
        items.frame(inventory, Material.PURPLE_STAINED_GLASS_PANE);
        inventory.setItem(22, items.material(Material.CLOCK, "§e원래 파티 명단을 불러오는 중...",
                MazeMenuAction.of(MazeMenuAction.Type.ADMIN_TRANSFER_PICK, ownerId.toString(), Integer.toString(slot)), List.of()));
        back(inventory, 49, MazeMenuAction.of(MazeMenuAction.Type.ADMIN_SAVES, ownerId.toString()));
        items.fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inventory);

        runtime.saves(player, ownerId).whenComplete((saves, failure) -> onMain(() -> {
            if (!isCurrent(player, holder)) return;
            if (failure != null) {
                inventory.setItem(22, items.material(Material.BARRIER, "§c세이브를 불러오지 못했습니다",
                        MazeMenuAction.of(MazeMenuAction.Type.ADMIN_SAVES, ownerId.toString()), List.of("§7" + rootMessage(failure))));
                return;
            }
            SaveGame save = saves.stream().filter(candidate -> candidate.slot().number() == slot).findFirst().orElse(null);
            if (save == null) {
                inventory.setItem(22, items.material(Material.BARRIER, "§c해당 세이브가 없습니다",
                        MazeMenuAction.of(MazeMenuAction.Type.ADMIN_SAVES, ownerId.toString()), List.of("§7목록으로 돌아가 새로고침하세요.")));
                return;
            }
            int targetSlot = 20;
            for (UUID memberId : save.slot().roster().members()) {
                OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
                inventory.setItem(targetSlot++, items.playerHead(member, "§d" + displayName(member),
                        MazeMenuAction.of(MazeMenuAction.Type.ADMIN_TRANSFER, ownerId.toString(), Integer.toString(slot), memberId.toString()),
                        List.of(memberId.equals(ownerId) ? "§7현재 소유자" : "§7저장 당시 파티원", "", "§d클릭 후 이전 확인")));
            }
        }));
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId.equals(player.getUniqueId())) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String encoded = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        MazeMenuAction action = MazeMenuAction.decode(encoded).orElse(null);
        if (action == null) return;
        runtime.recordActivity(player);
        try {
            dispatch(player, event, action);
        } catch (IllegalArgumentException failure) {
            invalid(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onAnswerChat(AsyncPlayerChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Instant expiresAt = answerPrompts.get(playerId);
        if (expiresAt == null) return;
        if (Instant.now().isAfter(expiresAt)) {
            answerPrompts.remove(playerId, expiresAt);
            return;
        }
        if (!answerPrompts.remove(playerId, expiresAt)) return;
        event.setCancelled(true);
        String answer = event.getMessage().trim();
        onMain(() -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) return;
            if (answer.equalsIgnoreCase("취소") || answer.equalsIgnoreCase("cancel")) {
                player.sendMessage("§7정답 입력을 취소했습니다.");
            } else {
                runtime.submitAnswer(player, answer);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        answerPrompts.remove(event.getPlayer().getUniqueId());
    }

    private void dispatch(Player player, InventoryClickEvent event, MazeMenuAction action) {
        switch (action.type()) {
            case MAIN -> openMain(player);
            case CLOSE -> player.closeInventory();
            case PARTY -> openParty(player);
            case PARTY_CREATE -> { runtime.createParty(player); openParty(player); }
            case PARTY_INVITE_LIST -> openInvitePlayers(player, integer(action, 0, 0, 100));
            case PARTY_INVITE -> player(action, 0).ifPresentOrElse(target -> { runtime.invite(player, target); openParty(player); }, () -> invalid(player));
            case PARTY_INVITATIONS -> openInvitations(player, integer(action, 0, 0, 100));
            case PARTY_ACCEPT -> handleInvitation(player, event, action, true);
            case PARTY_DECLINE -> handleInvitation(player, event, action, false);
            case PARTY_KICK, PARTY_LEAVE, PARTY_DISBAND, SAVE_DELETE, RUN_LEAVE, QUEUE_CANCEL -> openConfirm(player, action);
            case SAVES -> openSaves(player, action.uuid(0).orElse(player.getUniqueId()));
            case SAVE_START -> openConfirm(player, action);
            case SAVE_RESUME -> handleSave(player, event, action);
            case HINTS -> openHints(player);
            case HINT_REQUEST -> { player.closeInventory(); runtime.requestHint(player); }
            case HINT_VIEW -> { player.closeInventory(); runtime.viewHint(player, integer(action, 0, 1, 3)); }
            case HINT_APPROVE -> { player.closeInventory(); runtime.confirmHint(player, true); }
            case HINT_DECLINE -> { player.closeInventory(); runtime.confirmHint(player, false); }
            case LEADERBOARD -> openLeaderboard(player, integer(action, 0, 1, 4));
            case ANSWER_PROMPT -> beginAnswerPrompt(player);
            case ADMIN -> openAdmin(player);
            case ADMIN_RELOAD, ADMIN_DELETE, ADMIN_TRANSFER -> {
                if (requireAdmin(player)) openConfirm(player, action);
            }
            case ADMIN_VERIFY_WORLD -> { if (requireAdmin(player)) { player.closeInventory(); verifyWorldAction.accept(player); } }
            case ADMIN_WAND -> { if (requireAdmin(player)) { player.closeInventory(); authoring.give(player); } }
            case ADMIN_SELECTION -> { if (requireAdmin(player)) { player.closeInventory(); authoring.printSelection(player); } }
            case ADMIN_PLAYERS -> openAdminPlayers(player, integer(action, 0, 0, 100));
            case ADMIN_SAVES -> {
                if (!requireAdmin(player)) return;
                if (action.arguments().size() == 1) openSaves(player, action.uuid(0).orElse(player.getUniqueId()), true);
                else handleAdminSave(player, event, action);
            }
            case ADMIN_TRANSFER_PICK -> openAdminTransfer(player,
                    action.uuid(0).orElse(player.getUniqueId()), integer(action, 1, 1, 3));
            case CONFIRM -> confirm(player, action);
            case CANCEL -> openCancelDestination(player, action);
            default -> invalid(player);
        }
    }

    private void handleInvitation(Player player, InventoryClickEvent event, MazeMenuAction action, boolean accept) {
        if (!accept || event.isRightClick()) {
            action.uuid(0).map(Bukkit::getPlayer).ifPresentOrElse(leader -> {
                runtime.decline(player, leader);
                openInvitations(player, 0);
            }, () -> player.sendMessage("§c초대를 보낸 파티장이 온라인이 아닙니다."));
            return;
        }
        action.uuid(0).map(Bukkit::getPlayer).ifPresentOrElse(leader -> {
            runtime.accept(player, leader);
            openParty(player);
        }, () -> player.sendMessage("§c초대를 보낸 파티장이 온라인이 아닙니다."));
    }

    private void handleSave(Player player, InventoryClickEvent event, MazeMenuAction action) {
        int slot = integer(action, 0, 1, 3);
        UUID owner = action.uuid(1).orElse(player.getUniqueId());
        if (event.isShiftClick()) {
            openConfirm(player, MazeMenuAction.of(MazeMenuAction.Type.SAVE_DELETE, Integer.toString(slot), owner.toString()));
        } else if (event.isRightClick()) {
            openConfirm(player, MazeMenuAction.of(MazeMenuAction.Type.SAVE_START, Integer.toString(slot)));
        } else {
            player.closeInventory();
            runtime.requestResume(player, slot, owner);
        }
    }

    private void handleAdminSave(Player player, InventoryClickEvent event, MazeMenuAction action) {
        UUID ownerId = action.uuid(0).orElse(null);
        int slot = integer(action, 1, 1, 3);
        if (ownerId == null) { invalid(player); return; }
        if (event.isShiftClick()) {
            openConfirm(player, MazeMenuAction.of(MazeMenuAction.Type.ADMIN_DELETE, ownerId.toString(), Integer.toString(slot)));
        } else if (event.isRightClick()) {
            openAdminTransfer(player, ownerId, slot);
        }
    }

    private void beginAnswerPrompt(Player player) {
        MazeRuntimeService.RunView run = runtime.run(player.getUniqueId()).orElse(null);
        if (run == null || run.state() != SessionState.ACTIVE) {
            player.sendMessage("§c정답을 제출할 활성 미궁이 없습니다.");
            return;
        }
        answerPrompts.put(player.getUniqueId(), Instant.now().plusSeconds(60));
        player.closeInventory();
        player.sendMessage("§a[정답 입력] §f60초 안에 채팅으로 정답을 입력하세요.");
        player.sendMessage("§7입력을 중단하려면 §f취소§7라고 입력하세요. 이 메시지는 다른 플레이어에게 보이지 않습니다.");
    }

    private void openConfirm(Player player, MazeMenuAction requested) {
        MenuHolder holder = create(player, MenuType.CONFIRM, player.getUniqueId(), 0, 27, "§4정말 실행할까요?");
        Inventory inventory = holder.inventory;
        items.frame(inventory, Material.RED_STAINED_GLASS_PANE);
        inventory.setItem(11, items.model(PuzzleItemModel.CONFIRM, "§a확인",
                MazeMenuAction.confirmation(requested), List.of("§7이 작업을 실행합니다.")));
        inventory.setItem(15, items.model(PuzzleItemModel.CANCEL, "§c취소",
                cancelAction(requested), List.of("§7이전 화면으로 돌아갑니다.")));
        items.fillEmpty(inventory, Material.BLACK_STAINED_GLASS_PANE);
        player.openInventory(inventory);
    }

    private void confirm(Player player, MazeMenuAction confirmation) {
        MazeMenuAction requested = confirmation.confirmedAction().orElse(null);
        if (requested == null) { invalid(player); return; }
        player.closeInventory();
        switch (requested.type()) {
            case PARTY_KICK -> player(requested, 0).ifPresentOrElse(target -> runtime.kick(player, target), () -> invalid(player));
            case PARTY_LEAVE -> runtime.leaveOpenParty(player);
            case PARTY_DISBAND -> runtime.disbandParty(player);
            case SAVE_START -> runtime.requestStart(player, integer(requested, 0, 1, 3), true);
            case SAVE_DELETE -> runtime.deleteSave(player, requested.uuid(1).orElse(player.getUniqueId()), integer(requested, 0, 1, 3));
            case RUN_LEAVE -> runtime.leave(player);
            case QUEUE_CANCEL -> runtime.cancelQueue(player);
            case ADMIN_RELOAD -> { if (requireAdmin(player)) reloadAction.accept(player); }
            case ADMIN_DELETE -> {
                if (requireAdmin(player)) runtime.deleteSave(player,
                        requested.uuid(0).orElse(player.getUniqueId()), integer(requested, 1, 1, 3));
            }
            case ADMIN_TRANSFER -> {
                if (requireAdmin(player) && requested.uuid(0).isPresent() && requested.uuid(2).isPresent()) {
                    runtime.transferOwnership(player, requested.uuid(0).orElseThrow(),
                            integer(requested, 1, 1, 3), requested.uuid(2).orElseThrow());
                }
            }
            default -> invalid(player);
        }
    }

    private void openCancelDestination(Player player, MazeMenuAction action) {
        if (action.arguments().isEmpty()) { openMain(player); return; }
        try {
            switch (MazeMenuAction.Type.valueOf(action.arguments().get(0))) {
                case PARTY -> openParty(player);
                case SAVES -> openSaves(player);
                default -> openMain(player);
            }
        } catch (IllegalArgumentException failure) {
            openMain(player);
        }
    }

    private void renderSaves(Player player, MenuHolder holder, List<SaveGame> saves) {
        Inventory inventory = holder.inventory;
        boolean administrator = holder.type == MenuType.ADMIN_SAVES;
        for (int slot = 1; slot <= 3; slot++) {
            int currentSlot = slot;
            SaveGame save = saves.stream().filter(candidate -> candidate.slot().number() == currentSlot)
                    .sorted(Comparator.comparing((SaveGame candidate) -> !candidate.slot().ownerId().equals(holder.subjectId)))
                    .findFirst().orElse(null);
            int position = 18 + slot * 2;
            if (save == null) {
                inventory.setItem(position, items.model(saveSlotModel(slot), administrator ? "§7빈 슬롯 " + slot : "§a빈 슬롯 " + slot,
                        administrator ? MazeMenuAction.of(MazeMenuAction.Type.ADMIN_SAVES, holder.subjectId.toString())
                                : MazeMenuAction.of(MazeMenuAction.Type.SAVE_START, Integer.toString(slot)),
                        administrator ? List.of("§8관리할 세이브가 없습니다.")
                                : List.of("§7새 미궁을 처음부터 시작합니다.", "", "§a클릭 후 확인")));
            } else {
                List<String> lore = new ArrayList<>(List.of(
                        "§7진행: §d" + save.snapshot().currentRoom() + "§7/§f" + save.snapshot().roomCount() + "번 방",
                        "§7파티: §f" + save.slot().roster().size() + "명",
                        "§7소유자: §f" + displayName(Bukkit.getOfflinePlayer(save.slot().ownerId())),
                        "§7저장: §f" + DATE_TIME.format(save.slot().updatedAt()),
                        "§7만료: §c" + DATE_TIME.format(save.slot().expiresAt())));
                lore.add("");
                if (administrator) {
                    lore.add("§d우클릭: 소유권 이전");
                    lore.add("§cShift+클릭: 삭제");
                } else {
                    lore.add("§a좌클릭: 재개");
                    lore.add("§6우클릭: 새로 시작");
                    lore.add("§cShift+클릭: 삭제");
                }
                inventory.setItem(position, items.model(saveSlotModel(slot), "§e슬롯 " + slot + " · 저장됨",
                        administrator
                                ? MazeMenuAction.of(MazeMenuAction.Type.ADMIN_SAVES, holder.subjectId.toString(), Integer.toString(slot))
                                : MazeMenuAction.of(MazeMenuAction.Type.SAVE_RESUME, Integer.toString(slot), save.slot().ownerId().toString()), lore));
            }
        }
        inventory.setItem(31, items.material(Material.WRITABLE_BOOK, administrator ? "§d관리자 슬롯 안내" : "§7슬롯 사용 안내",
                administrator ? MazeMenuAction.of(MazeMenuAction.Type.ADMIN_SAVES, holder.subjectId.toString())
                        : MazeMenuAction.of(MazeMenuAction.Type.SAVES, holder.subjectId.toString()),
                administrator ? List.of("§d우클릭 §7소유권 이전", "§cShift+클릭 §7세이브 삭제")
                        : List.of("§a좌클릭 §7진행 재개", "§6우클릭 §7새로 시작", "§cShift+클릭 §7세이브 삭제")));
    }

    private void renderLeaderboard(Inventory inventory, List<LeaderboardEntry> entries) {
        if (entries.isEmpty()) {
            inventory.setItem(22, items.material(Material.PAPER, "§7아직 완주 기록이 없습니다",
                    MazeMenuAction.of(MazeMenuAction.Type.MAIN), List.of("§8첫 기록의 주인공이 되어 보세요.")));
            return;
        }
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 29, 31, 33};
        for (int index = 0; index < Math.min(slots.length, entries.size()); index++) {
            LeaderboardEntry entry = entries.get(index);
            OfflinePlayer leader = Bukkit.getOfflinePlayer(entry.run().roster().leaderId());
            String color = entry.rank() == 1 ? "§6§l" : entry.rank() == 2 ? "§f§l" : entry.rank() == 3 ? "§c§l" : "§7";
            inventory.setItem(slots[index], items.playerHead(leader, color + "#" + entry.rank() + " · " + displayName(leader),
                    MazeMenuAction.of(MazeMenuAction.Type.LEADERBOARD, Integer.toString(entry.run().roster().size())),
                    List.of("§7기록: §f" + formatDuration(entry.run().metrics().activePlayTime()),
                            "§7실패: §f" + entry.run().metrics().failures(),
                            "§7힌트: §f" + entry.run().metrics().hintsUsed(),
                            "§7완주: §f" + DATE_TIME.format(entry.run().completedAt()))));
        }
    }

    private void pagination(Inventory inventory, int page, int total, MazeMenuAction.Type type) {
        if (page > 0) inventory.setItem(45, items.material(Material.ARROW, "§e이전 페이지",
                MazeMenuAction.of(type, Integer.toString(page - 1)), List.of()));
        if ((page + 1) * CONTENT_SLOTS.length < total) inventory.setItem(53, items.material(Material.ARROW, "§e다음 페이지",
                MazeMenuAction.of(type, Integer.toString(page + 1)), List.of()));
    }

    private MazeMenuAction cancelAction(MazeMenuAction action) {
        return switch (action.type()) {
            case PARTY_KICK, PARTY_LEAVE, PARTY_DISBAND -> MazeMenuAction.of(MazeMenuAction.Type.PARTY);
            case SAVE_START, SAVE_DELETE -> MazeMenuAction.of(MazeMenuAction.Type.SAVES);
            case ADMIN_RELOAD -> MazeMenuAction.of(MazeMenuAction.Type.ADMIN);
            case ADMIN_DELETE, ADMIN_TRANSFER -> MazeMenuAction.of(MazeMenuAction.Type.ADMIN_SAVES,
                    action.arguments().isEmpty() ? "" : action.arguments().get(0));
            default -> MazeMenuAction.of(MazeMenuAction.Type.MAIN);
        };
    }

    private MenuHolder create(Player player, MenuType type, UUID subjectId, int value, int size, String title) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), type, subjectId, value);
        holder.inventory = Bukkit.createInventory(holder, size, title);
        return holder;
    }

    private void decorate(Inventory inventory) {
        items.frame(inventory, Material.BLUE_STAINED_GLASS_PANE);
    }

    private void back(Inventory inventory, int slot, MazeMenuAction destination) {
        inventory.setItem(slot, items.model(PuzzleItemModel.BACK, "§c뒤로", destination, List.of()));
    }

    private java.util.Optional<Player> player(MazeMenuAction action, int index) {
        return action.uuid(index).map(Bukkit::getPlayer).filter(Objects::nonNull);
    }

    private int integer(MazeMenuAction action, int index, int min, int max) {
        return action.requireInteger(index, min, max);
    }

    static List<MazeMenuAction.Type> dashboardActions(SessionState state) {
        return switch (state) {
            case QUEUED -> List.of(MazeMenuAction.Type.QUEUE_CANCEL);
            case ACTIVE -> List.of(MazeMenuAction.Type.ANSWER_PROMPT, MazeMenuAction.Type.RUN_LEAVE);
            case SUSPENDED -> List.of(MazeMenuAction.Type.RUN_LEAVE);
            default -> List.of();
        };
    }

    private int normalizePage(int requested, int total) {
        int pages = Math.max(1, (int) Math.ceil(total / (double) CONTENT_SLOTS.length));
        return Math.max(0, Math.min(requested, pages - 1));
    }

    private boolean isCurrent(Player player, MenuHolder holder) {
        return player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == holder;
    }

    private boolean requireAdmin(Player player) {
        if (player.hasPermission("mcpuzzle.admin")) return true;
        player.closeInventory();
        player.sendMessage("§c관리자 권한이 없습니다.");
        return false;
    }

    private void onMain(Runnable action) {
        if (plugin.getServer().isPrimaryThread()) action.run();
        else plugin.getServer().getScheduler().runTask(plugin, action);
    }

    private void invalid(Player player) {
        player.closeInventory();
        player.sendMessage("§c메뉴 상태가 만료되었습니다. /maze를 다시 열어 주세요.");
    }

    private String displayName(OfflinePlayer player) {
        return Objects.toString(player.getName(), player.getUniqueId().toString().substring(0, 8));
    }

    private String role(Player player, PartyView party) {
        return player.getUniqueId().equals(party.leaderId()) ? "§6파티장" : "§f파티원";
    }

    private String lifecycleName(PartyLifecycle lifecycle) {
        return switch (lifecycle) {
            case OPEN -> "모집 중";
            case QUEUED -> "입장 대기";
            case IN_RUN -> "미궁 진행 중";
            case CLOSED -> "종료됨";
        };
    }

    private String stateName(SessionState state) {
        return switch (state) {
            case WAITING -> "준비 중";
            case QUEUED -> "입장 대기";
            case PROVISIONING -> "월드 생성 중";
            case ACTIVE -> "진행 중";
            case SUSPENDED -> "저장 중단";
            case COMPLETED -> "완주";
            case ABANDONED -> "종료";
            case CLEANUP -> "정리 중";
        };
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        return "%02d:%02d:%02d".formatted(seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return Objects.toString(current.getMessage(), current.getClass().getSimpleName());
    }

    private PuzzleItemModel saveSlotModel(int slot) {
        return switch (slot) {
            case 1 -> PuzzleItemModel.SLOT_1;
            case 2 -> PuzzleItemModel.SLOT_2;
            case 3 -> PuzzleItemModel.SLOT_3;
            default -> throw new IllegalArgumentException("slot must be 1..3");
        };
    }

    private PuzzleItemModel hintViewModel(int tier) {
        return switch (tier) {
            case 1 -> PuzzleItemModel.HINT_VIEW_1;
            case 2 -> PuzzleItemModel.HINT_VIEW_2;
            case 3 -> PuzzleItemModel.HINT_VIEW_3;
            default -> throw new IllegalArgumentException("tier must be 1..3");
        };
    }

    private enum MenuType {
        MAIN, PARTY, INVITATIONS, INVITE_PLAYERS, SAVES, HINTS, LEADERBOARD, CONFIRM,
        ADMIN, ADMIN_PLAYERS, ADMIN_SAVES, ADMIN_TRANSFER
    }

    private static final class MenuHolder implements InventoryHolder {
        private final UUID playerId;
        private final MenuType type;
        private final UUID subjectId;
        private final int value;
        private Inventory inventory;

        private MenuHolder(UUID playerId, MenuType type, UUID subjectId, int value) {
            this.playerId = playerId;
            this.type = type;
            this.subjectId = subjectId;
            this.value = value;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }
}
