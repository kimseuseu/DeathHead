package com.deathhead;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class KeyItem {

    private final DeathHeadPlugin plugin;
    private String keyDisplayName;

    public KeyItem(DeathHeadPlugin plugin) {
        this.plugin = plugin;
        refreshDisplayName();
    }

    public void refreshDisplayName() {
        this.keyDisplayName = plugin.getItemString("key.name", "§6머리 열쇠");
    }

    public ItemStack createKey(int amount) {
        String materialName = plugin.getItemsConfig().getString("key.material", "TRIPWIRE_HOOK");
        Material material = Material.valueOf(materialName.toUpperCase());

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(keyDisplayName);

            List<String> rawLore = plugin.getItemsConfig().getStringList("key.lore");
            if (!rawLore.isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String line : rawLore) lore.add(line.replace('&', '§'));
                meta.setLore(lore);
            }

            int cmd = plugin.getItemsConfig().getInt("key.custom-model-data", 0);
            if (cmd > 0) meta.setCustomModelData(cmd);

            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isKey(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        return meta.getDisplayName().equals(keyDisplayName);
    }
}
