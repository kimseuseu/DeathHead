package com.deathhead;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * PlaceholderAPI 연동 유틸리티.
 * PlaceholderAPI가 설치되어 있으면 %placeholder% 를 자동 치환합니다.
 * 없으면 원본 문자열을 그대로 반환합니다.
 */
public final class PlaceholderUtil {

    private static boolean papiEnabled = false;

    private PlaceholderUtil() {}

    public static void init() {
        papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (papiEnabled) {
            Bukkit.getLogger().info("[DeathHead] PlaceholderAPI 연동 활성화");
        }
    }

    public static boolean isEnabled() {
        return papiEnabled;
    }

    /**
     * PlaceholderAPI 플레이스홀더를 치환합니다.
     * player가 null이거나 PAPI가 없으면 원본 반환.
     */
    public static String replace(Player player, String text) {
        if (text == null) return null;
        if (!papiEnabled || player == null) return text;
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Exception e) {
            return text;
        }
    }
}
