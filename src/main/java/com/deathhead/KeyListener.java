package com.deathhead;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KeyListener implements Listener {

    private final DeathHeadPlugin plugin;
    private final Map<UUID, Long> useCooldowns = new ConcurrentHashMap<>();

    public KeyListener(DeathHeadPlugin plugin) {
        this.plugin = plugin;
    }

    /** 봉인된 머리 블록 파괴 시 PDC 보존된 아이템으로 드롭 */
    @EventHandler
    public void onHeadBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return;
        if (!(block.getState() instanceof Skull skull)) return;

        String headId = skull.getPersistentDataContainer().get(
                plugin.getDeathListener().getHeadIdKey(), PersistentDataType.STRING);
        if (headId == null) return;

        // 기본 드롭 취소
        event.setDropItems(false);

        // PDC 보존된 머리 아이템 생성
        ItemStack headItem = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) headItem.getItemMeta();
        if (meta == null) return;

        // 원본 스킨 복사
        org.bukkit.profile.PlayerProfile profile = skull.getOwnerProfile();
        if (profile != null) {
            meta.setOwnerProfile(profile);
        }
        meta.getPersistentDataContainer().set(
                plugin.getDeathListener().getHeadIdKey(), PersistentDataType.STRING, headId);

        // HeadData에서 lore 복원
        HeadData data = plugin.getHeadStorage().get(headId);
        if (data != null) {
            meta.displayName(Component.text(data.getOwnerName(), NamedTextColor.RED)
                    .append(Component.text("의 머리", NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(plugin.getDeathListener().buildSkullLore(data.getItems(), data.getExpiresAt()));
            meta.getPersistentDataContainer().set(
                    plugin.getDeathListener().getExpiresAtKey(), PersistentDataType.LONG, data.getExpiresAt());
            meta.setMaxStackSize(1);
        }

        headItem.setItemMeta(meta);

        // 아이템 드롭
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().dropItemNaturally(dropLoc, headItem);
    }

    /** 머리 아이템 설치 시 PDC를 블록 TileEntity에 복사 */
    @EventHandler
    public void onHeadPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return;

        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.PLAYER_HEAD) return;

        if (!(item.getItemMeta() instanceof SkullMeta itemMeta)) return;

        String headId = itemMeta.getPersistentDataContainer().get(
                plugin.getDeathListener().getHeadIdKey(), PersistentDataType.STRING);
        if (headId == null) return;

        // 블록의 TileEntity에 headId 복사
        if (block.getState() instanceof Skull skull) {
            skull.getPersistentDataContainer().set(
                    plugin.getDeathListener().getHeadIdKey(), PersistentDataType.STRING, headId);
            skull.update();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // 손에 열쇠를 들고 있는지 확인
        if (!plugin.getKeyItem().isKey(hand)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // 플레이어 머리 블록인지 확인
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return;

        // 사용 쿨다운 (300ms)
        long now = System.currentTimeMillis();
        Long lastUse = useCooldowns.get(player.getUniqueId());
        if (lastUse != null && (now - lastUse) < 300) return;
        useCooldowns.put(player.getUniqueId(), now);

        event.setCancelled(true);

        // 블록에서 headId 추출
        if (!(block.getState() instanceof Skull skull)) return;

        String headId = skull.getPersistentDataContainer().get(
                plugin.getDeathListener().getHeadIdKey(), PersistentDataType.STRING);

        if (headId == null) {
            // PDC가 없는 일반 머리
            return;
        }

        // 캐시에서 데이터 조회
        HeadData data = plugin.getHeadStorage().get(headId);

        if (data == null) {
            player.sendMessage(plugin.getMessage("expired", "§7이 머리는 이미 부패했습니다."));
            return;
        }

        // ── 회수 성공 ──
        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);

        // 봉인 아이템 드롭
        for (ItemStack item : data.getItems()) {
            block.getWorld().dropItemNaturally(dropLoc, item);
        }

        // 블록 파괴
        block.setType(Material.AIR);

        // 열쇠 1개 소모
        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // YAML 삭제
        plugin.getHeadStorage().remove(headId);

        // 회수 메시지
        player.sendMessage(plugin.getMessage("retrieve", "§a유실된 아이템을 되찾았습니다!"));

        // 회수 이펙트
        block.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, dropLoc, 15, 0.3, 0.3, 0.3, 0);
        player.playSound(dropLoc, org.bukkit.Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
    }

    /** 퇴장 시 쿨다운 정리 */
    public void cleanupPlayer(UUID uuid) {
        useCooldowns.remove(uuid);
    }
}
