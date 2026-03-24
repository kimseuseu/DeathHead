package com.deathhead;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinListener implements Listener {

    private final DeathHeadPlugin plugin;

    public JoinListener(DeathHeadPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // ── 연출 중 재접속 안전장치 ──
        GameMode originalMode = plugin.getDeathListener().removeAnimatingPlayer(player.getUniqueId());
        if (originalMode != null) {
            player.setGameMode(originalMode);
            Location spawnLoc = player.getBedSpawnLocation();
            if (spawnLoc == null) {
                spawnLoc = player.getWorld().getSpawnLocation();
            }
            player.teleport(spawnLoc);
        }

        // ── OP 접속 메시지 ──
        if (player.isOp()) {
            player.sendMessage("");
            player.sendMessage("§c§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            player.sendMessage("");
            player.sendMessage("  §f💀 §e§lDeathHead §fv" + plugin.getDescription().getVersion());
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

    /** 퇴장 시 쿨다운 맵 정리 — 메모리 누수 방지 */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        java.util.UUID uuid = event.getPlayer().getUniqueId();
        plugin.getDeathListener().cleanupPlayer(uuid);
        plugin.getKeyListener().cleanupPlayer(uuid);
    }
}
