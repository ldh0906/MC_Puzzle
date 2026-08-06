package dev.mcpuzzle.paper.authoring;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class AuthoringWandService implements Listener {
    private final NamespacedKey key;
    private final Map<UUID, Selection> selections = new HashMap<>();

    public AuthoringWandService(Plugin plugin) {
        this.key = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "authoring_wand");
    }

    public void give(Player player) {
        if (!player.isOp()) { player.sendMessage("§cOP만 제작 완드를 받을 수 있습니다."); return; }
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta(); meta.setDisplayName("§6미궁 좌표 선택 완드");
        meta.setLore(java.util.List.of("§7좌클릭: 첫 번째 모서리", "§7우클릭: 두 번째 모서리"));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta); player.getInventory().addItem(wand);
        player.sendMessage("§a제작 완드를 지급했습니다.");
    }

    public void printSelection(Player player) {
        if (!player.isOp()) { player.sendMessage("§cOP만 사용할 수 있습니다."); return; }
        Selection selection = selections.get(player.getUniqueId());
        if (selection == null || selection.first == null || selection.second == null) {
            player.sendMessage("§c두 모서리를 모두 선택하세요."); return;
        }
        if (!selection.first.getWorld().equals(selection.second.getWorld())) {
            player.sendMessage("§c두 좌표는 같은 월드여야 합니다."); return;
        }
        Location min = new Location(selection.first.getWorld(), Math.min(selection.first.getBlockX(), selection.second.getBlockX()),
                Math.min(selection.first.getBlockY(), selection.second.getBlockY()), Math.min(selection.first.getBlockZ(), selection.second.getBlockZ()));
        Location max = new Location(selection.first.getWorld(), Math.max(selection.first.getBlockX(), selection.second.getBlockX()),
                Math.max(selection.first.getBlockY(), selection.second.getBlockY()), Math.max(selection.first.getBlockZ(), selection.second.getBlockZ()));
        String json = "\"bounds\": { \"min\": { \"x\": %d, \"y\": %d, \"z\": %d }, \"max\": { \"x\": %d, \"y\": %d, \"z\": %d } }"
                .formatted(min.getBlockX(), min.getBlockY(), min.getBlockZ(), max.getBlockX(), max.getBlockY(), max.getBlockZ());
        player.sendMessage(Component.text("[클릭해서 JSON 좌표 복사]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.copyToClipboard(json)).hoverEvent(Component.text(json)));
        player.sendMessage("§7" + json);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isOp() || event.getClickedBlock() == null || !isWand(event.getItem())) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        event.setCancelled(true);
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.first = event.getClickedBlock().getLocation();
            player.sendMessage("§a첫 번째 좌표: " + format(selection.first));
        } else {
            selection.second = event.getClickedBlock().getLocation();
            player.sendMessage("§a두 번째 좌표: " + format(selection.second));
        }
    }

    private boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
    private String format(Location location) { return location.getWorld().getName() + " " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ(); }
    private static final class Selection { private Location first; private Location second; }
}
