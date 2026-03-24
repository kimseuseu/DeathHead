package com.deathhead;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class HeadData {

    private final String headId;
    private final UUID ownerUuid;
    private final String ownerName;
    private final String deathWorld;
    private final int deathX;
    private final int deathY;
    private final int deathZ;
    private final long createdAt;
    private final long expiresAt;
    private final List<ItemStack> items;

    public HeadData(String headId, UUID ownerUuid, String ownerName,
                    String deathWorld, int deathX, int deathY, int deathZ,
                    long createdAt, long expiresAt, List<ItemStack> items) {
        this.headId = headId;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.deathWorld = deathWorld;
        this.deathX = deathX;
        this.deathY = deathY;
        this.deathZ = deathZ;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.items = items;
    }

    public String getHeadId() { return headId; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public String getDeathWorld() { return deathWorld; }
    public int getDeathX() { return deathX; }
    public int getDeathY() { return deathY; }
    public int getDeathZ() { return deathZ; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public List<ItemStack> getItems() { return items; }

    public boolean isExpired() {
        return System.currentTimeMillis() / 1000L >= expiresAt;
    }
}
