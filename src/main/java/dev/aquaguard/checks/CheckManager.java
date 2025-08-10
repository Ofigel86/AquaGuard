package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Управление включением/выключением чеков.
 * - Читает/пишет состояние в config.yml -> checks-toggles.map
 * - Если чек не указан в map, берётся значение checks-toggles.enabled-by-default (по умолчанию true)
 */
public class CheckManager {
    private final AquaGuard plugin;
    private final boolean defaultEnabled;
    private final Map<String, Boolean> map = new ConcurrentHashMap<>();

    public CheckManager(AquaGuard plugin) {
        this.plugin = plugin;
        this.defaultEnabled = plugin.getConfig().getBoolean("checks-toggles.enabled-by-default", true);

        var sec = plugin.getConfig().getConfigurationSection("checks-toggles.map");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                map.put(k, plugin.getConfig().getBoolean("checks-toggles.map." + k, defaultEnabled));
            }
        }
    }

    /**
     * Включён ли чек.
     */
    public boolean enabled(String check) {
        return map.getOrDefault(check, defaultEnabled);
    }

    /**
     * Переключить чек.
     */
    public void toggle(String check) {
        set(check, !enabled(check));
    }

    /**
     * Установить явное состояние чека и записать в конфиг.
     */
    public void set(String check, boolean on) {
        map.put(check, on);
        plugin.getConfig().set("checks-toggles.map." + check, on);
        plugin.saveConfig();
    }
}
