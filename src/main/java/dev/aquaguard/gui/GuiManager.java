package dev.aquaguard.gui;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.bypass.BypassManager;
import dev.aquaguard.checks.CheckManager;
import dev.aquaguard.core.ViolationManager;
import dev.aquaguard.freeze.FreezeManager;
import dev.aquaguard.penalty.PenaltyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GuiManager implements Listener {
    private final AquaGuard plugin;
    private final ViolationManager vl;
    private final PenaltyManager penalties;
    private final CheckManager checks;
    private final BypassManager bypass;
    private final FreezeManager freeze;

    public GuiManager(AquaGuard plugin, ViolationManager vl, PenaltyManager penalties,
                      CheckManager checks, BypassManager bypass, FreezeManager freeze) {
        this.plugin = plugin; this.vl = vl; this.penalties = penalties;
        this.checks = checks; this.bypass = bypass; this.freeze = freeze;
    }

    private enum MenuType { MAIN, PLAYERS, DETAIL, CHECKS, BYPASS }

    private static class Holder implements InventoryHolder {
        final MenuType type; final int page; final UUID target;
        Holder(MenuType type, int page, UUID target) { this.type = type; this.page = page; this.target = target; }
        @Override public Inventory getInventory() { return null; }
    }

    // Персональные фильтры для вкладки Players
    private final Map<UUID, Boolean> playersFilterFlagged = new ConcurrentHashMap<>();

    // ========== Открытие меню ==========
    public void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(new Holder(MenuType.MAIN, 0, null), 27, "AquaGuard");
        inv.setItem(10, simple(Material.CHEST, ChatColor.GOLD + "Игроки (онлайн)", lore("Открыть список")));
        inv.setItem(12, simple(Material.BOOK, ChatColor.YELLOW + "Чеки", lore("Вкл/выкл проверок")));
        inv.setItem(14, simple(Material.NAME_TAG, ChatColor.AQUA + "Bypass", lore("Управление обходами")));
        String mode = penalties.mode().name().toLowerCase();
        inv.setItem(16, simple(Material.IRON_SWORD, ChatColor.AQUA + "Penalties: " + mode, lore("Клик: сменить режим")));
        boolean sb = plugin.getConfig().getBoolean("setback.enabled", true);
        inv.setItem(22, simple(sb ? Material.SLIME_BALL : Material.BARRIER,
                (sb ? ChatColor.GREEN : ChatColor.RED) + "Setback: " + (sb ? "ON" : "OFF"),
                lore("Клик: переключить")));
        p.openInventory(inv);
    }

    public void openPlayers(Player p, int page) {
        boolean flaggedOnly = playersFilterFlagged.getOrDefault(p.getUniqueId(), false);
        Inventory inv = Bukkit.createInventory(new Holder(MenuType.PLAYERS, page, null), 54,
                "AquaGuard | Players" + (flaggedOnly ? " (VL>0)" : ""));

        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(pl -> !flaggedOnly || vl.total(pl.getUniqueId()) > 0.0)
                .sorted(Comparator.comparingDouble((Player pl) -> -vl.total(pl.getUniqueId())))
                .collect(Collectors.toList());

        int perPage = 45, start = page * perPage, end = Math.min(players.size(), start + perPage);
        for (int i = start, slot = 0; i < end && slot < 45; i++, slot++) {
            Player t = players.get(i);
            boolean fz = freeze.is(t.getUniqueId());
            boolean bp = bypass.isBypassed(t.getUniqueId());
            ItemStack head = playerHead(t.getUniqueId(),
                    (bp ? "§b" : "") + (fz ? "§c" : "") + t.getName(),
                    vl.total(t.getUniqueId()));
            ItemMeta im = head.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(gray("VL: " + String.format(Locale.US, "%.1f", vl.total(t.getUniqueId()))));
            lore.add(gray("ЛКМ: детали | ПКМ: Freeze | Shift+ПКМ: Bypass"));
            im.setLore(lore); head.setItemMeta(im);
            inv.setItem(slot, head);
        }

        // Навигация/фильтр
        inv.setItem(45, simple(Material.ARROW, ChatColor.YELLOW + "←", null));
        inv.setItem(49, simple(Material.BARRIER, ChatColor.RED + "Назад", null));
        inv.setItem(53, simple(Material.ARROW, ChatColor.YELLOW + "→", null));
        inv.setItem(47, simple(flaggedOnly ? Material.LIME_DYE : Material.GRAY_DYE,
                (flaggedOnly ? ChatColor.GREEN : ChatColor.DARK_RED) + "Только с VL>0",
                lore("Клик: переключить фильтр")));

        p.openInventory(inv);
    }

    public void openDetail(Player viewer, UUID target) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        String name = op.getName() != null ? op.getName() : target.toString();
        Inventory inv = Bukkit.createInventory(new Holder(MenuType.DETAIL, 0, target), 54, "AquaGuard | " + name);

        inv.setItem(4, playerHead(target, name, vl.total(target)));

        List<Map.Entry<String, Double>> list = new ArrayList<>(vl.get(target).entrySet());
        list.sort(Comparator.comparingDouble((Map.Entry<String, Double> e) -> -e.getValue()));
        int slot = 9;
        for (var e : list) {
            if (slot >= 44) break;
            inv.setItem(slot++, simple(Material.PAPER,
                    ChatColor.GOLD + e.getKey() + ChatColor.GRAY + " | VL " + ChatColor.WHITE + String.format(Locale.US, "%.1f", e.getValue()),
                    null));
        }

        inv.setItem(45, simple(Material.ENDER_PEARL, ChatColor.AQUA + "TP к игроку", null));
        inv.setItem(46, simple(Material.PACKED_ICE, ChatColor.LIGHT_PURPLE + "Freeze: toggle", null));
        inv.setItem(47, simple(Material.NAME_TAG, ChatColor.AQUA + "Bypass: toggle", null));
        inv.setItem(48, simple(Material.BOOK, ChatColor.YELLOW + "Показать VL в чат", null));
        inv.setItem(49, simple(Material.BARRIER, ChatColor.RED + "Назад", null));
        viewer.openInventory(inv);
    }

    public void openChecks(Player p, int page) {
        Inventory inv = Bukkit.createInventory(new Holder(MenuType.CHECKS, page, null), 54, "AquaGuard | Checks");
        List<String> names = allCheckNames();
        int perPage = 36, start = page * perPage, end = Math.min(names.size(), start + perPage);
        int slot = 0;
        for (int i = start; i < end && slot < 36; i++, slot++) {
            String n = names.get(i);
            boolean en = checks.enabled(n);
            inv.setItem(slot, simple(en ? Material.LIME_DYE : Material.GRAY_DYE,
                    (en ? ChatColor.GREEN : ChatColor.DARK_RED) + n,
                    lore("Клик: переключить")));
        }

        // Массовые действия + навигация
        inv.setItem(36, simple(Material.ARROW, ChatColor.YELLOW + "←", null));
        inv.setItem(37, simple(Material.GREEN_DYE, ChatColor.GREEN + "Only A", lore("Включить все *A; выключить *B/*C")));
        inv.setItem(38, simple(Material.LIME_DYE, ChatColor.GREEN + "All ON", lore("Включить все чеки")));
        inv.setItem(39, simple(Material.GRAY_DYE, ChatColor.RED + "All OFF", lore("Выключить все чеки")));
        inv.setItem(40, simple(Material.BARRIER, ChatColor.RED + "Назад", null));
        inv.setItem(44, simple(Material.ARROW, ChatColor.YELLOW + "→", null));

        p.openInventory(inv);
    }

    public void openBypass(Player p) {
        Inventory inv = Bukkit.createInventory(new Holder(MenuType.BYPASS, 0, null), 54, "AquaGuard | Bypass");
        int slot = 0;
        for (Player t : Bukkit.getOnlinePlayers()) {
            boolean on = bypass.isBypassed(t.getUniqueId());
            ItemStack it = playerHead(t.getUniqueId(), (on ? "§a" : "§c") + t.getName(), 0.0);
            ItemMeta im = it.getItemMeta();
            im.setLore(lore("Клик: " + (on ? "снять" : "выдать") + " обход"));
            it.setItemMeta(im);
            inv.setItem(slot++, it);
            if (slot >= 45) break;
        }
        inv.setItem(49, simple(Material.BARRIER, ChatColor.RED + "Назад", null));
        p.openInventory(inv);
    }

    // ========== Обработчик кликов ==========
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        ClickType click = e.getClick();

        switch (h.type) {
            case MAIN -> handleMain(p, slot);
            case PLAYERS -> handlePlayers(p, h, slot, click);
            case DETAIL -> handleDetail(p, h, slot);
            case CHECKS -> handleChecks(p, h, slot);
            case BYPASS -> handleBypass(p, h, slot);
        }
    }

    private void handleMain(Player p, int slot) {
        if (slot == 10) openPlayers(p, 0);
        else if (slot == 12) openChecks(p, 0);
        else if (slot == 14) openBypass(p);
        else if (slot == 16) {
            PenaltyManager.Mode m = penalties.mode();
            PenaltyManager.Mode next = switch (m) {
                case OFF -> PenaltyManager.Mode.SIMULATE;
                case SIMULATE -> PenaltyManager.Mode.SOFT;
                case SOFT -> PenaltyManager.Mode.HARD;
                case HARD -> PenaltyManager.Mode.OFF;
            };
            penalties.setMode(next);
            openMain(p);
        } else if (slot == 22) {
            boolean sb = plugin.getConfig().getBoolean("setback.enabled", true);
            plugin.getConfig().set("setback.enabled", !sb);
            plugin.saveConfig();
            openMain(p);
        }
    }

    private void handlePlayers(Player p, Holder h, int slot, ClickType click) {
        if (slot == 49) { openMain(p); return; }
        if (slot == 45) { if (h.page > 0) openPlayers(p, h.page - 1); return; }
        if (slot == 53) { openPlayers(p, h.page + 1); return; }
        if (slot == 47) {
            boolean cur = playersFilterFlagged.getOrDefault(p.getUniqueId(), false);
            playersFilterFlagged.put(p.getUniqueId(), !cur);
            openPlayers(p, 0);
            return;
        }
        if (slot < 45) {
            ItemStack it = p.getOpenInventory().getTopInventory().getItem(slot);
            if (it != null && it.getType() == Material.PLAYER_HEAD) {
                SkullMeta sm = (SkullMeta) it.getItemMeta();
                OfflinePlayer op = sm.getOwningPlayer();
                if (op == null) return;

                if (click == ClickType.LEFT) {
                    openDetail(p, op.getUniqueId());
                } else if (click == ClickType.RIGHT) {
                    boolean now = !freeze.is(op.getUniqueId());
                    freeze.set(op.getUniqueId(), now);
                    p.sendMessage(gray("Freeze " + op.getName() + ": " + (now ? "ON" : "OFF")));
                    openPlayers(p, h.page);
                } else if (click == ClickType.SHIFT_RIGHT) {
                    boolean on = bypass.isBypassed(op.getUniqueId());
                    if (on) bypass.removeBypass(op.getUniqueId());
                    else bypass.addBypass(op.getUniqueId(), plugin.getConfig().getInt("bypass.default-expire-mins", 1440));
                    p.sendMessage(gray("Bypass " + op.getName() + ": " + (!on ? "ON" : "OFF")));
                    openPlayers(p, h.page);
                }
            }
        }
    }

    private void handleDetail(Player p, Holder h, int slot) {
        UUID target = h.target;
        if (target == null) { openPlayers(p, 0); return; }
        if (slot == 49) { openPlayers(p, 0); return; }
        if (slot == 45) {
            Player t = Bukkit.getPlayer(target);
            if (t != null) { p.teleport(t.getLocation()); p.sendMessage(gray("TP к " + t.getName())); }
            else p.sendMessage(ChatColor.RED + "Игрок оффлайн.");
        } else if (slot == 46) {
            boolean now = !freeze.is(target);
            freeze.set(target, now);
            p.sendMessage(gray("Freeze: " + (now ? "ON" : "OFF")));
            openDetail(p, target);
        } else if (slot == 47) {
            boolean on = bypass.isBypassed(target);
            if (on) bypass.removeBypass(target);
            else bypass.addBypass(target, plugin.getConfig().getInt("bypass.default-expire-mins", 1440));
            p.sendMessage(gray("Bypass: " + (!on ? "ON" : "OFF")));
            openDetail(p, target);
        } else if (slot == 48) {
            double total = vl.total(target);
            p.sendMessage(ChatColor.GOLD + "VL: " + ChatColor.WHITE + String.format(Locale.US, "%.1f", total));
            vl.get(target).entrySet().stream()
                    .sorted(Comparator.comparingDouble(Map.Entry<String, Double>::getValue).reversed())
                    .limit(10)
                    .forEach(e -> p.sendMessage(" - " + e.getKey() + ": " + String.format(Locale.US, "%.1f", e.getValue())));
        }
    }

    private void handleChecks(Player p, Holder h, int slot) {
        if (slot == 40) { openMain(p); return; }
        if (slot == 36) { if (h.page > 0) openChecks(p, h.page - 1); return; }
        if (slot == 44) { openChecks(p, h.page + 1); return; }
        if (slot == 37) { for (String n : allCheckNames()) checks.set(n, n.endsWith("A")); openChecks(p, h.page); return; }
        if (slot == 38) { for (String n : allCheckNames()) checks.set(n, true); openChecks(p, h.page); return; }
        if (slot == 39) { for (String n : allCheckNames()) checks.set(n, false); openChecks(p, h.page); return; }
        if (slot < 36) {
            ItemStack it = p.getOpenInventory().getTopInventory().getItem(slot);
            if (it == null || it.getItemMeta() == null) return;
            String name = ChatColor.stripColor(it.getItemMeta().getDisplayName());
            checks.toggle(name);
            openChecks(p, h.page);
        }
    }

    private void handleBypass(Player p, Holder h, int slot) {
        if (slot == 49) { openMain(p); return; }
        if (slot < 45) {
            ItemStack it = p.getOpenInventory().getTopInventory().getItem(slot);
            if (it != null && it.getType() == Material.PLAYER_HEAD) {
                SkullMeta sm = (SkullMeta) it.getItemMeta();
                OfflinePlayer op = sm.getOwningPlayer();
                if (op == null) return;
                boolean on = bypass.isBypassed(op.getUniqueId());
                if (on) bypass.removeBypass(op.getUniqueId());
                else bypass.addBypass(op.getUniqueId(), plugin.getConfig().getInt("bypass.default-expire-mins", 1440));
                openBypass(p);
            }
        }
    }

    // ========== Helpers ==========
    private ItemStack simple(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        if (lore != null) im.setLore(lore);
        it.setItemMeta(im);
        return it;
    }
    private List<String> lore(String s) { return Collections.singletonList(gray(s)); }
    private String gray(String s) { return ChatColor.GRAY + s; }

    private ItemStack playerHead(UUID uuid, String name, double totalVl) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) it.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        sm.setOwningPlayer(op);
        sm.setDisplayName(name);
        sm.setLore(Collections.singletonList(gray("VL: " + String.format(Locale.US, "%.1f", totalVl))));
        it.setItemMeta(sm);
        return it;
    }

    private List<String> allCheckNames() {
        return Arrays.asList(
                // Movement
                "SpeedA","SpeedB","NoSlowA","FlyA","NoFallA","JesusA","StepA","PhaseA",
                // World
                "FastPlaceA","FastBreakA","ScaffoldA","TowerA","PlaceReachA","BreakReachA",
                // Combat (KA lite)
                "ReachA","WallHitA","AttackCooldownA","AttackIntervalB","AimSnapA","TargetSwitchC",
                // Misc
                "AutoClickerA",
                // AutoTotem
                "AutoTotemA","AutoTotemB","AutoTotemC"
        );
    }
}
