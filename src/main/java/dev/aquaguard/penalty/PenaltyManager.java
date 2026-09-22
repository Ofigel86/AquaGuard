package dev.aquaguard.penalty;

import dev.aquaguard.AquaGuard;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;

/**
 * Мягкое наказание: урез урона по суммарному VL.
 * pvp-only=false больше не выключает систему целиком — оно лишь расширяет её на мобов.
 */
public final class PenaltyManager implements Listener {
    public enum Mode { OFF, SIMULATE, SOFT, HARD }

    private final AquaGuard plugin;
    private final Map<UUID, Long> simulateNotice = new ConcurrentHashMap<>();

    public PenaltyManager(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public Mode mode() {
        String raw = plugin.settings().penaltiesMode().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "off" -> Mode.OFF;
            case "soft" -> Mode.SOFT;
            case "hard" -> Mode.HARD;
            default -> Mode.SIMULATE;
        };
    }

    public void setMode(Mode mode) {
        plugin.settings().setPenaltiesMode(mode.name().toLowerCase(Locale.ROOT));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        Mode mode = mode();
        if (mode == Mode.OFF) return;
        Player attacker = attacker(event);
        if (attacker == null) return;
        boolean pvp = event.getEntity() instanceof Player;
        if (plugin.getConfig().getBoolean("penalties.pvp-only", true) && !pvp) return;
        if (plugin.exemption().bypass(attacker)) return;
        int maxPing = plugin.getConfig().getInt("penalties.max-ping-ms", 250);
        try {
            if (attacker.getPing() > maxPing) return;
        } catch (Throwable ignored) {
        }
        double total = plugin.violations().total(attacker.getUniqueId());
        double softMin = plugin.getConfig().getDouble("penalties.min-total-vl-soft", 12);
        double hardMin = plugin.getConfig().getDouble("penalties.min-total-vl-hard", 24);
        double soft = plugin.getConfig().getDouble("penalties.soft-scale", 0.75);
        double hard = plugin.getConfig().getDouble("penalties.hard-scale", 0.15);
        boolean shouldHard = total >= hardMin;
        boolean shouldSoft = total >= softMin;
        if (!shouldSoft) return;
        if (mode == Mode.SIMULATE) {
            attacker.sendMessage(plugin.messages().format(shouldHard ? "penalty-sim-hard" : "penalty-sim-soft",
                    "scale", String.format(Locale.US, "%.2f", shouldHard ? hard : soft)));
            return;
        }
        if (mode == Mode.HARD && shouldHard) event.setDamage(Math.max(0.2, event.getDamage() * hard));
        else event.setDamage(event.getDamage() * soft);
    }

    private static Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) return player;
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
