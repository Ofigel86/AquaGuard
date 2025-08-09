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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class MovementListener implements Listener {
    private final AquaGuard plugin;
    private final DataManager data;
    private final ViolationManager vl;

    public MovementListener(AquaGuard plugin, DataManager data, ViolationManager vl) {
        this.plugin = plugin;
        this.data = data;
        this.vl = vl;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player p = e.getPlayer();

        // игнор микро-движений/телепортов
        if (e.getFrom().distanceSquared(e.getTo()) < 1e-6) return;

        DataManager.PlayerData pd = data.get(p);

        Location from = e.getFrom(), to = e.getTo();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double horizontal = Math.hypot(dx, dz);
        boolean onGround = p.isOnGround();

        // Air ticks
        if (!onGround && !p.isFlying() && !p.isGliding()) pd.airTicks++;
        else pd.airTicks = 0;

        // NoFall: копим высоту падения во время падения
        if (dy < 0 && !isInLiquid(p) && !p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            pd.fallDistance += -dy;
        }
        // Приземление
        if (!pd.lastOnGround && onGround) {
            if (pd.fallDistance >= plugin.getConfig().getDouble("checks.NoFallA.min-fall", 3.0)
                    && !isLandingExempt(p)) {
                pd.awaitingFallDamage = true;
                pd.landAtMs = System.currentTimeMillis();
            }
            pd.fallDistance = 0.0;
        }

        // Checks
        checkSpeedA(p, pd, horizontal, onGround);
        updateSpeedWindow(p, pd, horizontal, onGround, p.isSprinting());
        checkSpeedB(p, pd);
        checkFlyA(p, pd, dy, onGround);
        checkNoFallA(p, pd);
        checkJesusA(p, pd, horizontal);

        // save state
        pd.lastOnGround = onGround;
        pd.lastLoc = to;
    }

    private void checkSpeedA(Player p, DataManager.PlayerData pd, double horiz, boolean onGround) {
        if (isExempt(p) || pd.hadRecentVelocity(1200)) { pd.speedStreak = 0; return; }

        double walkG = plugin.getConfig().getDouble("checks.SpeedA.ground-walk", 0.215);
        double sprintG = plugin.getConfig().getDouble("checks.SpeedA.ground-sprint", 0.36);
        double walkA = plugin.getConfig().getDouble("checks.SpeedA.air-walk", 0.30);
        double sprintA = plugin.getConfig().getDouble("checks.SpeedA.air-sprint", 0.42);
        double effPer = plugin.getConfig().getDouble("checks.SpeedA.speed-effect-per-level", 0.06);
        double margin = plugin.getConfig().getDouble("checks.SpeedA.horizontal-margin", 0.03);
        int streakToFlag = plugin.getConfig().getInt("checks.SpeedA.streak-to-flag", 2);
        double addVl = plugin.getConfig().getDouble("checks.SpeedA.add-vl", 1.5);

        boolean sprinting = p.isSprinting();
        double allowed = onGround ? (sprinting ? sprintG : walkG) : (sprinting ? sprintA : walkA);

        PotionEffect eff = p.getPotionEffect(PotionEffectType.SPEED);
        if (eff != null) allowed += effPer * (eff.getAmplifier() + 1);

        if (horiz > (allowed + margin)) {
            if (++pd.speedStreak >= streakToFlag) {
                flag(p, "SpeedA", addVl,
                        String.format("h=%.3f > %.3f onGround=%s sprint=%s", horiz, allowed, onGround, sprinting));
                pd.speedStreak = streakToFlag;
            }
        } else {
            pd.speedStreak = Math.max(0, pd.speedStreak - 1);
        }
    }

    private void updateSpeedWindow(Player p, DataManager.PlayerData pd, double h, boolean onGround, boolean sprinting) {
        long now = System.currentTimeMillis();
        pd.speedWin.addLast(new DataManager.PlayerData.Sample(now, h, onGround, sprinting));
        long window = plugin.getConfig().getLong("checks.SpeedB.window-ms", 900);
        while (!pd.speedWin.isEmpty() && (now - pd.speedWin.getFirst().t) > window) {
            pd.speedWin.removeFirst();
        }
    }

    private void checkSpeedB(Player p, DataManager.PlayerData pd) {
        if (isExempt(p) || pd.hadRecentVelocity(1200)) { pd.speedBStreak = 0; return; }
        if (pd.speedWin.size() < 5) return;

        long now = System.currentTimeMillis();
        long oldest = pd.speedWin.getFirst().t;
        double secs = Math.max(0.2, (now - oldest) / 1000.0);

        double sumH = 0.0;
        int air = 0, total = 0, sprintTicks = 0;
        for (var s : pd.speedWin) {
            sumH += s.h; total++;
            if (!s.onGround) air++;
            if (s.sprinting) sprintTicks++;
        }
        double bps = sumH / secs;

        double walkBps = plugin.getConfig().getDouble("checks.SpeedB.walk-bps", 4.4);
        double sprintBps = plugin.getConfig().getDouble("checks.SpeedB.sprint-bps", 5.9);
        double jumpBps = plugin.getConfig().getDouble("checks.SpeedB.jump-bps", 7.2);
        double marginBps = plugin.getConfig().getDouble("checks.SpeedB.margin-bps", 0.25);
        int streakToFlag = plugin.getConfig().getInt("checks.SpeedB.streak-to-flag", 2);
        double addVl = plugin.getConfig().getDouble("checks.SpeedB.add-vl", 1.5);

        double airRatio = (total == 0) ? 0.0 : (air / (double) total);
        boolean mostlySprint = sprintTicks > total / 2;
        double allowedBps = (airRatio > 0.3) ? jumpBps : (mostlySprint ? sprintBps : walkBps);

        PotionEffect speed = p.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) allowedBps *= (1.0 + 0.2 * (speed.getAmplifier() + 1));

        if (bps > (allowedBps + marginBps)) {
            if (++pd.speedBStreak >= streakToFlag) {
                flag(p, "SpeedB", addVl,
                        String.format("bps=%.2f>%.2f air=%.0f%%", bps, allowedBps, airRatio * 100));
                pd.speedBStreak = streakToFlag;
            }
        } else {
            pd.speedBStreak = Math.max(0, pd.speedBStreak - 1);
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
                    flag(p, "FlyA", addVl, String.format("airTicks=%d hover=%s", pd.airTicks, hovering));
                    pd.flyStreak = streakToFlag;
                }
            } else {
                pd.flyStreak = Math.max(0, pd.flyStreak - 1);
            }
        } else {
            pd.flyStreak = Math.max(0, pd.flyStreak - 1);
        }
    }

    private void checkNoFallA(Player p, DataManager.PlayerData pd) {
        if (!pd.awaitingFallDamage) return;

        long grace = plugin.getConfig().getLong("checks.NoFallA.grace-ms", 450);
        if ((System.currentTimeMillis() - pd.landAtMs) < grace) return;

        // Если урона не было и нет легальных причин — флаг
        if (!isLandingExempt(p)) {
            double addVl = plugin.getConfig().getDouble("checks.NoFallA.add-vl", 2.0);
            flag(p, "NoFallA", addVl, "no fall dmg after landing");
        }
        pd.awaitingFallDamage = false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        DataManager.PlayerData pd = data.get(p);
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            pd.awaitingFallDamage = false;
            pd.fallDistance = 0.0;
        }
    }

    private void checkJesusA(Player p, DataManager.PlayerData pd, double horiz) {
        if (!isInLiquid(p)) return;
        if (p.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) return;

        double swimMax = plugin.getConfig().getDouble("checks.JesusA.swim-max-h", 0.30);
        double surfaceMax = plugin.getConfig().getDouble("checks.JesusA.surface-max-h", 0.36);
        double margin = plugin.getConfig().getDouble("checks.JesusA.margin", 0.03);
        int streakToFlag = plugin.getConfig().getInt("checks.JesusA.streak-to-flag", 3);
        double addVl = plugin.getConfig().getDouble("checks.JesusA.add-vl", 1.0);

        // Грубо различаем «внутри воды» и «на поверхности»
        Material feet = p.getLocation().getBlock().getType();
        double allowed = (feet == Material.WATER || feet == Material.LAVA) ? swimMax : surfaceMax;

        if (horiz > (allowed + margin)) {
            if (++pd.speedStreak >= streakToFlag) {
                flag(p, "JesusA", addVl, String.format("h=%.3f > %.3f", horiz, allowed));
                pd.speedStreak = streakToFlag;
            }
        }
    }

    private void flag(Player p, String check, double addVl, String debug) {
        vl.add(p.getUniqueId(), check, addVl, debug);
        vl.maybePunish(p.getUniqueId(), check); // автокик, если порог для проверки задан в конфиге
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

    private boolean isLandingExempt(Player p) {
        Material below = p.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
        if (below == Material.SLIME_BLOCK || below == Material.HONEY_BLOCK || below == Material.HAY_BLOCK) return true;
        if (isInLiquid(p)) return true;
        if (p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;
        return false;
    }
}
