package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.util.Compat;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

public final class PlayerListener implements Listener {
    private final AquaGuard plugin;

    public PlayerListener(AquaGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            plugin.data().get(player).clientBrand = Compat.clientBrand(player);
        }, 40L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        plugin.playerChecks().onUse(event.getPlayer(), event.getItem(), event.getAction());
        plugin.playerChecks().onHeld(event.getPlayer(), event.getItem());
        if ((event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK)
                && event.getClickedBlock() != null) {
            plugin.playerChecks().onGhost(event.getPlayer(), event.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal)) return;
        Player player = event.getPlayer();
        if (player == null) return;
        plugin.playerChecks().onCrystalPlace(player, event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        plugin.playerChecks().onConsume(event.getPlayer(), event.getItem());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player) plugin.playerChecks().onShoot(player, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;
        plugin.playerChecks().onRegen(player, System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!isContainer(event.getView())) return;
        var data = plugin.data().get(player);
        data.inventoryOpen = true;
        data.inventoryOpenMs = System.currentTimeMillis();
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) plugin.data().get(player).inventoryOpen = false;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        plugin.playerChecks().onHeld(player, event.getCurrentItem());
        plugin.playerChecks().onHeld(player, event.getCursor());
        if (!isContainer(event.getView())) return;
        if (event.getClickedInventory() == null || event.getClickedInventory().equals(player.getInventory())) return;
        plugin.playerChecks().onContainerClick(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        plugin.playerChecks().onHeld(player, event.getItem().getItemStack());
    }

    private static boolean isContainer(InventoryView view) {
        InventoryType type = view.getType();
        return type == InventoryType.CHEST || type == InventoryType.BARREL || type == InventoryType.SHULKER_BOX
                || type == InventoryType.HOPPER || type == InventoryType.DISPENSER || type == InventoryType.DROPPER
                || type == InventoryType.ENDER_CHEST;
    }
}
