package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.bypass.BypassManager;
import dev.aquaguard.core.DataManager;
import dev.aquaguard.core.ViolationManager;
import dev.aquaguard.freeze.FreezeManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MovementListener implements Listener {
    private final AquaGuard plugin;
    private final DataManager data;
    private final ViolationManager vl;
    private final CheckManager checks;
    private final BypassManager bypass;
    private final FreezeManager freeze;

    // Локальные стрейки для Strafe (не трогаем DataManager)
    private static class Local {
        int strafeAStreak = 0;
        int strafeBStreak = 0;
        int strafeCStreak = 0;
        // чтобы быстро брать предыдущую "h" без доступа к окну
        double lastH = 0.0;
    }
    private final Map<UUID, Local> local = new ConcurrentHashMap<>();
    private Local st(Player p) { return local.computeIfAbsent(p.getUniqueId(), k -> new Local()); }

    public MovementListener(AquaGuard plugin, DataManager data, ViolationManager vl,
                            CheckManager checks, BypassManager bypass, FreezeManager freeze) {
        this.plugin = plugin; this.data = data; this.vl = vl;
        this.checks = checks; this.bypass = bypass; this.freeze = freeze;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        Player p = e.getPlayer();
        if (bypass.isBypassed(p.getUniqueId())) return;
        if (freeze.is(p.getUniqueId())) { e.setTo(e.getFrom()); return; }
        if (e.getFrom().distanceSquared(e.getTo()) < 1e-6) return;

        DataManager.PlayerData pd = data.get(p);
        Local ls = st(p);

        Location from = e.getFrom(), to = e.getTo();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double horizontal = Math.hypot(dx, dz);
        boolean onGround = p.isOnGround();

        if (!onGround && !p.isFlying() && !p.isGliding()) pd.airTicks++;
        else pd.airTicks = 0;

        updateLastSafeGround(p, pd, to, onGround);

        if (dy < 0 && !isInOrNearLiquid(p) && !p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            pd.fallDistance += -dy;
        }
        if (!pd.lastOnGround && onGround) {
            if (pd.fallDistance >= plugin.getConfig().getDouble("checks.NoFallA.min-fall", 4.0)
                    && !isLandingExempt(p)) {
                pd.awaitingFallDamage = true;
                pd.landAtMs = System.currentTimeMillis();
            }
            pd.fallDistance = 0.0;
        }

        if (checks.enabled("SpeedA")) checkSpeedA(p, pd, horizontal, onGround);
        updateSpeedWindow(p, pd, horizontal, onGround, p.isSprinting());
        if (checks.enabled("SpeedB")) checkSpeedB(p, pd);

        if (checks.enabled("StrafeA")) checkStrafeA(p, pd, ls, horizontal, onGround);
        if (checks.enabled("StrafeB")) checkStrafeB(p, pd, ls, dx, dz, horizontal);
        if (checks.enabled("StrafeC")) checkStrafeC(p, pd);

        if (checks.enabled("NoSlowA")) checkNoSlowA(p, pd, horizontal, onGround);
        if (checks.enabled("FlyA")) checkFlyA(p, pd, dy, onGround);
        if (checks.enabled("NoFallA")) checkNoFallA(p, pd);
        if (checks.enabled("JesusA")) checkJesusA(p, pd, horizontal);
        if (checks.enabled("StepA")) checkStepA(p, pd, dy, onGround);
        if (checks.enabled("PhaseA")) checkPhaseA(p, pd);

        if (pd.requestSetback) { applySetback(e, p, pd); pd.requestSetback = false; }

        // keep state
        pd.lastOnGround = onGround;
        pd.lastLoc = to;
        ls.lastH = horizontal;
    }

    private void updateLastSafeGround(Player p, DataManager.PlayerData pd, Location to, boolean onGround) {
        if (!onGround) return;
        if (isExempt(p)) return;
        if (isInOrNearLiquid(p)) return;
        if (pd.hadRecentVelocity(300)) return;
        pd.lastSafeGround = to.clone();
    }

    // ===== SpeedA =====
    private void checkSpeedA(Player p, DataManager.PlayerData pd, double horiz, boolean onGround) {
        if (isExempt(p) || pd.hadRecentVelocity(1200)) { pd.speedStreak = 0; return; }

        double walkG = plugin.getConfig().getDouble("checks.SpeedA.ground-walk", 0.215);
        double sprintG = plugin.getConfig().getDouble("checks.SpeedA.ground-sprint", 0.36);
        double walkA = plugin.getConfig().getDouble("checks.SpeedA.air-walk", 0.30);
        double sprintA = plugin.getConfig().getDouble("checks.SpeedA.air-sprint", 0.42);
        double effPer = plugin.getConfig().getDouble("checks.SpeedA.speed-effect-per-level", 0.06);
        double margin = plugin.getConfig().getDouble("checks.SpeedA.horizontal-margin", 0.05);
        int streakToFlag = plugin.getConfig().getInt("checks.SpeedA.streak-to-flag", 3);
        double addVl = plugin.getConfig().getDouble("checks.SpeedA.add-vl", 1.5);

        boolean sprinting = p.isSprinting();
        double allowed = onGround ? (sprinting ? sprintG : walkG) : (sprinting ? sprintA : walkA);

        PotionEffect eff = p.getPotionEffect(PotionEffectType.SPEED);
        if (eff != null) allowed += effPer * (eff.getAmplifier() + 1);
        allowed += envTickBonus(p, onGround);

        if (horiz > (allowed + margin)) {
            if (++pd.speedStreak >= streakToFlag) {
                flag(p, "SpeedA", addVl, String.format("h=%.3f > %.3f onGround=%s sprint=%s", horiz, allowed, onGround, sprinting));
                requestSetback(p, pd, plugin.getConfig().getString("checks.SpeedA.setback", "from"));
                pd.speedStreak = streakToFlag;
            }
        } else {
            pd.speedStreak = Math.max(0, pd.speedStreak - 1);
        }
    }

    private double envTickBonus(Player p, boolean onGround) {
        if (!onGround) return 0.0;
        Material below = p.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
        switch (below) {
            case ICE:
            case PACKED_ICE:
            case FROSTED_ICE: return 0.10;
            case BLUE_ICE: return 0.14;
            case SOUL_SAND:
            case SOUL_SOIL:
                var boots = p.getInventory().getBoots();
                if (boots != null) {
                    int lvl = boots.getEnchantmentLevel(Enchantment.SOUL_SPEED);
                    if (lvl > 0) return 0.08 + 0.04 * (lvl - 1);
                }
            default: return 0.0;
        }
    }

    // ===== SpeedB =====
    private void updateSpeedWindow(Player p, DataManager.PlayerData pd, double h, boolean onGround, boolean sprinting) {
        long now = System.currentTimeMillis();
        pd.speedWin.addLast(new DataManager.PlayerData.Sample(now, h, onGround, sprinting));
        long window = plugin.getConfig().getLong("checks.SpeedB.window-ms", 900);
        while (!pd.speedWin.isEmpty() && (now - pd.speedWin.getFirst().t) > window) pd.speedWin.removeFirst();
    }

    private void checkSpeedB(Player p, DataManager.PlayerData pd) {
        if (isExempt(p) || pd.hadRecentVelocity(1200)) { pd.speedBStreak = 0; return; }
        long combatImm = plugin.getConfig().getLong("checks.SpeedB.combat-immunity-ms", 400);
        if ((System.currentTimeMillis() - pd.lastCombatMs) <= combatImm) { pd.speedBStreak = 0; return; }
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
        double jumpBps = plugin.getConfig().getDouble("checks.SpeedB.jump-bps", 7.8);
        double marginBps = plugin.getConfig().getDouble("checks.SpeedB.margin-bps", 0.50);
        int streakToFlag = plugin.getConfig().getInt("checks.SpeedB.streak-to-flag", 2);
        double addVl = plugin.getConfig().getDouble("checks.SpeedB.add-vl", 1.5);

        boolean anyAir = air > 0;
        boolean mostlySprint = sprintTicks > total / 2;
        double allowedBps = mostlySprint ? sprintBps : walkBps;
        if (anyAir) allowedBps = Math.max(allowedBps, jumpBps);

        PotionEffect speed = p.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) allowedBps *= (1.0 + 0.2 * (speed.getAmplifier() + 1));
        allowedBps *= envBpsMultiplier(p);

        if (isInOrNearLiquid(p)) {
            var boots = p.getInventory().getBoots();
            int ds = boots != null ? boots.getEnchantmentLevel(Enchantment.DEPTH_STRIDER) : 0;
            if (ds > 0) allowedBps *= (1.0 + 0.15 * ds);
            if (p.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) allowedBps *= 1.6;
        }

        if (bps > (allowedBps + marginBps)) {
            if (++pd.speedBStreak >= streakToFlag) {
                flag(p, "SpeedB", addVl, String.format("bps=%.2f>%.2f", bps, allowedBps));
                requestSetback(p, pd, plugin.getConfig().getString("checks.SpeedB.setback", "from"));
                pd.speedBStreak = streakToFlag;
            }
        } else {
            pd.speedBStreak = Math.max(0, pd.speedBStreak - 1);
        }
    }

    private double envBpsMultiplier(Player p) {
        Material below = p.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
        switch (below) {
            case ICE:
            case PACKED_ICE:
            case FROSTED_ICE: return 1.25;
            case BLUE_ICE: return 1.35;
            case SOUL_SAND:
            case SOUL_SOIL:
                var boots = p.getInventory().getBoots();
                int lvl = boots != null ? boots.getEnchantmentLevel(Enchantment.SOUL_SPEED) : 0;
                if (lvl > 0) return 1.15 + 0.08 * (lvl - 1);
            default: return 1.0;
        }
    }

    // ===== StrafeA: спайк ускорения =====
    private void checkStrafeA(Player p, DataManager.PlayerData pd, Local ls, double h, boolean onGround) {
        if (isExempt(p) || pd.hadRecentVelocity(1200)) { ls.strafeAStreak = 0; return; }
        if (isInOrNearLiquid(p)) { ls.strafeAStreak = 0; return; }

        double prevH = ls.lastH;
        double delta = Math.max(0.0, h - prevH);

        double accLimit = plugin.getConfig().getDouble("checks.StrafeA.acc-limit", 0.15);
        double margin = plugin.getConfig().getDouble("checks.StrafeA.margin", 0.03);
        int streakToFlag = plugin.getConfig().getInt("checks.StrafeA.streak-to-flag", 2);
        double addVl = plugin.getConfig().getDouble("checks.StrafeA.add-vl", 1.0);

        // Учитываем среду/эффекты — немного повышаем лимит
        double boost = 0.0;
        if (onGround) boost += envTickBonus(p, true);
        PotionEffect eff = p.getPotionEffect(PotionEffectType.SPEED);
        if (eff != null) boost += 0.01 * (eff.getAmplifier() + 1);
        double allowed = accLimit + boost + margin;

        if (delta > allowed) {
            if (++ls.strafeAStreak >= streakToFlag) {
                flag(p, "StrafeA", addVl, String.format("Δh=%.3f > %.3f (prev=%.3f, cur=%.3f)", delta, allowed, prevH, h));
                ls.strafeAStreak = streakToFlag;
            }
        } else {
            ls.strafeAStreak = Math.max(0, ls.strafeAStreak - 1);
        }
    }

    // ===== StrafeB: большой угол между взглядом и движением при высокой скорости =====
    private void checkStrafeB(Player p, DataManager.PlayerData pd, Local ls, double dx, double dz, double h) {
        if (isExempt(p) || pd.hadRecentVelocity(1200)) { ls.strafeBStreak = 0; return; }
        if (isInOrNearLiquid(p)) { ls.strafeBStreak = 0; return; }

        double minH = plugin.getConfig().getDouble("checks.StrafeB.min-h", 0.25);
        if (h < minH) { ls.strafeBStreak = Math.max(0, ls.strafeBStreak - 1); return; }

        // угол между вектором движения и направлением взгляда (горизонтально)
        Vector move = new Vector(dx, 0, dz);
        if (move.lengthSquared() < 1e-8) { ls.strafeBStreak = Math.max(0, ls.strafeBStreak - 1); return; }
        move.normalize();
        Vector look = p.getEyeLocation().getDirection().setY(0).normalize();

        double dot = Math.max(-1.0, Math.min(1.0, move.dot(look)));
        double deg = Math.toDegrees(Math.acos(dot));

        double angleThr = plugin.getConfig().getDouble("checks.StrafeB.angle-deg", 95.0);
        int streakToFlag = plugin.getConfig().getInt("checks.StrafeB.streak-to-flag", 3);
        double addVl = plugin.getConfig().getDouble("checks.StrafeB.add-vl", 1.0);

        // Очень большой угол (почти перпендикуляр/назад) на заметной скорости — редкий легит
        if (deg >= angleThr) {
            if (++ls.strafeBStreak >= streakToFlag) {
                flag(p, "StrafeB", addVl, String.format("angle=%.1f°, h=%.3f", deg, h));
                ls.strafeBStreak = streakToFlag;
            }
        } else {
            ls.strafeBStreak = Math.max(0, ls.strafeBStreak - 1);
        }
    }

    // ===== StrafeC: air-control — bps выше порога при высоком airRatio =====
    private void checkStrafeC(Player p, DataManager.PlayerData pd) {
        if (isExempt(p) || pd.hadRecentVelocity(1200)) { resetStrafeC(p); return; }
        if (isInOrNearLiquid(p)) { resetStrafeC(p); return; }
        if (pd.speedWin.size() < 6) { resetStrafeC(p); return; }

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
        double airRatio = (total == 0) ? 0.0 : (air / (double) total);

        double jumpBps = plugin.getConfig().getDouble("checks.SpeedB.jump-bps", 7.8);
        double extra = plugin.getConfig().getDouble("checks.StrafeC.extra-bps", 0.20);
        double minAir = plugin.getConfig().getDouble("checks.StrafeC.air-ratio", 0.70);
        int streakToFlag = plugin.getConfig().getInt("checks.StrafeC.streak-to-flag", 2);
        double addVl = plugin.getConfig().getDouble("checks.StrafeC.add-vl", 1.0);

        // эффект скорости — множитель
        PotionEffect sp = p.getPotionEffect(PotionEffectType.SPEED);
        double allowed = jumpBps + extra;
        if (sp != null) allowed *= (1.0 + 0.2 * (sp.getAmplifier() + 1));
        allowed *= envBpsMultiplier(p);

        var boots = p.getInventory().getBoots();
        int ds = boots != null ? boots.getEnchantmentLevel(Enchantment.DEPTH_STRIDER) : 0;
        if (isInOrNearLiquid(p) && ds > 0) allowed *= (1.0 + 0.15 * ds);

        Local ls = st(p);
        if (airRatio >= minAir && bps > allowed) {
            if (++ls.strafeCStreak >= streakToFlag) {
                flag(p, "StrafeC", addVl, String.format("bps=%.2f>%.2f air=%.0f%%", bps, allowed, airRatio * 100));
                ls.strafeCStreak = streakToFlag;
            }
        } else {
            ls.strafeCStreak = Math.max(0, ls.strafeCStreak - 1);
        }
    }

    private void resetStrafeC(Player p) {
        st(p).strafeCStreak = 0;
    }

    // ===== NoSlowA (щит) =====
    private void checkNoSlowA(Player p, DataManager.PlayerData pd, double horiz, boolean onGround) {
        if (!onGround) return;
        if (isInOrNearLiquid(p)) return;
        if (!p.isBlocking()) return;
        if (isExempt(p) || pd.hadRecentVelocity(1200)) return;

        double allowed = plugin.getConfig().getDouble("checks.NoSlowA.blocking-walk", 0.20);
        double margin = plugin.getConfig().getDouble("checks.NoSlowA.margin", 0.05);
        int streakToFlag = plugin.getConfig().getInt("checks.NoSlowA.streak-to-flag", 3);
        double addVl = plugin.getConfig().getDouble("checks.NoSlowA.add-vl", 1.0);

        PotionEffect eff = p.getPotionEffect(PotionEffectType.SPEED);
        if (eff != null) allowed += 0.04 * (eff.getAmplifier() + 1);

        if (horiz > (allowed + margin)) {
            if (++pd.noSlowStreak >= streakToFlag) {
                flag(p, "NoSlowA", addVl, String.format("h=%.3f > %.3f (blocking)", horiz, allowed));
                requestSetback(p, pd, plugin.getConfig().getString("checks.NoSlowA.setback", "from"));
                pd.noSlowStreak = streakToFlag;
            }
        } else {
            pd.noSlowStreak = Math.max(0, pd.noSlowStreak - 1);
        }
    }

    // ===== FlyA (hover-only) =====
    private void checkFlyA(Player p, DataManager.PlayerData pd, double dy, boolean onGround) {
        if (isExempt(p)) { pd.flyStreak = 0; return; }

        boolean liquid = isInOrNearLiquid(p);
        if (!onGround && !p.isFlying() && !p.isGliding() && !liquid) {
            int hoverTicks = plugin.getConfig().getInt("checks.FlyA.hover-threshold-ticks", 7);
            int streakToFlag = plugin.getConfig().getInt("checks.FlyA.streak-to-flag", 3);
            double addVl = plugin.getConfig().getDouble("checks.FlyA.add-vl", 1.0);

            boolean falling = dy < -0.08 || pd.fallDistance > 1.5;
            if (falling) { pd.flyStreak = Math.max(0, pd.flyStreak - 1); return; }

            boolean hovering = Math.abs(dy) < 0.003 && pd.airTicks >= hoverTicks && !pd.hadRecentVelocity(800);
            if (hovering) {
                if (++pd.flyStreak >= streakToFlag) {
                    flag(p, "FlyA", addVl, String.format("hover airTicks=%d", pd.airTicks));
                    requestSetback(p, pd, plugin.getConfig().getString("checks.FlyA.setback", "safe"));
                    pd.flyStreak = streakToFlag;
                }
            } else {
                pd.flyStreak = Math.max(0, pd.flyStreak - 1);
            }
        } else {
            pd.flyStreak = Math.max(0, pd.flyStreak - 1);
        }
    }

    // ===== NoFallA =====
    private void checkNoFallA(Player p, DataManager.PlayerData pd) {
        if (!pd.awaitingFallDamage) return;
        long grace = plugin.getConfig().getLong("checks.NoFallA.grace-ms", 450);
        if ((System.currentTimeMillis() - pd.landAtMs) < grace) return;

        if (!isLandingExempt(p)) {
            double addVl = plugin.getConfig().getDouble("checks.NoFallA.add-vl", 2.0);
            flag(p, "NoFallA", addVl, "no fall dmg after landing");
            requestSetback(p, pd, plugin.getConfig().getString("checks.NoFallA.setback", "safe"));
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

    // ===== JesusA (усилен) =====
    private void checkJesusA(Player p, DataManager.PlayerData pd, double horiz) {
        if (!isInWater(p)) return; // только когда реально в воде/колонне/водорослях
        if (p.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) return;

        // различаем "в воде" и "на поверхности"
        Block head = p.getLocation().clone().add(0, 1, 0).getBlock();
        boolean headWater = isWaterLike(head);

        double swimMax = plugin.getConfig().getDouble("checks.JesusA.swim-max-h", 0.30);
        double surfaceMax = plugin.getConfig().getDouble("checks.JesusA.surface-max-h", 0.36);
        double margin = plugin.getConfig().getDouble("checks.JesusA.margin", 0.03);
        int streakToFlag = plugin.getConfig().getInt("checks.JesusA.streak-to-flag", 3);
        double addVl = plugin.getConfig().getDouble("checks.JesusA.add-vl", 1.0);

        var boots = p.getInventory().getBoots();
        int ds = boots != null ? boots.getEnchantmentLevel(Enchantment.DEPTH_STRIDER) : 0;
        double dsBonus = (ds > 0) ? (0.05 * ds) : 0.0;

        // Если голова в воде — считаем "плавание", иначе "поверхность"
        double allowed = (headWater ? (swimMax + dsBonus) : (surfaceMax + dsBonus));
        if (p.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) allowed += 0.20;

        if (horiz > (allowed + margin)) {
            if (++pd.speedStreak >= streakToFlag) {
                flag(p, "JesusA", addVl, String.format("h=%.3f > %.3f (%s)", horiz, allowed, headWater ? "swim" : "surface"));
                requestSetback(p, pd, plugin.getConfig().getString("checks.JesusA.setback", "from"));
                pd.speedStreak = streakToFlag;
            }
        } else {
            pd.speedStreak = Math.max(0, pd.speedStreak - 1);
        }
    }

    private void checkStepA(Player p, DataManager.PlayerData pd, double dy, boolean onGround) {
        if (!onGround || !pd.lastOnGround) { pd.stepStreak = Math.max(0, pd.stepStreak - 1); return; }
        if (isExempt(p)) { pd.stepStreak = 0; return; }
        if (isInOrNearLiquid(p)) { pd.stepStreak = 0; return; }

        double maxStep = plugin.getConfig().getDouble("checks.StepA.max-step", 0.6);
        double margin = plugin.getConfig().getDouble("checks.StepA.margin", 0.05);
        int streakToFlag = plugin.getConfig().getInt("checks.StepA.streak-to-flag", 2);
        double addVl = plugin.getConfig().getDouble("checks.StepA.add-vl", 1.0);

        if (dy <= 0) { pd.stepStreak = Math.max(0, pd.stepStreak - 1); return; }
        if (isSteppableBlockBelow(p)) { pd.stepStreak = Math.max(0, pd.stepStreak - 1); return; }

        if (dy > (maxStep + margin)) {
            if (++pd.stepStreak >= streakToFlag) {
                flag(p, "StepA", addVl, String.format("dy=%.3f > %.3f", dy, maxStep));
                requestSetback(p, pd, plugin.getConfig().getString("checks.StepA.setback", "from"));
                pd.stepStreak = streakToFlag;
            }
        } else {
            pd.stepStreak = Math.max(0, pd.stepStreak - 1);
        }
    }

    private void checkPhaseA(Player p, DataManager.PlayerData pd) {
        if (isExempt(p)) { pd.phaseStreak = 0; return; }
        if (isInOrNearLiquid(p)) { pd.phaseStreak = 0; return; }

        Block feet = p.getLocation().getBlock();
        Block head = p.getLocation().clone().add(0, 1, 0).getBlock();

        if (isHardSolid(feet) || isHardSolid(head)) {
            int streakToFlag = plugin.getConfig().getInt("checks.PhaseA.streak-to-flag", 2);
            double addVl = plugin.getConfig().getDouble("checks.PhaseA.add-vl", 2.0);
            if (++pd.phaseStreak >= streakToFlag) {
                flag(p, "PhaseA", addVl, "inside solid");
                requestSetback(p, pd, plugin.getConfig().getString("checks.PhaseA.setback", "safe"));
                pd.phaseStreak = streakToFlag;
            }
        } else {
            pd.phaseStreak = Math.max(0, pd.phaseStreak - 1);
        }
    }

    // ===== flag & setback =====
    private void flag(Player p, String check, double addVl, String debug) {
        if (!checks.enabled(check)) return;
        vl.add(p.getUniqueId(), check, addVl, debug);
        vl.maybePunish(p.getUniqueId(), check);
    }

    private void requestSetback(Player p, DataManager.PlayerData pd, String mode) {
        if (!plugin.getConfig().getBoolean("setback.enabled", true)) return;
        if ("none".equalsIgnoreCase(mode)) return;
        pd.requestSetback = true;
        pd.preferSafeSetback = "safe".equalsIgnoreCase(mode);
    }

    private void applySetback(PlayerMoveEvent e, Player p, DataManager.PlayerData pd) {
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("setback.cooldown-ms", 400);
        if ((now - pd.lastSetbackMs) < cd) return;
        int maxPing = plugin.getConfig().getInt("setback.max-ping-ms", 220);
        try { if (p.getPing() > maxPing) return; } catch (Throwable ignored) {}

        Location target;
        if (pd.preferSafeSetback && pd.lastSafeGround != null) {
            target = pd.lastSafeGround.clone();
            target.setYaw(e.getTo().getYaw());
            target.setPitch(e.getTo().getPitch());
        } else {
            target = e.getFrom().clone();
        }
        e.setTo(target);
        p.setFallDistance(0.0f);
        pd.lastSetbackMs = now;
    }

    // ===== helpers =====

    private boolean isSteppableBlockBelow(Player p) {
        Block b = p.getLocation().clone().subtract(0, 1, 0).getBlock();
        Material t = b.getType();
        String n = t.name();
        if (n.contains("STAIRS") || n.contains("SLAB") || n.contains("CARPET")) return true;
        if (t == Material.SCAFFOLDING) return true;
        if (t == Material.SNOW) return true;
        return false;
    }

    private boolean isHardSolid(Block b) {
        Material t = b.getType();
        if (!t.isSolid()) return false;
        if (t == Material.POWDER_SNOW || t == Material.COBWEB) return false;
        if (t == Material.SCAFFOLDING) return false;
        if (t == Material.LADDER || t.name().contains("VINE")) return false;
        return true;
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
        return bt == Material.SLIME_BLOCK || bt == Material.HONEY_BLOCK || isInOrNearLiquid(p);
    }

    private boolean isInOrNearLiquid(Player p) {
        Block feet = p.getLocation().getBlock();
        Block below = p.getLocation().clone().subtract(0, 1, 0).getBlock();
        return isLiquidLike(feet) || isLiquidLike(below);
    }
    private boolean isInWater(Player p) {
        Block feet = p.getLocation().getBlock();
        return isWaterLike(feet);
    }
    private boolean isLiquidLike(Block b) {
        if (b.isLiquid()) return true;
        Material t = b.getType();
        if (t == Material.LAVA) return true;
        return isWaterLike(b);
    }
    private boolean isWaterLike(Block b) {
        Material t = b.getType();
        return t == Material.WATER
                || t == Material.BUBBLE_COLUMN
                || t == Material.KELP
                || t == Material.KELP_PLANT
                || t == Material.SEAGRASS
                || t == Material.TALL_SEAGRASS;
    }

    private boolean isLandingExempt(Player p) {
        Material below = p.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
        if (below == Material.SLIME_BLOCK || below == Material.HONEY_BLOCK || below == Material.HAY_BLOCK) return true;
        if (isInOrNearLiquid(p)) return true;
        if (p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;
        return false;
    }
}
