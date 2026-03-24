package com.deathhead;

import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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

import java.util.*;

public class DeathListener implements Listener {

    private final DeathHeadPlugin plugin;
    private final NamespacedKey headIdKey;
    private final Map<UUID, Long> deathCooldowns = new HashMap<>();
    private final Map<UUID, GameMode> animatingPlayers = new HashMap<>();

    public DeathListener(DeathHeadPlugin plugin) {
        this.plugin = plugin;
        this.headIdKey = new NamespacedKey(plugin, "head_id");
    }

    // DeathHead 폭죽 데미지 차단
    @EventHandler
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework) {
            if (firework.getScoreboardTags().contains("deathhead_no_damage")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location deathLoc = player.getLocation().clone();
        GameMode originalMode = player.getGameMode();

        // ── 사망 쿨다운 체크 ──
        int cooldownSeconds = plugin.getConfig().getInt("head.death-cooldown", 10);
        long now = System.currentTimeMillis();
        Long lastDeath = deathCooldowns.get(player.getUniqueId());
        if (lastDeath != null && (now - lastDeath) < cooldownSeconds * 1000L) {
            // 쿨다운 중: 머리 생성 없이 일반 사망 처리
            String msg = plugin.getConfig().getString("messages.cooldown", "§7사망 직후에는 머리가 생성되지 않습니다.");
            player.sendMessage(msg.replace('&', '§'));
            return;
        }
        deathCooldowns.put(player.getUniqueId(), now);

        // ── 사망 방지권 체크 ──
        if (hasAndConsumeProtectionTicket(player)) {
            player.sendMessage("§b§l[!] §f사망 패널티 방지권§7이 사용되어 아이템이 유실되지 않습니다.");
            // 연출만 실행 (봉인 없음)
            runDeathAnimation(player, deathLoc, originalMode, null, null, 0);
            return;
        }

        // ── 아이템 봉인 ──
        List<ItemStack> sealedItems = sealItems(player);
        String headId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        // ── HeadData 저장 ──
        long createdAt = now / 1000L;
        int ttlMinutes = plugin.getConfig().getInt("head.ttl-minutes", 60);
        long expiresAt = createdAt + (ttlMinutes * 60L);

        HeadData data = new HeadData(headId, player.getUniqueId(), player.getName(),
                deathLoc.getWorld().getName(),
                deathLoc.getBlockX(), deathLoc.getBlockY(), deathLoc.getBlockZ(),
                createdAt, expiresAt, sealedItems);
        plugin.getHeadStorage().save(data);

        // ── 사망 메시지 ──
        if (!sealedItems.isEmpty()) {
            String msg = plugin.getConfig().getString("messages.death",
                    "§c아이템 일부가 유실되었습니다. 머리를 찾아 회수하세요.");
            player.sendMessage(msg.replace('&', '§'));
        }

        // ── 연출 + 머리 드롭 ──
        runDeathAnimation(player, deathLoc, originalMode, headId, sealedItems, expiresAt);
    }

    private void runDeathAnimation(Player player, Location deathLoc, GameMode originalMode,
                                    String headId, List<ItemStack> sealedItems, long expiresAt) {
        UUID playerId = player.getUniqueId();

        // 5틱(250ms) 대기 — 클라이언트 미니맵 모드가 사망 위치를 기록할 시간 확보
        new BukkitRunnable() {
            @Override
            public void run() {
                Player p = Bukkit.getPlayer(playerId);
                if (p == null || !p.isOnline()) return;
                p.spigot().respawn();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Player p2 = Bukkit.getPlayer(playerId);
                        if (p2 == null || !p2.isOnline()) return;

                        animatingPlayers.put(playerId, originalMode);
                        p2.setGameMode(GameMode.SPECTATOR);

                        Location aerialLoc = deathLoc.clone().add(0, 5, 0);
                        aerialLoc.setYaw(p2.getLocation().getYaw());
                        aerialLoc.setPitch(90f);
                        p2.teleport(aerialLoc);

                        p2.addPotionEffect(new PotionEffect(
                                PotionEffectType.BLINDNESS, 25, 0, false, false, false));

                        p2.sendTitle(
                                "§c§l☠ YOU DIED",
                                "§7" + p2.getName(),
                                5, 40, 15
                        );

                        // 카메라 안정화 후 폭죽 + 파티클 + 머리 드롭 (10틱 후)
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                // 폭죽+파티클은 플레이어 무관 (위치 기반)
                                spawnDeathFirework(deathLoc);
                                spawnDeathParticles(deathLoc);
                                // 머리 드롭도 월드에 드롭이므로 오프라인이어도 OK
                                if (headId != null && sealedItems != null) {
                                    Player p3 = Bukkit.getPlayer(playerId);
                                    if (p3 != null && p3.isOnline()) {
                                        dropPlayerHead(p3, deathLoc, headId, sealedItems, expiresAt);
                                    }
                                }
                            }
                        }.runTaskLater(plugin, 10L);

                        // 3초(60틱) 후 원래 모드로 복귀 + 스폰 위치로 리스폰
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                animatingPlayers.remove(playerId);
                                Player p4 = Bukkit.getPlayer(playerId);
                                if (p4 == null || !p4.isOnline()) return;
                                p4.setGameMode(originalMode);
                                Location spawnLoc = p4.getBedSpawnLocation();
                                if (spawnLoc == null) {
                                    spawnLoc = p4.getWorld().getSpawnLocation();
                                }
                                p4.teleport(spawnLoc);
                            }
                        }.runTaskLater(plugin, 60L);
                    }
                }.runTaskLater(plugin, 1L);
            }
        }.runTaskLater(plugin, 5L);
    }

    /** 사망 방지권 소지 여부 확인 + 1개 소모 */
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

    /** 아이템이 보호 대상(방지권/열쇠)인지 확인 */
    private boolean isProtectedItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        // 열쇠
        if (plugin.getKeyItem().isKey(item)) return true;
        // 방지권
        if (item.getType() == Material.PAPER &&
                meta.getPersistentDataContainer().has(plugin.getProtectionKey(), PersistentDataType.BYTE)) {
            return true;
        }
        return false;
    }

    /** 인벤토리에서 10~15% 무작위 아이템 제거 후 반환 (방지권/열쇠 제외) */
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

    private void dropPlayerHead(Player player, Location location, String headId, List<ItemStack> sealedItems, long expiresAt) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setPlayerProfile(player.getPlayerProfile());
            meta.setDisplayName("§c" + player.getName() + "§7의 머리");

            // lore 디자인
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm");
            String expireStr = sdf.format(new java.util.Date(expiresAt * 1000L));

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add("§8§m                                    ");
            lore.add("");
            lore.add("  §c§l⚠ §f봉인된 아이템 §7(§f" + sealedItems.size() + "개§7)");
            lore.add("");
            for (int i = 0; i < Math.min(sealedItems.size(), 6); i++) {
                ItemStack sealed = sealedItems.get(i);
                String name = sealed.getItemMeta() != null && sealed.getItemMeta().hasDisplayName()
                        ? sealed.getItemMeta().getDisplayName()
                        : formatMaterialName(sealed.getType());
                int amt = sealed.getAmount();
                lore.add("  §8▸ §f" + name + (amt > 1 ? " §7x" + amt : ""));
            }
            if (sealedItems.size() > 6) {
                lore.add("  §8  ... §7외 §f" + (sealedItems.size() - 6) + "§7개");
            }
            lore.add("");
            lore.add("§8§m                                    ");
            lore.add("");
            lore.add("  §c⏳ §7부패 시각: §f" + expireStr + " §8| §c썩으면 아이템 영구 유실");
            lore.add("");
            lore.add("  §e§l⬦ §e봉인 해제 방법");
            lore.add("  §71. §f머리를 바닥에 설치");
            lore.add("  §72. §6머리 열쇠§7를 들고 §f우클릭");
            lore.add("");
            lore.add("§8§m                                    ");
            lore.add("");
            lore.add("  §b§l🔍 §b[인벤토리 Shift+우클릭] §7봉인 아이템 미리보기");
            lore.add("");
            meta.setLore(lore);

            // headId PDC 부착
            meta.getPersistentDataContainer().set(headIdKey, PersistentDataType.STRING, headId);

            // 같은 플레이어 머리라도 겹치지 않도록 스택 크기 1
            meta.setMaxStackSize(1);
            skull.setItemMeta(meta);
        }

        Item droppedItem = location.getWorld().dropItemNaturally(location, skull);

        // 엔티티 보호
        droppedItem.setMetadata("deathhead", new FixedMetadataValue(plugin, true));
        droppedItem.addScoreboardTag("deathhead");
        droppedItem.setCustomNameVisible(false);
        droppedItem.customName(net.kyori.adventure.text.Component.text("DeathHead"));

        // 아이템 소멸 방지 (무제한)
        droppedItem.setUnlimitedLifetime(true);
    }

    private void spawnDeathFirework(Location location) {
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

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!firework.isDead()) {
                    firework.detonate();
                }
            }
        }.runTaskLater(plugin, 1L);
    }

    private void spawnDeathParticles(Location location) {
        World world = location.getWorld();
        Location particleLoc = location.clone().add(0, 0.5, 0);

        Particle.DustOptions redDust = new Particle.DustOptions(
                org.bukkit.Color.fromRGB(180, 0, 0), 1.5f);
        world.spawnParticle(Particle.DUST, particleLoc, 30, 0.5, 0.5, 0.5, 0, redDust);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick >= 5) {
                    cancel();
                    return;
                }
                Location rising = particleLoc.clone().add(0, tick * 0.6, 0);
                world.spawnParticle(Particle.SOUL, rising, 8, 0.3, 0.2, 0.3, 0.02);
                world.spawnParticle(Particle.DUST, rising, 5, 0.4, 0.3, 0.4, 0, redDust);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 6L);
    }

    /** Material 이름을 읽기 좋게 변환 (DIAMOND_SWORD → Diamond Sword) */
    private String formatMaterialName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    public NamespacedKey getHeadIdKey() {
        return headIdKey;
    }

    /** 연출 중인 플레이어의 원래 GameMode 반환 (연출 중이 아니면 null) */
    public GameMode removeAnimatingPlayer(UUID uuid) {
        return animatingPlayers.remove(uuid);
    }

    /** 퇴장 시 쿨다운 맵 정리 */
    public void cleanupPlayer(UUID uuid) {
        deathCooldowns.remove(uuid);
    }
}
