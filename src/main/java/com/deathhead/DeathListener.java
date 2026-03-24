package com.deathhead;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Display;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeathListener implements Listener {

    private static final long LABEL_UPDATE_INTERVAL = 20L;
    private static final int MAX_LORE_ITEMS = 6;

    private final DeathHeadPlugin plugin;
    private final NamespacedKey headIdKey;
    private final Map<UUID, Long> deathCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, GameMode> animatingPlayers = new ConcurrentHashMap<>();

    public DeathListener(DeathHeadPlugin plugin) {
        this.plugin = plugin;
        this.headIdKey = new NamespacedKey(plugin, "head_id");
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (!item.getScoreboardTags().contains("deathhead")) return;

        for (org.bukkit.entity.Entity passenger : item.getPassengers()) {
            if (passenger instanceof TextDisplay) {
                passenger.remove();
            }
        }
    }

    @EventHandler
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getScoreboardTags().contains("deathhead_no_damage")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLoc = player.getLocation().clone();
        GameMode originalMode = player.getGameMode();

        int cooldownSeconds = plugin.getConfig().getInt("head.death-cooldown", 10);
        long now = System.currentTimeMillis();
        Long lastDeath = deathCooldowns.get(player.getUniqueId());
        if (lastDeath != null && (now - lastDeath) < cooldownSeconds * 1000L) {
            sendConfigMessage(player, "messages.cooldown", "§7사망 직후에는 머리가 터지지 않습니다.");
            return;
        }
        deathCooldowns.put(player.getUniqueId(), now);

        if (hasAndConsumeProtectionTicket(player)) {
            player.sendMessage("§b§l[!] §f사망 패널티 방지권§7이 사용되어 아이템이 유실되지 않습니다.");
            runDeathAnimation(player, deathLoc, originalMode, null, null, 0);
            return;
        }

        List<ItemStack> sealedItems = sealItems(player);
        String headId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        long createdAt = now / 1000L;
        int ttlMinutes = plugin.getConfig().getInt("head.ttl-minutes", 60);
        long expiresAt = createdAt + (ttlMinutes * 60L);

        HeadData data = new HeadData(headId, player.getUniqueId(), player.getName(),
                deathLoc.getWorld().getName(),
                deathLoc.getBlockX(), deathLoc.getBlockY(), deathLoc.getBlockZ(),
                createdAt, expiresAt, sealedItems);
        plugin.getHeadStorage().save(data);

        if (!sealedItems.isEmpty()) {
            sendConfigMessage(player, "messages.death", "§c아이템 일부가 유실되었습니다. 머리를 찾아 회수하세요.");
        }

        runDeathAnimation(player, deathLoc, originalMode, headId, sealedItems, expiresAt);
    }

    // ─── 사망 연출 ───

    private void runDeathAnimation(Player player, Location deathLoc, GameMode originalMode,
                                    String headId, List<ItemStack> sealedItems, long expiresAt) {
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                doRespawn(playerId, deathLoc, originalMode, headId, sealedItems, expiresAt), 5L);
    }

    private void doRespawn(UUID playerId, Location deathLoc, GameMode originalMode,
                            String headId, List<ItemStack> sealedItems, long expiresAt) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null || !p.isOnline()) return;
        p.spigot().respawn();

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                doSpectator(playerId, deathLoc, originalMode, headId, sealedItems, expiresAt), 1L);
    }

    private void doSpectator(UUID playerId, Location deathLoc, GameMode originalMode,
                              String headId, List<ItemStack> sealedItems, long expiresAt) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null || !p.isOnline()) return;

        animatingPlayers.put(playerId, originalMode);
        p.setGameMode(GameMode.SPECTATOR);

        Location aerialLoc = deathLoc.clone().add(0, 5, 0);
        aerialLoc.setYaw(p.getLocation().getYaw());
        aerialLoc.setPitch(90f);
        p.teleport(aerialLoc);

        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
        p.sendTitle("§c§l☠ YOU DIED", "§7" + p.getName(), 5, 40, 15);

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                doEffects(playerId, deathLoc, headId, sealedItems, expiresAt), 10L);

        Bukkit.getScheduler().runTaskLater(plugin, () ->
                doRestore(playerId, originalMode), 60L);
    }

    private void doEffects(UUID playerId, Location deathLoc,
                            String headId, List<ItemStack> sealedItems, long expiresAt) {
        spawnFirework(deathLoc);
        spawnParticles(deathLoc);

        if (headId != null && sealedItems != null) {
            Player p = Bukkit.getPlayer(playerId);
            if (p != null && p.isOnline()) {
                dropPlayerHead(p, deathLoc, headId, sealedItems, expiresAt);
            }
        }
    }

    private void doRestore(UUID playerId, GameMode originalMode) {
        animatingPlayers.remove(playerId);
        Player p = Bukkit.getPlayer(playerId);
        if (p == null || !p.isOnline()) return;

        p.setGameMode(originalMode);
        Location spawnLoc = p.getBedSpawnLocation();
        if (spawnLoc == null) {
            spawnLoc = p.getWorld().getSpawnLocation();
        }
        p.teleport(spawnLoc);
    }

    // ─── 아이템 봉인 ───

    private boolean hasAndConsumeProtectionTicket(Player player) {
        NamespacedKey protectionKey = plugin.getProtectionKey();
        PlayerInventory inv = player.getInventory();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() != Material.PAPER) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            if (meta.getPersistentDataContainer().has(protectionKey, PersistentDataType.BYTE)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    inv.setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    private boolean isProtectedItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();

        if (plugin.getKeyItem().isKey(item)) return true;
        return item.getType() == Material.PAPER
                && meta.getPersistentDataContainer().has(plugin.getProtectionKey(), PersistentDataType.BYTE);
    }

    private List<ItemStack> sealItems(Player player) {
        PlayerInventory inv = player.getInventory();
        List<Integer> filledSlots = new ArrayList<>();

        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR && !isProtectedItem(item)) {
                filledSlots.add(i);
            }
        }

        if (filledSlots.isEmpty()) return Collections.emptyList();

        int minPercent = plugin.getConfig().getInt("penalty.min-percent", 10);
        int maxPercent = plugin.getConfig().getInt("penalty.max-percent", 15);
        double percent = (minPercent + Math.random() * (maxPercent - minPercent)) / 100.0;
        int amountToRemove = Math.max(1, (int) Math.round(filledSlots.size() * percent));

        Collections.shuffle(filledSlots);
        List<ItemStack> sealed = new ArrayList<>();

        for (int i = 0; i < amountToRemove && i < filledSlots.size(); i++) {
            int slot = filledSlots.get(i);
            ItemStack item = inv.getItem(slot);
            if (item != null) {
                sealed.add(item.clone());
                inv.setItem(slot, null);
            }
        }
        return sealed;
    }

    // ─── 머리 드롭 ───

    private void dropPlayerHead(Player player, Location location, String headId,
                                 List<ItemStack> sealedItems, long expiresAt) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta == null) return;

        meta.setPlayerProfile(player.getPlayerProfile());
        meta.setDisplayName("§c" + player.getName() + "§7의 머리");
        meta.setLore(buildSkullLore(sealedItems, expiresAt));
        meta.getPersistentDataContainer().set(headIdKey, PersistentDataType.STRING, headId);
        meta.setMaxStackSize(1);
        skull.setItemMeta(meta);

        Item droppedItem = location.getWorld().dropItemNaturally(location, skull);
        droppedItem.setMetadata("deathhead", new FixedMetadataValue(plugin, true));
        droppedItem.addScoreboardTag("deathhead");
        droppedItem.setCustomNameVisible(false);
        droppedItem.customName(Component.text("DeathHead"));
        droppedItem.setUnlimitedLifetime(true);
        droppedItem.setGlowing(true);

        spawnHeadLabel(droppedItem, player.getName(), expiresAt);
    }

    private List<String> buildSkullLore(List<ItemStack> sealedItems, long expiresAt) {
        String expireStr = new SimpleDateFormat("HH:mm").format(new Date(expiresAt * 1000L));

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§8§m                                    ");
        lore.add("");
        lore.add("  §c§l⚠ §f봉인된 아이템 §7(§f" + sealedItems.size() + "개§7)");
        lore.add("");

        int displayCount = Math.min(sealedItems.size(), MAX_LORE_ITEMS);
        for (int i = 0; i < displayCount; i++) {
            ItemStack sealed = sealedItems.get(i);
            String name = getItemDisplayName(sealed);
            int amt = sealed.getAmount();
            lore.add("  §8▸ §f" + name + (amt > 1 ? " §7x" + amt : ""));
        }
        if (sealedItems.size() > MAX_LORE_ITEMS) {
            lore.add("  §8  ... §7외 §f" + (sealedItems.size() - MAX_LORE_ITEMS) + "§7개");
        }

        lore.add("");
        lore.add("§8§m                                    ");
        lore.add("");
        lore.add("  §c⏳ §7부패 시각: §f" + expireStr + " §8| §c부패 시 아이템 영구 유실");
        lore.add("");
        lore.add("  §e§l⬦ §e봉인 해제 방법");
        lore.add("  §71. §f머리를 바닥에 설치");
        lore.add("  §72. §6머리 열쇠§7를 들고 §f우클릭");
        lore.add("");
        lore.add("§8§m                                    ");
        lore.add("");
        lore.add("  §b§l🔍 §b[인벤토리 Shift+우클릭] §7봉인 아이템 미리보기");
        lore.add("");
        return lore;
    }

    // ─── TextDisplay ───

    private void spawnHeadLabel(Item droppedItem, String playerName, long expiresAt) {
        TextDisplay display = droppedItem.getWorld().spawn(droppedItem.getLocation(), TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setSeeThrough(false);
            td.setShadowed(true);
            td.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
            td.setBrightness(new Display.Brightness(15, 15));
            td.text(buildLabelText(playerName, expiresAt));
            td.setTransformation(new Transformation(
                    new Vector3f(0f, 1.2f, 0f),
                    new AxisAngle4f(0, 0, 0, 1),
                    new Vector3f(1, 1, 1),
                    new AxisAngle4f(0, 0, 0, 1)
            ));
        });

        droppedItem.addPassenger(display);

        final int[] taskId = new int[1];
        taskId[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (droppedItem.isDead() || !droppedItem.isValid()) {
                if (!display.isDead()) display.remove();
                Bukkit.getScheduler().cancelTask(taskId[0]);
                return;
            }
            long remaining = expiresAt - (System.currentTimeMillis() / 1000L);
            if (remaining <= 0) {
                display.text(Component.text("§c" + playerName + "§7의 머리\n§4§l부패 완료"));
                Bukkit.getScheduler().cancelTask(taskId[0]);
                return;
            }
            display.text(buildLabelText(playerName, expiresAt));
        }, LABEL_UPDATE_INTERVAL, LABEL_UPDATE_INTERVAL);
    }

    private Component buildLabelText(String playerName, long expiresAt) {
        long remaining = expiresAt - (System.currentTimeMillis() / 1000L);
        String timeStr = remaining <= 0
                ? "§4§l부패 완료"
                : "§c⏳ " + String.format("%02d:%02d", remaining / 60, remaining % 60);
        return Component.text("§c" + playerName + "§7의 머리\n" + timeStr);
    }

    // ─── 이펙트 ───

    private void spawnFirework(Location location) {
        FireworkEffect effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.RED, Color.WHITE)
                .withFade(Color.fromRGB(80, 0, 0), Color.SILVER)
                .withTrail()
                .withFlicker()
                .build();

        Firework firework = location.getWorld().spawn(location, Firework.class);
        firework.setSilent(true);
        firework.setInvulnerable(true);
        firework.addScoreboardTag("deathhead_no_damage");
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(effect);
        meta.setPower(0);
        firework.setFireworkMeta(meta);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!firework.isDead()) firework.detonate();
        }, 1L);
    }

    private void spawnParticles(Location location) {
        World world = location.getWorld();
        Location particleLoc = location.clone().add(0, 0.5, 0);

        Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.5f);
        world.spawnParticle(Particle.DUST, particleLoc, 30, 0.5, 0.5, 0.5, 0, redDust);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 5) { cancel(); return; }
                Location rising = particleLoc.clone().add(0, tick * 0.6, 0);
                world.spawnParticle(Particle.SOUL, rising, 8, 0.3, 0.2, 0.3, 0.02);
                world.spawnParticle(Particle.DUST, rising, 5, 0.4, 0.3, 0.4, 0, redDust);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 6L);
    }

    // ─── 유틸 ───

    private String getItemDisplayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return formatMaterialName(item.getType());
    }

    private String formatMaterialName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private void sendConfigMessage(Player player, String configKey, String defaultMsg) {
        String msg = plugin.getConfig().getString(configKey, defaultMsg);
        player.sendMessage(msg.replace('&', '§'));
    }

    // ─── public API ───

    public NamespacedKey getHeadIdKey() { return headIdKey; }

    public GameMode removeAnimatingPlayer(UUID uuid) {
        return animatingPlayers.remove(uuid);
    }

    public void cleanupPlayer(UUID uuid) {
        deathCooldowns.remove(uuid);
    }
}
