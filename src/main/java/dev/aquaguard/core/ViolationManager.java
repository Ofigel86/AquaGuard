package dev.aquaguard.core;

import dev.aquaguard.AquaGuard;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VL + имена. Старый violations.yml (uuid.check: number) читается и пишется в новом виде.
 */
public final class ViolationManager {
    private final AquaGuard plugin;
    private final VlLedger ledger = new VlLedger();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFlag = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> flagCount = new ConcurrentHashMap<>();
    private final File file;

    public ViolationManager(AquaGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "violations.yml");
    }

    public double add(UUID uuid, String name, String check, double amount) {
        if (name != null) names.put(uuid, name);
        lastFlag.put(uuid, System.currentTimeMillis());
        flagCount.merge(uuid, 1, Integer::sum);
        return ledger.add(uuid, check, amount);
    }

    public double get(UUID uuid, String check) {
        return ledger.get(uuid, check);
    }

    public Map<String, Double> get(UUID uuid) {
        return ledger.view(uuid);
    }

    public double total(UUID uuid) {
        return ledger.total(uuid);
    }

    public void reset(UUID uuid) {
        ledger.reset(uuid);
        flagCount.remove(uuid);
    }

    public void reset(UUID uuid, String check) {
        ledger.reset(uuid, check);
    }

    public void decayAll(double amount) {
        ledger.decayAll(amount);
    }

    public String name(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            names.put(uuid, online.getName());
            return online.getName();
        }
        String cached = names.get(uuid);
        if (cached != null) return cached;
        String offline = Bukkit.getOfflinePlayer(uuid).getName();
        return offline != null ? offline : uuid.toString().substring(0, 8);
    }

    public long lastFlag(UUID uuid) {
        return lastFlag.getOrDefault(uuid, 0L);
    }

    public int flagCount(UUID uuid) {
        return flagCount.getOrDefault(uuid, 0);
    }

    public void maybeLegacyPunish(Player player, String check) {
        double threshold = 0;
        if (plugin.getConfig().contains("checks." + check + ".punish-threshold")) {
            threshold = plugin.getConfig().getDouble("checks." + check + ".punish-threshold", 0);
        } else if (plugin.getConfig().contains("checks.AutoTotem." + check + ".punish-threshold")) {
            threshold = plugin.getConfig().getDouble("checks.AutoTotem." + check + ".punish-threshold", 0);
        }
        if (threshold <= 0 || player == null) return;
        if (ledger.get(player.getUniqueId(), check) < threshold) return;
        String reason = plugin.getConfig().getString("legacy-punish.reason",
                "Kicked by AquaGuard (" + check + ")");
        reason = reason.replace("%check%", check).replace("%player%", player.getName());
        player.kickPlayer(reason);
    }

    public void load() {
        if (!file.exists()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            for (String uid : yml.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(uid);
                } catch (IllegalArgumentException ex) {
                    continue;
                }
                ConfigurationSection section = yml.getConfigurationSection(uid);
                if (section == null) continue;
                String name = section.getString("name");
                if (name != null) names.put(uuid, name);
                lastFlag.put(uuid, section.getLong("last-flag", 0));
                flagCount.put(uuid, section.getInt("flag-count", 0));
                Map<String, Double> values = new HashMap<>();
                ConfigurationSection checks = section.getConfigurationSection("checks");
                if (checks != null) {
                    for (String check : checks.getKeys(false)) {
                        values.put(check, checks.getDouble(check));
                    }
                } else {
                    for (String key : section.getKeys(false)) {
                        if (section.isDouble(key) || section.isInt(key)) values.put(key, section.getDouble(key));
                    }
                }
                ledger.load(uuid, values);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("violations.yml: " + ex.getMessage());
        }
    }

    public void save() {
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs() && !file.getParentFile().exists()) {
                plugin.getLogger().warning("Не удалось создать папку данных");
            }
            YamlConfiguration yml = new YamlConfiguration();
            for (Map.Entry<UUID, Map<String, Double>> e : ledger.snapshot().entrySet()) {
                String base = e.getKey().toString();
                String name = names.get(e.getKey());
                if (name != null) yml.set(base + ".name", name);
                yml.set(base + ".last-flag", lastFlag.getOrDefault(e.getKey(), 0L));
                yml.set(base + ".flag-count", flagCount.getOrDefault(e.getKey(), 0));
                for (Map.Entry<String, Double> c : e.getValue().entrySet()) {
                    yml.set(base + ".checks." + c.getKey(), c.getValue());
                }
            }
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить violations.yml: " + ex.getMessage());
        }
    }
}
