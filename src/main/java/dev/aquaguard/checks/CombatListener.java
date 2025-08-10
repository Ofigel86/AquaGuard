package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.bypass.BypassManager;
import dev.aquaguard.core.DataManager;
import dev.aquaguard.core.ViolationManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.UUID;

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

    // ===== PvP: основное событие урона =====
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (bypass.isBypassed(attacker.getUniqueId())) return;

        long now = System.currentTimeMillis();
        var pad = data.get(attacker);
        var pvd = data.get(victim);
        pad.lastCombatMs = now;
        pvd.lastCombatMs = now;

        // окно интервалов ударов (для AttackIntervalB/ACoolA)
        pad.attackTimes.addLast(now);
        long atkWindow = Math.max(2000L, plugin.getConfig().getLong("checks.AutoClickerA.window-ms", 3000));
        while (!pad.attackTimes.isEmpty() && (now - pad.attackTimes.getFirst()) > atkWindow) {
            pad.attackTimes.removeFirst();
        }

        // KA/TPAura сигналы
        if (checks.enabled("ReachA")) checkReachA(attacker, victim);
        if (checks.enabled("WallHitA") && !attacker.hasLineOfSight(victim)) {
            flag(attacker, "WallHitA", plugin.getConfig().getDouble("checks.WallHitA.add-vl", 1.0), "no LOS");
        }
        if (checks.enabled("AttackCooldownA")) checkAttackCooldownA(attacker);
        if (checks.enabled("AttackIntervalB")) checkAttackIntervalB(attacker);
        if (checks.enabled("AimSnapA")) checkAimSnapA(attacker, victim, now);
        if (checks.enabled("TargetSwitchC")) checkTargetSwitchC(attacker, victim, now);
    }

    // ===== AutoTotem: триггеры =====

    // Swap (F) — быстрый перенос тотема в offhand
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (bypass.isBypassed(p.getUniqueId())) return;

        ItemStack main = e.getMainHandItem();
        if (main != null && main.getType() == Material.TOTEM_OF_UNDYING) {
            var pd = data.get(p);
            pd.lastOffhandTotemMs = System.currentTimeMillis();
            pd.lastOffhandByInventoryMs = 0L;
        }
    }

    // Инвентарь — перенос тотема в offhand через GUI
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (bypass.isBypassed(p.getUniqueId())) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack off = p.getInventory().getItem(EquipmentSlot.OFF_HAND);
            if (off != null && off.getType() == Material.TOTEM_OF_UNDYING) {
                var pd = data.get(p);
                long now = System.currentTimeMillis();
                pd.lastOffhandTotemMs = now;
                pd.lastOffhandByInventoryMs = now;
            }
        });
    }

    // Отметка «низкое HP» для автототема
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (bypass.isBypassed(p.getUniqueId())) return;

        double after = Math.max(0.0, p.getHealth() - e.getFinalDamage());
        double lowHearts = plugin.getConfig().getDouble("checks.AutoTotem.low-hp-hearts", 2.0);
        if (after <= (lowHearts * 2.0)) {
            data.get(p).lastLowHpMs = System.currentTimeMillis();
        }
    }

    // Само воскрешение — анализ паттернов автототема
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onResurrect(EntityResurrectEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (bypass.isBypassed(p.getUniqueId())) return;

        var pd = data.get(p);
        long now = System.currentTimeMillis();

        pd.resurrectTimes.addLast(now);
        long repWin = Math.max(10 * 60_000L, plugin.getConfig().getLong("checks.AutoTotem.repeat-window-ms", 10 * 60_000L));
        while (!pd.resurrectTimes.isEmpty() && (now - pd.resurrectTimes.getFirst()) > repWin) {
            pd.resurrectTimes.removeFirst();
        }

        long quickSwap = plugin.getConfig().getLong("checks.AutoTotem.quick-swap-ms", 200);
        boolean justSwapped = (pd.lastOffhandTotemMs > 0) && ((now - pd.lastOffhandTotemMs) <= quickSwap);
        boolean cameFromInv = (pd.lastOffhandByInventoryMs > 0) && ((now - pd.lastOffhandByInventoryMs) <= quickSwap);
        boolean lowHpSwap = (pd.lastLowHpMs > 0) && ((now - pd.lastLowHpMs) <= Math.max(300, quickSwap + 100));

        if (checks.enabled("AutoTotemA") && (justSwapped || cameFromInv)) {
            flag(p, "AutoTotemA", plugin.getConfig().getDouble("checks.AutoTotem.AutoTotemA.add-vl", 1.0),
                    "quick-swap=" + (now - pd.lastOffhandTotemMs) + "ms inv=" + cameFromInv);
        }
        if (checks.enabled("AutoTotemB")) {
            int minQuick = plugin.getConfig().getInt("checks.AutoTotem.AutoTotemB.min-quick-uses", 3);
            int quickCount = 0;
            for (Long t : pd.resurrectTimes) {
                if ((t - pd.lastOffhandTotemMs) <= quickSwap) quickCount++;
            }
            if (quickCount >= minQuick) {
                flag(p, "AutoTotemB", plugin.getConfig().getDouble("checks.AutoTotem.AutoTotemB.add-vl", 1.0),
                        "quick-uses=" + quickCount);
            }
        }
        if (checks.enabled("AutoTotemC") && lowHpSwap && justSwapped) {
            flag(p, "AutoTotemC", plugin.getConfig().getDouble("checks.AutoTotem.AutoTotemC.add-vl", 1.0),
                    "lowHP→swap " + (now - pd.lastLowHpMs) + "ms");
        }
    }

    // ===== Реализация PvP-сигналов =====
    private void checkReachA(Player attacker, Player victim) {
        Location ae = attacker.getEyeLocation();
        Location vc = victim.getLocation().add(0.0, 0.9, 0.0);
        double dist = ae.distance(vc);

        double baseMax = plugin.getConfig().getDouble("checks.ReachA.base-max", 3.2);
        double pingCoeff = plugin.getConfig().getDouble("checks.ReachA.ping-coeff", 0.003);
        double extra = plugin.getConfig().getDouble("checks.ReachA.extra-margin", 0.25);

        int ping = 0;
        try { ping = attacker.getPing(); } catch (Throwable ignored) {}
        ping = Math.max(0, Math.min(300, ping));

        double allowed = baseMax + pingCoeff * ping + extra;
        if (dist > allowed) {
            flag(attacker, "ReachA", plugin.getConfig().getDouble("checks.ReachA.add-vl", 1.5),
                    String.format("dist=%.2f>%.2f", dist, allowed));
        }
    }

    private void checkAttackCooldownA(Player attacker) {
        var pd = data.get(attacker);
        if (pd.attackTimes.size() < 2) return;
        var arr = new ArrayList<>(pd.attackTimes);
        long dt = arr.get(arr.size() - 1) - arr.get(arr.size() - 2);

        long minMs = plugin.getConfig().getLong("checks.AttackCooldownA.min-ms", 220);
        if (dt > 0 && dt < minMs) {
            flag(attacker, "AttackCooldownA", plugin.getConfig().getDouble("checks.AttackCooldownA.add-vl", 0.5),
                    "dt=" + dt + "ms");
        }
    }

    private void checkAttackIntervalB(Player attacker) {
        var pd = data.get(attacker);
        if (pd.attackTimes.size() < 6) return;

        var arr = new ArrayList<>(pd.attackTimes);
        double[] iv = new double[arr.size() - 1];
        for (int i = 1; i < arr.size(); i++) iv[i - 1] = (arr.get(i) - arr.get(i - 1));

        double mean = 0.0; for (double v : iv) mean += v; mean /= iv.length;
        double var = 0.0; for (double v : iv) var += (v - mean) * (v - mean); var /= iv.length;
        double std = Math.sqrt(var);

        double cps = iv.length / (Math.max(0.2, (arr.get(arr.size() - 1) - arr.get(0)) / 1000.0));
        if (cps < plugin.getConfig().getDouble("checks.AttackIntervalB.min-cps", 6.0)) return;

        double maxStd = plugin.getConfig().getDouble("checks.AttackIntervalB.max-std-ms", 50.0);
        if (std < maxStd) {
            flag(attacker, "AttackIntervalB", plugin.getConfig().getDouble("checks.AttackIntervalB.add-vl", 0.5),
                    String.format("cps=%.1f std=%.0fms", cps, std));
        }
    }

    private void checkAimSnapA(Player attacker, Player victim, long now) {
        var pd = data.get(attacker);
        float yaw = attacker.getLocation().getYaw();
        float pitch = attacker.getLocation().getPitch();

        long dt = (pd.lastHitMs > 0) ? (now - pd.lastHitMs) : 9999;
        double dYaw = angleDiff(pd.lastHitYaw, yaw);
        double dPitch = Math.abs(pd.lastHitPitch - pitch);

        long maxWindow = plugin.getConfig().getLong("checks.AimSnapA.window-ms", 150);
        double yawThr = plugin.getConfig().getDouble("checks.AimSnapA.max-dyaw", 120.0);
        double pitchThr = plugin.getConfig().getDouble("checks.AimSnapA.max-dpitch", 60.0);

        if (dt <= maxWindow && (dYaw > yawThr || dPitch > pitchThr)) {
            flag(attacker, "AimSnapA", plugin.getConfig().getDouble("checks.AimSnapA.add-vl", 0.5),
                    String.format("dYaw=%.0f dPitch=%.0f dt=%dms", dYaw, dPitch, dt));
        }

        pd.lastHitYaw = yaw;
        pd.lastHitPitch = pitch;
        pd.lastHitMs = now;
    }

    private void checkTargetSwitchC(Player attacker, Player victim, long now) {
        var pd = data.get(attacker);
        UUID vid = victim.getUniqueId();
        if (pd.lastTarget != null && !pd.lastTarget.equals(vid)) {
            pd.lastTargetSwitchMs = now;
        }
        long dt = (pd.lastTargetSwitchMs > 0) ? (now - pd.lastTargetSwitchMs) : 9999;
        long max = plugin.getConfig().getLong("checks.TargetSwitchC.max-ms", 70);

        if (dt <= max) {
            double delta = horizontalAngleTo(attacker, victim);
            double cone = plugin.getConfig().getDouble("checks.TargetSwitchC.cone-deg", 6.0);
            if (delta <= cone) {
                flag(attacker, "TargetSwitchC", plugin.getConfig().getDouble("checks.TargetSwitchC.add-vl", 1.0),
                        String.format("switch-hit dt=%dms ang=%.1f°", dt, delta));
            }
        }
        pd.lastTarget = vid;
    }

    // ===== helpers =====

    private Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p2) return p2;
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

    // Горизонтальный угол между направлением взгляда и вектором на цель (в градусах)
    private static double horizontalAngleTo(Player from, Entity to) {
        Location eye = from.getEyeLocation();
        Vector look = eye.getDirection().setY(0).normalize();
        Vector dir = to.getLocation().add(0, 0.9, 0).toVector().subtract(eye.toVector()).setY(0).normalize();
        double dot = Math.max(-1.0, Math.min(1.0, look.dot(dir)));
        return Math.toDegrees(Math.acos(dot));
    }
}
