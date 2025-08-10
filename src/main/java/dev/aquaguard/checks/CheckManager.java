package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    public boolean enabled(String check) { return map.getOrDefault(check, defaultEnabled); }
    public void toggle(String check) { set(check, !enabled(check)); }

    public void set(String check, boolean on) {
        map.put(check, on);
        plugin.getConfig().set("checks-toggles.map." + check, on);
        plugin.saveConfig();
    }
}
