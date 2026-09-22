package dev.aquaguard.cmd;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.checks.CheckCatalog;
import dev.aquaguard.checks.CheckInfo;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AgTabCompleter implements TabCompleter {
    private static final List<String> ROOT = List.of(
            "help", "ping", "gui", "vl", "vlreset", "penalties", "setback", "code", "bypass",
            "checks", "freeze", "reload", "whoami", "alerts", "history", "top", "info", "watch",
            "verbose", "tp", "kick", "flag", "stats", "webhook", "settings"
    );
    private final AquaGuard plugin;

    public AgTabCompleter(AquaGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean staff = sender.hasPermission("ag.alerts") || plugin.owner().isOwner(sender);
        if (!staff) {
            if (args.length == 1) return filter(List.of("help", "code", "whoami"), args[0]);
            return List.of();
        }
        if (args.length == 1) return filter(ROOT, args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "vl", "vlreset", "info", "watch", "tp", "kick", "flag", "freeze", "gui", "history" -> players(args[1]);
                case "penalties" -> filter(List.of("off", "simulate", "soft", "hard"), args[1]);
                case "setback", "alerts" -> filter(List.of("on", "off", "toggle"), args[1]);
                case "bypass" -> filter(List.of("add", "remove", "list", "code"), args[1]);
                case "checks" -> {
                    List<String> names = new ArrayList<>();
                    names.add("list");
                    names.add("stable");
                    for (CheckInfo info : CheckCatalog.all()) names.add(info.id());
                    yield filter(names, args[1]);
                }
                case "settings" -> filter(List.of("clear"), args[1]);
                case "webhook" -> filter(List.of("test"), args[1]);
                default -> List.of();
            };
        }
        if (args.length == 3) {
            return switch (sub) {
                case "bypass" -> args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove") ? players(args[2]) : List.of();
                case "checks" -> filter(List.of("on", "off", "toggle"), args[2]);
                case "freeze" -> filter(List.of("on", "off"), args[2]);
                case "flag" -> {
                    List<String> names = new ArrayList<>();
                    for (CheckInfo info : CheckCatalog.all()) names.add(info.id());
                    yield filter(names, args[2]);
                }
                case "vlreset" -> {
                    List<String> names = new ArrayList<>();
                    for (CheckInfo info : CheckCatalog.all()) names.add(info.id());
                    yield filter(names, args[2]);
                }
                default -> List.of();
            };
        }
        return List.of();
    }

    private static List<String> players(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) names.add(player.getName());
        return filter(names, prefix);
    }

    private static List<String> filter(List<String> options, String prefix) {
        String needle = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(needle)) out.add(option);
        }
        return out;
    }
}
