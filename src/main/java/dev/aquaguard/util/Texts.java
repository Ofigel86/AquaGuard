package dev.aquaguard.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public final class Texts {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private Texts() {}

    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static void send(CommandSender sender, String message) {
        if (sender == null || message == null || message.isEmpty()) return;
        sender.sendMessage(color(message));
    }

    public static Component legacy(String message) {
        return LEGACY.deserialize(color(message));
    }

    public static String strip(String message) {
        return ChatColor.stripColor(color(message == null ? "" : message));
    }
}
