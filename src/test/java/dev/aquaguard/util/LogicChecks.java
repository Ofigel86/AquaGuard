package dev.aquaguard.util;

import dev.aquaguard.core.PunishmentLadder;
import dev.aquaguard.core.VlLedger;

import java.util.List;
import java.util.UUID;

/**
 * Набор проверок формул. Гоняется и из JUnit, и напрямую через main без сервера.
 */
public final class LogicChecks {
    private LogicChecks() {}

    public static void main(String[] args) {
        runAll();
        System.out.println("AquaGuard logic checks passed");
    }

    public static void runAll() {
        angles();
        aabb();
        reach();
        speed();
        clicks();
        scaffold();
        breakTime();
        ledger();
        ladder();
    }

    private static void angles() {
        eq(20, MathUtil.angleDiff(350f, 10f), 0.01);
        eq(20, MathUtil.angleDiff(10f, 350f), 0.01);
        eq(180, MathUtil.angleDiff(0f, 180f), 0.01);
        eq(20, MathUtil.angleDiff(-10f, 10f), 0.01);
        double[] look = MathUtil.lookXZ(0);
        eq(0, look[0], 1e-9);
        eq(1, look[1], 1e-9);
        eq(1, MathUtil.forwardDot(0, 0, 4), 1e-9);
        eq(-1, MathUtil.forwardDot(0, 0, -4), 1e-9);
        eq(1, MathUtil.forwardDot(90, -4, 0), 1e-6);
        eq(90, MathUtil.horizontalAngle(1, 0, 0, 1), 0.01);
    }

    private static void aabb() {
        eq(Math.sqrt(3), MathUtil.distanceToAabb(0, 0, 0, 1, 1, 1, 2, 2, 2), 1e-9);
        eq(0, MathUtil.distanceToAabb(1.2, 1.2, 1.2, 1, 1, 1, 2, 2, 2), 1e-9);
        check(MathUtil.aabbOverlap(0, 0, 0, 1, 1, 1, 0.5, 0.5, 0.5, 2, 2, 2));
        check(!MathUtil.aabbOverlap(0, 0, 0, 1, 1, 1, 1.1, 0, 0, 2, 1, 1));
    }

    private static void reach() {
        double allowed = ReachMath.allowed(3.1, 0.004, 0.001, 0.2, 100, 50, 20, 0);
        eq(3.775, allowed, 1e-9);
        double lagged = ReachMath.allowed(3.0, 0, 0, 0, 0, 0, 15, 0);
        check(lagged > 3.0);
        check(lagged <= 3.0 * 1.25 + 1e-9);
    }

    private static void speed() {
        eq(1.0, SpeedMath.attributeMultiplier(0.1), 1e-9);
        eq(1.2, SpeedMath.attributeMultiplier(0.12), 1e-9);
        eq(0.41, SpeedMath.tickAllowed(0.36, 1, 0, 0.05), 1e-9);
        eq(1.35, SpeedMath.maxJumpHeight(-1, 0.42), 1e-9);
        check(SpeedMath.maxJumpHeight(1, 0.42) > 2.0);
    }

    private static void clicks() {
        long[] flat = new long[12];
        for (int i = 0; i < flat.length; i++) flat[i] = i * 50L;
        ClickPattern.Result robotic = ClickPattern.analyze(flat, 8, 15, 0.12);
        check(robotic.robotic());
        check(robotic.cps() > 15);

        long[] human = new long[]{0, 140, 310, 420, 700, 980, 1100, 1400};
        ClickPattern.Result messy = ClickPattern.analyze(human, 8, 15, 0.12);
        check(!messy.robotic());
    }

    private static void scaffold() {
        int bridge = ScaffoldMath.placementScore(true, true, true, false, 82, -0.8, false);
        check(bridge >= 8);
        int building = ScaffoldMath.placementScore(false, false, false, false, 20, 0.9, true);
        check(building <= 0);
    }

    private static void breakTime() {
        long hand = BreakTimeMath.expectedMillis(1.5f, 1, false, 0, -1, 1);
        eq(7500, hand, 0);
        long pick = BreakTimeMath.expectedMillis(1.5f, 8, true, 0, -1, 1);
        eq(300, pick, 0);
        eq(8, BreakTimeMath.toolSpeed("DIAMOND_PICKAXE"), 0);
        eq(12, BreakTimeMath.toolSpeed("GOLDEN_PICKAXE"), 0);
        eq(1, BreakTimeMath.toolSpeed("AIR"), 0);
    }

    private static void ledger() {
        VlLedger ledger = new VlLedger();
        UUID id = UUID.randomUUID();
        eq(1.5, ledger.add(id, "SpeedA", 1.5), 1e-9);
        eq(3.0, ledger.add(id, "SpeedA", 1.5), 1e-9);
        ledger.add(id, "FlyA", 2);
        eq(5.0, ledger.total(id), 1e-9);
        ledger.decayAll(1.5);
        eq(1.5, ledger.get(id, "SpeedA"), 1e-9);
        eq(0.5, ledger.get(id, "FlyA"), 1e-9);
        ledger.decayAll(10);
        eq(0, ledger.total(id), 1e-9);
        ledger.add(id, "PhaseA", 4);
        ledger.reset(id, "PhaseA");
        eq(0, ledger.total(id), 1e-9);
    }

    private static void ladder() {
        PunishmentLadder ladder = new PunishmentLadder(List.of(
                new PunishmentLadder.Step(40, List.of("ban %player%")),
                new PunishmentLadder.Step(20, List.of("kick %player% %check%"))
        ));
        eq(-1, ladder.highestReached(10), 0);
        eq(0, ladder.highestReached(20), 0);
        eq(1, ladder.highestReached(40), 0);
        same("kick Steve SpeedA", PunishmentLadder.applyPlaceholders(
                "kick %player% %check%", "Steve", "SpeedA", "1", "20", "world"));
    }

    private static void eq(double expected, double actual, double eps) {
        if (Math.abs(expected - actual) > eps) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void same(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected " + expected + " but was " + actual);
        }
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("condition failed");
    }
}
