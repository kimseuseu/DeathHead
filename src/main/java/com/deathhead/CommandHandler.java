package com.deathhead;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CommandHandler implements CommandExecutor, TabCompleter {

    private final DeathHeadPlugin plugin;

    public CommandHandler(DeathHeadPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "key" -> handleKey(sender, args);
            case "ticket" -> handleTicket(sender, args);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleKey(CommandSender sender, String[] args) {
        if (!sender.hasPermission("deathhead.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        Player target;
        int amount = 1;

        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
                return;
            }
            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c올바른 숫자를 입력하세요.");
                    return;
                }
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§c사용법: /dh key <플레이어> [개수]");
            return;
        }

        target.getInventory().addItem(plugin.getKeyItem().createKey(amount));
        sender.sendMessage("§a" + target.getName() + "에게 머리 열쇠 " + amount + "개를 지급했습니다.");
        if (sender != target) {
            target.sendMessage("§a머리 열쇠 " + amount + "개를 받았습니다.");
        }
    }

    private void handleTicket(CommandSender sender, String[] args) {
        if (!sender.hasPermission("deathhead.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }

        Player target;
        int amount = 1;

        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§c플레이어를 찾을 수 없습니다: " + args[1]);
                return;
            }
            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c올바른 숫자를 입력하세요.");
                    return;
                }
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§c사용법: /dh ticket <플레이어> [개수]");
            return;
        }

        target.getInventory().addItem(plugin.createProtectionTicket(amount));
        sender.sendMessage("§a" + target.getName() + "에게 사망 방지권 " + amount + "개를 지급했습니다.");
        if (sender != target) {
            target.sendMessage("§b사망 패널티 방지권 " + amount + "개를 받았습니다.");
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("deathhead.admin")) {
            sender.sendMessage("§c권한이 없습니다.");
            return;
        }
        plugin.reloadConfig();
        sender.sendMessage("§aDeathHead 설정을 리로드했습니다.");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§e§l━━━ DeathHead v2.1 ━━━");
        sender.sendMessage("§6/dh key [플레이어] [개수] §7- 열쇠 지급");
        sender.sendMessage("§6/dh ticket [플레이어] [개수] §7- 사망 방지권 지급");
        sender.sendMessage("§6/dh reload §7- 설정 리로드");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                       @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        String input = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            for (String sub : List.of("key", "ticket", "reload")) {
                if (sub.startsWith(input)) completions.add(sub);
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("key") || args[0].equalsIgnoreCase("ticket"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(input)) completions.add(p.getName());
            }
        }
        return completions;
    }
}
