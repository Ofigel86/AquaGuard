package dev.aquaguard.checks.combat;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.PlayerData;
import dev.aquaguard.util.BlockUtil;
import dev.aquaguard.util.ClickPattern;
import dev.aquaguard.util.Compat;
import dev.aquaguard.util.MathUtil;
import dev.aquaguard.util.ReachMath;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CombatChecks {
    private final AquaGuard plugin;
    private final Map<UUID, ArrayDeque<Double>> reachSamples = new ConcurrentHashMap<>();

    public CombatChecks(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public void onAttack(Player attacker, Entity victim, boolean melee) {
        if (attacker == null || plugin.exemption().bypass(attacker)) return;
        long now = System.currentTimeMillis();
        PlayerData data = plugin.data().get(attacker);
        data.lastCombatMs = now;
        if (victim instanceof Player playerVictim) plugin.data().get(playerVictim).lastCombatMs = now;
        data.attackTimes.addLast(now);
        PlayerData.trim(data.attackTimes, now, Math.max(2000, plugin.getConfig().getLong("checks.AutoClickerA.window-ms", 3000)));

        if (melee && victim instanceof LivingEntity living) {
            if (plugin.checks().enabled("ReachA")) reach(attacker, living, data, false);
            if (plugin.checks().enabled("ReachB")) reach(attacker, living, data, true);
            if (plugin.checks().enabled("WallHitA") && victim instanceof Player) wall(attacker, living);
            if (plugin.checks().enabled("HitboxA") && victim instanceof Player) hitbox(attacker, living);
            if (plugin.checks().enabled("KeepSprintA")) keepSprint(attacker, data);
        }
        if (plugin.checks().enabled("AutoClickerA")) autoClicker(attacker, data);
        if (plugin.checks().enabled("AttackCooldownA")) cooldown(attacker, data);
        if (plugin.checks().enabled("AttackIntervalB")) interval(attacker, data);
        if (melee && victim instanceof Player playerVictim) {
            if (plugin.checks().enabled("AimSnapA")) aim(attacker, playerVictim, data, now);
            if (plugin.checks().enabled("TargetSwitchC")) targetSwitch(attacker, playerVictim, data, now);
            if (plugin.checks().enabled("MultiAuraA")) multi(attacker, playerVictim, data, now);
        }
        data.lastYaw = attacker.getLocation().getYaw();
        data.lastPitch = attacker.getLocation().getPitch();
        data.lastHitMs = now;
    }

    public void critical(Player attacker, PlayerData data, boolean critical) {
        if (!plugin.checks().enabled("CriticalsA") || !critical || plugin.exemption().lagging(attacker)) return;
        boolean fake = data.airTicks <= i("CriticalsA", "max-air-ticks", 2)
                && data.fallDistance < d("CriticalsA", "max-fall", 0.15)
                && Math.abs(data.lastDy) < 0.12
                && !attacker.isInWater()
                && !attacker.isInsideVehicle();
        if (fake && data.streak("CriticalsA", true) >= i("CriticalsA", "streak-to-flag", 4)) {
            plugin.flagger().flag(attacker, "CriticalsA", plugin.flagger().vl("CriticalsA", 1.0),
                    "crit air=" + data.airTicks, "none");
        } else if (!fake) data.streak("CriticalsA", false);
    }

    private void reach(Player attacker, LivingEntity victim, PlayerData data, boolean average) {
        if (plugin.exemption().creativeLike(attacker) || plugin.exemption().lagging(attacker)) return;
        if (!average && plugin.getConfig().getBoolean("checks.ReachA.players-only", true) && !(victim instanceof Player)) return;
        double dist = eyeToBox(attacker, victim);
        Vector vel = victim.getVelocity();
        dist = Math.max(0, dist - vel.length() * (Compat.ping(attacker) / 1000.0));
        double base = Compat.attribute(attacker, "entity_interaction_range", d("ReachA", "base-max", 3.0));
        if (attacker.getGameMode() == GameMode.CREATIVE) base = Math.max(base, 5.5);
        double extra = d("ReachA", "extra-margin", 0.35);
        if (plugin.exemption().bedrock(data)) extra += plugin.exemption().bedrockReachExtra();
        int victimPing = victim instanceof Player vp ? Compat.ping(vp) : 0;
        double allowed = ReachMath.allowed(base, d("ReachA", "ping-coeff", 0.004), 0.001, extra,
                Compat.ping(attacker), victimPing, Compat.tps(), 0);
        if (!average) {
            ArrayDeque<Double> samples = reachSamples.computeIfAbsent(attacker.getUniqueId(), id -> new ArrayDeque<>());
            samples.addLast(dist);
            while (samples.size() > 8) samples.removeFirst();
            if (dist > allowed && data.streak("ReachA", true) >= i("ReachA", "streak-to-flag", 3)) {
                plugin.flagger().flag(attacker, "ReachA", plugin.flagger().vl("ReachA", 1.5),
                        String.format(Locale.US, "dist=%.2f>%.2f ping=%d", dist, allowed, Compat.ping(attacker)), "none");
            } else if (dist <= allowed) data.streak("ReachA", false);
            return;
        }
        ArrayDeque<Double> samples = reachSamples.get(attacker.getUniqueId());
        if (samples == null || samples.size() < 6) return;
        double sum = 0;
        for (double v : samples) sum += v;
        double avg = sum / samples.size();
        if (avg > allowed - 0.05) {
            plugin.flagger().flag(attacker, "ReachB", plugin.flagger().vl("ReachB", 1.0),
                    String.format(Locale.US, "avg=%.2f>%.2f", avg, allowed - 0.05), "none");
        }
    }

    private void wall(Player attacker, LivingEntity victim) {
        double dist = attacker.getEyeLocation().distance(victim.getLocation());
        if (dist < 1.3) {
            attackerData(attacker).streak("WallHitA", false);
            return;
        }
        boolean los = BlockUtil.hasLineOfSight(attacker, victim);
        PlayerData data = attackerData(attacker);
        if (!los && data.streak("WallHitA", true) >= i("WallHitA", "streak-to-flag", 3)) {
            plugin.flagger().flag(attacker, "WallHitA", plugin.flagger().vl("WallHitA", 1.0), "no line of sight", "none");
        } else if (los) data.streak("WallHitA", false);
    }

    private void hitbox(Player attacker, LivingEntity victim) {
        if (plugin.exemption().lagging(attacker)) return;
        double angle = angleToBox(attacker, victim);
        double dist = eyeToBox(attacker, victim);
        double maxAngle = d("HitboxA", "max-angle", 42);
        boolean fail = dist > 1.8 && angle > maxAngle;
        PlayerData data = attackerData(attacker);
        if (fail && data.streak("HitboxA", true) >= i("HitboxA", "streak-to-flag", 3)) {
            plugin.flagger().flag(attacker, "HitboxA", plugin.flagger().vl("HitboxA", 1.0),
                    String.format(Locale.US, "angle=%.1f dist=%.2f", angle, dist), "none");
        } else if (!fail) data.streak("HitboxA", false);
    }

    private void autoClicker(Player attacker, PlayerData data) {
        if (data.attackTimes.size() < 8 || plugin.exemption().lagging(attacker)) return;
        long[] stamps = data.attackTimes.stream().mapToLong(Long::longValue).toArray();
        ClickPattern.Result result = ClickPattern.analyze(stamps,
                d("AutoClickerA", "min-cps", 9),
                d("AutoClickerA", "max-std-ms", 12),
                d("AutoClickerA", "max-cv", 0.08));
        double hard = d("AutoClickerA", "cps-limit", 16);
        boolean fail = result.cps() >= hard || result.robotic();
        if (fail && data.streak("AutoClickerA", true) >= i("AutoClickerA", "streak-to-flag", 2)) {
            plugin.flagger().flag(attacker, "AutoClickerA", plugin.flagger().vl("AutoClickerA", 1.0),
                    String.format(Locale.US, "cps=%.1f std=%.1f", result.cps(), result.stdMs()), "none");
        } else if (!fail) data.streak("AutoClickerA", false);
    }

    private void cooldown(Player attacker, PlayerData data) {
        if (data.attackTimes.size() < 2 || plugin.exemption().lagging(attacker)) return;
        Long[] arr = data.attackTimes.toArray(Long[]::new);
        long dt = arr[arr.length - 1] - arr[arr.length - 2];
        long min;
        if (plugin.getConfig().contains("checks.AttackCooldownA.min-ms")
                && !plugin.getConfig().contains("checks.AttackCooldownA.ratio")) {
            double scale = MathUtil.clamp(20.0 / Math.max(18.0, Compat.tps()), 0.9, 1.4);
            min = (long) Math.floor(plugin.getConfig().getLong("checks.AttackCooldownA.min-ms", 220) * scale);
        } else {
            double speed = Compat.attribute(attacker, "attack_speed", 4.0);
            min = (long) (1000.0 / Math.max(1.0, speed) * d("AttackCooldownA", "ratio", 0.45));
        }
        boolean fail = dt > 0 && dt < min;
        if (fail && data.streak("AttackCooldownA", true) >= 4) {
            plugin.flagger().flag(attacker, "AttackCooldownA", plugin.flagger().vl("AttackCooldownA", 0.5),
                    "dt=" + dt + " need>=" + min, "none");
        } else if (!fail) data.streak("AttackCooldownA", false);
    }

    private void interval(Player attacker, PlayerData data) {
        if (data.attackTimes.size() < 8) return;
        long[] stamps = data.attackTimes.stream().mapToLong(Long::longValue).toArray();
        ClickPattern.Result result = ClickPattern.analyze(stamps,
                d("AttackIntervalB", "min-cps", 6),
                d("AttackIntervalB", "max-std-ms", 20),
                d("AttackIntervalB", "max-cv", 0.12));
        if (result.robotic() && data.streak("AttackIntervalB", true) >= 2) {
            plugin.flagger().flag(attacker, "AttackIntervalB", plugin.flagger().vl("AttackIntervalB", 0.5),
                    String.format(Locale.US, "cps=%.1f std=%.0f cv=%.2f", result.cps(), result.stdMs(), result.cv()), "none");
        } else if (!result.robotic()) data.streak("AttackIntervalB", false);
    }

    private void aim(Player attacker, Player victim, PlayerData data, long now) {
        if (data.lastHitMs == 0) return;
        long dt = now - data.lastHitMs;
        double dYaw = MathUtil.angleDiff(data.lastYaw, attacker.getLocation().getYaw());
        double yawSpeed = dt > 0 ? dYaw * 1000.0 / dt : 0;
        double angle = angleToBox(attacker, victim);
        boolean fail = dt <= l("AimSnapA", "window-ms", 150)
                && yawSpeed > d("AimSnapA", "max-yaw-deg-per-s", 1100)
                && angle <= d("AimSnapA", "cone-after-deg", 10);
        if (fail && data.streak("AimSnapA", true) >= 3) {
            plugin.flagger().flag(attacker, "AimSnapA", plugin.flagger().vl("AimSnapA", 0.5),
                    String.format(Locale.US, "yaw=%.0f/s cone=%.1f", yawSpeed, angle), "none");
        } else if (!fail) data.streak("AimSnapA", false);
    }

    private void targetSwitch(Player attacker, Player victim, PlayerData data, long now) {
        UUID id = victim.getUniqueId();
        long dt = 9999;
        if (data.lastTarget != null && !data.lastTarget.equals(id)) {
            dt = data.lastTargetSwitchMs == 0 ? 9999 : now - data.lastTargetSwitchMs;
            data.lastTargetSwitchMs = now;
        }
        data.lastTarget = id;
        double angle = angleToBox(attacker, victim);
        boolean fail = dt <= l("TargetSwitchC", "max-ms", 60) && angle <= d("TargetSwitchC", "cone-deg", 8)
                && attacker.getLocation().distanceSquared(victim.getLocation()) < 25;
        if (fail && data.streak("TargetSwitchC", true) >= 3) {
            plugin.flagger().flag(attacker, "TargetSwitchC", plugin.flagger().vl("TargetSwitchC", 1.0),
                    "switch " + dt + "ms", "none");
        } else if (!fail) data.streak("TargetSwitchC", false);
    }

    private void multi(Player attacker, Player victim, PlayerData data, long now) {
        data.recentTargets.addLast(victim.getUniqueId());
        while (data.recentTargets.size() > 12) data.recentTargets.removeFirst();
        long window = l("MultiAuraA", "window-ms", 800);
        int unique = 0;
        var seen = new java.util.HashSet<UUID>();
        long[] times = data.attackTimes.stream().mapToLong(Long::longValue).toArray();
        int idx = Math.max(0, times.length - data.recentTargets.size());
        int tIndex = idx;
        for (UUID id : data.recentTargets) {
            long when = tIndex < times.length ? times[tIndex] : now;
            tIndex++;
            if (now - when <= window && seen.add(id)) unique++;
        }
        int need = i("MultiAuraA", "min-targets", 4);
        if (unique >= need && data.streak("MultiAuraA", true) >= 2) {
            plugin.flagger().flag(attacker, "MultiAuraA", plugin.flagger().vl("MultiAuraA", 1.5),
                    "targets=" + unique + " in " + window + "ms", "none");
        } else if (unique < need) data.streak("MultiAuraA", false);
    }

    private void keepSprint(Player attacker, PlayerData data) {
        data.sprintingOnHit = attacker.isSprinting();
        data.hBeforeHit = data.lastH;
        data.postHitMoves = 0;
    }

    public void afterMove(Player player, PlayerData data, double horizontal) {
        if (!plugin.checks().enabled("KeepSprintA") || !data.sprintingOnHit) return;
        if (data.postHitMoves++ > 3) {
            data.sprintingOnHit = false;
            return;
        }
        if (data.hadRecentVelocity(500) || !player.isSprinting() || data.hBeforeHit < 0.22) return;
        boolean kept = horizontal > data.hBeforeHit * d("KeepSprintA", "kept-ratio", 0.92);
        if (kept && data.streak("KeepSprintA", true) >= i("KeepSprintA", "streak-to-flag", 5)) {
            plugin.flagger().flag(player, "KeepSprintA", plugin.flagger().vl("KeepSprintA", 0.5),
                    String.format(Locale.US, "h=%.3f before=%.3f", horizontal, data.hBeforeHit), "none");
            data.sprintingOnHit = false;
        } else if (!kept) data.streak("KeepSprintA", false);
    }

    public void forget(UUID id) {
        reachSamples.remove(id);
    }

    private PlayerData attackerData(Player player) {
        return plugin.data().get(player);
    }

    private static double eyeToBox(Player attacker, LivingEntity victim) {
        Location eye = attacker.getEyeLocation();
        BoundingBox box = victim.getBoundingBox().expand(0.1);
        return MathUtil.distanceToAabb(eye.getX(), eye.getY(), eye.getZ(),
                box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    private static double angleToBox(Player attacker, LivingEntity victim) {
        Location eye = attacker.getEyeLocation();
        Vector look = eye.getDirection();
        BoundingBox box = victim.getBoundingBox().expand(0.2);
        double cx = MathUtil.clamp(eye.getX(), box.getMinX(), box.getMaxX());
        double cy = MathUtil.clamp(eye.getY(), box.getMinY(), box.getMaxY());
        double cz = MathUtil.clamp(eye.getZ(), box.getMinZ(), box.getMaxZ());
        Vector to = new Vector(cx, cy, cz).subtract(eye.toVector());
        if (to.lengthSquared() < 1e-6 || look.lengthSquared() < 1e-6) return 0;
        double dot = MathUtil.clamp(look.normalize().dot(to.normalize()), -1, 1);
        return Math.toDegrees(Math.acos(dot));
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
