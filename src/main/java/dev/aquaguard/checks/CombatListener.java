package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.bypass.BypassManager;
import dev.aquaguard.core.DataManager;
import dev.aquaguard.core.ViolationManager;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatListener implements Listener {
    private final AquaGuard plugin;
    private final DataManager data;
    private final ViolationManager vl;
    private final CheckManager checks;
    private final BypassManager bypass;

    public CombatListener(AquaGuard plugin, DataManager data, ViolationManager vl,
                          CheckManager checks, BypassManager bypass) {
        this.plugin = plugin;
        this.data = data;
        this.vl = vl;
        this.checks = checks;
        this.bypass = bypass;
    }

    // Локальный кэш стриков (без изменений в DataManager)
    private static class Local {
        int reachStreak = 0;
        int wallStreak = 0;
        int acoolStreak = 0;
        int intervalStreak = 0;
        int aimStreak = 0;
        int switchStreak = 0;
        int totemAStreak = 0, totemBStreak = 0, totemCStreak = 0;
    }
    private final Map<UUID, Local> local = new ConcurrentHashMap<>();
    private Local st(Player p) { return local.computeIfAbsent(p.getUniqueId(), k -> new Local()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        local.remove(e.getPlayer().getUniqueId());
    }

    // ===== PvP: основное событие урона =====
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null) return;
        if (!(e.getEntity() instanceof Player)) return;
        Player victim = (Player) e.getEntity();
        if (bypass.isBypassed(attacker.getUniqueId())) return;

        long now = System.currentTimeMillis();
        DataManager.PlayerData pad = data.get(attacker);
        DataManager.PlayerData pvd = data.get(victim);
        pad.lastCombatMs = now;
        pvd.lastCombatMs = now;

        // окно интервалов ударов (для AttackIntervalB/AttackCooldownA)
        pad.attackTimes.addLast(now);
        long atkWindow = Math.max(2000L, cfgL("checks.AutoClickerA.window-ms", 3000));
        while (!pad.attackTimes.isEmpty() && (now - pad.attackTimes.getFirst()) > atkWindow) {
            pad.attackTimes.removeFirst();
        }

        // Checks
        if (checks.enabled("ReachA"))            checkReachA(attacker, victim);
        if (checks.enabled("WallHitA"))          checkWallHitA(attacker, victim);
        if (checks.enabled("AttackCooldownA"))   checkAttackCooldownA(attacker);
        if (checks.enabled("AttackIntervalB"))   checkAttackIntervalB(attacker);
        if (checks.enabled("AimSnapA"))          checkAimSnapA(attacker, victim, now);
        if (checks.enabled("TargetSwitchC"))     checkTargetSwitchC(attacker, victim, now);
    }

    // ===== AutoTotem: триггеры =====

    // Быстрый перенос тотема в offhand (клавиша F)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (bypass.isBypassed(p.getUniqueId())) return;

        ItemStack main = e.getMainHandItem();
        if (main != null && main.getType() == Material.TOTEM_OF_UNDYING) {
            DataManager.PlayerData pd = data.get(p);
            pd.lastOffhandTotemMs = System.currentTimeMillis();
            pd.lastOffhandByInventoryMs = 0L;
        }
    }

    // Перенос тотема в offhand через GUI
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (bypass.isBypassed(p.getUniqueId())) return;

        // Проверим уже после применения клика
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack off = p.getInventory().getItem(EquipmentSlot.OFF_HAND);
            if (off != null && off.getType() == Material.TOTEM_OF_UNDYING) {
                DataManager.PlayerData pd = data.get(p);
                long now = System.currentTimeMillis();
                pd.lastOffhandTotemMs = now;
                pd.lastOffhandByInventoryMs = now;
            }
        });
    }

    // Маркер низкого HP — для сценария "lowHP -> swap"
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (bypass.isBypassed(p.getUniqueId())) return;

        double after = Math.max(0.0, p.getHealth() - e.getFinalDamage());
        double lowHearts = cfgD("checks.AutoTotem.low-hp-hearts", 2.0);
        if (after <= (lowHearts * 2.0)) {
            data.get(p).lastLowHpMs = System.currentTimeMillis();
        }
    }

    // Воскрес (тотем) — анализ паттернов
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onResurrect(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();
        if (bypass.isBypassed(p.getUniqueId())) return;

        DataManager.PlayerData pd = data.get(p);
        long now = System.currentTimeMillis();

        pd.resurrectTimes.addLast(now);
        long repWin = Math.max(10 * 60_000L, cfgL("checks.AutoTotem.repeat-window-ms", 10 * 60_000L));
        while (!pd.resurrectTimes.isEmpty() && (now - pd.resurrectTimes.getFirst()) > repWin) {
            pd.resurrectTimes.removeFirst();
        }

        long quickSwap = cfgL("checks.AutoTotem.quick-swap-ms", 200);
        boolean justSwapped = (pd.lastOffhandTotemMs > 0) && ((now - pd.lastOffhandTotemMs) <= quickSwap);
        boolean cameFromInv = (pd.lastOffhandByInventoryMs > 0) && ((now - pd.lastOffhandByInventoryMs) <= quickSwap);
        boolean lowHpSwap = (pd.lastLowHpMs > 0) && ((now - pd.lastLowHpMs) <= Math.max(300, quickSwap + 100));

        Local ls = st(p);

        // A: мгновенный свап под тотем прямо перед тотемом
        if (checks.enabled("AutoTotemA") && (justSwapped || cameFromInv)) {
            if (++ls.totemAStreak >= 1) {
                flag(p, "AutoTotemA", cfgDAny(new String[]{
                        "checks.AutoTotemA.add-vl", "checks.AutoTotem.AutoTotemA.add-vl"}, 1.0),
                        "quick-swap=" + (now - pd.lastOffhandTotemMs) + "ms inv=" + cameFromInv);
                ls.totemAStreak = 1;
            }
        } else ls.totemAStreak = Math.max(0, ls.totemAStreak - 1);

        // B: часто повторяющиеся "быстрые" воскрешения (стата за окно)
        if (checks.enabled("AutoTotemB")) {
            int minQuick = (int) cfgDAny(new String[]{
                    "checks.AutoTotemB.min-quick-uses", "checks.AutoTotem.AutoTotemB.min-quick-uses"}, 3.0);
            int quickCount = 0;
            for (Long t : pd.resurrectTimes) {
                if ((t - pd.lastOffhandTotemMs) <= quickSwap) quickCount++;
            }
            if (quickCount >= minQuick) {
                if (++ls.totemBStreak >= 1) {
                    flag(p, "AutoTotemB", cfgDAny(new String[]{
                            "checks.AutoTotemB.add-vl", "checks.AutoTotem.AutoTotemB.add-vl"}, 1.0),
                            "quick-uses=" + quickCount);
                    ls.totemBStreak = 1;
                }
            } else ls.totemBStreak = Math.max(0, ls.totemBStreak - 1);
        }

        // C: низкое ХП -> быстрый свап -> тотем
        if (checks.enabled("AutoTotemC") && lowHpSwap && justSwapped) {
            if (++ls.totemCStreak >= 1) {
                flag(p, "AutoTotemC", cfgDAny(new String[]{
                        "checks.AutoTotemC.add-vl", "checks.AutoTotem.AutoTotemC.add-vl"}, 1.0),
                        "lowHP→swap " + (now - pd.lastLowHpMs) + "ms");
                ls.totemCStreak = 1;
            }
        } else ls.totemCStreak = Math.max(0, ls.totemCStreak - 1);
    }

    // ===== Реализация PvP-сигналов =====

    // ReachA — дистанция до хитбокса, учёт пинга и TPS
    private void checkReachA(Player attacker, Player victim) {
        Local ls = st(attacker);

        double baseMax   = cfgD("checks.ReachA.base-max", 3.2);
        double pingCoeff = cfgD("checks.ReachA.ping-coeff", 0.003);
        double extra     = cfgD("checks.ReachA.extra-margin", 0.25);

        int ap = safePing(attacker);
        int vp = safePing(victim);
        int ping = clamp(ap, 0, 350);
        int mixPing = clamp((ap + vp) / 2, 0, 350);

        double tps = currentTPS();
        double tpsScale = clampD(20.0 / Math.max(18.0, tps), 1.0, 1.25);

        double allowed = (baseMax + pingCoeff * ping + 0.0015 * mixPing + extra) * tpsScale;

        double dist = reachDistanceEyeToBox(attacker, victim);

        // простая предикция: жертва движется, клиент атакера видел её ближе
        Vector vVel = velocityApprox(victim);
        dist = Math.max(0.0, dist - vVel.length() * (ap / 1000.0));

        if (dist > allowed) {
            if (++ls.reachStreak >= 2) {
                flag(attacker, "ReachA", cfgD("checks.ReachA.add-vl", 1.5),
                        String.format("dist=%.2f>%.2f ping=%d/%d tps=%.1f", dist, allowed, ap, vp, tps));
                ls.reachStreak = 2;
            }
        } else {
            ls.reachStreak = Math.max(0, ls.reachStreak - 1);
        }
    }

    // WallHitA — LOS через rayTrace до центра AABB
    private void checkWallHitA(Player attacker, Player victim) {
        Local ls = st(attacker);
        if (hasLine(attacker, victim)) { ls.wallStreak = Math.max(0, ls.wallStreak - 1); return; }

        if (++ls.wallStreak >= 2) {
            flag(attacker, "WallHitA", cfgD("checks.WallHitA.add-vl", 1.0), "no LOS by rayTrace");
            ls.wallStreak = 2;
        }
    }

    // AttackCooldownA — быстрые клики ниже кулдауна, нормализовано по TPS
    private void checkAttackCooldownA(Player attacker) {
        DataManager.PlayerData pd = data.get(attacker);
        if (pd.attackTimes.size() < 2) return;
        ArrayList<Long> arr = new ArrayList<>(pd.attackTimes);
        long dt = arr.get(arr.size() - 1) - arr.get(arr.size() - 2);

        long minMs = cfgL("checks.AttackCooldownA.min-ms", 220);
        double tps = currentTPS();
        double scale = clampD(20.0 / Math.max(18.0, tps), 0.9, 1.4);
        long need = (long) Math.floor(minMs * scale);

        Local ls = st(attacker);
        if (dt > 0 && dt < need) {
            if (++ls.acoolStreak >= 2) {
                flag(attacker, "AttackCooldownA", cfgD("checks.AttackCooldownA.add-vl", 0.5),
                        "dt=" + dt + "ms need>=" + need + "ms (tps=" + String.format("%.1f", tps) + ")");
                ls.acoolStreak = 2;
            }
        } else ls.acoolStreak = Math.max(0, ls.acoolStreak - 1);
    }

    // AttackIntervalB — ровный интервал на высокой CPS (std и CV)
    private void checkAttackIntervalB(Player attacker) {
        DataManager.PlayerData pd = data.get(attacker);
        if (pd.attackTimes.size() < 6) return;

        ArrayList<Long> arr = new ArrayList<>(pd.attackTimes);
        int n = arr.size() - 1;
        double[] iv = new double[n];
        for (int i = 1; i < arr.size(); i++) iv[i - 1] = (arr.get(i) - arr.get(i - 1));

        double mean = 0.0; for (double v : iv) mean += v; mean /= n;
        double var = 0.0; for (double v : iv) { double d = v - mean; var += d * d; } var /= n;
        double std = Math.sqrt(var);
        double cv = (mean > 0) ? std / mean : 1.0;

        double secs = Math.max(0.2, (arr.get(arr.size() - 1) - arr.get(0)) / 1000.0);
        double cps = n / secs;

        double minCps = cfgD("checks.AttackIntervalB.min-cps", 6.0);
        double maxStd = cfgD("checks.AttackIntervalB.max-std-ms", 50.0);
        double maxCv  = cfgD("checks.AttackIntervalB.max-cv", 0.18);

        Local ls = st(attacker);
        if (cps >= minCps && (std < maxStd || cv < maxCv)) {
            if (++ls.intervalStreak >= 2) {
                flag(attacker, "AttackIntervalB", cfgD("checks.AttackIntervalB.add-vl", 0.5),
                        String.format("cps=%.1f std=%.0fms cv=%.2f", cps, std, cv));
                ls.intervalStreak = 2;
            }
        } else ls.intervalStreak = Math.max(0, ls.intervalStreak - 1);
    }

    // AimSnapA — быстрый “щелчок” прицелом к цели (угловая скорость + попадание в конус)
    private void checkAimSnapA(Player attacker, Player victim, long now) {
        DataManager.PlayerData pd = data.get(attacker);
        float yaw = attacker.getLocation().getYaw();
        float pitch = attacker.getLocation().getPitch();

        long dt = (pd.lastHitMs > 0) ? (now - pd.lastHitMs) : 9999;
        double dYaw = angleDiff(pd.lastHitYaw, yaw);
        double dPitch = Math.abs(pd.lastHitPitch - pitch);

        double yawSpeed = (dt > 0) ? (dYaw * 1000.0 / dt) : 0.0;
        double pitchSpeed = (dt > 0) ? (dPitch * 1000.0 / dt) : 0.0;

        double cone = cfgD("checks.AimSnapA.cone-after-deg", 8.0);
        double angToTarget = horizontalAngleTo(attacker, victim);

        long maxWindow = cfgL("checks.AimSnapA.window-ms", 150);
        double yawThrSpd = cfgD("checks.AimSnapA.max-yaw-deg-per-s", 900.0);
        double pitchThrSpd = cfgD("checks.AimSnapA.max-pitch-deg-per-s", 700.0);

        Local ls = st(attacker);
        if (dt <= maxWindow && (yawSpeed > yawThrSpd || pitchSpeed > pitchThrSpd) && angToTarget <= cone) {
            if (++ls.aimStreak >= 2) {
                flag(attacker, "AimSnapA", cfgD("checks.AimSnapA.add-vl", 0.5),
                        String.format("yaw=%.0f/s pitch=%.0f/s dt=%dms cone=%.1f°", yawSpeed, pitchSpeed, dt, angToTarget));
                ls.aimStreak = 2;
            }
        } else ls.aimStreak = Math.max(0, ls.aimStreak - 1);

        pd.lastHitYaw = yaw;
        pd.lastHitPitch = pitch;
        pd.lastHitMs = now;
    }

    // TargetSwitchC — быстрое переключение между целями внутри узкого конуса на близкой дистанции
    private void checkTargetSwitchC(Player attacker, Player victim, long now) {
        DataManager.PlayerData pd = data.get(attacker);
        UUID vid = victim.getUniqueId();
        if (pd.lastTarget != null && !pd.lastTarget.equals(vid)) {
            pd.lastTargetSwitchMs = now;
        }
        long dt = (pd.lastTargetSwitchMs > 0) ? (now - pd.lastTargetSwitchMs) : 9999;
        long max = cfgL("checks.TargetSwitchC.max-ms", 70);

        Local ls = st(attacker);
        if (dt <= max) {
            double delta = horizontalAngleTo(attacker, victim);
            double cone = cfgD("checks.TargetSwitchC.cone-deg", 6.0);
            double maxDist = cfgD("checks.TargetSwitchC.max-distance", 5.5);
            if (delta <= cone && attacker.getLocation().distanceSquared(victim.getLocation()) <= (maxDist * maxDist)) {
                if (++ls.switchStreak >= 2) {
                    flag(attacker, "TargetSwitchC", cfgD("checks.TargetSwitchC.add-vl", 1.0),
                            String.format("switch-hit dt=%dms ang=%.1f°", dt, delta));
                    ls.switchStreak = 2;
                }
            } else ls.switchStreak = Math.max(0, ls.switchStreak - 1);
        } else ls.switchStreak = Math.max(0, ls.switchStreak - 1);

        pd.lastTarget = vid;
    }

    // ===== helpers =====

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (damager instanceof Projectile) {
            Projectile pr = (Projectile) damager;
            if (pr.getShooter() instanceof Player) return (Player) pr.getShooter();
        }
        return null;
    }

    private void flag(Player p, String check, double addVl, String debug) {
        if (!checks.enabled(check)) return;
        vl.add(p.getUniqueId(), check, addVl, debug);
        vl.maybePunish(p.getUniqueId(), check);
    }

    private static double angleDiff(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return (d > 180f) ? (360f - d) : d;
    }

    // Горизонтальный угол между взглядом и вектором на цель
    private static double horizontalAngleTo(Player from, Entity to) {
        Location eye = from.getEyeLocation();
        Vector look = eye.getDirection().setY(0).normalize();
        Vector dir = to.getLocation().add(0, 0.9, 0).toVector().subtract(eye.toVector()).setY(0).normalize();
        double dot = Math.max(-1.0, Math.min(1.0, look.dot(dir)));
        return Math.toDegrees(Math.acos(dot));
    }

    // Дистанция от глаз до ближайшей точки хитбокса цели
    private static double reachDistanceEyeToBox(Player attacker, LivingEntity victim) {
        Location eye = attacker.getEyeLocation();
        Vector eyePos = eye.toVector();

        try {
            BoundingBox box = victim.getBoundingBox();
            box = box.expand(0.01); // небольшой запас
            double x = clampD(eyePos.getX(), box.getMinX(), box.getMaxX());
            double y = clampD(eyePos.getY(), box.getMinY(), box.getMaxY());
            double z = clampD(eyePos.getZ(), box.getMinZ(), box.getMaxZ());
            return eyePos.distance(new Vector(x, y, z));
        } catch (Throwable ignore) {
            // На очень старых ядрах — фолбек к центру 0.9Y
            Location vc = victim.getLocation().add(0.0, 0.9, 0.0);
            return eye.distance(vc);
        }
    }

    // Точный LOS через rayTraceBlocks
    private static boolean hasLine(Player attacker, LivingEntity victim) {
        Location eye = attacker.getEyeLocation();
        World w = eye.getWorld();
        if (w == null) return attacker.hasLineOfSight(victim);

        Vector target = victimCenterVector(victim);
        Vector dir = target.clone().subtract(eye.toVector());
        double max = dir.length() + 0.5;
        if (max <= 0.01) return true;
        dir.normalize();

        RayTraceResult r = w.rayTraceBlocks(eye, dir, max, FluidCollisionMode.NEVER, true);
        return r == null; // нет блока на пути — LOS есть
    }

    private static Vector victimCenterVector(LivingEntity victim) {
        try {
            BoundingBox b = victim.getBoundingBox();
            double cx = (b.getMinX() + b.getMaxX()) * 0.5;
            double cy = (b.getMinY() + b.getMaxY()) * 0.5;
            double cz = (b.getMinZ() + b.getMaxZ()) * 0.5;
            return new Vector(cx, cy, cz);
        } catch (Throwable ignore) {
            return victim.getEyeLocation().toVector();
        }
    }

    private static int safePing(Player p) {
        try { return Math.max(0, p.getPing()); } catch (Throwable t) { return 0; }
    }

    private static Vector velocityApprox(Entity e) {
        try { return e.getVelocity(); } catch (Throwable t) { return new Vector(); }
    }

    // ===== конфиг/утилы =====

    // Работает и на Spigot (через рефлексию Paper#getTPS), и на Paper
    private double currentTPS() {
        try {
            Object server = Bukkit.getServer();
            Method m = server.getClass().getMethod("getTPS");
            Object res = m.invoke(server);
            if (res instanceof double[]) {
                double[] tps = (double[]) res;
                if (tps.length > 0) return Math.min(20.0, tps[0]);
            }
        } catch (Throwable ignored) {}
        return 20.0;
    }

    private double cfgD(String path, double def) {
        try { return plugin.getConfig().getDouble(path, def); } catch (Throwable t) { return def; }
    }
    private long cfgL(String path, long def) {
        try { return plugin.getConfig().getLong(path, def); } catch (Throwable t) { return def; }
    }
    // Берёт первое существующее значение из списка путей (для обратной совместимости)
    private double cfgDAny(String[] paths, double def) {
        for (String p : paths) {
            try {
                if (plugin.getConfig().isSet(p)) return plugin.getConfig().getDouble(p, def);
            } catch (Throwable ignored) {}
        }
        return def;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double clampD(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
