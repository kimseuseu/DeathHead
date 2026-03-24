package com.deathhead;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DeathHeadPlugin extends JavaPlugin {

    private HeadStorage headStorage;
    private KeyItem keyItem;
    private KeyListener keyListener;
    private DeathListener deathListener;
    private NamespacedKey protectionKey;
    private FileConfiguration messagesConfig;
    private FileConfiguration itemsConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfAbsent("messages.yml");
        saveResourceIfAbsent("items.yml");
        loadCustomConfigs();

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

        deathListener.startLoreUpdater();

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

    public void reloadAllConfigs() {
        reloadConfig();
        loadCustomConfigs();
    }

    private void loadCustomConfigs() {
        messagesConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        itemsConfig = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "items.yml"));

        InputStream msgDefaults = getResource("messages.yml");
        if (msgDefaults != null) {
            messagesConfig.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(msgDefaults, StandardCharsets.UTF_8)));
        }
        InputStream itemDefaults = getResource("items.yml");
        if (itemDefaults != null) {
            itemsConfig.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(itemDefaults, StandardCharsets.UTF_8)));
        }
    }

    private void saveResourceIfAbsent(String resourceName) {
        File file = new File(getDataFolder(), resourceName);
        if (!file.exists()) saveResource(resourceName, false);
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
        String materialName = itemsConfig.getString("protection-ticket.material", "PAPER");
        Material material = Material.valueOf(materialName.toUpperCase());

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(getItemString("protection-ticket.name", "§b사망 패널티 방지권"));

        List<String> rawLore = itemsConfig.getStringList("protection-ticket.lore");
        if (!rawLore.isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : rawLore) lore.add(line.replace('&', '§'));
            meta.setLore(lore);
        }

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

    // ─── 설정 헬퍼 ───

    public String getMessage(String key, String defaultMsg) {
        return messagesConfig.getString(key, defaultMsg).replace('&', '§');
    }

    public String getMessage(String key, String defaultMsg, String... replacements) {
        String msg = getMessage(key, defaultMsg);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    public String getItemString(String key, String defaultVal) {
        return itemsConfig.getString(key, defaultVal).replace('&', '§');
    }

    public FileConfiguration getItemsConfig() { return itemsConfig; }
    public FileConfiguration getMessagesConfig() { return messagesConfig; }
    public NamespacedKey getProtectionKey() { return protectionKey; }
    public HeadStorage getHeadStorage() { return headStorage; }
    public KeyItem getKeyItem() { return keyItem; }
    public DeathListener getDeathListener() { return deathListener; }
    public KeyListener getKeyListener() { return keyListener; }
}
