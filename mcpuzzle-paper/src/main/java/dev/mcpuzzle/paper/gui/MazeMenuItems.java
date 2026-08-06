package dev.mcpuzzle.paper.gui;

import dev.mcpuzzle.paper.resourcepack.PuzzleItemModel;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Objects;

final class MazeMenuItems {
    private final NamespacedKey actionKey;

    MazeMenuItems(NamespacedKey actionKey) {
        this.actionKey = Objects.requireNonNull(actionKey, "actionKey");
    }

    ItemStack model(PuzzleItemModel model, String name, MazeMenuAction action, List<String> lore) {
        return finish(model.createItem(), name, action, lore);
    }

    ItemStack material(Material material, String name, MazeMenuAction action, List<String> lore) {
        return finish(new ItemStack(material), name, action, lore);
    }

    ItemStack playerHead(OfflinePlayer player, String name, MazeMenuAction action, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(player);
        item.setItemMeta(meta);
        return finish(item, name, action, lore);
    }

    ItemStack decoration(Material material) {
        return finish(new ItemStack(material), " ", null, List.of());
    }

    void frame(Inventory inventory, Material material) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int column = slot % 9;
            if (slot < 9 || slot >= inventory.getSize() - 9 || column == 0 || column == 8) {
                inventory.setItem(slot, decoration(material));
            }
        }
    }

    void fillEmpty(Inventory inventory, Material material) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, decoration(material));
        }
    }

    private ItemStack finish(ItemStack item, String name, MazeMenuAction action, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        if (action != null) {
            meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action.encode());
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_POTION_EFFECTS);
        item.setItemMeta(meta);
        return item;
    }
}
