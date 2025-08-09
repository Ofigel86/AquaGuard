package dev.aquaguard.core;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayDeque;
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
        // Movement state
        public int airTicks = 0;
        public int speedStreak = 0;
        public int speedBStreak = 0;
        public int flyStreak = 0;
        public long lastVelocityMs = 0L;
        public boolean lastOnGround = true;
        public Location lastLoc = null;

        // SpeedB window
        public static class Sample {
            public long t; public double h;
            public boolean onGround; public boolean sprinting;
            public Sample(long t, double h, boolean onGround, boolean sprinting) {
                this.t = t; this.h = h; this.onGround = onGround; this.sprinting = sprinting;
            }
        }
        public ArrayDeque<Sample> speedWin = new ArrayDeque<>();

        // NoFall
        public double fallDistance = 0.0;
        public boolean awaitingFallDamage = false;
        public long landAtMs = 0L;

        // Setback
        public Location lastSafeGround = null;    // последняя безопасная точка на земле
        public boolean requestSetback = false;    // запросить откат в конце тик-обработки
        public boolean preferSafeSetback = false; // true → "safe", false → "from"
        public long lastSetbackMs = 0L;           // кулдаун между откатами

        public boolean hadRecentVelocity(long windowMs) {
            return (System.currentTimeMillis() - lastVelocityMs) <= windowMs;
        }
    }
}
