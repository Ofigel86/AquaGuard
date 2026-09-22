package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Тумблеры чеков живут в toggles.yml, чтобы /ag checks не переписывал config.yml и не съедал комментарии.
 * Экспериментальные чеки выключены, пока их явно не включат.
 */
public final class CheckManager {
    private final AquaGuard plugin;
    private final File file;
    private final Map<String, Boolean> map = new LinkedHashMap<>();
    private boolean defaultEnabled = true;

    public CheckManager(AquaGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "toggles.yml");
        reload();
    }

    public void reload() {
        defaultEnabled = plugin.getConfig().getBoolean("checks-toggles.enabled-by-default", true);
        map.clear();
        if (!file.exists()) importLegacy();
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            if (yml.contains("enabled-by-default")) {
                defaultEnabled = yml.getBoolean("enabled-by-default", defaultEnabled);
            }
            var section = yml.getConfigurationSection("map");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    map.put(CheckCatalog.resolve(key), section.getBoolean(key));
                }
            }
        }
    }

    private void importLegacy() {
        var section = plugin.getConfig().getConfigurationSection("checks-toggles.map");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            map.put(CheckCatalog.resolve(key), plugin.getConfig().getBoolean("checks-toggles.map." + key, defaultEnabled));
        }
        if (!map.isEmpty()) save();
    }

    public boolean enabled(String check) {
        String id = CheckCatalog.resolve(check);
        Boolean explicit = map.get(id);
        if (explicit != null) return explicit;
        CheckInfo info = CheckCatalog.get(id);
        if (info != null && info.experimental()) return false;
        return defaultEnabled;
    }

    public void set(String check, boolean on) {
        map.put(CheckCatalog.resolve(check), on);
        save();
    }

    public void toggle(String check) {
        String id = CheckCatalog.resolve(check);
        set(id, !enabled(id));
    }

    public void setCategory(CheckInfo.Category category, boolean on) {
        for (CheckInfo info : CheckCatalog.byCategory(category)) set(info.id(), on);
    }

    public void setAll(boolean on) {
        for (CheckInfo info : CheckCatalog.all()) map.put(info.id(), on);
        save();
    }

    public void setStableOnly() {
        for (CheckInfo info : CheckCatalog.all()) map.put(info.id(), !info.experimental());
        save();
    }

    public int enabledCount() {
        int n = 0;
        for (CheckInfo info : CheckCatalog.all()) if (enabled(info.id())) n++;
        return n;
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("enabled-by-default", defaultEnabled);
        for (Map.Entry<String, Boolean> e : map.entrySet()) {
            yml.set("map." + e.getKey(), e.getValue());
        }
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("toggles.yml: " + ex.getMessage());
        }
    }
}
