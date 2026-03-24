package com.deathhead;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class HeadStorage {

    private final DeathHeadPlugin plugin;
    private final File headsFolder;
    private final Map<String, HeadData> cache = new ConcurrentHashMap<>();

    public HeadStorage(DeathHeadPlugin plugin) {
        this.plugin = plugin;
        this.headsFolder = new File(plugin.getDataFolder(), "heads");
        if (!headsFolder.exists()) {
            headsFolder.mkdirs();
        }
    }

    /** 서버 시작 시 기존 YAML 전부 로드 */
    public void loadAll() {
        cache.clear();
        File[] files = headsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                HeadData data = readFile(file);
                if (data != null) {
                    if (data.isExpired()) {
                        file.delete();
                    } else {
                        cache.put(data.getHeadId(), data);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load head file: " + file.getName(), e);
            }
        }
        plugin.getLogger().info("Loaded " + cache.size() + " head data entries.");
    }

    /** 캐시에서 조회 */
    public HeadData get(String headId) {
        HeadData data = cache.get(headId);
        if (data != null && data.isExpired()) {
            remove(headId);
            return null;
        }
        return data;
    }

    /** 사망 시 저장 (캐시 + 비동기 파일) */
    public void save(HeadData data) {
        cache.put(data.getHeadId(), data);

        new BukkitRunnable() {
            @Override
            public void run() {
                writeFile(data);
            }
        }.runTaskAsynchronously(plugin);
    }

    /** 회수 시 삭제 (캐시 + 비동기 파일) */
    public void remove(String headId) {
        cache.remove(headId);

        new BukkitRunnable() {
            @Override
            public void run() {
                File file = new File(headsFolder, headId + ".yml");
                if (file.exists()) {
                    file.delete();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    /** 만료 스캐너 시작 (1분 주기) */
    public void startExpiryScanner() {
        int intervalSeconds = plugin.getConfig().getInt("head.cleanup-interval", 60);
        long intervalTicks = intervalSeconds * 20L;

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis() / 1000L;
                List<String> expired = new ArrayList<>();

                for (Map.Entry<String, HeadData> entry : cache.entrySet()) {
                    if (now >= entry.getValue().getExpiresAt()) {
                        expired.add(entry.getKey());
                    }
                }

                if (!expired.isEmpty()) {
                    // 비동기에서 파일 삭제
                    for (String headId : expired) {
                        cache.remove(headId);
                        File file = new File(headsFolder, headId + ".yml");
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                    plugin.getLogger().info("Expired " + expired.size() + " head(s).");
                }
            }
        }.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);
    }

    /** 서버 종료 시 dirty 데이터 flush */
    public void saveAll() {
        for (HeadData data : cache.values()) {
            writeFile(data);
        }
    }

    private void writeFile(HeadData data) {
        File file = new File(headsFolder, data.getHeadId() + ".yml");
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("headId", data.getHeadId());
        yml.set("owner", data.getOwnerUuid().toString());
        yml.set("ownerName", data.getOwnerName());
        yml.set("deathWorld", data.getDeathWorld());
        yml.set("deathX", data.getDeathX());
        yml.set("deathY", data.getDeathY());
        yml.set("deathZ", data.getDeathZ());
        yml.set("createdAt", data.getCreatedAt());
        yml.set("expiresAt", data.getExpiresAt());

        // ItemStack 리스트를 직렬화
        List<Map<String, Object>> serializedItems = new ArrayList<>();
        for (ItemStack item : data.getItems()) {
            serializedItems.add(item.serialize());
        }
        yml.set("items", serializedItems);

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save head file: " + file.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private HeadData readFile(File file) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String headId = yml.getString("headId");
        if (headId == null) return null;

        UUID ownerUuid = UUID.fromString(yml.getString("owner", ""));
        String ownerName = yml.getString("ownerName", "Unknown");
        String deathWorld = yml.getString("deathWorld", "world");
        int deathX = yml.getInt("deathX");
        int deathY = yml.getInt("deathY");
        int deathZ = yml.getInt("deathZ");
        long createdAt = yml.getLong("createdAt");
        long expiresAt = yml.getLong("expiresAt");

        List<ItemStack> items = new ArrayList<>();
        List<?> rawItems = yml.getList("items");
        if (rawItems != null) {
            for (Object obj : rawItems) {
                if (obj instanceof Map) {
                    try {
                        items.add(ItemStack.deserialize((Map<String, Object>) obj));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to deserialize item in " + file.getName());
                    }
                }
            }
        }

        return new HeadData(headId, ownerUuid, ownerName,
                deathWorld, deathX, deathY, deathZ,
                createdAt, expiresAt, items);
    }
}
