package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.PlayerData;
import dev.aquaguard.util.Compat;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

public final class SetbackService {
    private final AquaGuard plugin;

    public SetbackService(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.settings().setbackEnabled();
    }

    public void request(PlayerData data, String mode) {
        if (!enabled() || data == null || mode == null || mode.equalsIgnoreCase("none")) return;
        data.requestSetback = true;
        data.preferSafeSetback = mode.equalsIgnoreCase("safe");
    }

    public void apply(PlayerMoveEvent event, Player player, PlayerData data) {
        if (!data.requestSetback) return;
        data.requestSetback = false;
        if (!enabled()) return;
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("setback.cooldown-ms", 400);
        if (now - data.lastSetbackMs < cooldown) return;
        if (Compat.ping(player) > plugin.getConfig().getInt("setback.max-ping-ms", 280)) return;

        Location target;
        if (data.preferSafeSetback && data.lastSafeGround != null
                && data.lastSafeGround.getWorld() == event.getFrom().getWorld()) {
            target = data.lastSafeGround.clone();
            if (event.getTo() != null) {
                target.setYaw(event.getTo().getYaw());
                target.setPitch(event.getTo().getPitch());
            }
        } else {
            target = event.getFrom().clone();
        }
        World world = target.getWorld();
        if (world != null && !world.isChunkLoaded(target.getBlockX() >> 4, target.getBlockZ() >> 4)) {
            target = event.getFrom().clone();
        }
        event.setTo(target);
        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0f);
        data.lastSetbackMs = now;
        data.fallDistance = 0;
        data.jumpStartY = Double.NaN;
    }
}
