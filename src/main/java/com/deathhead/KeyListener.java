package com.deathhead;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Item;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Iterator;
import java.util.List;
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

    @EventHandler
    public void onHeadBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isSealedHead(block)) return;
        event.setDropItems(false);
        plugin.getDeathListener().removeBlockLabel(block.getLocation());
        dropPreservedHead(block);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplodedBlocks(event.blockList());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplodedBlocks(event.blockList());
    }

    private void handleExplodedBlocks(List<Block> blocks) {
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block block = it.next();
            if (!isSealedHead(block)) continue;
            it.remove();
            plugin.getDeathListener().removeBlockLabel(block.getLocation());
            dropPreservedHead(block);
        }
    }

    private boolean isSealedHead(Block block) {
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return false;
        if (!(block.getState() instanceof Skull skull)) return false;
        return skull.getPersistentDataContainer().has(plugin.getDeathListener().getHeadIdKey(), PersistentDataType.STRING);
    }

    private void dropPreservedHead(Block block) {
        if (!(block.getState() instanceof Skull skull)) return;

        String headId = skull.getPersistentDataContainer().get(
                plugin.getDeathListener().getHeadIdKey(), PersistentDataType.STRING);
        if (headId == null) return;

        ItemStack headItem = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) headItem.getItemMeta();
        if (meta == null) return;

        org.bukkit.profile.PlayerProfile profile = skull.getOwnerProfile();
        if (profile != null) meta.setOwnerProfile(profile);

        meta.getPersistentDataContainer().set(
                plugin.getDeathListener().getHeadIdKey(), PersistentDataType.STRING, headId);

        HeadData data = plugin.getHeadStorage().get(headId);
        if (data != null) {
            meta.displayName(Component.text(data.getOwnerName(), NamedTextColor.RED)
                    .append(Component.text("의 머리", NamedTextColor.GRAY))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(plugin.getDeathListener().buildSkullLore(data.getItems(), data.getExpiresAt()));
            meta.getPersistentDataContainer().set(
                    plugin.getDeathListener().getExpiresAtKey(), PersistentDataType.LONG, data.getExpiresAt());
            meta.getPersistentDataContainer().set(
                    plugin.getDeathListener().getOwnerNameKey(), PersistentDataType.STRING, data.getOwnerName());
            meta.setMaxStackSize(1);
        }

        headItem.setItemMeta(meta);

        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);
        Item droppedItem = block.getWorld().dropItemNaturally(dropLoc, headItem);

        if (data != null) {
            droppedItem.addScoreboardTag("deathhead");
            droppedItem.setCustomNameVisible(false);
            droppedItem.customName(net.kyori.adventure.text.Component.text("DeathHead"));
            droppedItem.setUnlimitedLifetime(true);
            droppedItem.setGlowing(true);
            droppedItem.setMetadata("deathhead", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        }
    }

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

        if (block.getState() instanceof Skull skull) {
            skull.getPersistentDataContainer().set(
                    plugin.getDeathListener().getHeadIdKey(), PersistentDataType.STRING, headId);
            skull.update();
        }

        HeadData data = plugin.getHeadStorage().get(headId);
        if (data != null) {
            plugin.getDeathListener().spawnBlockLabel(block.getLocation(), data.getOwnerName(), data.getExpiresAt());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (!plugin.getKeyItem().isKey(hand)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) return;

        long now = System.currentTimeMillis();
        Long lastUse = useCooldowns.get(player.getUniqueId());
        if (lastUse != null && (now - lastUse) < 300) return;
        useCooldowns.put(player.getUniqueId(), now);

        event.setCancelled(true);

        if (!(block.getState() instanceof Skull skull)) return;

        String headId = skull.getPersistentDataContainer().get(
                plugin.getDeathListener().getHeadIdKey(), PersistentDataType.STRING);

        if (headId == null) return;

        HeadData data = plugin.getHeadStorage().get(headId);

        if (data == null) {
            player.sendMessage(plugin.getMessage("expired", "§7이 머리는 이미 부패했습니다."));
            return;
        }

        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);

        for (ItemStack item : data.getItems()) {
            block.getWorld().dropItemNaturally(dropLoc, item);
        }

        plugin.getDeathListener().removeBlockLabel(block.getLocation());
        block.setType(Material.AIR);

        if (hand.getAmount() > 1) {
            hand.setAmount(hand.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        plugin.getHeadStorage().remove(headId);

        player.sendMessage(plugin.getMessage("retrieve", "§a유실된 아이템을 되찾았습니다!"));

        block.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, dropLoc, 15, 0.3, 0.3, 0.3, 0);
        player.playSound(dropLoc, org.bukkit.Sound.BLOCK_CHEST_OPEN, 1f, 1.2f);
    }

    public void cleanupPlayer(UUID uuid) {
        useCooldowns.remove(uuid);
    }
}
