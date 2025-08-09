package dev.aquaguard.core;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DataManager implements Listener {
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();

    public PlayerData get(Player p) {
        return data.computeIfAbsent(p.getUniqueId(), id -> new PlayerData());
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { get(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { data.remove(e.getPlayer().getUniqueId()); }

    public static class PlayerData {
        public int airTicks = 0;
        public int speedStreak = 0;
        public int flyStreak = 0;
        public long lastVelocityMs = 0L;
        public double lastDeltaY = 0.0;
        public boolean lastOnGround = true;
        public Location lastLoc = null;

        public boolean hadRecentVelocity(long windowMs) {
            return (System.currentTimeMillis() - lastVelocityMs) <= windowMs;
        }
    }
}