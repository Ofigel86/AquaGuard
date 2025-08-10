package dev.aquaguard.bypass;

import dev.aquaguard.AquaGuard;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Персональный обход античита на аккаунт (с истечением).
 * - bypass.yml: UUID -> until (ms). 0 = бессрочно.
 * - Коды из config.yml (bypass.codes): одноразовые, активируют обход на bypass.default-expire-mins минут.
 *
 * Конфиг:
 * bypass:
 *   default-expire-mins: 1440
 *   codes: []
 */
public class BypassManager {
    private final AquaGuard plugin;
    private final Map<UUID, Long> bypassUntil = new ConcurrentHashMap<>();
    private final Set<String> codes = Collections.synchronizedSet(new HashSet<>());

    private final File file;
    private YamlConfiguration yml;

    public BypassManager(AquaGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bypass.yml");
        this.yml = new YamlConfiguration();
        load();

        // одноразовые коды из конфига
        codes.addAll(plugin.getConfig().getStringList("bypass.codes"));
    }

    /**
     * Активен ли обход у игрока.
     */
    public boolean isBypassed(UUID id) {
        Long until = bypassUntil.get(id);
        if (until == null) return false;
        if (until <= 0) return true;      // бессрочно
        if (System.currentTimeMillis() > until) {
            bypassUntil.remove(id);
            save();
            return false;
        }
        return true;
    }

    /**
     * Выдать обход на minutes минут (0 = бессрочно).
     */
    public void addBypass(UUID id, long minutes) {
        long until = (minutes <= 0) ? 0L : (System.currentTimeMillis() + minutes * 60_000L);
        bypassUntil.put(id, until);
        save();
    }

    /**
     * Снять обход.
     */
    public void removeBypass(UUID id) {
        bypassUntil.remove(id);
        save();
    }

    /**
     * Список обходов (только чтение).
     */
    public Map<UUID, Long> list() {
        return Collections.unmodifiableMap(bypassUntil);
    }

    /**
     * Активировать обход по одноразовому коду из config.yml (bypass.codes).
     * Возвращает true, если код действителен и был применён.
     */
    public boolean claimCode(UUID id, String code) {
        if (code == null || code.isEmpty()) return false;
        synchronized (codes) {
            if (!codes.contains(code)) return false;
            codes.remove(code);
        }
        int def = plugin.getConfig().getInt("bypass.default-expire-mins", 1440);
        addBypass(id, def);

        // Удалим код из конфигурации (чтобы не использовать повторно)
        List<String> cfg = new ArrayList<>(plugin.getConfig().getStringList("bypass.codes"));
        cfg.remove(code);
        plugin.getConfig().set("bypass.codes", cfg);
        plugin.saveConfig();
        return true;
    }

    // ===== persist =====

    private void load() {
        if (!file.exists()) return;
        try {
            yml.load(file);
            for (String uid : yml.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(uid);
                    long until = yml.getLong(uid + ".until", 0L);
                    bypassUntil.put(id, until);
                } catch (Exception ignored) {}
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Bypass load failed: " + ex.getMessage());
        }
    }

    private void save() {
        try {
            yml = new YamlConfiguration();
            for (Map.Entry<UUID, Long> e : bypassUntil.entrySet()) {
                yml.set(e.getKey().toString() + ".until", e.getValue());
            }
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Bypass save failed: " + ex.getMessage());
        }
    }
}
