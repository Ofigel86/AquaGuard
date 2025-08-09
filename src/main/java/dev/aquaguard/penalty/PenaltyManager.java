package dev.aquaguard.penalty;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.ViolationManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class PenaltyManager implements Listener {
    private final AquaGuard plugin;
    private final ViolationManager vl;

    public enum Mode { OFF, SIMULATE, SOFT, HARD }

    public PenaltyManager(AquaGuard plugin, ViolationManager vl) {
        this.plugin = plugin; this.vl = vl;
    }

    public Mode mode() {
        String m = plugin.getConfig().getString("penalties.mode", "simulate").toLowerCase();
        return switch (m) {
            case "off" -> Mode.OFF;
            case "soft" -> Mode.SOFT;
            case "hard" -> Mode.HARD;
            default -> Mode.SIMULATE;
        };
    }

    public void setMode(Mode m) {
        plugin.getConfig().set("penalties.mode", m.name().toLowerCase());
        plugin.saveConfig();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!plugin.getConfig().getBoolean("penalties.pvp-only", true)) return;

        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p2) attacker = p2;
        if (attacker == null || !(e.getEntity() instanceof Player)) return;

        if (attacker.hasPermission("ag.bypass")) return;

        int maxPing = plugin.getConfig().getInt("penalties.max-ping-ms", 220);
        try {
            if (attacker.getPing() > maxPing) return;
        } catch (Throwable ignored) {}

        double total = vl.total(attacker.getUniqueId());
        double softMin = plugin.getConfig().getDouble("penalties.min-total-vl-soft", 12.0);
        double hardMin = plugin.getConfig().getDouble("penalties.min-total-vl-hard", 20.0);

        Mode m = mode();
        double soft = plugin.getConfig().getDouble("penalties.soft-scale", 0.75);
        double hard = plugin.getConfig().getDouble("penalties.hard-scale", 0.15);

        boolean shouldSoft = total >= softMin;
        boolean shouldHard = total >= hardMin;

        if (m == Mode.OFF) return;

        if (m == Mode.SIMULATE) {
            if (shouldHard) attacker.sendMessage("AquaGuard simulate: урон был бы x" + hard);
            else if (shouldSoft) attacker.sendMessage("AquaGuard simulate: урон был бы x" + soft);
            return;
        }

        if (m == Mode.HARD && shouldHard) {
            e.setDamage(Math.max(0.2, e.getDamage() * hard));
        } else if ((m == Mode.SOFT || m == Mode.HARD) && shouldSoft) {
            e.setDamage(e.getDamage() * soft);
        }
    }
}