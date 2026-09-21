package dev.aquaguard.checks.movement;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.checks.MoveContext;
import dev.aquaguard.util.BlockUtil;
import dev.aquaguard.util.Compat;
import dev.aquaguard.util.MathUtil;
import dev.aquaguard.util.SpeedMath;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * Чеки с более высоким риском ложных срабатываний. По умолчанию выключены, пороги жёсткие.
 */
public final class MovementExtraChecks {
    private final AquaGuard plugin;

    public MovementExtraChecks(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public void run(MoveContext ctx) {
        if (plugin.checks().enabled("StrafeA")) strafeA(ctx);
        if (plugin.checks().enabled("StrafeB")) strafeB(ctx);
        if (plugin.checks().enabled("StrafeC")) strafeC(ctx);
        if (plugin.checks().enabled("TimerA")) timer(ctx);
        if (plugin.checks().enabled("BlinkA")) blink(ctx);
        if (plugin.checks().enabled("SpiderA")) spider(ctx);
        if (plugin.checks().enabled("ElytraA")) elytra(ctx);
        if (plugin.checks().enabled("BoatFlyA")) boatFly(ctx);
        if (plugin.checks().enabled("GroundSpoofA")) groundSpoof(ctx);
        if (plugin.checks().enabled("InventoryMoveA")) inventoryMove(ctx);
        if (plugin.checks().enabled("VelocityA")) velocity(ctx);
        if (plugin.checks().enabled("InvalidPitchA")) pitch(ctx);
    }

    private void strafeA(MoveContext ctx) {
        if (ctx.lagging || ctx.data.hadRecentVelocity(800) || BlockUtil.isIce(ctx.below.getType())) {
            ctx.data.streak("StrafeA", false);
            return;
        }
        double delta = Math.max(0, ctx.horizontal - ctx.data.lastH);
        double allowed = d("StrafeA", "acc-limit", 0.22) + d("StrafeA", "margin", 0.04);
        boolean fail = delta > allowed && ctx.horizontal > 0.25;
        if (fail && ctx.data.streak("StrafeA", true) >= i("StrafeA", "streak-to-flag", 4)) {
            plugin.flagger().flag(ctx.player, "StrafeA", plugin.flagger().vl("StrafeA", 1.0),
                    String.format(java.util.Locale.US, "Δh=%.3f", delta), "none");
        } else if (!fail) ctx.data.streak("StrafeA", false);
    }

    private void strafeB(MoveContext ctx) {
        double angle = MathUtil.horizontalAngle(ctx.data.lastDx, ctx.data.lastDz, ctx.dx, ctx.dz);
        boolean fail = angle >= d("StrafeB", "angle-deg", 120)
                && ctx.horizontal > d("StrafeB", "min-h", 0.22)
                && ctx.data.lastH > d("StrafeB", "min-h", 0.22)
                && !BlockUtil.isIce(ctx.below.getType())
                && !ctx.data.hadRecentVelocity(800);
        if (fail && ctx.data.streak("StrafeB", true) >= i("StrafeB", "streak-to-flag", 4)) {
            plugin.flagger().flag(ctx.player, "StrafeB", plugin.flagger().vl("StrafeB", 1.0),
                    String.format(java.util.Locale.US, "turn=%.0f°", angle), "none");
        } else if (!fail) ctx.data.streak("StrafeB", false);
    }

    private void strafeC(MoveContext ctx) {
        if (ctx.serverGround || ctx.lagging || ctx.player.isGliding() || ctx.player.isInsideVehicle()
                || plugin.exemption().creativeLike(ctx.player) || plugin.exemption().grace(ctx.data)
                || ctx.data.hadRecentVelocity(900) || BlockUtil.isIce(ctx.below.getType())
                || BlockUtil.isLiquidLike(ctx.feet) || ctx.data.airTicks < i("StrafeC", "min-air", 6)) {
            ctx.data.streak("StrafeC", false);
            return;
        }
        double allowed = d("StrafeC", "air-h", 0.72);
        allowed *= Math.max(1.0, SpeedMath.attributeMultiplier(Compat.attribute(ctx.player, "movement_speed", 0.1)));
        if (ctx.bedrock) allowed += plugin.exemption().bedrockSpeedExtra();
        boolean fail = ctx.horizontal > allowed;
        if (fail && ctx.data.streak("StrafeC", true) >= i("StrafeC", "streak-to-flag", 4)) {
            plugin.flagger().flag(ctx.player, "StrafeC", plugin.flagger().vl("StrafeC", 1.0),
                    String.format(java.util.Locale.US, "air h=%.3f>%.3f", ctx.horizontal, allowed), "none");
        } else if (!fail) ctx.data.streak("StrafeC", false);
    }

    private void timer(MoveContext ctx) {
        if (ctx.horizontal < 0.01 && Math.abs(ctx.dy) < 0.01) return;
        if (ctx.data.moveWindowStart == 0 || ctx.now - ctx.data.moveWindowStart > 1000) {
            ctx.data.moveWindowStart = ctx.now;
            ctx.data.movesInWindow = 1;
            return;
        }
        ctx.data.movesInWindow++;
        long span = ctx.now - ctx.data.moveWindowStart;
        int limit = i("TimerA", "max-moves", 28);
        if (span >= 900 && ctx.data.movesInWindow > limit && Compat.tps() >= 19.2 && !ctx.lagging) {
            plugin.flagger().flag(ctx.player, "TimerA", plugin.flagger().vl("TimerA", 1.0),
                    "moves=" + ctx.data.movesInWindow + " span=" + span, "none");
            ctx.data.moveWindowStart = ctx.now;
            ctx.data.movesInWindow = 0;
        }
    }

    private void blink(MoveContext ctx) {
        long gap = ctx.data.lastMoveMs == 0 ? 0 : ctx.now - ctx.data.lastMoveMs;
        boolean fail = gap > l("BlinkA", "gap-ms", 500)
                && ctx.horizontal > d("BlinkA", "min-distance", 5.0)
                && !ctx.player.isGliding() && !ctx.player.isInsideVehicle()
                && !plugin.exemption().grace(ctx.data)
                && !ctx.lagging;
        if (fail) {
            plugin.flagger().flag(ctx.player, "BlinkA", plugin.flagger().vl("BlinkA", 1.5),
                    String.format(java.util.Locale.US, "gap=%dms dist=%.2f", gap, ctx.horizontal),
                    plugin.flagger().setback("BlinkA", "from"));
        }
    }

    private void spider(MoveContext ctx) {
        if (ctx.serverGround || BlockUtil.isClimbable(ctx.feet) || BlockUtil.isLiquidLike(ctx.feet)
                || ctx.player.hasPotionEffect(PotionEffectType.LEVITATION) || ctx.data.hadRecentVelocity(800)) {
            ctx.data.streak("SpiderA", false);
            return;
        }
        boolean climbing = ctx.data.airTicks > 10 && ctx.dy > 0.05 && ctx.dy < 0.28 && BlockUtil.hasWallAdjacent(ctx.player);
        if (climbing && ctx.data.streak("SpiderA", true) >= i("SpiderA", "streak-to-flag", 6)) {
            plugin.flagger().flag(ctx.player, "SpiderA", plugin.flagger().vl("SpiderA", 1.5),
                    String.format(java.util.Locale.US, "dy=%.3f air=%d", ctx.dy, ctx.data.airTicks),
                    plugin.flagger().setback("SpiderA", "from"));
        } else if (!climbing) ctx.data.streak("SpiderA", false);
    }

    private void elytra(MoveContext ctx) {
        if (!ctx.player.isGliding()) {
            ctx.data.streak("ElytraA", false);
            return;
        }
        ItemStack chest = ctx.player.getInventory().getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA) {
            plugin.flagger().flag(ctx.player, "ElytraA", plugin.flagger().vl("ElytraA", 2.0),
                    "glide without elytra", plugin.flagger().setback("ElytraA", "safe"));
            return;
        }
        boolean boosted = ctx.now - ctx.data.lastFireworkMs < l("ElytraA", "firework-grace-ms", 2500);
        double hLimit = boosted ? d("ElytraA", "boosted-h", 8.0) : d("ElytraA", "glide-h", 2.8);
        boolean badUp = !boosted && ctx.dy > d("ElytraA", "max-up", 0.18) && ctx.data.airTicks > 8;
        boolean badH = ctx.horizontal > hLimit;
        if ((badUp || badH) && ctx.data.streak("ElytraA", true) >= i("ElytraA", "streak-to-flag", 4)) {
            plugin.flagger().flag(ctx.player, "ElytraA", plugin.flagger().vl("ElytraA", 1.5),
                    String.format(java.util.Locale.US, "h=%.2f dy=%.3f boost=%s", ctx.horizontal, ctx.dy, boosted),
                    plugin.flagger().setback("ElytraA", "safe"));
        } else if (!badUp && !badH) ctx.data.streak("ElytraA", false);
    }

    private void boatFly(MoveContext ctx) {
        if (!(ctx.player.getVehicle() instanceof Boat boat)) {
            ctx.data.streak("BoatFlyA", false);
            return;
        }
        if (boat.isInWater() || BlockUtil.isLiquidLike(ctx.below) || ctx.below.getType() == Material.BUBBLE_COLUMN
                || BlockUtil.isBounce(ctx.below.getType())) {
            ctx.data.streak("BoatFlyA", false);
            return;
        }
        double dy = boat.getVelocity().getY();
        boolean fail = dy > d("BoatFlyA", "min-dy", 0.18) && !ctx.serverGround;
        if (fail && ctx.data.streak("BoatFlyA", true) >= i("BoatFlyA", "streak-to-flag", 5)) {
            plugin.flagger().flag(ctx.player, "BoatFlyA", plugin.flagger().vl("BoatFlyA", 1.5),
                    String.format(java.util.Locale.US, "boatDy=%.3f", dy), "none");
        } else if (!fail) ctx.data.streak("BoatFlyA", false);
    }

    private void groundSpoof(MoveContext ctx) {
        if (plugin.exemption().creativeLike(ctx.player) || ctx.player.isGliding() || ctx.player.isInsideVehicle()
                || BlockUtil.isLiquidLike(ctx.feet) || BlockUtil.isClimbable(ctx.feet) || ctx.lagging) {
            ctx.data.streak("GroundSpoofA", false);
            return;
        }
        boolean spoof = ctx.clientGround && !ctx.serverGround && ctx.data.airTicks >= i("GroundSpoofA", "min-air", 12)
                && Math.abs(ctx.dy) < 0.05 && !ctx.data.hadRecentVelocity(600);
        if (spoof && ctx.data.streak("GroundSpoofA", true) >= i("GroundSpoofA", "streak-to-flag", 6)) {
            plugin.flagger().flag(ctx.player, "GroundSpoofA", plugin.flagger().vl("GroundSpoofA", 1.0),
                    "client ground air=" + ctx.data.airTicks, "none");
        } else if (!spoof) ctx.data.streak("GroundSpoofA", false);
    }

    private void inventoryMove(MoveContext ctx) {
        if (!ctx.data.inventoryOpen || ctx.now - ctx.data.inventoryOpenMs < 400 || ctx.lagging) {
            ctx.data.streak("InventoryMoveA", false);
            return;
        }
        boolean fail = ctx.horizontal > d("InventoryMoveA", "max-h", 0.20) && ctx.serverGround;
        if (fail && ctx.data.streak("InventoryMoveA", true) >= i("InventoryMoveA", "streak-to-flag", 5)) {
            plugin.flagger().flag(ctx.player, "InventoryMoveA", plugin.flagger().vl("InventoryMoveA", 1.0),
                    String.format(java.util.Locale.US, "h=%.3f inv", ctx.horizontal), "none");
        } else if (!fail) ctx.data.streak("InventoryMoveA", false);
    }

    private void velocity(MoveContext ctx) {
        if (ctx.data.velocityX == 0 && ctx.data.velocityZ == 0) return;
        if (ctx.now - ctx.data.lastVelocityMs > 400) {
            ctx.data.velocityX = 0;
            ctx.data.velocityZ = 0;
            return;
        }
        if (ctx.player.isBlocking() || ctx.feet.getType() == Material.COBWEB || BlockUtil.isLiquidLike(ctx.feet) || ctx.lagging) {
            return;
        }
        double expected = Math.hypot(ctx.data.velocityX, ctx.data.velocityZ);
        double resist = Compat.attribute(ctx.player, "knockback_resistance", 0);
        expected *= Math.max(0.15, 1.0 - resist);
        if (expected < d("VelocityA", "min-expected", 0.28)) return;
        boolean ignored = ctx.horizontal < expected * d("VelocityA", "taken-ratio", 0.22);
        if (ignored && ctx.data.streak("VelocityA", true) >= i("VelocityA", "streak-to-flag", 3)) {
            plugin.flagger().flag(ctx.player, "VelocityA", plugin.flagger().vl("VelocityA", 1.5),
                    String.format(java.util.Locale.US, "got=%.3f expected=%.3f", ctx.horizontal, expected), "none");
            ctx.data.velocityX = 0;
            ctx.data.velocityZ = 0;
        } else if (!ignored) ctx.data.streak("VelocityA", false);
    }

    private void pitch(MoveContext ctx) {
        float pitch = ctx.to.getPitch();
        if (pitch < -90.01f || pitch > 90.01f) {
            plugin.flagger().flag(ctx.player, "InvalidPitchA", plugin.flagger().vl("InvalidPitchA", 2.0),
                    "pitch=" + pitch, "none");
        }
    }

    private double d(String check, String key, double def) {
        return plugin.getConfig().getDouble("checks." + check + "." + key, def);
    }

    private int i(String check, String key, int def) {
        return plugin.getConfig().getInt("checks." + check + "." + key, def);
    }

    private long l(String check, String key, long def) {
        return plugin.getConfig().getLong("checks." + check + "." + key, def);
    }
}
