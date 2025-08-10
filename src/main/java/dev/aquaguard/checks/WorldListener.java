package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.bypass.BypassManager;
import dev.aquaguard.core.DataManager;
import dev.aquaguard.core.ViolationManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WorldListener: FastPlaceA / FastBreakA + ScaffoldA / TowerA (упрощённые).
 * - Счётчики scaffoldScore / towerStreak храним локально (а не в DataManager), чтобы не требовать его правки.
 */
public class WorldListener implements Listener {
    private final AquaGuard plugin;
    private final DataManager data;
    private final ViolationManager vl;
    private final CheckManager checks;
    private final BypassManager bypass;

    // Локальное состояние по игроку (чтобы не зависеть от полей в DataManager)
    private static class LocalState {
        int scaffoldScore = 0;
        int towerStreak = 0;
    }
    private final ConcurrentHashMap<UUID, LocalState> local = new ConcurrentHashMap<>();
    private LocalState st(Player p) { return local.computeIfAbsent(p.getUniqueId(), k -> new LocalState()); }

    public WorldListener(AquaGuard plugin, DataManager data, ViolationManager vl, CheckManager checks, BypassManager bypass) {
        this.plugin = plugin; this.data = data; this.vl = vl; this.checks = checks; this.bypass = bypass;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (bypass.isBypassed(p.getUniqueId())) return;

        var pd = data.get(p);
        var ls = st(p);
        long now = System.currentTimeMillis();

        // FastPlaceA (CPS по окну)
        pd.placeTimes.addLast(now);
        long window = plugin.getConfig().getLong("checks.FastPlaceA.window-ms", 1000);
        while (!pd.placeTimes.isEmpty() && (now - pd.placeTimes.getFirst()) > window) pd.placeTimes.removeFirst();
        if (checks.enabled("FastPlaceA")) {
            double cps = pd.placeTimes.size() / Math.max(0.2, window / 1000.0);
            double limit = plugin.getConfig().getDouble("checks.FastPlaceA.cps-limit", 10.0);
            double margin = plugin.getConfig().getDouble("checks.FastPlaceA.margin-cps", 1.0);
            if (cps > (limit + margin)) {
                flag(p, "FastPlaceA", plugin.getConfig().getDouble("checks.FastPlaceA.add-vl", 1.0),
                        String.format("cps=%.1f>%.1f", cps, limit));
            }
        }

        // ScaffoldA эвристика (вперёд/под себя)
        if (checks.enabled("ScaffoldA")) {
            Location pl = p.getLocation();
            Location bl = e.getBlockPlaced().getLocation();

            boolean belowFeet = bl.getBlockY() <= pl.getBlockY() - 1;
            double vx = bl.getX() + 0.5 - pl.getX();
            double vz = bl.getZ() + 0.5 - pl.getZ();
            double dotFwd = Math.cos(Math.toRadians(pl.getYaw())) * vx + Math.sin(Math.toRadians(pl.getYaw())) * vz;
            boolean forward = dotFwd > 0.4;

            if (belowFeet || forward) {
                ls.scaffoldScore = Math.min(ls.scaffoldScore + (p.isSprinting() ? 2 : 1), 100);
            } else {
                ls.scaffoldScore = Math.max(0, ls.scaffoldScore - 1);
            }

            double thr = plugin.getConfig().getDouble("checks.ScaffoldA.score-threshold", 6.0);
            if (ls.scaffoldScore >= thr) {
                flag(p, "ScaffoldA", plugin.getConfig().getDouble("checks.ScaffoldA.add-vl", 1.5), "pattern forward/below");
                ls.scaffoldScore = (int) thr;
            }
        }

        // TowerA (ставит под собой во время прыжка)
        if (checks.enabled("TowerA")) {
            Location pl = p.getLocation();
            Location bl = e.getBlockPlaced().getLocation();
            boolean under = bl.getBlockX() == pl.getBlockX() && bl.getBlockZ() == pl.getBlockZ() && bl.getBlockY() == pl.getBlockY() - 1;
            if (under && p.getVelocity().getY() > 0.2) ls.towerStreak++;
            else ls.towerStreak = Math.max(0, ls.towerStreak - 1);

            int need = plugin.getConfig().getInt("checks.TowerA.streak-to-flag", 3);
            if (ls.towerStreak >= need) {
                flag(p, "TowerA", plugin.getConfig().getDouble("checks.TowerA.add-vl", 1.0), "place under while jumping");
                ls.towerStreak = need;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (bypass.isBypassed(p.getUniqueId())) return;

        var pd = data.get(p);
        long now = System.currentTimeMillis();

        // FastBreakA (CPS по окну)
        pd.breakTimes.addLast(now);
        long window = plugin.getConfig().getLong("checks.FastBreakA.window-ms", 1000);
        while (!pd.breakTimes.isEmpty() && (now - pd.breakTimes.getFirst()) > window) pd.breakTimes.removeFirst();

        if (checks.enabled("FastBreakA")) {
            double cps = pd.breakTimes.size() / Math.max(0.2, window / 1000.0);
            double limit = plugin.getConfig().getDouble("checks.FastBreakA.cps-limit", 8.0);
            double margin = plugin.getConfig().getDouble("checks.FastBreakA.margin-cps", 1.0);
            if (cps > (limit + margin)) {
                flag(p, "FastBreakA", plugin.getConfig().getDouble("checks.FastBreakA.add-vl", 1.0),
                        String.format("cps=%.1f>%.1f", cps, limit));
            }
        }
    }

    private void flag(Player p, String check, double addVl, String debug) {
        if (!checks.enabled(check)) return;
        vl.add(p.getUniqueId(), check, addVl, debug);
        vl.maybePunish(p.getUniqueId(), check);
    }
}
