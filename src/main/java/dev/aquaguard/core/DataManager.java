package dev.aquaguard.core;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DataManager implements Listener {
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    public PlayerData get(Player player) {
        return data.computeIfAbsent(player.getUniqueId(), id -> new PlayerData());
    }

    public PlayerData peek(UUID id) {
        return data.get(id);
    }

    public void remove(UUID id) {
        data.remove(id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerData pd = get(event.getPlayer());
        pd.joinMs = System.currentTimeMillis();
        pd.lastLoc = event.getPlayer().getLocation();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        data.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        get(event.getPlayer()).markTeleport(event.getTo());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        get(event.getPlayer()).markTeleport(event.getPlayer().getLocation());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        get(event.getPlayer()).markTeleport(event.getRespawnLocation());
    }
}
