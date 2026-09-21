package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.PlayerData;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.util.Vector;

public final class VelocityListener implements Listener {
    private final AquaGuard plugin;

    public VelocityListener(AquaGuard plugin) {
        this.plugin = plugin;
    }

    private void mark(Player player) {
        if (player == null) return;
        PlayerData data = plugin.data().get(player);
        data.lastVelocityMs = System.currentTimeMillis();
        data.jumpStartY = Double.NaN;
        data.resetStreak("FlyA");
        data.resetStreak("FlyB");
        data.resetStreak("SpeedA");
        data.resetStreak("HighJumpA");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        Player player = event.getPlayer();
        mark(player);
        Vector velocity = event.getVelocity();
        PlayerData data = plugin.data().get(player);
        data.velocityX = velocity.getX();
        data.velocityY = velocity.getY();
        data.velocityZ = velocity.getZ();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            mark(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        PlayerData data = plugin.data().get(event.getPlayer());
        data.lastRiptideMs = System.currentTimeMillis();
        mark(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRocket(PlayerInteractEvent event) {
        var item = event.getItem();
        if (item != null && item.getType() == Material.FIREWORK_ROCKET && event.getPlayer().isGliding()) {
            plugin.data().get(event.getPlayer()).lastFireworkMs = System.currentTimeMillis();
            mark(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) mark(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) mark(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) mark(player);
    }
}
