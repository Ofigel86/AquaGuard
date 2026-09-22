package dev.aquaguard.cmd;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.checks.CheckCatalog;
import dev.aquaguard.checks.CheckInfo;
import dev.aquaguard.core.FlagRecord;
import dev.aquaguard.penalty.PenaltyManager;
import dev.aquaguard.util.Compat;
import dev.aquaguard.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AgCommand implements CommandExecutor {
    private final AquaGuard plugin;
    private final Map<UUID, Integer> codeAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> codeLock = new ConcurrentHashMap<>();

    public AgCommand(AquaGuard plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender, label);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help", "?" -> help(sender, label);
            case "ping", "status", "version" -> status(sender);
            case "whoami" -> whoami(sender);
            case "code" -> code(sender, args);
            case "alerts" -> alerts(sender, args);
            case "gui" -> gui(sender, args);
            case "vl" -> vl(sender, args);
            case "vlreset", "reset" -> vlReset(sender, args);
            case "penalties" -> penalties(sender, args, label);
            case "setback" -> setback(sender, args, label);
            case "bypass" -> bypass(sender, args, label);
            case "checks" -> checks(sender, args, label);
            case "freeze" -> freeze(sender, args, label);
            case "reload" -> reload(sender);
            case "history" -> history(sender, args);
            case "top" -> top(sender);
            case "info" -> info(sender, args);
            case "watch" -> watch(sender, args);
            case "verbose" -> verbose(sender);
            case "tp" -> tp(sender, args);
            case "kick" -> kick(sender, args);
            case "flag" -> flag(sender, args);
            case "stats" -> stats(sender);
            case "webhook" -> webhook(sender);
            case "settings" -> settings(sender, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        Texts.send(sender, "&3Aqua&bGuard &8" + plugin.getDescription().getVersion());
        Texts.send(sender, "&7/" + label + " ping &8· &7gui &8· &7vl [ник] &8· &7info <ник>");
        Texts.send(sender, "&7/" + label + " alerts &8· &7checks list &8· &7freeze <ник> &8· &7watch <ник>");
        Texts.send(sender, "&7/" + label + " penalties &8· &7setback &8· &7bypass &8· &7history &8· &7top");
        Texts.send(sender, "&7/" + label + " vlreset <ник> [чек] &8· &7reload &8· &7stats");
        if (!(sender instanceof Player)) Texts.send(sender, "&7/" + label + " code <секрет> &8— &7для игрока");
    }

    private void status(CommandSender sender) {
        if (!admin(sender)) return;
        Texts.send(sender, "&3Aqua&bGuard &f" + plugin.getDescription().getVersion()
                + " &7checks &f" + plugin.checks().enabledCount() + "&7/&f" + CheckCatalog.all().size()
                + " &7tps &f" + String.format(Locale.US, "%.2f", Compat.tps())
                + " &7mspt &f" + String.format(Locale.US, "%.1f", Compat.averageTickMs()));
        Texts.send(sender, "&7penalties=&f" + plugin.penalties().mode().name().toLowerCase(Locale.ROOT)
                + " &7setback=&f" + (plugin.settings().setbackEnabled() ? "on" : "off")
                + " &7punishments=&f" + (plugin.punishments().enabled() ? plugin.punishments().mode() : "off")
                + " &7webhook=&f" + (plugin.webhook().enabled() ? "on" : "off"));
    }

    private void whoami(CommandSender sender) {
        if (sender instanceof Player player) {
            Texts.send(sender, "&7Ты &f" + player.getName() + " &8" + player.getUniqueId()
                    + (plugin.owner().isListedOwner(player) ? " &aowner" : ""));
        } else Texts.send(sender, "&7Консоль");
    }

    private void code(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-player");
            return;
        }
        if (args.length < 2) {
            Texts.send(sender, "&7Использование: &f/ag code <секрет>");
            return;
        }
        long now = System.currentTimeMillis();
        Long lock = codeLock.get(player.getUniqueId());
        if (lock != null && now < lock) {
            Texts.send(sender, "&cСлишком много попыток. Подожди.");
            return;
        }
        if (plugin.bypass().claimCode(player.getUniqueId(), args[1])) {
            codeAttempts.remove(player.getUniqueId());
            Texts.send(sender, "&aОбход активирован.");
            return;
        }
        int attempts = codeAttempts.merge(player.getUniqueId(), 1, Integer::sum);
        if (attempts >= 5) {
            codeLock.put(player.getUniqueId(), now + 120_000);
            codeAttempts.remove(player.getUniqueId());
        }
        Texts.send(sender, "&cНеверный или использованный код.");
    }

    private void alerts(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-player");
            return;
        }
        if (!player.hasPermission("ag.alerts") && !plugin.owner().isOwner(player)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        boolean on = args.length >= 2 ? switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> true;
            case "off" -> false;
            default -> !plugin.alerts().alertsOn(player);
        } : plugin.alerts().toggle(player);
        if (args.length >= 2) {
            if (on && !plugin.alerts().alertsOn(player)) plugin.alerts().toggle(player);
            if (!on && plugin.alerts().alertsOn(player)) plugin.alerts().toggle(player);
        }
        Texts.send(sender, "&7Алерты: " + (plugin.alerts().alertsOn(player) ? "&aON" : "&cOFF"));
    }

    private void gui(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-player");
            return;
        }
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "player-not-found");
                return;
            }
            plugin.gui().openDetail(player, target.getUniqueId());
            return;
        }
        plugin.gui().openMain(player);
    }

    private void vl(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        UUID id = null;
        String name = null;
        if (args.length >= 2) {
            Player online = Bukkit.getPlayerExact(args[1]);
            if (online != null) {
                id = online.getUniqueId();
                name = online.getName();
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
                if (offline.hasPlayedBefore() || offline.isOnline()) {
                    id = offline.getUniqueId();
                    name = offline.getName();
                }
            }
        } else if (sender instanceof Player player) {
            id = player.getUniqueId();
            name = player.getName();
        }
        if (id == null) {
            plugin.messages().send(sender, "player-not-found");
            return;
        }
        Map<String, Double> map = plugin.violations().get(id);
        if (map.isEmpty()) {
            Texts.send(sender, "&7VL пуст для &f" + name);
            return;
        }
        Texts.send(sender, "&7VL &f" + name + "&7:");
        map.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<String, Double> e) -> e.getValue()).reversed())
                .forEach(e -> Texts.send(sender, "&8 - &f" + e.getKey() + " &7" + fmt(e.getValue())));
        Texts.send(sender, "&7Итого: &f" + fmt(plugin.violations().total(id)));
    }

    private void vlReset(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Texts.send(sender, "&7Использование: &f/ag vlreset <ник> [чек]");
            return;
        }
        UUID id = resolve(args[1]);
        if (id == null) {
            plugin.messages().send(sender, "player-not-found");
            return;
        }
        if (args.length >= 3) plugin.violations().reset(id, CheckCatalog.resolve(args[2]));
        else {
            plugin.violations().reset(id);
            plugin.punishments().clear(id);
        }
        Texts.send(sender, "&aVL сброшен для &f" + plugin.violations().name(id));
    }

    private void penalties(CommandSender sender, String[] args, String label) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Texts.send(sender, "&7Режим: &f" + plugin.penalties().mode().name().toLowerCase(Locale.ROOT));
            Texts.send(sender, "&7/" + label + " penalties <off|simulate|soft|hard>");
            return;
        }
        PenaltyManager.Mode mode = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "off" -> PenaltyManager.Mode.OFF;
            case "simulate" -> PenaltyManager.Mode.SIMULATE;
            case "soft" -> PenaltyManager.Mode.SOFT;
            case "hard" -> PenaltyManager.Mode.HARD;
            default -> null;
        };
        if (mode == null) {
            Texts.send(sender, "&7off, simulate, soft, hard");
            return;
        }
        plugin.penalties().setMode(mode);
        Texts.send(sender, "&7Penalties: &f" + mode.name().toLowerCase(Locale.ROOT));
    }

    private void setback(CommandSender sender, String[] args, String label) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Texts.send(sender, "&7Setback: &f" + (plugin.settings().setbackEnabled() ? "on" : "off"));
            return;
        }
        boolean cur = plugin.settings().setbackEnabled();
        boolean next = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on" -> true;
            case "off" -> false;
            case "toggle" -> !cur;
            default -> cur;
        };
        plugin.settings().setSetbackEnabled(next);
        Texts.send(sender, "&7Setback: &f" + (next ? "on" : "off"));
    }

    private void bypass(CommandSender sender, String[] args, String label) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Texts.send(sender, "&7/" + label + " bypass add <ник> [минуты] &8· &7remove <ник> &8· &7list &8· &7code");
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                if (args.length < 3) return;
                UUID id = resolve(args[2]);
                if (id == null) {
                    plugin.messages().send(sender, "player-not-found");
                    return;
                }
                long mins = args.length >= 4 ? parseLong(args[3], plugin.getConfig().getInt("bypass.default-expire-mins", 1440))
                        : plugin.getConfig().getInt("bypass.default-expire-mins", 1440);
                plugin.bypass().addBypass(id, mins);
                Texts.send(sender, "&aBypass &f" + plugin.violations().name(id) + " &7на &f" + mins + " &7мин.");
            }
            case "remove" -> {
                if (args.length < 3) return;
                UUID id = resolve(args[2]);
                if (id == null) {
                    plugin.messages().send(sender, "player-not-found");
                    return;
                }
                plugin.bypass().removeBypass(id);
                Texts.send(sender, "&eBypass снят с &f" + plugin.violations().name(id));
            }
            case "list" -> {
                Texts.send(sender, "&7Активные обходы:");
                plugin.bypass().list().forEach((id, exp) -> {
                    long left = exp <= 0 ? -1 : Math.max(0, (exp - System.currentTimeMillis()) / 60000);
                    Texts.send(sender, "&8 - &f" + plugin.violations().name(id) + " &7" + (left < 0 ? "бессрочно" : left + " мин"));
                });
            }
            case "code" -> Texts.send(sender, "&aКод: &f" + plugin.bypass().createCode());
            default -> Texts.send(sender, "&7add, remove, list, code");
        }
    }

    private void checks(CommandSender sender, String[] args, String label) {
        if (!admin(sender)) return;
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            CheckInfo.Category filter = args.length >= 3 ? CheckInfo.Category.byName(args[2]) : null;
            for (CheckInfo info : CheckCatalog.byCategory(filter)) {
                boolean on = plugin.checks().enabled(info.id());
                Texts.send(sender, (on ? "&a" : "&c") + info.id() + " &8" + info.category().title()
                        + (info.experimental() ? " &8exp" : "") + " &7" + info.description());
            }
            return;
        }
        if (args[1].equalsIgnoreCase("stable")) {
            plugin.checks().setStableOnly();
            Texts.send(sender, "&aВключены только стабильные чеки.");
            return;
        }
        if (args.length < 3) {
            Texts.send(sender, "&7/" + label + " checks <имя> <on|off|toggle> &8· &7list &8· &7stable");
            return;
        }
        String id = CheckCatalog.resolve(args[1]);
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "on" -> plugin.checks().set(id, true);
            case "off" -> plugin.checks().set(id, false);
            case "toggle" -> plugin.checks().toggle(id);
            default -> {
                Texts.send(sender, "&7on, off, toggle");
                return;
            }
        }
        Texts.send(sender, "&7" + id + ": " + (plugin.checks().enabled(id) ? "&aON" : "&cOFF"));
    }

    private void freeze(CommandSender sender, String[] args, String label) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Texts.send(sender, "&7/" + label + " freeze <ник> [on|off]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found");
            return;
        }
        boolean on = args.length >= 3 ? args[2].equalsIgnoreCase("on") : !plugin.freeze().isFrozen(target.getUniqueId());
        if (args.length >= 3 && args[2].equalsIgnoreCase("off")) on = false;
        plugin.freeze().set(target.getUniqueId(), on);
        Texts.send(sender, "&7Freeze &f" + target.getName() + "&7: " + (on ? "&aON" : "&cOFF"));
    }

    private void reload(CommandSender sender) {
        if (!admin(sender)) return;
        plugin.reloadAll();
        plugin.messages().send(sender, "reloaded");
    }

    private void history(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length >= 2) {
            UUID id = resolve(args[1]);
            if (id == null) {
                plugin.messages().send(sender, "player-not-found");
                return;
            }
            for (FlagRecord record : plugin.history().recent(id, 15)) printFlag(sender, record);
            return;
        }
        for (FlagRecord record : plugin.history().recent(15)) printFlag(sender, record);
    }

    private void top(CommandSender sender) {
        if (!admin(sender)) return;
        Bukkit.getOnlinePlayers().stream()
                .sorted((a, b) -> Double.compare(plugin.violations().total(b.getUniqueId()), plugin.violations().total(a.getUniqueId())))
                .limit(10)
                .forEach(p -> Texts.send(sender, "&f" + p.getName() + " &7" + fmt(plugin.violations().total(p.getUniqueId()))
                        + " &8ping " + Compat.ping(p)));
    }

    private void info(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) {
            Texts.send(sender, "&7/ag info <ник>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found");
            return;
        }
        var data = plugin.data().get(target);
        Texts.send(sender, "&f" + target.getName() + " &8" + target.getUniqueId());
        Texts.send(sender, "&7мир &f" + target.getWorld().getName() + " &7гм &f" + target.getGameMode().name().toLowerCase(Locale.ROOT)
                + " &7пинг &f" + Compat.ping(target) + " &7бренд &f" + (data.clientBrand == null || data.clientBrand.isBlank() ? "?" : data.clientBrand));
        Texts.send(sender, "&7VL &f" + fmt(plugin.violations().total(target.getUniqueId()))
                + " &7freeze &f" + plugin.freeze().isFrozen(target.getUniqueId())
                + " &7bypass &f" + plugin.bypass().isBypassed(target.getUniqueId()));
        Texts.send(sender, String.format(Locale.US, "&7h=%.3f dy=%.3f air=%d ground=%s bps=%.2f",
                data.lastH, data.lastDy, data.airTicks, data.lastServerGround, data.lastBps));
    }

    private void watch(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-player");
            return;
        }
        if (args.length < 2) {
            plugin.watch().clear(player.getUniqueId());
            Texts.send(sender, "&7Наблюдение выключено.");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found");
            return;
        }
        boolean on = plugin.watch().toggle(player, target);
        Texts.send(sender, on ? "&aСмотришь &f" + target.getName() : "&7Наблюдение выключено.");
    }

    private void verbose(CommandSender sender) {
        if (!admin(sender)) return;
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "only-player");
            return;
        }
        Texts.send(sender, "&7Verbose: " + (plugin.alerts().toggleVerbose(player) ? "&aON" : "&cOFF"));
    }

    private void tp(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (!(sender instanceof Player player) || args.length < 2) return;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found");
            return;
        }
        player.teleport(target.getLocation());
        Texts.send(sender, "&7Телепорт к &f" + target.getName());
    }

    private void kick(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 2) return;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found");
            return;
        }
        String reason = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "AquaGuard";
        target.kickPlayer(reason);
        Texts.send(sender, "&eКикнут &f" + target.getName());
    }

    private void flag(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length < 3) {
            Texts.send(sender, "&7/ag flag <ник> <чек> [vl]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found");
            return;
        }
        double amount = args.length >= 4 ? parseDouble(args[3], 1) : 1;
        plugin.flagger().flag(target, CheckCatalog.resolve(args[2]), amount, "manual by " + sender.getName(), "none");
        Texts.send(sender, "&eТестовый флаг отправлен.");
    }

    private void stats(CommandSender sender) {
        if (!admin(sender)) return;
        Texts.send(sender, "&7Флагов с запуска: &f" + plugin.stats().totalFlags()
                + " &7онлайн &f" + Bukkit.getOnlinePlayers().size()
                + " &7frozen &f" + plugin.freeze().onlineFrozen());
        plugin.stats().snapshot().entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Long> e) -> e.getValue()).reversed())
                .limit(8)
                .forEach(e -> Texts.send(sender, "&8 - &f" + e.getKey() + " &7" + e.getValue()));
    }

    private void webhook(CommandSender sender) {
        if (!admin(sender)) return;
        if (!plugin.webhook().enabled()) {
            Texts.send(sender, "&7Вебхук выключен. Укажи webhook.url и webhook.enabled: true");
            return;
        }
        plugin.webhook().test(sender instanceof Player player ? player.getUniqueId() : null);
        Texts.send(sender, "&aТестовый вебхук отправлен.");
    }

    private void settings(CommandSender sender, String[] args) {
        if (!admin(sender)) return;
        if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
            plugin.settings().clear();
            Texts.send(sender, "&eПереключатели сброшены к config.yml.");
            return;
        }
        Texts.send(sender, "&7/ag settings clear &8— &7убрать overrides из settings.yml");
    }

    private void printFlag(CommandSender sender, FlagRecord record) {
        Texts.send(sender, "&c" + record.check() + " &f" + record.name() + " &7VL " + fmt(record.vl())
                + " &8" + record.debug());
    }

    private boolean admin(CommandSender sender) {
        if (plugin.owner().isOwner(sender)) return true;
        plugin.messages().send(sender, "no-permission");
        return false;
    }

    private UUID resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.isOnline() || offline.hasPlayedBefore()) return offline.getUniqueId();
        return null;
    }

    private static long parseLong(String raw, long def) {
        try { return Long.parseLong(raw); } catch (NumberFormatException ex) { return def; }
    }

    private static double parseDouble(String raw, double def) {
        try { return Double.parseDouble(raw); } catch (NumberFormatException ex) { return def; }
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }
}
