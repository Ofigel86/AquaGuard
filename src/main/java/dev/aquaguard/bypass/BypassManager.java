package dev.aquaguard.bypass;

import dev.aquaguard.AquaGuard;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BypassManager {
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final AquaGuard plugin;
    private final Map<UUID, Long> until = new ConcurrentHashMap<>();
    private final Set<String> codes = Collections.synchronizedSet(new HashSet<>());
    private final File file;
    private final SecureRandom random = new SecureRandom();

    public BypassManager(AquaGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bypass.yml");
        load();
    }

    public boolean isBypassed(UUID id) {
        Long exp = until.get(id);
        if (exp == null) return false;
        if (exp <= 0) return true;
        if (System.currentTimeMillis() > exp) {
            until.remove(id);
            save();
            return false;
        }
        return true;
    }

    public void addBypass(UUID id, long minutes) {
        long exp = minutes <= 0 ? 0L : System.currentTimeMillis() + minutes * 60_000L;
        until.put(id, exp);
        save();
    }

    public void removeBypass(UUID id) {
        until.remove(id);
        save();
    }

    public Map<UUID, Long> list() {
        return Map.copyOf(until);
    }

    public boolean claimCode(UUID id, String code) {
        if (code == null || code.isBlank()) return false;
        String normalized = code.trim().toUpperCase(java.util.Locale.ROOT);
        synchronized (codes) {
            if (!codes.remove(normalized)) return false;
        }
        int minutes = plugin.getConfig().getInt("bypass.default-expire-mins", 1440);
        addBypass(id, minutes);
        save();
        return true;
    }

    public String createCode() {
        StringBuilder builder = new StringBuilder(4 + 4 + 4 + 2);
        for (int group = 0; group < 3; group++) {
            if (group > 0) builder.append('-');
            for (int i = 0; i < 4; i++) builder.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        String code = builder.toString();
        synchronized (codes) {
            codes.add(code);
        }
        save();
        return code;
    }

    public void reloadCodes() {
        load();
    }

    private void load() {
        until.clear();
        codes.clear();
        if (file.exists()) {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            var players = yml.getConfigurationSection("players");
            if (players != null) {
                for (String uid : players.getKeys(false)) {
                    try {
                        until.put(UUID.fromString(uid), yml.getLong("players." + uid + ".until", 0));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            } else {
                for (String uid : yml.getKeys(false)) {
                    if (uid.equals("codes")) continue;
                    try {
                        until.put(UUID.fromString(uid), yml.getLong(uid + ".until", yml.getLong(uid, 0)));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            codes.addAll(yml.getStringList("codes"));
        }
        if (codes.isEmpty()) codes.addAll(plugin.getConfig().getStringList("bypass.codes"));
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : until.entrySet()) {
            yml.set("players." + e.getKey() + ".until", e.getValue());
        }
        yml.set("codes", new ArrayList<>(codes));
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("bypass.yml: " + ex.getMessage());
        }
    }
}
