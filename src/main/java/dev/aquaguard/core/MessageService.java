package dev.aquaguard.core;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.util.Texts;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class MessageService {
    private final AquaGuard plugin;
    private YamlConfiguration messages = new YamlConfiguration();
    private String prefix = "&3Aqua&bGuard &8» &7";

    public MessageService(AquaGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        YamlConfiguration defaults = new YamlConfiguration();
        try (var in = plugin.getResource("messages.yml")) {
            if (in != null) {
                defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("messages.yml: " + ex.getMessage());
        }
        messages = defaults;
        java.io.File file = new java.io.File(plugin.getDataFolder(), "messages.yml");
        if (file.exists()) {
            try {
                YamlConfiguration fileMessages = YamlConfiguration.loadConfiguration(file);
                fileMessages.setDefaults(defaults);
                fileMessages.options().copyDefaults(true);
                messages = fileMessages;
            } catch (Exception ex) {
                plugin.getLogger().warning("Не удалось прочитать messages.yml: " + ex.getMessage());
            }
        }
        prefix = messages.getString("prefix", prefix);
    }

    public String prefix() {
        return Texts.color(prefix);
    }

    public String raw(String key) {
        String value = messages.getString(key);
        if (value == null || value.isEmpty()) value = fallback(key);
        return value.replace("%prefix%", prefix);
    }

    public String format(String key, Map<String, String> vars) {
        String text = raw(key);
        if (vars != null) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                text = text.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
            }
        }
        return Texts.color(text);
    }

    public String format(String key, String... pairs) {
        Map<String, String> vars = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) vars.put(pairs[i], pairs[i + 1]);
        return format(key, vars);
    }

    public void send(CommandSender sender, String key, String... pairs) {
        Texts.send(sender, format(key, pairs));
    }

    private static String fallback(String key) {
        return switch (key) {
            case "no-permission" -> "%prefix%&cНет прав.";
            case "only-player" -> "%prefix%Только в игре.";
            case "player-not-found" -> "%prefix%Игрок не найден.";
            case "reloaded" -> "%prefix%Конфиг, сообщения и тумблеры перечитаны.";
            case "frozen-bar" -> "&bAquaGuard &7· ты заморожен. Напиши в чат.";
            default -> "%prefix%" + key;
        };
    }
}
