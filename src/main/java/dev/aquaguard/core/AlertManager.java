package dev.aquaguard.core;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AlertManager {
    private final AquaGuard plugin;
    private final Set<UUID> muted = ConcurrentHashMap.newKeySet();
    private final Set<UUID> verbose = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> lastAlert = new ConcurrentHashMap<>();
    private final Map<String, Integer> suppressed = new ConcurrentHashMap<>();
    private final File file;

    public AlertManager(AquaGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "alerts.yml");
        load();
    }

    public boolean receives(Player player) {
        if (player == null) return false;
        String perm = plugin.getConfig().getString("alerts-permission", "ag.alerts");
        return player.hasPermission(perm) && !muted.contains(player.getUniqueId());
    }

    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        boolean nowMuted = !muted.contains(id);
        if (nowMuted) muted.add(id);
        else muted.remove(id);
        save();
        return !nowMuted;
    }

    public boolean alertsOn(Player player) {
        return !muted.contains(player.getUniqueId());
    }

    public boolean toggleVerbose(Player player) {
        UUID id = player.getUniqueId();
        if (verbose.contains(id)) {
            verbose.remove(id);
            return false;
        }
        verbose.add(id);
        return true;
    }

    public void broadcast(FlagRecord record) {
        long cooldown = plugin.getConfig().getLong("alerts.cooldown-ms", 1500);
        String key = record.uuid() + ":" + record.check();
        long now = System.currentTimeMillis();
        Long prev = lastAlert.get(key);
        if (prev != null && now - prev < cooldown) {
            suppressed.merge(key, 1, Integer::sum);
            return;
        }
        int extra = suppressed.getOrDefault(key, 0);
        suppressed.remove(key);
        lastAlert.put(key, now);

        String plain = String.format(Locale.US,
                "[AquaGuard] %s flagged %s VL=%.1f total=%.1f ping=%d %s%s",
                record.name(), record.check(), record.vl(), record.total(), record.ping(),
                record.debug(), extra > 0 ? " x" + (extra + 1) : "");
        if (plugin.getConfig().getBoolean("alerts.console", true)) {
            plugin.getLogger().info(plain);
        }
        Component component = component(record, extra);
        String perm = plugin.getConfig().getString("alerts-permission", "ag.alerts");
        boolean sound = plugin.getConfig().getBoolean("alerts.sound", true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission(perm) || muted.contains(player.getUniqueId())) continue;
            player.sendMessage(component);
            if (verbose.contains(player.getUniqueId())) {
                player.sendMessage(Texts.color("&8debug &7" + record.world() + " &8" + record.debug()));
            }
            if (sound) {
                try {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.4f, 1.6f);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private Component component(FlagRecord record, int extra) {
        String vl = String.format(Locale.US, "%.1f", record.vl());
        String total = String.format(Locale.US, "%.1f", record.total());
        Component hover = Component.text("VL " + vl + " / total " + total + "\n", NamedTextColor.GRAY)
                .append(Component.text(record.world() + " · ping " + record.ping() + "\n", NamedTextColor.DARK_GRAY))
                .append(Component.text(record.debug() + "\n", NamedTextColor.WHITE))
                .append(Component.text("Клик — телепорт", NamedTextColor.AQUA));
        Component msg = Component.text("Aqua", NamedTextColor.DARK_AQUA, TextDecoration.BOLD)
                .append(Component.text("Guard", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false))
                .append(Component.text(record.name(), NamedTextColor.WHITE))
                .append(Component.text(" flagged ", NamedTextColor.GRAY))
                .append(Component.text(record.check(), NamedTextColor.RED))
                .append(Component.text(" VL ", NamedTextColor.DARK_GRAY))
                .append(Component.text(vl, NamedTextColor.GOLD))
                .append(Component.text(" ping ", NamedTextColor.DARK_GRAY))
                .append(Component.text(Integer.toString(record.ping()), NamedTextColor.YELLOW))
                .append(Component.text(" " + trim(record.debug(), 72), NamedTextColor.DARK_GRAY));
        if (extra > 0) {
            msg = msg.append(Component.text(" x" + (extra + 1), NamedTextColor.DARK_RED));
        }
        return msg.hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.runCommand("/ag tp " + record.name()));
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String id : yml.getStringList("muted")) {
            try {
                muted.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("muted", muted.stream().map(UUID::toString).sorted().toList());
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("alerts.yml: " + ex.getMessage());
        }
    }
}
