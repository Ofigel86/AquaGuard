package dev.aquaguard.core;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerData {
    public int airTicks;
    public boolean lastClientGround = true;
    public boolean lastServerGround = true;
    public double lastH;
    public double lastDx;
    public double lastDz;
    public double lastDy;
    public Location lastLoc;
    public Location lastSafeGround;
    public double fallDistance;
    public boolean awaitingFallDamage;
    public long landAtMs;
    public long lastVelocityMs;
    public double velocityX;
    public double velocityY;
    public double velocityZ;
    public long lastTeleportMs;
    public long joinMs = System.currentTimeMillis();
    public long lastRiptideMs;
    public long lastFireworkMs;
    public long lastSetbackMs;
    public boolean requestSetback;
    public boolean preferSafeSetback;
    public double jumpStartY = Double.NaN;
    public double jumpPeak;
    public long lastMoveMs;
    public long moveWindowStart;
    public int movesInWindow;
    public double lastBps;
    public long lastCombatMs;
    public float lastYaw;
    public float lastPitch;
    public UUID lastTarget;
    public long lastTargetSwitchMs;
    public long lastHitMs;
    public boolean sprintingOnHit;
    public double hBeforeHit;
    public int postHitMoves;
    public boolean inventoryOpen;
    public long inventoryOpenMs;
    public String clientBrand = "";
    public long useStartMs;
    public Material using;
    public long bowDrawStartMs;
    public long lastRegenMs;
    public long lastOffhandTotemMs;
    public long lastOffhandInventoryMs;
    public boolean offhandWasTotem;
    public long lastLowHpMs;
    public Location lastPlaced;
    public int scaffoldScore;
    public int towerCount;
    public long towerWindowStart;
    public double towerStartY;

    public final ArrayDeque<Sample> speedWin = new ArrayDeque<>();
    public final ArrayDeque<Long> placeTimes = new ArrayDeque<>();
    public final ArrayDeque<Long> breakTimes = new ArrayDeque<>();
    public final ArrayDeque<Long> attackTimes = new ArrayDeque<>();
    public final ArrayDeque<Long> clickTimes = new ArrayDeque<>();
    public final ArrayDeque<Long> resurrectTimes = new ArrayDeque<>();
    public final ArrayDeque<Long> totemSwaps = new ArrayDeque<>();
    public final ArrayDeque<UUID> recentTargets = new ArrayDeque<>();
    public final Map<String, Long> breakStart = new ConcurrentHashMap<>();
    public final Map<String, Integer> streaks = new ConcurrentHashMap<>();

    public static final class Sample {
        public final long t;
        public final double h;
        public final boolean ground;
        public final boolean sprint;

        public Sample(long t, double h, boolean ground, boolean sprint) {
            this.t = t;
            this.h = h;
            this.ground = ground;
            this.sprint = sprint;
        }
    }

    public int streak(String check, boolean fail) {
        if (fail) return streaks.merge(check, 1, Integer::sum);
        int next = Math.max(0, streaks.getOrDefault(check, 0) - 1);
        if (next == 0) streaks.remove(check);
        else streaks.put(check, next);
        return next;
    }

    public void resetStreak(String check) {
        streaks.remove(check);
    }

    public boolean hadRecentVelocity(long windowMs) {
        return System.currentTimeMillis() - lastVelocityMs <= windowMs;
    }

    public static void trim(ArrayDeque<Long> queue, long now, long windowMs) {
        while (!queue.isEmpty() && now - queue.peekFirst() > windowMs) queue.removeFirst();
    }

    public void markTeleport() {
        markTeleport(null);
    }

    public void markTeleport(Location to) {
        lastTeleportMs = System.currentTimeMillis();
        airTicks = 0;
        fallDistance = 0;
        awaitingFallDamage = false;
        jumpStartY = Double.NaN;
        requestSetback = false;
        speedWin.clear();
        moveWindowStart = 0;
        movesInWindow = 0;
        lastH = 0;
        lastDx = 0;
        lastDz = 0;
        if (to != null) lastLoc = to.clone();
        resetStreak("BlinkA");
        resetStreak("PhaseA");
        resetStreak("FlyA");
        resetStreak("FlyB");
        resetStreak("SpeedA");
        resetStreak("SpeedB");
    }
}
