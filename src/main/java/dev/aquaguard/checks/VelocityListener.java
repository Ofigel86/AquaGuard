package dev.aquaguard.checks;

import dev.aquaguard.core.DataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerVelocityEvent;

/**
 * Отмечает время последнего velocity, чтобы чеки движения учитывали иммунитет
 * (например, Speed/Fly не флагали сразу после нокбэка/рывка).
 */
public class VelocityListener implements Listener {
    private final DataManager data;

    public VelocityListener(DataManager data) {
        this.data = data;
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent e) {
        data.get(e.getPlayer()).lastVelocityMs = System.currentTimeMillis();
    }
}
