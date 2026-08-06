package dev.mcpuzzle.paper.resourcepack;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public enum PuzzleItemModel {
    PARTY(12001),
    START(12002),
    SAVES(12003),
    HINT(12004),
    SLOT_1(12005),
    SLOT_2(12006),
    SLOT_3(12007),
    BACK(12008),
    HINT_REQUEST(12009),
    HINT_VIEW_1(12010),
    HINT_VIEW_2(12011),
    HINT_VIEW_3(12012),
    APPROVE(12013),
    REJECT(12014),
    CONFIRM(12015),
    CANCEL(12016);

    private final int customModelData;

    PuzzleItemModel(int customModelData) {
        this.customModelData = customModelData;
    }

    public int customModelData() {
        return customModelData;
    }

    public ItemStack createItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(customModelData);
        item.setItemMeta(meta);
        return item;
    }
}
