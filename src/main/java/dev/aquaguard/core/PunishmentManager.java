package dev.aquaguard.core;

import dev.aquaguard.AquaGuard;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PunishmentManager {
    private final AquaGuard plugin;
    private final Map<UUID, Integer> appliedStep = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRun = new ConcurrentHashMap<>();
    private PunishmentLadder ladder = new PunishmentLadder(List.of());

    public PunishmentManager(AquaGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        List<PunishmentLadder.Step> steps = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("punishments.ladder");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                double min = section.getDouble(key + ".min-total-vl", section.getDouble(key + ".vl", 0));
                List<String> commands = section.getStringList(key + ".commands");
                if (!commands.isEmpty()) steps.add(new PunishmentLadder.Step(min, commands));
            }
        }
        if (steps.isEmpty()) {
            List<Map<?, ?>> list = plugin.getConfig().getMapList("punishments.ladder");
            for (Map<?, ?> map : list) {
                Object minObj = map.containsKey("min-total-vl") ? map.get("min-total-vl") : map.get("vl");
                double min = minObj instanceof Number n ? n.doubleValue() : 0;
                Object raw = map.get("commands");
                List<String> commands = new ArrayList<>();
                if (raw instanceof List<?> items) {
                    for (Object item : items) if (item != null) commands.add(item.toString());
                }
                if (!commands.isEmpty()) steps.add(new PunishmentLadder.Step(min, commands));
            }
        }
        ladder = new PunishmentLadder(steps);
    }

    public PunishmentLadder ladder() {
        return ladder;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("punishments.enabled", false);
    }

    public String mode() {
        return plugin.getConfig().getString("punishments.mode", "log").toLowerCase(Locale.ROOT);
    }

    public void onFlag(Player player, String check, double total) {
        if (!enabled() || player == null || ladder.size() == 0) return;
        int reached = ladder.highestReached(total);
        if (reached < 0) return;
        int prev = appliedStep.getOrDefault(player.getUniqueId(), -1);
        if (reached <= prev) return;
        long now = System.currentTimeMillis();
        long minInterval = plugin.getConfig().getLong("punishments.min-interval-ms", 4000);
        Long last = lastRun.get(player.getUniqueId());
        if (last != null && now - last < minInterval) return;
        appliedStep.put(player.getUniqueId(), reached);
        lastRun.put(player.getUniqueId(), now);
        PunishmentLadder.Step step = ladder.step(reached);
        if (step == null) return;
        String vl = String.format(Locale.US, "%.1f", total);
        String ping = Integer.toString(safePing(player));
        String world = player.getWorld().getName();
        for (String command : step.commands()) {
            String resolved = PunishmentLadder.applyPlaceholders(command, player.getName(), check, vl, ping, world);
            run(player, resolved);
        }
    }

    public void clear(UUID uuid) {
        appliedStep.remove(uuid);
        lastRun.remove(uuid);
    }

    private void run(Player player, String command) {
        if (command == null || command.isBlank()) return;
        String trimmed = command.trim();
        boolean execute = "execute".equals(mode());
        plugin.getLogger().info("Punishment " + mode() + ": " + trimmed);
        if (!execute) return;
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("kick ")) {
            String reason = trimmed.substring(5).replace(player.getName(), "").trim();
            if (reason.isEmpty()) reason = "AquaGuard";
            player.kickPlayer(reason);
            return;
        }
        String console = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), console));
    }

    private static int safePing(Player player) {
        try {
            return player.getPing();
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
