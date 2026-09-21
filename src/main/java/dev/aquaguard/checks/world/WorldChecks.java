package dev.aquaguard.checks.world;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.PlayerData;
import dev.aquaguard.util.BlockUtil;
import dev.aquaguard.util.BreakTimeMath;
import dev.aquaguard.util.Compat;
import dev.aquaguard.util.MathUtil;
import dev.aquaguard.util.ScaffoldMath;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Locale;

public final class WorldChecks {
    private final AquaGuard plugin;

    public WorldChecks(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public void onPlace(Player player, Block placed, Block against) {
        if (plugin.exemption().bypass(player) || plugin.exemption().worldExempt(player)) return;
        PlayerData data = plugin.data().get(player);
        long now = System.currentTimeMillis();
        data.placeTimes.addLast(now);
        PlayerData.trim(data.placeTimes, now, plugin.getConfig().getLong("checks.FastPlaceA.window-ms", 1000));
        if (plugin.checks().enabled("FastPlaceA")) fastPlace(player, data);
        if (plugin.checks().enabled("PlaceReachA")) placeReach(player, placed);
        if (plugin.checks().enabled("AirPlaceA")) airPlace(player, placed, against);
        if (plugin.checks().enabled("ScaffoldA")) scaffold(player, data, placed);
        if (plugin.checks().enabled("ScaffoldB")) scaffoldLock(player, data, placed);
        if (plugin.checks().enabled("TowerA")) tower(player, data, placed);
        data.lastPlaced = placed.getLocation();
        data.lastYaw = player.getLocation().getYaw();
    }

    public void onBreakStart(Player player, Block block) {
        if (player == null || block == null) return;
        plugin.data().get(player).breakStart.put(key(block), System.currentTimeMillis());
    }

    public void onBreak(Player player, Block block) {
        if (plugin.exemption().bypass(player) || plugin.exemption().worldExempt(player)) return;
        PlayerData data = plugin.data().get(player);
        long now = System.currentTimeMillis();
        float hardness = block.getType().getHardness();
        if (hardness > 0) {
            data.breakTimes.addLast(now);
            PlayerData.trim(data.breakTimes, now, plugin.getConfig().getLong("checks.FastBreakA.window-ms", 1000));
        }
        if (plugin.checks().enabled("FastBreakA")) fastBreak(player, data);
        if (plugin.checks().enabled("BreakReachA")) breakReach(player, block);
        if (plugin.checks().enabled("NukerA")) nuker(player, data, block);
        if (plugin.checks().enabled("ImpossibleBreakA")) impossible(player, data, block, now);
        data.breakStart.remove(key(block));
    }

    private void fastPlace(Player player, PlayerData data) {
        if (plugin.exemption().creativeLike(player)) return;
        long window = Math.max(200, plugin.getConfig().getLong("checks.FastPlaceA.window-ms", 1000));
        double cps = rate(data.placeTimes, window);
        double limit = d("FastPlaceA", "cps-limit", 12) + d("FastPlaceA", "margin-cps", 2);
        if (cps > limit) {
            plugin.flagger().flag(player, "FastPlaceA", plugin.flagger().vl("FastPlaceA", 1.0),
                    String.format(Locale.US, "cps=%.1f>%.1f", cps, limit), "none");
        }
    }

    private void fastBreak(Player player, PlayerData data) {
        if (plugin.exemption().creativeLike(player)) return;
        long window = Math.max(200, plugin.getConfig().getLong("checks.FastBreakA.window-ms", 1000));
        double cps = rate(data.breakTimes, window);
        double limit = d("FastBreakA", "cps-limit", 8) + d("FastBreakA", "margin-cps", 1);
        if (cps > limit) {
            plugin.flagger().flag(player, "FastBreakA", plugin.flagger().vl("FastBreakA", 1.0),
                    String.format(Locale.US, "cps=%.1f>%.1f", cps, limit), "none");
        }
    }

    private void placeReach(Player player, Block placed) {
        double dist = eyeToBlock(player, placed);
        double allowed = interactionRange(player) + d("PlaceReachA", "margin", 0.45)
                + Compat.ping(player) * d("PlaceReachA", "ping-coeff", 0.002);
        if (plugin.getConfig().contains("checks.PlaceReachA.max-distance")) {
            allowed = Math.min(allowed, d("PlaceReachA", "max-distance", 5.0) + d("PlaceReachA", "margin", 0.45));
        }
        if (dist > allowed) {
            plugin.flagger().flag(player, "PlaceReachA", plugin.flagger().vl("PlaceReachA", 1.0),
                    String.format(Locale.US, "dist=%.2f>%.2f", dist, allowed),
                    plugin.flagger().setback("PlaceReachA", "from"));
        }
    }

    private void breakReach(Player player, Block block) {
        if (plugin.exemption().creativeLike(player)) return;
        double dist = eyeToBlock(player, block);
        double allowed = interactionRange(player) + d("BreakReachA", "margin", 0.5)
                + Compat.ping(player) * d("BreakReachA", "ping-coeff", 0.002);
        if (plugin.getConfig().contains("checks.BreakReachA.max-distance")) {
            allowed = Math.min(allowed, d("BreakReachA", "max-distance", 5.0) + d("BreakReachA", "margin", 0.5));
        }
        if (dist > allowed) {
            plugin.flagger().flag(player, "BreakReachA", plugin.flagger().vl("BreakReachA", 1.0),
                    String.format(Locale.US, "dist=%.2f>%.2f", dist, allowed), "none");
        }
    }

    private void airPlace(Player player, Block placed, Block against) {
        if (against == null || against.getType() != Material.AIR) return;
        if (hasNeighbor(placed)) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;
        plugin.flagger().flag(player, "AirPlaceA", plugin.flagger().vl("AirPlaceA", 2.0),
                "placed " + placed.getType() + " against air", plugin.flagger().setback("AirPlaceA", "from"));
    }

    private void nuker(Player player, PlayerData data, Block block) {
        if (plugin.exemption().creativeLike(player) || block.getType().getHardness() <= 0) return;
        boolean fast = data.breakTimes.size() >= i("NukerA", "blocks-per-second", 8);
        double angle = lookAngle(player, block);
        boolean blind = angle > d("NukerA", "max-angle", 55) && eyeToBlock(player, block) > 3.2;
        if ((fast || blind) && data.streak("NukerA", true) >= 2) {
            plugin.flagger().flag(player, "NukerA", plugin.flagger().vl("NukerA", 1.5),
                    String.format(Locale.US, "rate=%d angle=%.0f", data.breakTimes.size(), angle), "none");
        } else if (!fast && !blind) data.streak("NukerA", false);
    }

    private void impossible(Player player, PlayerData data, Block block, long now) {
        if (plugin.exemption().creativeLike(player)) return;
        float hardness = block.getType().getHardness();
        if (hardness <= 0) return;
        Long started = data.breakStart.get(key(block));
        if (started == null) return;
        long actual = now - started;
        ItemStack tool = player.getInventory().getItemInMainHand();
        String category = BlockUtil.toolCategory(tool);
        boolean correct = BlockUtil.correctTool(category, block.getType());
        int eff = BlockUtil.enchant(tool, Enchantment.EFFICIENCY);
        PotionEffect haste = player.getPotionEffect(PotionEffectType.HASTE);
        int hasteAmp = haste == null ? -1 : haste.getAmplifier();
        double breakSpeed = Compat.attribute(player, "block_break_speed", 1.0);
        long expected = BreakTimeMath.expectedMillis(hardness, BreakTimeMath.toolSpeed(tool == null ? "AIR" : tool.getType().name()),
                correct, eff, hasteAmp, breakSpeed);
        double ratio = d("ImpossibleBreakA", "ratio", 0.4);
        if (expected > l("ImpossibleBreakA", "min-expected-ms", 350) && actual < expected * ratio) {
            plugin.flagger().flag(player, "ImpossibleBreakA", plugin.flagger().vl("ImpossibleBreakA", 1.5),
                    "took=" + actual + "ms expected=" + expected + "ms " + block.getType(), "none");
        }
    }

    private void scaffold(Player player, PlayerData data, Block placed) {
        Location pl = player.getLocation();
        boolean below = placed.getY() <= pl.getBlockY() - 1;
        double vx = placed.getX() + 0.5 - pl.getX();
        double vz = placed.getZ() + 0.5 - pl.getZ();
        double dot = MathUtil.forwardDot(pl.getYaw(), vx, vz);
        boolean line = data.lastPlaced != null && data.lastPlaced.getWorld() == placed.getWorld()
                && data.lastPlaced.getBlockY() == placed.getY()
                && Math.abs(data.lastPlaced.getBlockX() - placed.getX()) + Math.abs(data.lastPlaced.getBlockZ() - placed.getZ()) == 1;
        boolean looking = BlockUtil.rayHitsBlock(player, placed);
        int gained = ScaffoldMath.placementScore(below, line, player.isSneaking(), player.isSprinting(), pl.getPitch(), dot, looking);
        data.scaffoldScore = Math.max(0, Math.min(30, data.scaffoldScore + gained - (gained > 0 ? 0 : 1)));
        double thr = d("ScaffoldA", "score-threshold", 12);
        if (data.placeTimes.size() >= 4 && data.scaffoldScore >= thr) {
            plugin.flagger().flag(player, "ScaffoldA", plugin.flagger().vl("ScaffoldA", 1.5),
                    "score=" + data.scaffoldScore, plugin.flagger().setback("ScaffoldA", "from"));
            data.scaffoldScore = (int) thr;
        }
    }

    private void scaffoldLock(Player player, PlayerData data, Block placed) {
        if (data.lastPlaced == null) return;
        Location pl = player.getLocation();
        boolean below = placed.getY() <= pl.getBlockY() - 1;
        double yawDelta = MathUtil.angleDiff(data.lastYaw, pl.getYaw());
        boolean locked = below && pl.getPitch() > 72 && player.isSneaking() && yawDelta < d("ScaffoldB", "max-yaw-delta", 1.5)
                && data.placeTimes.size() >= 5;
        if (locked && data.streak("ScaffoldB", true) >= i("ScaffoldB", "streak-to-flag", 5)) {
            plugin.flagger().flag(player, "ScaffoldB", plugin.flagger().vl("ScaffoldB", 1.0),
                    String.format(Locale.US, "yawΔ=%.2f pitch=%.0f", yawDelta, pl.getPitch()), "none");
        } else if (!locked) data.streak("ScaffoldB", false);
    }

    private void tower(Player player, PlayerData data, Block placed) {
        Location pl = player.getLocation();
        boolean under = placed.getX() == pl.getBlockX() && placed.getZ() == pl.getBlockZ() && placed.getY() == pl.getBlockY() - 1;
        if (!under) {
            data.towerCount = 0;
            data.towerWindowStart = 0;
            return;
        }
        if (data.towerWindowStart == 0) {
            data.towerWindowStart = System.currentTimeMillis();
            data.towerStartY = pl.getY();
            data.towerCount = 1;
            return;
        }
        data.towerCount++;
        double seconds = Math.max(0.3, (System.currentTimeMillis() - data.towerWindowStart) / 1000.0);
        double bps = (pl.getY() - data.towerStartY) / seconds;
        PotionEffect boost = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        int amp = boost == null ? -1 : boost.getAmplifier();
        double allowed = 2.4 + Math.max(0, amp + 1) * 0.8;
        if (data.towerCount >= 4 && bps > allowed) {
            plugin.flagger().flag(player, "TowerA", plugin.flagger().vl("TowerA", 1.0),
                    String.format(Locale.US, "tower=%.2f bps > %.2f", bps, allowed),
                    plugin.flagger().setback("TowerA", "from"));
        }
    }

    private double interactionRange(Player player) {
        double base = Compat.attribute(player, "block_interaction_range", player.getGameMode() == GameMode.CREATIVE ? 5.0 : 4.5);
        if (player.getGameMode() == GameMode.CREATIVE) base = Math.max(base, 5.0);
        return base;
    }

    private static double eyeToBlock(Player player, Block block) {
        var eye = player.getEyeLocation();
        return MathUtil.distanceToAabb(eye.getX(), eye.getY(), eye.getZ(),
                block.getX(), block.getY(), block.getZ(), block.getX() + 1, block.getY() + 1, block.getZ() + 1);
    }

    private static double lookAngle(Player player, Block block) {
        var eye = player.getEyeLocation();
        var dir = eye.getDirection();
        var to = block.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector());
        if (to.lengthSquared() < 1e-6) return 0;
        double dot = MathUtil.clamp(dir.normalize().dot(to.normalize()), -1, 1);
        return Math.toDegrees(Math.acos(dot));
    }

    private static boolean hasNeighbor(Block block) {
        return solidish(block.getRelative(1, 0, 0)) || solidish(block.getRelative(-1, 0, 0))
                || solidish(block.getRelative(0, 1, 0)) || solidish(block.getRelative(0, -1, 0))
                || solidish(block.getRelative(0, 0, 1)) || solidish(block.getRelative(0, 0, -1));
    }

    private static boolean solidish(Block block) {
        return block.getType().isSolid() || BlockUtil.isLiquidLike(block);
    }

    private static String key(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
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
