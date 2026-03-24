package com.deathhead;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public class DeathHeadPlugin extends JavaPlugin {

    private HeadStorage headStorage;
    private KeyItem keyItem;
    private KeyListener keyListener;
    private DeathListener deathListener;
    private NamespacedKey protectionKey;

    @Override
    public void onEnable() {
        // config.yml 생성/로드
        saveDefaultConfig();

        // 시스템 초기화
        protectionKey = new NamespacedKey(this, "protection_ticket");
        keyItem = new KeyItem(this);
        headStorage = new HeadStorage(this);
        headStorage.loadAll();
        headStorage.startExpiryScanner();

        // 리스너 등록
        deathListener = new DeathListener(this);
        getServer().getPluginManager().registerEvents(deathListener, this);
        keyListener = new KeyListener(this);
        getServer().getPluginManager().registerEvents(keyListener, this);
        getServer().getPluginManager().registerEvents(new JoinListener(this), this);
        getServer().getPluginManager().registerEvents(new HeadPreviewListener(this), this);

        // 명령어 등록
        CommandHandler cmdHandler = new CommandHandler(this);
        getCommand("dh").setExecutor(cmdHandler);
        getCommand("dh").setTabCompleter(cmdHandler);

        // 크래프팅 레시피 등록
        registerKeyRecipe();

        // 콘솔 배너
        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§c╔══════════════════════════════════╗");
        Bukkit.getConsoleSender().sendMessage("§c║                                  ║");
        Bukkit.getConsoleSender().sendMessage("§c║  §f💀 §eDeathHead §fv" + getDescription().getVersion() + " §c             ║");
        Bukkit.getConsoleSender().sendMessage("§c║  §7R.E.P.O. Death + Item Seal     ║");
        Bukkit.getConsoleSender().sendMessage("§c║  §7Author: §fmoo_gi                ║");
        Bukkit.getConsoleSender().sendMessage("§c║                                  ║");
        Bukkit.getConsoleSender().sendMessage("§c║  §a✔ §f플러그인 활성화 완료!       ║");
        Bukkit.getConsoleSender().sendMessage("§c║  §7머리 봉인 + 열쇠 회수 시스템    ║");
        Bukkit.getConsoleSender().sendMessage("§c╚══════════════════════════════════╝");
        Bukkit.getConsoleSender().sendMessage("");
    }

    @Override
    public void onDisable() {
        // 캐시 flush
        if (headStorage != null) {
            headStorage.saveAll();
        }

        Bukkit.getConsoleSender().sendMessage("");
        Bukkit.getConsoleSender().sendMessage("§c[DeathHead] §7플러그인 비활성화 — 데이터 저장 완료");
        Bukkit.getConsoleSender().sendMessage("");
    }

    private void registerKeyRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "head_key_recipe");

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, keyItem.createKey(1));
        recipe.shape(
                " I ",
                " G ",
                " S "
        );
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('S', Material.STICK);

        Bukkit.addRecipe(recipe);
    }

    public ItemStack createProtectionTicket(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§b사망 패널티 방지권");
            meta.setLore(List.of(
                    "",
                    "§7사망 시 자동으로 사용되어",
                    "§7아이템 유실을 방지합니다.",
                    "",
                    "§8소모품 — 사망 시 1개 소모"
            ));
            meta.getPersistentDataContainer().set(protectionKey, PersistentDataType.BYTE, (byte) 1);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    public NamespacedKey getProtectionKey() { return protectionKey; }
    public HeadStorage getHeadStorage() { return headStorage; }
    public KeyItem getKeyItem() { return keyItem; }
    public DeathListener getDeathListener() { return deathListener; }
    public KeyListener getKeyListener() { return keyListener; }
}
