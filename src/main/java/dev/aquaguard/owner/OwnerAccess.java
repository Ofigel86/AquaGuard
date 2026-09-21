package dev.aquaguard.owner;

import dev.aquaguard.AquaGuard;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Владелец без OP. Раньше UUID писался в конфиг, но команда /ag была закрыта permission ag.admin,
 * поэтому автопривязка не давала реального доступа. Теперь владельцу выдаётся attachment.
 */
public final class OwnerAccess implements Listener {
    private final AquaGuard plugin;
    private final Set<UUID> owners = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public OwnerAccess(AquaGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        owners.clear();
        for (String raw : plugin.getConfig().getStringList("owner.uuids")) {
            try {
                owners.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) apply(player);
    }

    public boolean isOwner(CommandSender sender) {
        if (sender == null) return false;
        if (!(sender instanceof Player player)) return true;
        return sender.hasPermission("ag.admin") || owners.contains(player.getUniqueId());
    }

    public boolean isListedOwner(Player player) {
        return player != null && owners.contains(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        tryClaim(event.getPlayer());
        apply(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = attachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            try {
                event.getPlayer().removeAttachment(attachment);
            } catch (Throwable ignored) {
            }
        }
    }

    private void tryClaim(Player player) {
        if (!plugin.getConfig().getBoolean("owner.auto-claim.enabled", false)) return;
        String target = plugin.getConfig().getString("owner.auto-claim.name", "");
        if (target == null || target.isBlank() || target.equalsIgnoreCase("ТвойНик")) return;
        if (!player.getName().equalsIgnoreCase(target)) return;
        boolean requireOnline = plugin.getConfig().getBoolean("owner.auto-claim.require-online-mode", true);
        if (requireOnline && !plugin.getServer().getOnlineMode()) {
            plugin.messages().send(player, "owner-offline-mode");
            return;
        }
        List<String> uuids = plugin.getConfig().getStringList("owner.uuids");
        if (uuids.contains(player.getUniqueId().toString())) return;
        uuids.add(player.getUniqueId().toString());
        plugin.getConfig().set("owner.uuids", uuids);
        plugin.getConfig().set("owner.auto-claim.enabled", false);
        plugin.saveConfig();
        owners.add(player.getUniqueId());
        plugin.messages().send(player, "owner-claimed");
        plugin.getLogger().info("Owner claimed by " + player.getName() + " (" + player.getUniqueId() + ")");
    }

    private void apply(Player player) {
        if (!owners.contains(player.getUniqueId())) return;
        PermissionAttachment attachment = attachments.computeIfAbsent(player.getUniqueId(),
                id -> player.addAttachment(plugin));
        attachment.setPermission("ag.admin", true);
        attachment.setPermission("ag.alerts", true);
        attachment.setPermission("ag.gui", true);
    }
}
