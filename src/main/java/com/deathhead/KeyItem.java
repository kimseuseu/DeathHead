package com.deathhead;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class KeyItem {

    public static final String KEY_TAG = "deathhead_key";

    private final NamespacedKey keyKey;
    private final DeathHeadPlugin plugin;

    public KeyItem(DeathHeadPlugin plugin) {
        this.plugin = plugin;
        this.keyKey = new NamespacedKey(plugin, KEY_TAG);
    }

    public ItemStack createKey(int amount) {
        String materialName = plugin.getConfig().getString("key.material", "TRIPWIRE_HOOK");
        Material material = Material.valueOf(materialName.toUpperCase());

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = plugin.getConfig().getString("key.name", "§6머리 열쇠");
            meta.setDisplayName(name.replace('&', '§'));

            meta.setLore(List.of(
                    "§7사망한 플레이어의 머리에 사용하면",
                    "§7봉인된 아이템을 회수할 수 있습니다."
            ));

            int cmd = plugin.getConfig().getInt("key.custom-model-data", 0);
            if (cmd > 0) {
                meta.setCustomModelData(cmd);
            }

            meta.getPersistentDataContainer().set(keyKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isKey(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(keyKey, PersistentDataType.BYTE);
    }

    public NamespacedKey getKeyKey() {
        return keyKey;
    }
}
