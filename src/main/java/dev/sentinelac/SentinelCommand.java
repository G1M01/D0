package dev.sentinelac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public final class SentinelCommand implements CommandExecutor, TabCompleter {
    private final SentinelACPlugin plugin;

    public SentinelCommand(SentinelACPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("sentinel.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sender.sendMessage(ChatColor.GOLD + "SentinelAC " + plugin.getDescription().getVersion()
                    + ChatColor.GRAY + " | Connections: "
                    + (plugin.getConnectionGuard() == null ? 0 : plugin.getConnectionGuard().getConnectionCount())
                    + " | Evidence DB: " + (plugin.getEvidenceStore() == null ? "off" : "on"));
            return true;
        }
        if ("reload".equalsIgnoreCase(args[0])) {
            plugin.reloadConfig();
            plugin.getConnectionGuard().reloadSettings();
            sender.sendMessage(ChatColor.GREEN + "SentinelAC configuration reloaded.");
            return true;
        }
        if ("unban".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                sender.sendMessage(ChatColor.RED + "Usage: /sentinel unban <player>");
                return true;
            }
            Bukkit.getBanList(BanList.Type.NAME).pardon(args[1]);
            recordAction(sender.getName(), "UNBAN", args[1], "Administrative override");
            sender.sendMessage(ChatColor.GREEN + "Unbanned " + args[1] + ".");
            return true;
        }
        if ("ban".equalsIgnoreCase(args[0])) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Usage: /sentinel ban <player> <reason>");
                return true;
            }
            StringBuilder reason = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (reason.length() > 0) {
                    reason.append(' ');
                }
                reason.append(args[i]);
            }
            Bukkit.getBanList(BanList.Type.NAME).addBan(args[1], reason.toString(), null, sender.getName());
            recordAction(sender.getName(), "BAN", args[1], reason.toString());
            sender.sendMessage(ChatColor.GREEN + "Permanently banned " + args[1] + ".");
            for (CommandSender online : Bukkit.getOnlinePlayers()) {
                if (online.hasPermission("sentinel.admin")) {
                    online.sendMessage(ChatColor.RED + "[SentinelAC] " + args[1] + " was banned by "
                            + sender.getName() + ": " + reason);
                }
            }
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Usage: /sentinel <status|reload|ban|unban>");
        return true;
    }

    private void recordAction(String actor, String action, String target, String details) {
        if (plugin.getEvidenceStore() != null) {
            plugin.getEvidenceStore().recordAction(actor, action, target, details);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = Arrays.asList("status", "reload", "ban", "unban");
            List<String> matches = new ArrayList<String>();
            for (String option : options) {
                if (option.startsWith(args[0].toLowerCase())) {
                    matches.add(option);
                }
            }
            return matches;
        }
        return Collections.emptyList();
    }
}
