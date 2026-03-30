package com.deathhead;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class HeadPreviewListener implements Listener {

    private static final String GUI_TITLE_PREFIX = "§8봉인된 아이템 §7— §c";
    private final DeathHeadPlugin plugin;

    public HeadPreviewListener(DeathHeadPlugin plugin) {
        this.plugin = plugin;
    }

    /** 미리보기 GUI 내부 클릭 차단 */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith(GUI_TITLE_PREFIX)) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
        }
    }

    private void openPreviewGUI(Player player, HeadData data) {
        List<ItemStack> items = data.getItems();

        int rows = Math.max(1, Math.min(6, (items.size() + 8) / 9));
        int size = rows * 9;

        String title = GUI_TITLE_PREFIX + data.getOwnerName();
        Inventory gui = Bukkit.createInventory(null, size, title);

        for (int i = 0; i < items.size() && i < size; i++) {
            gui.setItem(i, items.get(i).clone());
        }

        player.openInventory(gui);
    }

    /** GUI에서 아이템 드래그 차단 */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTitle().startsWith(GUI_TITLE_PREFIX)) {
            event.setCancelled(true);
        }
    }
}
