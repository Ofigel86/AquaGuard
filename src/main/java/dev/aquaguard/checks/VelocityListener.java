package dev.aquaguard.checks;

import dev.aquaguard.core.DataManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerVelocityEvent;

public class VelocityListener implements Listener {
    private final DataManager data;

    public VelocityListener(DataManager data) { this.data = data; }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent e) {
        data.get(e.getPlayer()).lastVelocityMs = System.currentTimeMillis();
    }
}