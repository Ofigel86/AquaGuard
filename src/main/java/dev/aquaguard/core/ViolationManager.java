package dev.aquaguard.core;

import dev.aquaguard.AquaGuard;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранение VL (по каждому чеку), алерты и опциональный автокик по порогу.
 * - violations.yml: персистентность VL между рестартами
 * - add(uuid, check, amount, debug): начислить VL и отправить алерт
 * - maybePunish(uuid, check): кик по checks.<Check>.punish-threshold (если > 0)
 */
public class ViolationManager {
    private final AquaGuard plugin;
    private final Map<UUID, Map<String, Double>> vls = new ConcurrentHashMap<>();

    private final File file;
    private YamlConfiguration yml = new YamlConfiguration();

    public ViolationManager(AquaGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "violations.yml");
    }

    /**
     * Начислить VL по конкретной проверке и отправить алерт стаффу.
     */
    public void add(UUID uuid, String check, double amount, String debug) {
        Map<String, Double> m = vls.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        double newVl = m.getOrDefault(check, 0.0) + amount;
        m.put(check, newVl);

        // Алерт по пермишену
        String perm = plugin.getConfig().getString("alerts-permission", "ag.alerts");
        String name = resolveName(uuid);
        String msg = "AquaGuard | " + name + " flagged " + check
                + " VL=" + String.format(Locale.US, "%.1f", newVl)
                + " | " + debug;

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.hasPermission(perm))
                .forEach(p -> p.sendMessage(msg));
    }

    /**
     * Автокик по порогу для конкретного чека (если задан в конфиге).
     * checks.<Check>.punish-threshold > 0 — включён.
     */
    public void maybePunish(UUID uuid, String check) {
        double threshold = plugin.getConfig().getDouble("checks." + check + ".punish-threshold", 0.0);
        if (threshold <= 0) return;

        double cur = vls.getOrDefault(uuid, Collections.emptyMap()).getOrDefault(check, 0.0);
        if (cur >= threshold) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.kickPlayer("Kicked by AquaGuard (" + check + ")");
            }
        }
    }

    /**
     * Получить VL по всем чекам игрока (неизменяемая карта).
     */
    public Map<String, Double> get(UUID uuid) {
        return vls.getOrDefault(uuid, Collections.emptyMap());
    }

    /**
     * Суммарный VL игрока (по всем чекам).
     */
    public double total(UUID uuid) {
        Map<String, Double> m = vls.get(uuid);
        if (m == null) return 0.0;
        double s = 0.0;
        for (double v : m.values()) s += v;
        return s;
    }

    /**
     * Распад VL (minus amount) раз в N минут (настраивается в конфиге).
     */
    public void decayAll(double amount) {
        if (amount <= 0) return;
        for (Map<String, Double> m : vls.values()) {
            // проходим копией ключей, чтобы безопасно менять карту
            for (Map.Entry<String, Double> e : m.entrySet().toArray(new Map.Entry[0])) {
                double val = Math.max(0.0, e.getValue() - amount);
                if (val == 0.0) m.remove(e.getKey());
                else e.setValue(val);
            }
        }
    }

    /**
     * Загрузить violations.yml (персист).
     */
    public void load() {
        if (!file.exists()) return;
        try {
            yml.load(file);
            for (String uid : yml.getKeys(false)) {
                UUID uuid = UUID.fromString(uid);
                Map<String, Double> map = new ConcurrentHashMap<>();
                if (yml.isConfigurationSection(uid)) {
                    for (String check : Objects.requireNonNull(yml.getConfigurationSection(uid)).getKeys(false)) {
                        map.put(check, yml.getDouble(uid + "." + check));
                    }
                }
                vls.put(uuid, map);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to load violations.yml: " + ex.getMessage());
        }
    }

    /**
     * Сохранить violations.yml (персист).
     */
    public void save() {
        try {
            yml = new YamlConfiguration();
            for (Map.Entry<UUID, Map<String, Double>> e : vls.entrySet()) {
                for (Map.Entry<String, Double> c : e.getValue().entrySet()) {
                    yml.set(e.getKey().toString() + "." + c.getKey(), c.getValue());
                }
            }
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save violations.yml: " + ex.getMessage());
        }
    }

    // ========== helpers ==========

    private String resolveName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString();
    }
}
