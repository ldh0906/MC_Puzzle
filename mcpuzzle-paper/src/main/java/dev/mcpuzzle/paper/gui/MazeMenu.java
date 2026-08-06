package dev.mcpuzzle.paper.gui;

import dev.mcpuzzle.paper.runtime.MazeRuntimeService;
import dev.mcpuzzle.paper.resourcepack.PuzzleItemModel;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class MazeMenu implements Listener {
    private final MazeRuntimeService runtime;
    private final NamespacedKey actionKey;

    public MazeMenu(Plugin plugin, MazeRuntimeService runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.actionKey = new NamespacedKey(plugin, "menu_action");
    }

    public void openMain(Player player) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), MenuType.MAIN, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, "§1미궁"); holder.inventory = inventory;
        inventory.setItem(11, item(PuzzleItemModel.PARTY, "§e파티 상태", "party_status", List.of("§7클릭해 명단을 확인합니다.")));
        inventory.setItem(13, item(PuzzleItemModel.START, "§6새 미궁 시작", "saves", List.of("§7슬롯을 선택합니다.")));
        inventory.setItem(15, item(PuzzleItemModel.SAVES, "§b세이브 / 재개", "saves", List.of("§7슬롯 1~3")));
        inventory.setItem(22, item(PuzzleItemModel.HINT, "§d힌트", "hints", List.of("§7현재 방 힌트")));
        player.openInventory(inventory);
    }

    public void openSaves(Player player) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), MenuType.SAVES, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, "§1미궁 세이브 슬롯"); holder.inventory = inventory;
        for (int slot = 1; slot <= 3; slot++) {
            inventory.setItem(10 + slot * 2, item(saveSlotModel(slot), "§e슬롯 " + slot, "slot:" + slot,
                    List.of("§a좌클릭: 재개", "§6우클릭: 새로 시작", "§cShift+클릭: 삭제")));
        }
        inventory.setItem(22, item(PuzzleItemModel.BACK, "§c뒤로", "main", List.of()));
        player.openInventory(inventory);
        runtime.listSaves(player);
    }

    public void openHints(Player player) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), MenuType.HINTS, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, "§5미궁 힌트"); holder.inventory = inventory;
        inventory.setItem(10, item(PuzzleItemModel.HINT_REQUEST, "§d다음 힌트 요청", "hint_request", List.of("§7파티장이 승인해야 열립니다.")));
        for (int tier = 1; tier <= 3; tier++) inventory.setItem(11 + tier * 2,
                item(hintViewModel(tier), "§d힌트 " + tier + " 보기", "hint_view:" + tier, List.of("§7이미 열린 힌트는 무료입니다.")));
        inventory.setItem(22, item(PuzzleItemModel.APPROVE, "§a대기 요청 승인", "hint_confirm", List.of()));
        inventory.setItem(23, item(PuzzleItemModel.REJECT, "§c대기 요청 거절", "hint_decline", List.of()));
        player.openInventory(inventory);
    }

    private void openConfirm(Player player, MenuType type, int slot, String title) {
        MenuHolder holder = new MenuHolder(player.getUniqueId(), type, slot);
        Inventory inventory = Bukkit.createInventory(holder, 9, title); holder.inventory = inventory;
        inventory.setItem(3, item(PuzzleItemModel.CONFIRM, "§a확인", "confirm", List.of("§7슬롯 " + slot)));
        inventory.setItem(5, item(PuzzleItemModel.CANCEL, "§c취소", "cancel", List.of()));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.playerId.equals(player.getUniqueId())) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        runtime.recordActivity(player);
        switch (action) {
            case "main" -> openMain(player);
            case "saves" -> openSaves(player);
            case "hints" -> openHints(player);
            case "party_status" -> { player.closeInventory(); showParty(player); }
            case "hint_request" -> { player.closeInventory(); runtime.requestHint(player); }
            case "hint_confirm" -> { player.closeInventory(); runtime.confirmHint(player, true); }
            case "hint_decline" -> { player.closeInventory(); runtime.confirmHint(player, false); }
            case "confirm" -> {
                player.closeInventory();
                if (holder.type == MenuType.START_CONFIRM) runtime.requestStart(player, holder.slot, true);
                else if (holder.type == MenuType.DELETE_CONFIRM) runtime.deleteSave(player, player.getUniqueId(), holder.slot);
            }
            case "cancel" -> openSaves(player);
            default -> {
                if (action.startsWith("hint_view:")) { player.closeInventory(); runtime.viewHint(player, Integer.parseInt(action.substring(10))); }
                else if (action.startsWith("slot:")) handleSlot(player, event, Integer.parseInt(action.substring(5)));
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    private void handleSlot(Player player, InventoryClickEvent event, int slot) {
        if (event.isShiftClick()) openConfirm(player, MenuType.DELETE_CONFIRM, slot, "§4세이브 삭제 확인");
        else if (event.isRightClick()) openConfirm(player, MenuType.START_CONFIRM, slot, "§6새 미궁 시작 확인");
        else { player.closeInventory(); runtime.requestResume(player, slot); }
    }

    private void showParty(Player player) {
        runtime.party(player.getUniqueId()).ifPresentOrElse(party -> {
            player.sendMessage("§6[파티] §f파티장: " + Bukkit.getOfflinePlayer(party.leaderId()).getName());
            party.members().forEach(id -> player.sendMessage("§7- " + Objects.toString(Bukkit.getOfflinePlayer(id).getName(), id.toString())));
        }, () -> player.sendMessage("§c파티가 없습니다."));
    }

    private ItemStack item(PuzzleItemModel model, String name, String action, List<String> lore) {
        ItemStack item = model.createItem();
        ItemMeta meta = item.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); item.setItemMeta(meta); return item;
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

    private enum MenuType { MAIN, SAVES, HINTS, START_CONFIRM, DELETE_CONFIRM }
    private static final class MenuHolder implements InventoryHolder {
        private final UUID playerId; private final MenuType type; private final int slot; private Inventory inventory;
        private MenuHolder(UUID playerId, MenuType type, int slot) { this.playerId = playerId; this.type = type; this.slot = slot; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
