package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class WorldListener implements Listener {
    private final AquaGuard plugin;

    public WorldListener(AquaGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!event.canBuild()) return;
        plugin.worldChecks().onPlace(event.getPlayer(), event.getBlockPlaced(), event.getBlockAgainst());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        plugin.worldChecks().onBreakStart(event.getPlayer(), event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        plugin.worldChecks().onBreak(event.getPlayer(), event.getBlock());
    }
}
