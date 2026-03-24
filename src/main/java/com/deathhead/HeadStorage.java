package com.deathhead;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

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
        if (!headsFolder.exists()) headsFolder.mkdirs();
    }

    public void loadAll() {
        cache.clear();
        File[] files = headsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        int expired = 0;
        for (File file : files) {
            try {
                HeadData data = readFile(file);
                if (data == null) continue;
                if (data.isExpired()) {
                    file.delete();
                    expired++;
                } else {
                    cache.put(data.getHeadId(), data);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load: " + file.getName(), e);
            }
        }
        plugin.getLogger().info("Loaded " + cache.size() + " head(s), cleaned " + expired + " expired.");
    }

    public HeadData get(String headId) {
        HeadData data = cache.get(headId);
        if (data != null && data.isExpired()) {
            remove(headId);
            return null;
        }
        return data;
    }

    public void save(HeadData data) {
        cache.put(data.getHeadId(), data);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeFile(data));
    }

    public void remove(String headId) {
        cache.remove(headId);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            File file = new File(headsFolder, headId + ".yml");
            if (file.exists()) file.delete();
        });
    }

    public void startExpiryScanner() {
        int intervalSeconds = plugin.getConfig().getInt("head.cleanup-interval", 60);
        long intervalTicks = intervalSeconds * 20L;

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis() / 1000L;
            List<String> expired = new ArrayList<>();

            for (Map.Entry<String, HeadData> entry : cache.entrySet()) {
                if (now >= entry.getValue().getExpiresAt()) {
                    expired.add(entry.getKey());
                }
            }

            for (String headId : expired) {
                cache.remove(headId);
                File file = new File(headsFolder, headId + ".yml");
                if (file.exists()) file.delete();
            }

            if (!expired.isEmpty()) {
                plugin.getLogger().info("Expired " + expired.size() + " head(s).");
            }
        }, intervalTicks, intervalTicks);
    }

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

        List<Map<String, Object>> serializedItems = new ArrayList<>();
        for (ItemStack item : data.getItems()) {
            serializedItems.add(item.serialize());
        }
        yml.set("items", serializedItems);

        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save: " + file.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private HeadData readFile(File file) {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String headId = yml.getString("headId");
        if (headId == null) return null;

        String ownerStr = yml.getString("owner", "");
        if (ownerStr.isEmpty()) return null;
        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid UUID in " + file.getName());
            return null;
        }
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
