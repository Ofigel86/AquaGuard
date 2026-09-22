package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.FlagRecord;
import dev.aquaguard.core.PlayerData;
import dev.aquaguard.util.Compat;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class Flagger {
    private final AquaGuard plugin;

    public Flagger(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public void flag(Player player, String check, double amount, String debug, String setbackMode) {
        if (player == null || !plugin.checks().enabled(check)) return;
        if (plugin.exemption().bypass(player)) return;
        String id = CheckCatalog.resolve(check);
        double add = amount > 0 ? amount : plugin.getConfig().getDouble("checks." + id + ".add-vl", 1.0);
        double vl = plugin.violations().add(player.getUniqueId(), player.getName(), id, add);
        double total = plugin.violations().total(player.getUniqueId());
        int ping = Compat.ping(player);
        String world = player.getWorld().getName();
        String detail = debug == null ? "" : debug;
        FlagRecord record = new FlagRecord(System.currentTimeMillis(), player.getUniqueId(), player.getName(),
                id, vl, total, ping, world, detail);
        plugin.history().add(record);
        plugin.stats().increment(id);
        plugin.alerts().broadcast(record);
        plugin.violationLog().write(record);
        plugin.webhook().maybeSend(record);
        plugin.punishments().onFlag(player, id, total);
        plugin.violations().maybeLegacyPunish(player, id);
        if (setbackMode == null) setbackMode = plugin.getConfig().getString("checks." + id + ".setback", "none");
        PlayerData data = plugin.data().get(player);
        plugin.setback().request(data, setbackMode);
        if (plugin.getConfig().getBoolean("verbose-console", false)) {
            plugin.getLogger().info(String.format(Locale.US, "%s %s +%.1f -> %.1f | %s",
                    player.getName(), id, add, vl, detail));
        }
    }

    public double vl(String check, double def) {
        if (plugin.getConfig().contains("checks." + check + ".add-vl")) {
            return plugin.getConfig().getDouble("checks." + check + ".add-vl", def);
        }
        if (plugin.getConfig().contains("checks.AutoTotem." + check + ".add-vl")) {
            return plugin.getConfig().getDouble("checks.AutoTotem." + check + ".add-vl", def);
        }
        return def;
    }

    public String setback(String check, String def) {
        return plugin.getConfig().getString("checks." + check + ".setback", def);
    }
}
