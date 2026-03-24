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

import java.text.SimpleDateFormat;
import java.util.Date;
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
            event.getWhoClicked().sendMessage("§7이 머리는 이미 부패했습니다.");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        openPreviewGUI((Player) event.getWhoClicked(), data);
    }

    private void openPreviewGUI(Player player, HeadData data) {
        List<ItemStack> items = data.getItems();

        // GUI 크기: 아이템 수에 맞춰 (최소 1줄, 최대 6줄)
        int rows = Math.max(1, Math.min(6, (int) Math.ceil((items.size() + 2) / 9.0)));
        int size = rows * 9;

        String title = GUI_TITLE_PREFIX + data.getOwnerName();
        Inventory gui = Bukkit.createInventory(null, size, title);

        // 정보 아이템 (첫 슬롯)
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§e§l봉인 정보");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String deathTime = sdf.format(new Date(data.getCreatedAt() * 1000L));
            String expireTime = sdf.format(new Date(data.getExpiresAt() * 1000L));

            // 정확한 남은 시간 계산
            long remaining = data.getExpiresAt() - (System.currentTimeMillis() / 1000L);
            String remainStr;
            if (remaining <= 0) {
                remainStr = "§c부패 완료";
            } else if (remaining >= 3600) {
                long hours = remaining / 3600;
                long mins = (remaining % 3600) / 60;
                remainStr = hours + "시간 " + mins + "분";
            } else if (remaining >= 60) {
                long mins = remaining / 60;
                long secs = remaining % 60;
                remainStr = mins + "분 " + secs + "초";
            } else {
                remainStr = "§c" + remaining + "초";
            }

            infoMeta.setLore(List.of(
                    "",
                    "§7사망자: §f" + data.getOwnerName(),
                    "§7사망 위치: §f" + data.getDeathWorld() + " §7[§f" + data.getDeathX() + "§7, §f" + data.getDeathY() + "§7, §f" + data.getDeathZ() + "§7]",
                    "§7사망 시각: §f" + deathTime,
                    "",
                    "§7부패 시각: §f" + expireTime,
                    "§7남은 시간: §f" + remainStr,
                    "",
                    "§c⚠ 부패 시 아이템이 영구 유실됩니다!",
                    "",
                    "§7봉인 아이템: §f" + items.size() + "개"
            ));
            info.setItemMeta(infoMeta);
        }
        gui.setItem(0, info);

        // 봉인된 아이템 배치 (슬롯 2부터)
        for (int i = 0; i < items.size() && (i + 2) < size; i++) {
            gui.setItem(i + 2, items.get(i).clone());
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
