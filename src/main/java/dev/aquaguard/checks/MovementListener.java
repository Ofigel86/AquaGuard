package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.DataManager;
import dev.aquaguard.core.ViolationManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MovementListener implements Listener {
    private final AquaGuard plugin;
    private final DataManager data;
    private final ViolationManager vl;

    public MovementListener(AquaGuard plugin, DataManager data, ViolationManager vl) {
        this.plugin = plugin; this.data = data; this.vl = vl;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player p = e.getPlayer();

        if (e.getFrom().distanceSquared(e.getTo()) < 1e-6) return;

        DataManager.PlayerData pd = data.get(p);

        Location from = e.getFrom(), to = e.getTo();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double horizontal = Math.hypot(dx, dz);
        boolean onGround = p.isOnGround();

        if (!onGround && !p.isFlying() && !p.isGliding()) pd.airTicks++;
        else pd.airTicks = 0;

        checkSpeedA(p, pd, horizontal, onGround);
        checkFlyA(p, pd, dy, onGround);

        pd.lastDeltaY = dy;
        pd.lastOnGround = onGround;
        pd.lastLoc = to;
    }

    private void checkSpeedA(Player p, DataManager.PlayerData pd, double horiz, boolean onGround) {
        if (!onGround) { pd.speedStreak = Math.max(0, pd.speedStreak - 1); return; }
        if (isExempt(p) || pd.hadRecentVelocity(1200)) { pd.speedStreak = 0; return; }

        double walk = plugin.getConfig().getDouble("checks.SpeedA.ground-walk", 0.215);
        double sprint = plugin.getConfig().getDouble("checks.SpeedA.ground-sprint", 0.36);
        double effPer = plugin.getConfig().getDouble("checks.SpeedA.speed-effect-per-level", 0.06);
        double margin = plugin.getConfig().getDouble("checks.SpeedA.horizontal-margin", 0.05);
        int streakToFlag = plugin.getConfig().getInt("checks.SpeedA.streak-to-flag", 3);
        double addVl = plugin.getConfig().getDouble("checks.SpeedA.add-vl", 1.0);

        boolean sprinting = p.isSprinting();
        double allowed = sprinting ? sprint : walk;

        int amp = 0;
        PotionEffect eff = p.getPotionEffect(PotionEffectType.SPEED);
        if (eff != null) amp = eff.getAmplifier() + 1;
        allowed += effPer * amp;

        if (horiz > (allowed + margin)) {
            if (++pd.speedStreak >= streakToFlag) {
                vl.add(p.getUniqueId(), "SpeedA", addVl,
                        p.getName() + " moved " + String.format("%.3f", horiz) + " > " + String.format("%.3f", allowed));
                pd.speedStreak = streakToFlag;
            }
        } else {
            pd.speedStreak = Math.max(0, pd.speedStreak - 1);
        }
    }

    private void checkFlyA(Player p, DataManager.PlayerData pd, double dy, boolean onGround) {
        if (isExempt(p)) { pd.flyStreak = 0; return; }

        if (!onGround && !p.isFlying() && !p.isGliding() && !isInLiquid(p)) {
            int maxAir = plugin.getConfig().getInt("checks.FlyA.max-air-ticks", 16);
            int hoverTicks = plugin.getConfig().getInt("checks.FlyA.hover-threshold-ticks", 5);
            int streakToFlag = plugin.getConfig().getInt("checks.FlyA.streak-to-flag", 3);
            double addVl = plugin.getConfig().getDouble("checks.FlyA.add-vl", 1.0);

            boolean hovering = Math.abs(dy) < 1e-3 && pd.airTicks >= hoverTicks;

            if (pd.airTicks > maxAir || hovering) {
                if (++pd.flyStreak >= streakToFlag) {
                    vl.add(p.getUniqueId(), "FlyA", addVl,
                            p.getName() + " airTicks=" + pd.airTicks + " hover=" + hovering);
                    pd.flyStreak = streakToFlag;
                }
            } else {
                pd.flyStreak = Math.max(0, pd.flyStreak - 1);
            }
        } else {
            pd.flyStreak = Math.max(0, pd.flyStreak - 1);
        }
    }

    private boolean isExempt(Player p) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return true;
        if (p.isInsideVehicle() || p.isGliding() || p.isFlying()) return true;
        if (p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;

        Block feet = p.getLocation().getBlock();
        String feetName = feet.getType().name();
        if (feetName.contains("LADDER") || feetName.contains("VINE")) return true;

        Block below = p.getLocation().clone().subtract(0, 1, 0).getBlock();
        Material bt = below.getType();
        return bt == Material.SLIME_BLOCK || bt == Material.HONEY_BLOCK || isInLiquid(p);
    }

    private boolean isInLiquid(Player p) {
        Material t = p.getLocation().getBlock().getType();
        return t == Material.WATER || t == Material.LAVA || t == Material.KELP || t == Material.KELP_PLANT;
    }
}