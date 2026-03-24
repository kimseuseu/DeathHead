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
        saveDefaultConfig();

        protectionKey = new NamespacedKey(this, "protection_ticket");
        keyItem = new KeyItem(this);
        headStorage = new HeadStorage(this);
        headStorage.loadAll();
        headStorage.startExpiryScanner();

        deathListener = new DeathListener(this);
        keyListener = new KeyListener(this);
        var pm = getServer().getPluginManager();
        pm.registerEvents(deathListener, this);
        pm.registerEvents(keyListener, this);
        pm.registerEvents(new JoinListener(this), this);
        pm.registerEvents(new HeadPreviewListener(this), this);

        CommandHandler cmdHandler = new CommandHandler(this);
        getCommand("dh").setExecutor(cmdHandler);
        getCommand("dh").setTabCompleter(cmdHandler);

        registerKeyRecipe();
        printBanner();
    }

    @Override
    public void onDisable() {
        if (headStorage != null) headStorage.saveAll();
        Bukkit.getConsoleSender().sendMessage("§c[DeathHead] §7비활성화 — 데이터 저장 완료");
    }

    private void registerKeyRecipe() {
        NamespacedKey recipeKey = new NamespacedKey(this, "head_key_recipe");
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, keyItem.createKey(1));
        recipe.shape(" I ", " G ", " S ");
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('G', Material.GOLD_INGOT);
        recipe.setIngredient('S', Material.STICK);
        Bukkit.addRecipe(recipe);
    }

    public ItemStack createProtectionTicket(int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

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
        return item;
    }

    private void printBanner() {
        var console = Bukkit.getConsoleSender();
        String v = getDescription().getVersion();
        console.sendMessage("");
        console.sendMessage("§c╔══════════════════════════════════╗");
        console.sendMessage("§c║                                  ║");
        console.sendMessage("§c║  §f💀 §eDeathHead §fv" + v + " §c             ║");
        console.sendMessage("§c║  §7R.E.P.O. Death + Item Seal     ║");
        console.sendMessage("§c║  §7Author: §fmoo_gi                ║");
        console.sendMessage("§c║                                  ║");
        console.sendMessage("§c║  §a✔ §f플러그인 활성화 완료!       ║");
        console.sendMessage("§c║  §7머리 봉인 + 열쇠 회수 시스템    ║");
        console.sendMessage("§c╚══════════════════════════════════╝");
        console.sendMessage("");
    }

    public NamespacedKey getProtectionKey() { return protectionKey; }
    public HeadStorage getHeadStorage() { return headStorage; }
    public KeyItem getKeyItem() { return keyItem; }
    public DeathListener getDeathListener() { return deathListener; }
    public KeyListener getKeyListener() { return keyListener; }
}
