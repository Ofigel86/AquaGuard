package dev.aquaguard.checks.movement;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.checks.MoveContext;
import dev.aquaguard.core.PlayerData;
import dev.aquaguard.util.BlockUtil;
import dev.aquaguard.util.Compat;
import dev.aquaguard.util.SpeedMath;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Основные физические чеки. Пороги щедрые: лучше пропустить спорный спринт-прыжок, чем кикнуть легита.
 */
public final class MovementChecks {
    private final AquaGuard plugin;

    public MovementChecks(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public void run(MoveContext ctx) {
        if (plugin.checks().enabled("SpeedA")) speedA(ctx);
        if (plugin.checks().enabled("SpeedB")) speedB(ctx);
        if (plugin.checks().enabled("FlyA")) flyA(ctx);
        if (plugin.checks().enabled("FlyB")) flyB(ctx);
        if (plugin.checks().enabled("NoFallA")) noFall(ctx);
        if (plugin.checks().enabled("JesusA")) jesusA(ctx);
        if (plugin.checks().enabled("JesusB")) jesusB(ctx);
        if (plugin.checks().enabled("StepA")) step(ctx);
        if (plugin.checks().enabled("PhaseA")) phase(ctx);
        if (plugin.checks().enabled("NoSlowA")) noSlow(ctx);
        if (plugin.checks().enabled("HighJumpA")) highJump(ctx);
        if (plugin.checks().enabled("FastClimbA")) fastClimb(ctx);
        if (plugin.checks().enabled("NoWebA")) noWeb(ctx);
    }

    private boolean commonSkip(MoveContext ctx, boolean allowLiquid) {
        Player p = ctx.player;
        if (plugin.exemption().creativeLike(p) || plugin.exemption().worldExempt(p)) return true;
        if (plugin.exemption().grace(ctx.data)) return true;
        if (p.isFlying() || p.isGliding() || p.isInsideVehicle()) return true;
        if (p.isRiptiding() || ctx.now - ctx.data.lastRiptideMs < 1500) return true;
        if (p.hasPotionEffect(PotionEffectType.LEVITATION) || p.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;
        if (!allowLiquid && (BlockUtil.isLiquidLike(ctx.feet) || BlockUtil.isLiquidLike(ctx.below))) return true;
        if (BlockUtil.isClimbable(ctx.feet)) return true;
        return false;
    }

    private void speedA(MoveContext ctx) {
        if (commonSkip(ctx, false) || ctx.lagging) {
            ctx.data.resetStreak("SpeedA");
            return;
        }
        if (ctx.data.hadRecentVelocity(900) || bouncing(ctx)) {
            ctx.data.resetStreak("SpeedA");
            return;
        }
        boolean sprint = ctx.player.isSprinting();
        double base = ctx.serverGround
                ? d("SpeedA", sprint ? "ground-sprint" : "ground-walk", sprint ? 0.40 : 0.26)
                : d("SpeedA", sprint ? "air-sprint" : "air-walk", sprint ? 0.62 : 0.40);
        double bonus = BlockUtil.iceTickBonus(ctx.below.getType()) + soulBonus(ctx.player, ctx.below.getType());
        double allowed = SpeedMath.tickAllowed(base, attr(ctx.player), bonus, d("SpeedA", "horizontal-margin", 0.06));
        if (ctx.bedrock) allowed += plugin.exemption().bedrockSpeedExtra();
        int need = i("SpeedA", "streak-to-flag", 4);
        boolean fail = ctx.horizontal > allowed;
        if (fail && ctx.data.streak("SpeedA", true) >= need) {
            plugin.flagger().flag(ctx.player, "SpeedA", plugin.flagger().vl("SpeedA", 1.5),
                    String.format(java.util.Locale.US, "h=%.3f>%.3f ground=%s sprint=%s", ctx.horizontal, allowed, ctx.serverGround, sprint),
                    plugin.flagger().setback("SpeedA", "from"));
        } else if (!fail) ctx.data.streak("SpeedA", false);
    }

    private void speedB(MoveContext ctx) {
        updateWindow(ctx);
        if (commonSkip(ctx, false) || ctx.lagging || ctx.data.speedWin.size() < 6) {
            ctx.data.resetStreak("SpeedB");
            return;
        }
        if (ctx.data.hadRecentVelocity(900) || bouncing(ctx)) {
            ctx.data.resetStreak("SpeedB");
            return;
        }
        long combatImm = plugin.getConfig().getLong("checks.SpeedB.combat-immunity-ms", 500);
        if (ctx.now - ctx.data.lastCombatMs <= combatImm) {
            ctx.data.resetStreak("SpeedB");
            return;
        }
        long oldest = ctx.data.speedWin.peekFirst().t;
        double secs = Math.max(0.25, (ctx.now - oldest) / 1000.0);
        double sum = 0;
        int air = 0;
        int sprintTicks = 0;
        int total = 0;
        for (PlayerData.Sample sample : ctx.data.speedWin) {
            sum += sample.h;
            total++;
            if (!sample.ground) air++;
            if (sample.sprint) sprintTicks++;
        }
        double bps = sum / secs;
        ctx.data.lastBps = bps;
        boolean sprint = sprintTicks > total / 2;
        double base = sprint ? d("SpeedB", "sprint-bps", 6.6) : d("SpeedB", "walk-bps", 5.2);
        if (air > 0) base = Math.max(base, d("SpeedB", "jump-bps", 8.8));
        double liquid = 1.0;
        if (BlockUtil.isWaterLike(ctx.feet) || BlockUtil.isWaterLike(ctx.below)) {
            int ds = BlockUtil.enchant(ctx.player.getInventory().getBoots(), Enchantment.DEPTH_STRIDER);
            liquid = 1.0 + 0.18 * ds;
            if (ctx.player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) liquid *= 1.7;
        }
        double allowed = SpeedMath.bpsAllowed(base, attr(ctx.player), BlockUtil.iceBpsMultiplier(ctx.below.getType()),
                liquid, d("SpeedB", "margin-bps", 0.8));
        if (ctx.bedrock) allowed += 1.2;
        int need = i("SpeedB", "streak-to-flag", 3);
        boolean fail = bps > allowed;
        if (fail && ctx.data.streak("SpeedB", true) >= need) {
            plugin.flagger().flag(ctx.player, "SpeedB", plugin.flagger().vl("SpeedB", 1.5),
                    String.format(java.util.Locale.US, "bps=%.2f>%.2f", bps, allowed),
                    plugin.flagger().setback("SpeedB", "from"));
        } else if (!fail) ctx.data.streak("SpeedB", false);
    }

    private void updateWindow(MoveContext ctx) {
        ctx.data.speedWin.addLast(new PlayerData.Sample(ctx.now, ctx.horizontal, ctx.serverGround, ctx.player.isSprinting()));
        long window = plugin.getConfig().getLong("checks.SpeedB.window-ms", 900);
        while (!ctx.data.speedWin.isEmpty() && ctx.now - ctx.data.speedWin.peekFirst().t > window) {
            ctx.data.speedWin.removeFirst();
        }
    }

    private void flyA(MoveContext ctx) {
        if (commonSkip(ctx, false) || ctx.serverGround || ctx.lagging) {
            ctx.data.streak("FlyA", false);
            return;
        }
        int hoverTicks = i("FlyA", "hover-threshold-ticks", 10);
        boolean falling = ctx.dy < -0.06 || ctx.data.fallDistance > 1.2;
        if (falling || ctx.data.hadRecentVelocity(800) || ctx.feet.getType() == Material.BUBBLE_COLUMN
                || ctx.feet.getType() == Material.COBWEB || ctx.above.getType() == Material.COBWEB
                || ctx.feet.getType() == Material.POWDER_SNOW) {
            ctx.data.streak("FlyA", false);
            return;
        }
        boolean hovering = Math.abs(ctx.dy) < 0.004 && ctx.data.airTicks >= hoverTicks;
        int need = i("FlyA", "streak-to-flag", 4);
        if (hovering && ctx.data.streak("FlyA", true) >= need) {
            plugin.flagger().flag(ctx.player, "FlyA", plugin.flagger().vl("FlyA", 1.5),
                    "hover air=" + ctx.data.airTicks, plugin.flagger().setback("FlyA", "safe"));
        } else if (!hovering) ctx.data.streak("FlyA", false);
    }

    private void flyB(MoveContext ctx) {
        if (commonSkip(ctx, false) || ctx.serverGround) {
            ctx.data.streak("FlyB", false);
            return;
        }
        if (ctx.data.hadRecentVelocity(1000) || ctx.feet.getType() == Material.BUBBLE_COLUMN
                || ctx.feet.getType() == Material.COBWEB || ctx.above.getType() == Material.COBWEB
                || ctx.feet.getType() == Material.POWDER_SNOW) {
            ctx.data.streak("FlyB", false);
            return;
        }
        boolean rising = ctx.dy > d("FlyB", "min-dy", 0.08) && ctx.data.airTicks >= i("FlyB", "min-air-ticks", 8);
        int need = i("FlyB", "streak-to-flag", 4);
        if (rising && ctx.data.streak("FlyB", true) >= need) {
            plugin.flagger().flag(ctx.player, "FlyB", plugin.flagger().vl("FlyB", 2.0),
                    String.format(java.util.Locale.US, "dy=%.3f air=%d", ctx.dy, ctx.data.airTicks),
                    plugin.flagger().setback("FlyB", "safe"));
        } else if (!rising) ctx.data.streak("FlyB", false);
    }

    private void noFall(MoveContext ctx) {
        if (!ctx.data.awaitingFallDamage) return;
        long grace = plugin.getConfig().getLong("checks.NoFallA.grace-ms", 500);
        if (ctx.now - ctx.data.landAtMs < grace) return;
        if (landingExempt(ctx)) {
            ctx.data.awaitingFallDamage = false;
            return;
        }
        plugin.flagger().flag(ctx.player, "NoFallA", plugin.flagger().vl("NoFallA", 2.0),
                "no fall damage", plugin.flagger().setback("NoFallA", "safe"));
        ctx.data.awaitingFallDamage = false;
    }

    private boolean landingExempt(MoveContext ctx) {
        Material below = ctx.below.getType();
        if (below == Material.SLIME_BLOCK || below == Material.HONEY_BLOCK || below == Material.HAY_BLOCK) return true;
        if (BlockUtil.isLiquidLike(ctx.feet) || BlockUtil.isLiquidLike(ctx.below)) return true;
        if (ctx.player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return true;
        if (below == Material.POWDER_SNOW || below == Material.COBWEB) return true;
        ItemStack boots = ctx.player.getInventory().getBoots();
        int ff = BlockUtil.enchant(boots, Enchantment.FEATHER_FALLING);
        double min = d("NoFallA", "min-fall", 5.5);
        return ff >= 4 && ctx.data.fallDistance < min + 3;
    }

    private void jesusA(MoveContext ctx) {
        if (!BlockUtil.isWaterLike(ctx.feet) || plugin.exemption().creativeLike(ctx.player) || ctx.lagging) {
            ctx.data.streak("JesusA", false);
            return;
        }
        if (ctx.player.isInsideVehicle() || plugin.exemption().grace(ctx.data)) return;
        double allowed = d("JesusA", "swim-max-h", 0.28);
        if (!BlockUtil.isWaterLike(ctx.above)) allowed = d("JesusA", "surface-max-h", 0.40);
        int ds = BlockUtil.enchant(ctx.player.getInventory().getBoots(), Enchantment.DEPTH_STRIDER);
        allowed += 0.06 * ds;
        if (ctx.player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE)) allowed += 0.22;
        allowed += d("JesusA", "margin", 0.05);
        allowed *= Math.max(1, attr(ctx.player));
        int need = i("JesusA", "streak-to-flag", 4);
        boolean fail = ctx.horizontal > allowed;
        if (fail && ctx.data.streak("JesusA", true) >= need) {
            plugin.flagger().flag(ctx.player, "JesusA", plugin.flagger().vl("JesusA", 1.0),
                    String.format(java.util.Locale.US, "h=%.3f>%.3f", ctx.horizontal, allowed),
                    plugin.flagger().setback("JesusA", "from"));
        } else if (!fail) ctx.data.streak("JesusA", false);
    }

    private void jesusB(MoveContext ctx) {
        boolean waterFeet = BlockUtil.isWaterLike(ctx.feet);
        boolean supported = BlockUtil.serverOnGround(ctx.player) && !BlockUtil.isLiquidLike(ctx.below);
        if (!waterFeet || supported || ctx.player.isInsideVehicle() || plugin.exemption().creativeLike(ctx.player)) {
            ctx.data.streak("JesusB", false);
            return;
        }
        if (ctx.below.getType() == Material.FROSTED_ICE || ctx.below.getType() == Material.LILY_PAD) {
            ctx.data.streak("JesusB", false);
            return;
        }
        boolean walking = (ctx.clientGround || ctx.serverGround) && ctx.horizontal > d("JesusB", "min-h", 0.12);
        int need = i("JesusB", "streak-to-flag", 4);
        if (walking && ctx.data.streak("JesusB", true) >= need) {
            plugin.flagger().flag(ctx.player, "JesusB", plugin.flagger().vl("JesusB", 1.5),
                    "water walk h=" + String.format(java.util.Locale.US, "%.3f", ctx.horizontal),
                    plugin.flagger().setback("JesusB", "from"));
        } else if (!walking) ctx.data.streak("JesusB", false);
    }

    private void step(MoveContext ctx) {
        if (commonSkip(ctx, false) || !ctx.serverGround || !ctx.data.lastServerGround || ctx.lagging) {
            ctx.data.streak("StepA", false);
            return;
        }
        if (ctx.dy <= 0 || isSteppable(ctx.below.getType()) || ctx.data.hadRecentVelocity(600)) {
            ctx.data.streak("StepA", false);
            return;
        }
        double max = Compat.attribute(ctx.player, "step_height", d("StepA", "max-step", 0.6));
        double allowed = max + d("StepA", "margin", 0.08);
        int need = i("StepA", "streak-to-flag", 3);
        boolean fail = ctx.dy > allowed;
        if (fail && ctx.data.streak("StepA", true) >= need) {
            plugin.flagger().flag(ctx.player, "StepA", plugin.flagger().vl("StepA", 1.0),
                    String.format(java.util.Locale.US, "dy=%.3f>%.3f", ctx.dy, allowed),
                    plugin.flagger().setback("StepA", "from"));
        } else if (!fail) ctx.data.streak("StepA", false);
    }

    private void phase(MoveContext ctx) {
        if (plugin.exemption().creativeLike(ctx.player) || plugin.exemption().grace(ctx.data)) {
            ctx.data.resetStreak("PhaseA");
            return;
        }
        if (BlockUtil.isLiquidLike(ctx.feet) || ctx.player.isInsideVehicle() || ctx.player.isGliding()) {
            ctx.data.streak("PhaseA", false);
            return;
        }
        boolean inside = BlockUtil.insideSolid(ctx.player);
        int need = i("PhaseA", "streak-to-flag", 3);
        if (inside && ctx.data.streak("PhaseA", true) >= need) {
            plugin.flagger().flag(ctx.player, "PhaseA", plugin.flagger().vl("PhaseA", 2.0),
                    "inside solid " + ctx.feet.getType(), plugin.flagger().setback("PhaseA", "safe"));
        } else if (!inside) ctx.data.streak("PhaseA", false);
    }

    private void noSlow(MoveContext ctx) {
        if (!ctx.serverGround || commonSkip(ctx, false) || ctx.lagging || ctx.data.hadRecentVelocity(800)) {
            ctx.data.streak("NoSlowA", false);
            return;
        }
        ItemStack using = ctx.player.getItemInUse();
        boolean usingItem = ctx.player.isHandRaised() || ctx.player.isBlocking()
                || (using != null && using.getType() != Material.AIR && using.getType() != Material.CROSSBOW);
        if (!usingItem) {
            ctx.data.streak("NoSlowA", false);
            return;
        }
        double allowed = d("NoSlowA", "blocking-walk", 0.22) + d("NoSlowA", "margin", 0.06);
        allowed *= Math.max(1, attr(ctx.player));
        allowed += BlockUtil.iceTickBonus(ctx.below.getType());
        int need = i("NoSlowA", "streak-to-flag", 4);
        boolean fail = ctx.horizontal > allowed;
        if (fail && ctx.data.streak("NoSlowA", true) >= need) {
            plugin.flagger().flag(ctx.player, "NoSlowA", plugin.flagger().vl("NoSlowA", 1.0),
                    String.format(java.util.Locale.US, "h=%.3f>%.3f using", ctx.horizontal, allowed),
                    plugin.flagger().setback("NoSlowA", "from"));
        } else if (!fail) ctx.data.streak("NoSlowA", false);
    }

    private void highJump(MoveContext ctx) {
        if (commonSkip(ctx, false)) {
            ctx.data.jumpStartY = Double.NaN;
            return;
        }
        if (ctx.data.lastServerGround && !ctx.serverGround && ctx.dy > 0.05 && !ctx.data.hadRecentVelocity(700)) {
            ctx.data.jumpStartY = ctx.from.getY();
            ctx.data.jumpPeak = ctx.to.getY();
        }
        if (!ctx.serverGround && !Double.isNaN(ctx.data.jumpStartY)) {
            ctx.data.jumpPeak = Math.max(ctx.data.jumpPeak, ctx.to.getY());
        }
        if (ctx.serverGround && !Double.isNaN(ctx.data.jumpStartY)) {
            double gained = ctx.data.jumpPeak - ctx.data.jumpStartY;
            PotionEffect boost = ctx.player.getPotionEffect(PotionEffectType.JUMP_BOOST);
            int amp = boost == null ? -1 : boost.getAmplifier();
            double max = SpeedMath.maxJumpHeight(amp, Compat.attribute(ctx.player, "jump_strength", 0.42));
            max += d("HighJumpA", "margin", 0.45);
            if (!ctx.lagging && gained > max && ctx.data.streak("HighJumpA", true) >= i("HighJumpA", "streak-to-flag", 2)) {
                plugin.flagger().flag(ctx.player, "HighJumpA", plugin.flagger().vl("HighJumpA", 1.5),
                        String.format(java.util.Locale.US, "height=%.2f>%.2f", gained, max),
                        plugin.flagger().setback("HighJumpA", "safe"));
            } else if (gained <= max) ctx.data.streak("HighJumpA", false);
            ctx.data.jumpStartY = Double.NaN;
        }
    }

    private void fastClimb(MoveContext ctx) {
        if (!BlockUtil.isClimbable(ctx.feet) || ctx.feet.getType() == Material.SCAFFOLDING) {
            ctx.data.streak("FastClimbA", false);
            return;
        }
        if (plugin.exemption().creativeLike(ctx.player) || ctx.lagging) return;
        double max = d("FastClimbA", "max-dy", 0.30);
        boolean fail = ctx.dy > max;
        if (fail && ctx.data.streak("FastClimbA", true) >= i("FastClimbA", "streak-to-flag", 4)) {
            plugin.flagger().flag(ctx.player, "FastClimbA", plugin.flagger().vl("FastClimbA", 1.0),
                    String.format(java.util.Locale.US, "dy=%.3f", ctx.dy), "none");
        } else if (!fail) ctx.data.streak("FastClimbA", false);
    }

    private void noWeb(MoveContext ctx) {
        boolean web = ctx.feet.getType() == Material.COBWEB || ctx.above.getType() == Material.COBWEB;
        if (!web || plugin.exemption().creativeLike(ctx.player)) {
            ctx.data.streak("NoWebA", false);
            return;
        }
        boolean fail = ctx.horizontal > d("NoWebA", "max-h", 0.14) || ctx.dy > d("NoWebA", "max-dy", 0.14);
        if (fail && ctx.data.streak("NoWebA", true) >= i("NoWebA", "streak-to-flag", 3)) {
            plugin.flagger().flag(ctx.player, "NoWebA", plugin.flagger().vl("NoWebA", 1.5),
                    String.format(java.util.Locale.US, "h=%.3f dy=%.3f", ctx.horizontal, ctx.dy),
                    plugin.flagger().setback("NoWebA", "from"));
        } else if (!fail) ctx.data.streak("NoWebA", false);
    }

    public void onLandDamage(Player player) {
        PlayerData data = plugin.data().get(player);
        data.awaitingFallDamage = false;
        data.fallDistance = 0;
    }

    public void trackFall(MoveContext ctx) {
        if (ctx.dy < 0 && !BlockUtil.isLiquidLike(ctx.feet) && !ctx.player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            ctx.data.fallDistance += -ctx.dy;
        }
        if (!ctx.data.lastServerGround && ctx.serverGround) {
            double min = d("NoFallA", "min-fall", 5.5);
            if (ctx.data.fallDistance >= min && !landingExempt(ctx)) {
                ctx.data.awaitingFallDamage = true;
                ctx.data.landAtMs = ctx.now;
            }
            ctx.data.fallDistance = 0;
        }
    }

    private boolean isSteppable(Material type) {
        return org.bukkit.Tag.SLABS.isTagged(type) || org.bukkit.Tag.STAIRS.isTagged(type)
                || org.bukkit.Tag.CARPETS.isTagged(type) || org.bukkit.Tag.TRAPDOORS.isTagged(type)
                || type == Material.SCAFFOLDING || type == Material.SNOW || type == Material.SNOW_BLOCK;
    }

    private double soulBonus(Player player, Material below) {
        if (below != Material.SOUL_SAND && below != Material.SOUL_SOIL) return 0;
        int level = BlockUtil.enchant(player.getInventory().getBoots(), Enchantment.SOUL_SPEED);
        if (level <= 0) return 0;
        return 0.06 + 0.04 * (level - 1);
    }

    private boolean bouncing(MoveContext ctx) {
        return BlockUtil.isBounce(ctx.below.getType()) && Math.abs(ctx.dy) > 0.18;
    }

    private double attr(Player player) {
        double raw = Compat.attribute(player, "movement_speed", Double.NaN);
        double mult = Double.isNaN(raw) || raw <= 0 ? 1.0 : SpeedMath.attributeMultiplier(raw);
        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null && plugin.getConfig().contains("checks.SpeedA.speed-effect-per-level")) {
            double per = plugin.getConfig().getDouble("checks.SpeedA.speed-effect-per-level", 0.06);
            double legacy = 1.0 + (per / 0.36) * (speed.getAmplifier() + 1);
            mult = Math.max(mult, legacy);
        } else if ((Double.isNaN(raw) || raw <= 0) && speed != null) {
            mult += 0.2 * (speed.getAmplifier() + 1);
        }
        return mult;
    }

    private double d(String check, String key, double def) {
        return plugin.getConfig().getDouble("checks." + check + "." + key, def);
    }

    private int i(String check, String key, int def) {
        return plugin.getConfig().getInt("checks." + check + "." + key, def);
    }
}
