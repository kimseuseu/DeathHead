package com.deathhead;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeDisplayManager {

    private static final double VIEW_DISTANCE_SQ = 48.0 * 48.0;
    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    private final DeathHeadPlugin plugin;
    private final ProtocolManager pm;
    private final AtomicInteger idCounter = new AtomicInteger(Integer.MAX_VALUE / 2);

    private final Map<Integer, FakeDisplay> displays = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> vehicleToFake = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> playerVisible = new ConcurrentHashMap<>();

    record FakeDisplay(
            int entityId, UUID fakeUuid,
            UUID vehicleUuid, Location staticLoc,
            Component text, String world, float yOffset
    ) {}

    public FakeDisplayManager(DeathHeadPlugin plugin) {
        this.plugin = plugin;
        this.pm = ProtocolLibrary.getProtocolManager();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 20L);
    }

    public int createRiding(Entity vehicle, Component text, float yOffset) {
        int id = idCounter.decrementAndGet();
        displays.put(id, new FakeDisplay(id, UUID.randomUUID(),
                vehicle.getUniqueId(), null, text, vehicle.getWorld().getName(), yOffset));
        vehicleToFake.put(vehicle.getUniqueId(), id);
        return id;
    }

    public int createStatic(Location loc, Component text, float yOffset) {
        int id = idCounter.decrementAndGet();
        Location adjusted = loc.clone().add(0.5, yOffset, 0.5);
        displays.put(id, new FakeDisplay(id, UUID.randomUUID(),
                null, adjusted, text, loc.getWorld().getName(), 0f));
        return id;
    }

    public void updateText(int id, Component text) {
        FakeDisplay old = displays.get(id);
        if (old == null) return;
        displays.put(id, new FakeDisplay(old.entityId, old.fakeUuid,
                old.vehicleUuid, old.staticLoc, text, old.world, old.yOffset));

        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<Integer> vis = playerVisible.get(p.getUniqueId());
            if (vis != null && vis.contains(id)) {
                sendMeta(p, displays.get(id));
            }
        }
    }

    public void remove(int id) {
        FakeDisplay d = displays.remove(id);
        if (d == null) return;
        if (d.vehicleUuid != null) vehicleToFake.remove(d.vehicleUuid);

        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<Integer> vis = playerVisible.get(p.getUniqueId());
            if (vis != null && vis.remove(id)) {
                sendDestroy(p, id);
            }
        }
    }

    public void removeByVehicle(UUID vehicleUuid) {
        Integer id = vehicleToFake.get(vehicleUuid);
        if (id != null) remove(id);
    }

    public boolean hasDisplay(UUID vehicleUuid) {
        return vehicleToFake.containsKey(vehicleUuid);
    }

    public void onPlayerQuit(UUID uuid) {
        playerVisible.remove(uuid);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<Integer> vis = playerVisible.computeIfAbsent(
                    p.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
            Location pLoc = p.getLocation();
            String pWorld = pLoc.getWorld().getName();

            for (FakeDisplay d : displays.values()) {
                Location dLoc = getBaseLocation(d);
                boolean shouldSee = dLoc != null && d.world.equals(pWorld)
                        && pLoc.distanceSquared(dLoc) <= VIEW_DISTANCE_SQ;

                if (shouldSee && vis.add(d.entityId)) {
                    sendSpawn(p, d, dLoc);
                    sendMeta(p, d);
                    if (d.vehicleUuid != null) {
                        Entity vehicle = Bukkit.getEntity(d.vehicleUuid);
                        if (vehicle != null) sendMount(p, vehicle.getEntityId(), d.entityId);
                    }
                } else if (!shouldSee && vis.remove(d.entityId)) {
                    sendDestroy(p, d.entityId);
                }
            }
        }
    }

    private Location getBaseLocation(FakeDisplay d) {
        if (d.staticLoc != null) return d.staticLoc;
        if (d.vehicleUuid != null) {
            Entity e = Bukkit.getEntity(d.vehicleUuid);
            return e != null ? e.getLocation() : null;
        }
        return null;
    }

    private void sendSpawn(Player p, FakeDisplay d, Location displayLoc) {
        try {
            PacketContainer pkt = pm.createPacket(PacketType.Play.Server.SPAWN_ENTITY);
            pkt.getIntegers().write(0, d.entityId);
            pkt.getUUIDs().write(0, d.fakeUuid);
            pkt.getEntityTypeModifier().write(0, EntityType.TEXT_DISPLAY);
            pkt.getDoubles().write(0, displayLoc.getX())
                    .write(1, displayLoc.getY())
                    .write(2, displayLoc.getZ());
            pm.sendServerPacket(p, pkt);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send spawn packet: " + e.getMessage());
        }
    }

    private void sendMeta(Player p, FakeDisplay d) {
        try {
            PacketContainer pkt = pm.createPacket(PacketType.Play.Server.ENTITY_METADATA);
            pkt.getIntegers().write(0, d.entityId);

            String json = GSON.serialize(d.text);
            Object nmsComponent = WrappedChatComponent.fromJson(json).getHandle();

            List<WrappedDataValue> values = new ArrayList<>();

            // No gravity (index 5, flag 0x02)
            values.add(new WrappedDataValue(5,
                    WrappedDataWatcher.Registry.get(Boolean.class), true));
            // Billboard = CENTER (3)
            values.add(new WrappedDataValue(15,
                    WrappedDataWatcher.Registry.get(Byte.class), (byte) 3));
            // Brightness override (blockLight=15 << 4, skyLight=15 << 20)
            values.add(new WrappedDataValue(16,
                    WrappedDataWatcher.Registry.get(Integer.class), (15 << 4) | (15 << 20)));
            // Text (TextDisplay uses raw Component, not Optional)
            values.add(new WrappedDataValue(23,
                    WrappedDataWatcher.Registry.getChatComponentSerializer(false),
                    nmsComponent));
            // Background ARGB (fully transparent)
            values.add(new WrappedDataValue(25,
                    WrappedDataWatcher.Registry.get(Integer.class), 0));
            // Flags: has shadow
            values.add(new WrappedDataValue(27,
                    WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x01));

            // Translation (Y offset for riding displays)
            if (d.yOffset != 0) {
                try {
                    Class<?> serializersClass = Class.forName("net.minecraft.network.syncher.EntityDataSerializers");
                    java.lang.reflect.Field vectorField = serializersClass.getDeclaredField("VECTOR3");
                    vectorField.setAccessible(true);
                    Object nmsSerializer = vectorField.get(null);
                    var wrappedSerializer = WrappedDataWatcher.Registry.fromHandle(nmsSerializer);
                    values.add(new WrappedDataValue(11, wrappedSerializer,
                            new Vector3f(0f, d.yOffset, 0f)));
                } catch (Exception e) {
                    plugin.getLogger().warning("Vector3f translation failed: " + e.getMessage());
                }
            }

            pkt.getDataValueCollectionModifier().write(0, values);
            pm.sendServerPacket(p, pkt);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send metadata packet: " + e.getMessage());
        }
    }

    private void sendMount(Player p, int vehicleId, int passengerId) {
        try {
            PacketContainer pkt = pm.createPacket(PacketType.Play.Server.MOUNT);
            pkt.getIntegers().write(0, vehicleId);
            pkt.getIntegerArrays().write(0, new int[]{passengerId});
            pm.sendServerPacket(p, pkt);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send mount packet: " + e.getMessage());
        }
    }

    private void sendDestroy(Player p, int entityId) {
        try {
            PacketContainer pkt = pm.createPacket(PacketType.Play.Server.ENTITY_DESTROY);
            pkt.getIntLists().write(0, List.of(entityId));
            pm.sendServerPacket(p, pkt);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send destroy packet: " + e.getMessage());
        }
    }
}
