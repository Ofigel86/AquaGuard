package dev.aquaguard.freeze;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FreezeManager implements Listener {
    private final AquaGuard plugin;
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> vehiclePull = new ConcurrentHashMap<>();
    private final File file;

    public FreezeManager(AquaGuard plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "freeze.yml");
        load();
    }

    public boolean isFrozen(UUID id) {
        return frozen.contains(id);
    }

    public void set(UUID id, boolean on) {
        if (on) frozen.add(id);
        else frozen.remove(id);
        save();
        Player player = Bukkit.getPlayer(id);
        if (player != null) {
            plugin.messages().send(player, on ? "freeze-target-on" : "freeze-target-off");
        }
    }

    public void toggle(UUID id) {
        set(id, !isFrozen(id));
    }

    public Set<UUID> frozen() {
        return Set.copyOf(frozen);
    }

    public void tick() {
        String bar = plugin.messages().format("frozen-bar");
        for (UUID id : frozen) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            try {
                player.sendActionBar(bar);
            } catch (Throwable ignored) {
                player.sendMessage(bar);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onMove(PlayerMoveEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getY() != event.getTo().getY()
                || event.getFrom().getZ() != event.getTo().getZ()) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isFrozen(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isFrozen(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isFrozen(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && isFrozen(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onVehicle(VehicleMoveEvent event) {
        if (event.getTo() == null || event.getFrom().distanceSquared(event.getTo()) < 1.0E-6) return;
        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (!(passenger instanceof Player player) || !isFrozen(player.getUniqueId())) continue;
            long now = System.currentTimeMillis();
            Long last = vehiclePull.get(event.getVehicle().getUniqueId());
            if (last != null && now - last < 150) return;
            vehiclePull.put(event.getVehicle().getUniqueId(), now);
            event.getVehicle().teleport(event.getFrom());
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) return;
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN
                || event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) return;
        String msg = event.getMessage().toLowerCase(Locale.ROOT);
        if (msg.startsWith("/msg ") || msg.startsWith("/tell ") || msg.startsWith("/r ") || msg.startsWith("/w ")) return;
        if (plugin.owner().isOwner(event.getPlayer())) return;
        event.setCancelled(true);
        plugin.messages().send(event.getPlayer(), "freeze-no-command");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (isFrozen(event.getPlayer().getUniqueId()) && plugin.getConfig().getBoolean("freeze.alert-on-quit", true)) {
            String text = plugin.messages().format("freeze-quit", "player", event.getPlayer().getName());
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission(plugin.getConfig().getString("alerts-permission", "ag.alerts")))
                    .forEach(p -> Texts.send(p, text));
            plugin.getLogger().info(Texts.strip(text));
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!isFrozen(event.getPlayer().getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("freeze.allow-chat", true)) event.setCancelled(true);
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String id : yml.getStringList("frozen")) {
            try {
                frozen.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("frozen", frozen.stream().map(UUID::toString).sorted().toList());
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("freeze.yml: " + ex.getMessage());
        }
    }

    public int onlineFrozen() {
        int n = 0;
        for (Player player : Bukkit.getOnlinePlayers()) if (isFrozen(player.getUniqueId())) n++;
        return n;
    }

    public boolean canFreeze(Player player) {
        return player.getGameMode() != GameMode.SPECTATOR || true;
    }
}
