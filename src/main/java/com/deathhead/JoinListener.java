package com.deathhead;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class JoinListener implements Listener {

    private final DeathHeadPlugin plugin;

    public JoinListener(DeathHeadPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 연출 중 재접속 시 원래 모드로 복구
        GameMode originalMode = plugin.getDeathListener().removeAnimatingPlayer(player.getUniqueId());
        if (originalMode != null) {
            player.setGameMode(originalMode);
            Location spawnLoc = player.getBedSpawnLocation();
            if (spawnLoc == null) spawnLoc = player.getWorld().getSpawnLocation();
            player.teleport(spawnLoc);
        }

        if (player.isOp()) {
            sendOpWelcome(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        plugin.getDeathListener().cleanupPlayer(uuid);
        plugin.getKeyListener().cleanupPlayer(uuid);
    }

    private void sendOpWelcome(Player player) {
        String version = plugin.getDescription().getVersion();
        player.sendMessage("");
        player.sendMessage("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
        player.sendMessage("  §f💀 §e§lDeathHead §fv" + version);
        player.sendMessage("  §7R.E.P.O. Death + Item Seal System");
        player.sendMessage("");
        player.sendMessage("  §a✔ §f플러그인 정상 작동 중");
        player.sendMessage("  §7사망 → 아이템 봉인 → 열쇠로 회수");
        player.sendMessage("  §7/dh key §8- 열쇠 지급");
        player.sendMessage("  §7/dh reload §8- 설정 리로드");
        player.sendMessage("");
        player.sendMessage("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        player.sendMessage("");
    }
}
