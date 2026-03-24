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

    /** 인벤토리에서 머리를 Shift+우클릭 → 미리보기 GUI */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 미리보기 GUI 내부 클릭 차단 (모든 클릭 타입 — 숫자키, Shift 등 포함)
        if (event.getView().getTitle().startsWith(GUI_TITLE_PREFIX)) {
            event.setCancelled(true);
            event.setResult(org.bukkit.event.Event.Result.DENY);
            return;
        }

        // Shift+우클릭만 처리
        if (event.getClick() != ClickType.SHIFT_RIGHT) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String headId = meta.getPersistentDataContainer().get(
                plugin.getDeathListener().getHeadIdKey(), PersistentDataType.STRING);
        if (headId == null) return;

        HeadData data = plugin.getHeadStorage().get(headId);
        if (data == null) {
            event.getWhoClicked().sendMessage(plugin.getMessage("expired", "§7이 머리는 이미 부패했습니다."));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        openPreviewGUI((Player) event.getWhoClicked(), data);
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
