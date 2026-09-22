package dev.aquaguard.core;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WatchManager {
    private final AquaGuard plugin;
    private final Map<UUID, UUID> watching = new ConcurrentHashMap<>();

    public WatchManager(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public boolean toggle(Player staff, Player target) {
        UUID current = watching.get(staff.getUniqueId());
        if (current != null && current.equals(target.getUniqueId())) {
            watching.remove(staff.getUniqueId());
            return false;
        }
        watching.put(staff.getUniqueId(), target.getUniqueId());
        return true;
    }

    public void clear(UUID staff) {
        watching.remove(staff);
    }

    public void tick() {
        for (Map.Entry<UUID, UUID> entry : watching.entrySet()) {
            Player staff = Bukkit.getPlayer(entry.getKey());
            Player target = Bukkit.getPlayer(entry.getValue());
            if (staff == null) {
                watching.remove(entry.getKey());
                continue;
            }
            if (target == null) continue;
            PlayerData data = plugin.data().get(target);
            String bar = String.format(java.util.Locale.US,
                    "§b%s §7h=%.3f dy=%.3f air=%d g=%s ping=%d vl=%.1f bps=%.1f",
                    target.getName(), data.lastH, data.lastDy, data.airTicks, data.lastServerGround,
                    Compat.ping(target), plugin.violations().total(target.getUniqueId()), data.lastBps);
            try {
                staff.sendActionBar(bar);
            } catch (Throwable ignored) {
            }
        }
    }
}
