package dev.aquaguard.checks;

import dev.aquaguard.core.DataManager;
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
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

/**
 * Помечает "рывковые" состояния, которые дают иммунитет мувмент‑чекам:
 * - Нокбэк/рывок (PlayerVelocityEvent)
 * - Перл / Хорус (Teleport)
 * - Риптайд (Trident)
 * - Элитра‑буст: имитируем через PlayerInteractEvent с FIREWORK_ROCKET во время глайда
 * - Переключение глайда (EntityToggleGlideEvent)
 * - Посадка/выход из транспорта
 *
 * MovementListener использует DataManager.PlayerData.lastVelocityMs
 * в hadRecentVelocity(), чтобы не флагать сразу после таких событий.
 */
public class VelocityListener implements Listener {
    private final DataManager data;

    public VelocityListener(DataManager data) {
        this.data = data;
    }

    private void mark(Player p) {
        if (p != null) data.get(p).lastVelocityMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        mark(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        TeleportCause c = e.getCause();
        if (c == TeleportCause.ENDER_PEARL || c == TeleportCause.CHORUS_FRUIT) {
            mark(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent e) {
        mark(e.getPlayer());
    }

    // Имитируем элитра‑буст без Paper-события:
    // если игрок кликает фейерверком во время глайда — считаем это импульсом
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRocketUse(PlayerInteractEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        var item = e.getItem();
        if (item != null && item.getType() == Material.FIREWORK_ROCKET && p.isGliding()) {
            mark(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (e.getEntity() instanceof Player p) {
            mark(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player p) {
            mark(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent e) {
        if (e.getExited() instanceof Player p) {
            mark(p);
        }
    }
}
