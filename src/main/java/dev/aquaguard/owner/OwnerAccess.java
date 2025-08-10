package dev.aquaguard.owner;

import dev.aquaguard.AquaGuard;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Владелец (owner) без OP:
 * - Читает UUID из config.yml: owner.uuids: [ ... ]
 * - Автопривязка по нику при первом входе (owner.auto-claim.*), затем отключается.
 * - isOwner(sender): true для ag.admin, владельца или консоли.
 */
public class OwnerAccess implements Listener {
    private final AquaGuard plugin;
    private final Set<UUID> owners = new HashSet<>();

    public OwnerAccess(AquaGuard plugin) {
        this.plugin = plugin;
        loadOwners();
    }

    private void loadOwners() {
        owners.clear();
        List<String> list = plugin.getConfig().getStringList("owner.uuids");
        for (String s : list) {
            try { owners.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
    }

    /**
     * Проверка прав: ag.admin ИЛИ владелец (UUID в конфиге) ИЛИ консоль.
     */
    public boolean isOwner(CommandSender s) {
        if (s == null) return false;
        if (s.hasPermission("ag.admin")) return true;
        if (!(s instanceof Player p)) return true; // консоль
        return owners.contains(p.getUniqueId());
    }

    /**
     * Автопривязка по нику при первом входе:
     * owner.auto-claim.enabled: true
     * owner.auto-claim.name: "ТвойНик"
     * owner.auto-claim.require-online-mode: true/false
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("owner.auto-claim.enabled", false)) return;

        String target = plugin.getConfig().getString("owner.auto-claim.name", "");
        if (target == null || target.isEmpty()) return;
        if (!e.getPlayer().getName().equalsIgnoreCase(target)) return;

        boolean requireOnlineMode = plugin.getConfig().getBoolean("owner.auto-claim.require-online-mode", true);
        if (requireOnlineMode && !plugin.getServer().getOnlineMode()) {
            e.getPlayer().sendMessage("Автопривязка отключена: сервер offline-mode.");
            return;
        }

        UUID id = e.getPlayer().getUniqueId();
        List<String> uuids = plugin.getConfig().getStringList("owner.uuids");
        if (!uuids.contains(id.toString())) {
            uuids.add(id.toString());
            plugin.getConfig().set("owner.uuids", uuids);
            plugin.getConfig().set("owner.auto-claim.enabled", false);
            plugin.saveConfig();
            loadOwners();

            e.getPlayer().sendMessage("Ты привязан как владелец AquaGuard. Доступ к командам выдан.");
            plugin.getLogger().info("Owner auto-claimed by " + e.getPlayer().getName() + " (" + id + ")");
        }
    }

    /**
     * Перечитать список владельцев из конфига.
     */
    public void reload() {
        loadOwners();
    }
}
