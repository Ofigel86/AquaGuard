package dev.aquaguard.checks.player;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.PlayerData;
import dev.aquaguard.util.BlockUtil;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerChecks {
    private final AquaGuard plugin;
    private final Map<UUID, Long> illegalNotice = new ConcurrentHashMap<>();

    public PlayerChecks(AquaGuard plugin) {
        this.plugin = plugin;
    }

    public void onUse(Player player, ItemStack item, Action action) {
        if (item == null || plugin.exemption().bypass(player)) return;
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        PlayerData data = plugin.data().get(player);
        Material type = item.getType();
        if (type == Material.BOW) {
            data.bowDrawStartMs = System.currentTimeMillis();
        } else if (type == Material.CROSSBOW) {
            boolean charged = item.getItemMeta() instanceof org.bukkit.inventory.meta.CrossbowMeta meta && meta.isCharged();
            if (!charged) data.bowDrawStartMs = System.currentTimeMillis();
        }
        if (type.isEdible() || type == Material.POTION || type == Material.MILK_BUCKET || type == Material.HONEY_BOTTLE) {
            data.useStartMs = System.currentTimeMillis();
            data.using = type;
        }
    }

    public void onConsume(Player player, ItemStack item) {
        if (!plugin.checks().enabled("FastEatA") || plugin.exemption().bypass(player) || item == null) return;
        PlayerData data = plugin.data().get(player);
        if (data.useStartMs <= 0 || data.using != item.getType()) return;
        long took = System.currentTimeMillis() - data.useStartMs;
        long expected = item.getType() == Material.DRIED_KELP ? 800 : 1600;
        long min = (long) (expected * plugin.getConfig().getDouble("checks.FastEatA.ratio", 0.65));
        if (took > 0 && took < min) {
            plugin.flagger().flag(player, "FastEatA", plugin.flagger().vl("FastEatA", 1.0),
                    item.getType() + " in " + took + "ms", "none");
        }
        data.useStartMs = 0;
        data.using = null;
    }

    public void onShoot(Player player, EntityShootBowEvent event) {
        if (!plugin.checks().enabled("FastBowA") || plugin.exemption().bypass(player)) return;
        PlayerData data = plugin.data().get(player);
        if (data.bowDrawStartMs <= 0) return;
        long took = System.currentTimeMillis() - data.bowDrawStartMs;
        float force = event.getForce();
        ItemStack bow = event.getBow();
        boolean crossbow = bow != null && bow.getType() == Material.CROSSBOW;
        long min = crossbow ? l("FastBowA", "crossbow-min-ms", 200) : l("FastBowA", "bow-min-ms", 700);
        long adjusted = min;
        if (crossbow) {
            int quick = BlockUtil.enchant(bow, Enchantment.QUICK_CHARGE);
            adjusted = Math.max(50L, min - quick * 250L);
        }
        if (force >= d("FastBowA", "min-force", 0.9f) && took > 0 && took < adjusted) {
            plugin.flagger().flag(player, "FastBowA", plugin.flagger().vl("FastBowA", 1.5),
                    String.format(Locale.US, "%s force=%.2f in %dms", crossbow ? "crossbow" : "bow", force, took), "none");
        }
        data.bowDrawStartMs = 0;
    }

    public void onHeld(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || plugin.exemption().bypass(player)) return;
        if (plugin.exemption().creativeLike(player)) return;
        if (plugin.checks().enabled("IllegalStackA")) {
            int max = item.getMaxStackSize();
            int over = i("IllegalStackA", "over", 0);
            if (max > 0 && item.getAmount() > max + over && illegalReady(player)) {
                plugin.flagger().flag(player, "IllegalStackA", plugin.flagger().vl("IllegalStackA", 2.0),
                        item.getType() + " x" + item.getAmount() + ">" + max, "none");
            }
        }
        if (plugin.checks().enabled("IllegalEnchantA") && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasEnchants()) {
                int over = i("IllegalEnchantA", "over-max", 0);
                for (var entry : meta.getEnchants().entrySet()) {
                    Enchantment enchantment = entry.getKey();
                    int level = entry.getValue();
                    int cap = enchantment.getMaxLevel() + over;
                    if (enchantment.getMaxLevel() > 0 && level > cap && illegalReady(player)) {
                        String enchantName = enchantment.getKey() == null ? enchantment.toString() : enchantment.getKey().getKey();
                        plugin.flagger().flag(player, "IllegalEnchantA", plugin.flagger().vl("IllegalEnchantA", 1.5),
                                enchantName + " " + level + ">" + enchantment.getMaxLevel(), "none");
                        break;
                    }
                }
            }
        }
    }

    public void onRegen(Player player, long now) {
        if (!plugin.checks().enabled("RegenA") || plugin.exemption().bypass(player)) return;
        if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION)) return;
        PlayerData data = plugin.data().get(player);
        if (data.lastRegenMs > 0) {
            long dt = now - data.lastRegenMs;
            long min = l("RegenA", "min-interval-ms", 3200);
            if (dt > 0 && dt < min && data.streak("RegenA", true) >= i("RegenA", "streak-to-flag", 3)) {
                plugin.flagger().flag(player, "RegenA", plugin.flagger().vl("RegenA", 1.0), "regen dt=" + dt + "ms", "none");
            } else if (dt >= min) data.streak("RegenA", false);
        }
        data.lastRegenMs = now;
    }

    public void onContainerClick(Player player) {
        if (!plugin.checks().enabled("ChestStealerA") || plugin.exemption().bypass(player)) return;
        PlayerData data = plugin.data().get(player);
        long now = System.currentTimeMillis();
        data.clickTimes.addLast(now);
        PlayerData.trim(data.clickTimes, now, l("ChestStealerA", "window-ms", 1000));
        int limit = i("ChestStealerA", "clicks", 16);
        if (data.clickTimes.size() > limit) {
            plugin.flagger().flag(player, "ChestStealerA", plugin.flagger().vl("ChestStealerA", 1.0),
                    "clicks=" + data.clickTimes.size(), "none");
        }
    }

    public void onGhost(Player player, org.bukkit.block.Block block) {
        if (!plugin.checks().enabled("GhostHandA") || block == null || plugin.exemption().bypass(player)) return;
        if (plugin.exemption().creativeLike(player)) return;
        double dist = player.getEyeLocation().distance(block.getLocation().add(0.5, 0.5, 0.5));
        if (dist < d("GhostHandA", "min-distance", 2.0)) return;
        if (BlockUtil.rayHitsBlock(player, block)) {
            plugin.data().get(player).streak("GhostHandA", false);
            return;
        }
        var eye = player.getEyeLocation();
        var dir = block.getLocation().add(0.5, 0.5, 0.5).toVector().subtract(eye.toVector());
        var hit = eye.getWorld().rayTraceBlocks(eye, dir.normalize(), dist, org.bukkit.FluidCollisionMode.NEVER, true);
        if (hit == null || hit.getHitBlock() == null) return;
        if (plugin.data().get(player).streak("GhostHandA", true) >= i("GhostHandA", "streak-to-flag", 3)) {
            plugin.flagger().flag(player, "GhostHandA", plugin.flagger().vl("GhostHandA", 1.0),
                    "through " + hit.getHitBlock().getType() + " to " + block.getType(), "none");
        }
    }

    public void onCrystalPlace(Player player, Entity crystal) {
        if (!(crystal instanceof EnderCrystal)) return;
        plugin.data().get(player).breakStart.put("crystal:" + crystal.getUniqueId(), System.currentTimeMillis());
    }

    public void onCrystalHit(Player player, Entity crystal) {
        if (!plugin.checks().enabled("CrystalAuraA") || !(crystal instanceof EnderCrystal)) return;
        Long placed = plugin.data().get(player).breakStart.remove("crystal:" + crystal.getUniqueId());
        if (placed == null) return;
        long dt = System.currentTimeMillis() - placed;
        if (dt <= l("CrystalAuraA", "max-ms", 80)
                && plugin.data().get(player).streak("CrystalAuraA", true) >= i("CrystalAuraA", "streak-to-flag", 3)) {
            plugin.flagger().flag(player, "CrystalAuraA", plugin.flagger().vl("CrystalAuraA", 1.0),
                    "place-hit " + dt + "ms", "none");
        }
    }

    private boolean illegalReady(Player player) {
        long now = System.currentTimeMillis();
        Long prev = illegalNotice.get(player.getUniqueId());
        if (prev != null && now - prev < 2500) return false;
        illegalNotice.put(player.getUniqueId(), now);
        return true;
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
