package dev.aquaguard.checks;

import dev.aquaguard.core.DataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerToggleGlideEvent;
import org.bukkit.event.player.PlayerElytraBoostEvent; // Paper API
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

/**
 * Отмечает "рывковые" состояния, которые дают иммунитет мувмент‑чекам:
 * - Нокбэк/рывок (PlayerVelocityEvent)
 * - Перл / Хорус (Teleport)
 * - Риптайд (Trident)
 * - Элитра‑буст (Paper)
 * - Переключение глайда (вход/выход)
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

    private void mark(Object player) {
        if (player instanceof org.bukkit.entity.Player p) {
            data.get(p).lastVelocityMs = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent e) {
        // Нокбэк, перлы с импульсом, рывки от поршней/взрывов и т.п.
        data.get(e.getPlayer()).lastVelocityMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        // Перлы/хорус — типичные "скачки" позиции → даём иммунитет мувмент‑чекам
        TeleportCause c = e.getCause();
        if (c == TeleportCause.ENDER_PEARL || c == TeleportCause.CHORUS_FRUIT) {
            data.get(e.getPlayer()).lastVelocityMs = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent e) {
        // Риптайд даёт сильный импульс движения
        data.get(e.getPlayer()).lastVelocityMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onElytraBoost(PlayerElytraBoostEvent e) {
        // Paper: фейерверк‑буст элитры
        data.get(e.getPlayer()).lastVelocityMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onToggleGlide(PlayerToggleGlideEvent e) {
        // Вход/выход из глайда часто сопровождается скачками вертикали/горизонтали
        data.get(e.getPlayer()).lastVelocityMs = System.currentTimeMillis();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof org.bukkit.entity.Player p) {
            data.get(p).lastVelocityMs = System.currentTimeMillis();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVehicleExit(VehicleExitEvent e) {
        if (e.getExited() instanceof org.bukkit.entity.Player p) {
            data.get(p).lastVelocityMs = System.currentTimeMillis();
        }
    }
}
