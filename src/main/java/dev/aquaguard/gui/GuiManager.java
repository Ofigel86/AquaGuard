package dev.aquaguard.gui;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.core.ViolationManager;
import dev.aquaguard.penalty.PenaltyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

public class GuiManager implements Listener {
    private final AquaGuard plugin;
    private final ViolationManager vl;
    private final PenaltyManager penalties;

    public GuiManager(AquaGuard plugin, ViolationManager vl, PenaltyManager penalties) {
        this.plugin = plugin; this.vl = vl; this.penalties = penalties;
    }

    private enum MenuType { MAIN, PLAYERS, DETAIL }

    private static class Holder implements InventoryHolder {
        final MenuType type;
        final int page;
        final UUID target;
        Holder(MenuType type, int page, UUID target) { this.type = type; this.page = page; this.target = target; }
        @Override public Inventory getInventory() { return null; }
    }

    public void openMain(Player p) {
        Inventory inv = Bukkit.createInventory(new Holder(MenuType.MAIN, 0, null), 27, "AquaGuard");
        // Setback toggle
        boolean sb = plugin.getConfig().getBoolean("setback.enabled", true);
        inv.setItem(11, simple(sb ? Material.SLIME_BALL : Material.BARRIER,
                (sb ? ChatColor.GREEN : ChatColor.RED) + "Setback: " + (sb ? "ON" : "OFF"),
                Arrays.asList(ChatColor.GRAY + "Клик: переключить")));

        // Penalties mode
        String mode = penalties.mode().name().toLowerCase();
        inv.setItem(13, simple(Material.IRON_SWORD,
                ChatColor.AQUA + "Penalties: " + mode,
                Arrays.asList(ChatColor.GRAY + "Клик: циклично off → simulate → soft → hard")));

        // Players
        inv.setItem(15, simple(Material.CHEST,
                ChatColor.GOLD + "Игроки (онлайн)",
                Arrays.asList(ChatColor.GRAY + "Клик: открыть список")));

        p.openInventory(inv);
    }

    public void openPlayers(Player p, int page) {
        Inventory inv = Bukkit.createInventory(new Holder(MenuType.PLAYERS, page, null), 54, "AquaGuard | Players");
        // собрать и отсортировать онлайн-игроков по total VL
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .sorted(Comparator.comparingDouble((Player pl) -> -vl.total(pl.getUniqueId())))
                .collect(Collectors.toList());

        int perPage = 45;
        int start = page * perPage;
        int end = Math.min(players.size(), start + perPage);
        for (int i = start, slot = 0; i < end && slot < 45; i++, slot++) {
            Player t = players.get(i);
            inv.setItem(slot, playerHead(t.getUniqueId(), t.getName(), vl.total(t.getUniqueId())));
        }

        // Навигация
        if (page > 0) inv.setItem(45, simple(Material.ARROW, ChatColor.YELLOW + "← Предыдущая", null));
        inv.setItem(49, simple(Material.BARRIER, ChatColor.RED + "Назад", null));
        if (end < players.size()) inv.setItem(53, simple(Material.ARROW, ChatColor.YELLOW + "Следующая →", null));

        p.openInventory(inv);
    }

    public void openDetail(Player viewer, UUID target) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(target);
        String name = op.getName() != null ? op.getName() : target.toString();
        Inventory inv = Bukkit.createInventory(new Holder(MenuType.DETAIL, 0, target), 54, "AquaGuard | " + name);

        inv.setItem(4, playerHead(target, name, vl.total(target)));

        // Перечислим проверки
        List<Map.Entry<String, Double>> list = new ArrayList<>(vl.get(target).entrySet());
        list.sort(Comparator.comparingDouble((Map.Entry<String, Double> e) -> -e.getValue()));

        int slot = 9;
        for (var e : list) {
            if (slot >= 44) break;
            ItemStack it = simple(Material.PAPER,
                    ChatColor.GOLD + e.getKey() + ChatColor.GRAY + " | VL " + ChatColor.WHITE + String.format(Locale.US, "%.1f", e.getValue()),
                    null);
            inv.setItem(slot++, it);
        }

        // Кнопки
        inv.setItem(45, simple(Material.ENDER_PEARL, ChatColor.AQUA + "TP к игроку", null));
        inv.setItem(49, simple(Material.BARRIER, ChatColor.RED + "Назад", null));

        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);

        int slot = e.getRawSlot();
        switch (h.type) {
            case MAIN -> handleMain(p, slot);
            case PLAYERS -> handlePlayers(p, h, slot);
            case DETAIL -> handleDetail(p, h, slot);
        }
    }

    private void handleMain(Player p, int slot) {
        if (slot == 11) {
            boolean sb = plugin.getConfig().getBoolean("setback.enabled", true);
            plugin.getConfig().set("setback.enabled", !sb);
            plugin.saveConfig();
            openMain(p);
        } else if (slot == 13) {
            // цикл режима
            PenaltyManager.Mode m = penalties.mode();
            PenaltyManager.Mode next = switch (m) {
                case OFF -> PenaltyManager.Mode.SIMULATE;
                case SIMULATE -> PenaltyManager.Mode.SOFT;
                case SOFT -> PenaltyManager.Mode.HARD;
                case HARD -> PenaltyManager.Mode.OFF;
            };
            penalties.setMode(next);
            openMain(p);
        } else if (slot == 15) {
            openPlayers(p, 0);
        }
    }

    private void handlePlayers(Player p, Holder h, int slot) {
        if (slot == 49) { openMain(p); return; }
        if (slot == 45 && h.page > 0) { openPlayers(p, h.page - 1); return; }
        if (slot == 53) { openPlayers(p, h.page + 1); return; }

        if (slot < 45) {
            ItemStack it = p.getOpenInventory().getTopInventory().getItem(slot);
            if (it != null && it.getType() == Material.PLAYER_HEAD) {
                SkullMeta sm = (SkullMeta) it.getItemMeta();
                OfflinePlayer op = sm.getOwningPlayer();
                if (op != null) openDetail(p, op.getUniqueId());
            }
        }
    }

    private void handleDetail(Player p, Holder h, int slot) {
        if (slot == 49) {
            openPlayers(p, 0);
            return;
        }
        if (slot == 45 && h.target != null) {
            Player target = Bukkit.getPlayer(h.target);
            if (target != null) {
                p.teleport(target.getLocation());
                p.sendMessage(ChatColor.GRAY + "TP к " + ChatColor.GOLD + target.getName());
            } else {
                p.sendMessage(ChatColor.RED + "Игрок оффлайн.");
            }
        }
    }

    // helpers
    private ItemStack simple(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        if (lore != null) im.setLore(lore);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack playerHead(UUID uuid, String name, double totalVl) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) it.getItemMeta();
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        sm.setOwningPlayer(op);
        sm.setDisplayName(ChatColor.GOLD + name + ChatColor.GRAY + " | VL " + ChatColor.WHITE + String.format(Locale.US, "%.1f", totalVl));
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "UUID: " + uuid);
        sm.setLore(lore);
        it.setItemMeta(sm);
        return it;
        }
}
