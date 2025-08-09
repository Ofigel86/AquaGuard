package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.DataManager;
import dev.aquaguard.core.ViolationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class WorldListener implements Listener {
    private final AquaGuard plugin;
    private final DataManager data;
    private final ViolationManager vl;

    public WorldListener(AquaGuard plugin, DataManager data, ViolationManager vl) {
        this.plugin = plugin; this.data = data; this.vl = vl;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        var pd = data.get(p);

        long now = System.currentTimeMillis();
        pd.placeTimes.addLast(now);

        long window = plugin.getConfig().getLong("checks.FastPlaceA.window-ms", 1000);
        while (!pd.placeTimes.isEmpty() && (now - pd.placeTimes.getFirst()) > window) {
            pd.placeTimes.removeFirst();
        }

        int count = pd.placeTimes.size();
        double secs = Math.max(0.2, window / 1000.0);
        double cps = count / secs;

        double limit = plugin.getConfig().getDouble("checks.FastPlaceA.cps-limit", 10.0);
        double margin = plugin.getConfig().getDouble("checks.FastPlaceA.margin-cps", 1.0);
        int streakToFlag = plugin.getConfig().getInt("checks.FastPlaceA.streak-to-flag", 2);
        double addVl = plugin.getConfig().getDouble("checks.FastPlaceA.add-vl", 1.0);

        if (cps > (limit + margin)) {
            // Флагнем (простая версия без отдельного стрика)
            vl.add(p.getUniqueId(), "FastPlaceA", addVl, String.format("cps=%.1f > %.1f", cps, limit));
            vl.maybePunish(p.getUniqueId(), "FastPlaceA");
        }
    }
}
