package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.PlayerData;
import dev.aquaguard.util.Compat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public final class CombatListener implements Listener {
    private final AquaGuard plugin;

    public CombatListener(AquaGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null) return;
        boolean melee = event.getDamager() instanceof Player;
        plugin.combatChecks().onAttack(attacker, event.getEntity(), melee);
        if (melee && event.getEntity() instanceof Player) {
            plugin.combatChecks().critical(attacker, plugin.data().get(attacker), Compat.isCritical(event));
        }
        if (event.getEntity() instanceof EnderCrystal crystal && melee) {
            plugin.playerChecks().onCrystalHit(attacker, crystal);
        }
        if (event.getEntity() instanceof Player victim) {
            double after = Math.max(0, victim.getHealth() - event.getFinalDamage());
            double hearts = plugin.getConfig().getDouble("checks.AutoTotem.low-hp-hearts", 4);
            if (after <= hearts * 2) plugin.data().get(victim).lastLowHpMs = System.currentTimeMillis();
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (plugin.exemption().bypass(player)) return;
        ItemStack movingToOffhand = event.getOffHandItem();
        ItemStack currentOff = player.getInventory().getItemInOffHand();
        boolean wasTotem = currentOff != null && currentOff.getType() == Material.TOTEM_OF_UNDYING;
        boolean nowTotem = movingToOffhand != null && movingToOffhand.getType() == Material.TOTEM_OF_UNDYING;
        plugin.data().get(player).offhandWasTotem = nowTotem;
        if (nowTotem && !wasTotem) markTotem(player, false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClickBefore(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack off = player.getInventory().getItemInOffHand();
        plugin.data().get(player).offhandWasTotem = off != null && off.getType() == Material.TOTEM_OF_UNDYING;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.exemption().bypass(player)) return;
        boolean wasTotem = plugin.data().get(player).offhandWasTotem;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            ItemStack off = player.getInventory().getItemInOffHand();
            boolean nowTotem = off != null && off.getType() == Material.TOTEM_OF_UNDYING;
            plugin.data().get(player).offhandWasTotem = nowTotem;
            if (nowTotem && !wasTotem) markTotem(player, true);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (plugin.exemption().bypass(player)) return;
        PlayerData data = plugin.data().get(player);
        long now = System.currentTimeMillis();
        data.resurrectTimes.addLast(now);
        long window = Math.max(60_000, plugin.getConfig().getLong("checks.AutoTotem.repeat-window-ms", 600_000));
        PlayerData.trim(data.resurrectTimes, now, window);
        long quick = plugin.getConfig().getLong("checks.AutoTotem.quick-swap-ms", 90);
        boolean justSwapped = data.lastOffhandTotemMs > 0 && now - data.lastOffhandTotemMs <= quick;
        boolean fromInv = data.lastOffhandInventoryMs > 0 && now - data.lastOffhandInventoryMs <= quick;
        boolean low = data.lastLowHpMs > 0 && now - data.lastLowHpMs <= Math.max(250, quick + 80);
        if (plugin.checks().enabled("AutoTotemA") && justSwapped) {
            plugin.flagger().flag(player, "AutoTotemA", plugin.flagger().vl("AutoTotemA", 1.0),
                    "swap " + (now - data.lastOffhandTotemMs) + "ms inv=" + fromInv, "none");
        }
        if (plugin.checks().enabled("AutoTotemC") && low && justSwapped) {
            plugin.flagger().flag(player, "AutoTotemC", plugin.flagger().vl("AutoTotemC", 1.0),
                    "lowHP swap " + (now - data.lastLowHpMs) + "ms", "none");
        }
        if (plugin.checks().enabled("AutoTotemB")) {
            int quickUses = 0;
            for (Long pop : data.resurrectTimes) {
                for (Long swap : data.totemSwaps) {
                    long delta = pop - swap;
                    if (delta >= 0 && delta <= quick) {
                        quickUses++;
                        break;
                    }
                }
            }
            int min = plugin.getConfig().getInt("checks.AutoTotem.AutoTotemB.min-quick-uses",
                    plugin.getConfig().getInt("checks.AutoTotemB.min-quick-uses", 4));
            if (quickUses >= min) {
                plugin.flagger().flag(player, "AutoTotemB", plugin.flagger().vl("AutoTotemB", 1.0),
                        "quick-uses=" + quickUses, "none");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.combatChecks().forget(event.getPlayer().getUniqueId());
    }

    private void markTotem(Player player, boolean fromInventory) {
        PlayerData data = plugin.data().get(player);
        long now = System.currentTimeMillis();
        data.lastOffhandTotemMs = now;
        if (fromInventory) data.lastOffhandInventoryMs = now;
        data.totemSwaps.addLast(now);
        PlayerData.trim(data.totemSwaps, now, plugin.getConfig().getLong("checks.AutoTotem.repeat-window-ms", 600_000));
    }

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
}
