package dev.aquaguard.core;

import dev.aquaguard.AquaGuard;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

/**
 * Живые переключатели (setback, режим наказаний). Не трогают config.yml.
 */
public final class RuntimeSettings {
    private final AquaGuard plugin;
    private final File file;
    private Boolean setback;
    private String penaltiesMode;

    public RuntimeSettings(AquaGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "settings.yml");
        load();
    }

    public boolean setbackEnabled() {
        if (setback != null) return setback;
        return plugin.getConfig().getBoolean("setback.enabled", true);
    }

    public void setSetbackEnabled(boolean on) {
        setback = on;
        save();
    }

    public String penaltiesMode() {
        if (penaltiesMode != null && !penaltiesMode.isBlank()) return penaltiesMode;
        return plugin.getConfig().getString("penalties.mode", "simulate");
    }

    public void setPenaltiesMode(String mode) {
        penaltiesMode = mode;
        save();
    }

    public void clear() {
        setback = null;
        penaltiesMode = null;
        if (file.exists() && !file.delete()) plugin.getLogger().warning("Не удалось удалить settings.yml");
    }

    public void reload() {
        setback = null;
        penaltiesMode = null;
        load();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        if (yml.contains("setback")) setback = yml.getBoolean("setback");
        if (yml.contains("penalties-mode")) penaltiesMode = yml.getString("penalties-mode");
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        if (setback != null) yml.set("setback", setback);
        if (penaltiesMode != null) yml.set("penalties-mode", penaltiesMode);
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("settings.yml: " + ex.getMessage());
        }
    }
}
