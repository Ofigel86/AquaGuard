package dev.aquaguard.gui;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.checks.CheckCatalog;
import dev.aquaguard.checks.CheckInfo;
import dev.aquaguard.core.FlagRecord;
import dev.aquaguard.penalty.PenaltyManager;
import dev.aquaguard.util.Compat;
import dev.aquaguard.util.Texts;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class GuiManager implements Listener {
    private final AquaGuard plugin;

    public GuiManager(AquaGuard plugin) {
        this.plugin = plugin;
    }

    private enum Menu { MAIN, PLAYERS, DETAIL, CHECKS, BYPASS, HISTORY }

    private static final class Holder implements InventoryHolder {
        final Menu menu;
        final int page;
        final UUID target;
        final CheckInfo.Category category;
        Holder(Menu menu, int page, UUID target, CheckInfo.Category category) {
            this.menu = menu;
            this.page = page;
            this.target = target;
            this.category = category;
        }
        @Override public Inventory getInventory() { return null; }
    }

    public void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(Menu.MAIN, 0, null, null), 45, title("AquaGuard"));
        fill(inv, Material.GRAY_STAINED_GLASS_PANE);
        inv.setItem(10, item(Material.PLAYER_HEAD, "&eИгроки", "Онлайн, VL, пинг"));
        inv.setItem(12, item(Material.COMPARATOR, "&aЧеки", plugin.checks().enabledCount() + " включено"));
        inv.setItem(14, item(Material.NAME_TAG, "&bBypass", "Временный обход"));
        inv.setItem(16, item(Material.PAPER, "&6Последние флаги", "История с рестарта"));
        String mode = plugin.penalties().mode().name().toLowerCase(Locale.ROOT);
        inv.setItem(28, item(Material.IRON_SWORD, "&cPenalties: &f" + mode, "ЛКМ: следующий режим"));
        boolean sb = plugin.settings().setbackEnabled();
        inv.setItem(30, item(sb ? Material.SLIME_BALL : Material.BARRIER, (sb ? "&a" : "&c") + "Setback: " + (sb ? "ON" : "OFF"), "ЛКМ: переключить"));
        boolean alerts = plugin.alerts().alertsOn(player);
        inv.setItem(32, item(alerts ? Material.BELL : Material.REDSTONE_TORCH, (alerts ? "&a" : "&c") + "Алерты: " + (alerts ? "ON" : "OFF"), "Только для тебя"));
        inv.setItem(34, item(Material.BOOK, "&fСтатус",
                "TPS " + String.format(Locale.US, "%.1f", Compat.tps()),
                "Наказания " + (plugin.punishments().enabled() ? plugin.punishments().mode() : "off"),
                "Флагов " + plugin.stats().totalFlags()));
        inv.setItem(40, item(Material.REDSTONE, "&eПерезагрузить", "config + toggles + messages"));
        player.openInventory(inv);
        click(player);
    }

    public void openPlayers(Player player, int page) {
        Inventory inv = Bukkit.createInventory(new Holder(Menu.PLAYERS, page, null, null), 54, title("Игроки"));
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort((a, b) -> Double.compare(plugin.violations().total(b.getUniqueId()), plugin.violations().total(a.getUniqueId())));
        int per = 45;
        int start = Math.max(0, page) * per;
        for (int i = start, slot = 0; i < online.size() && slot < per; i++, slot++) {
            Player target = online.get(i);
            double vl = plugin.violations().total(target.getUniqueId());
            boolean frozen = plugin.freeze().isFrozen(target.getUniqueId());
            boolean bypass = plugin.bypass().isBypassed(target.getUniqueId());
            ItemStack head = head(target.getUniqueId(), (frozen ? "&c" : bypass ? "&b" : "&f") + target.getName(),
                    "VL " + fmt(vl),
                    "Пинг " + Compat.ping(target) + " · " + target.getWorld().getName(),
                    "ЛКМ детали · ПКМ freeze · Shift+ПКМ bypass");
            inv.setItem(slot, head);
        }
        inv.setItem(45, item(Material.ARROW, "&eНазад", null));
        inv.setItem(49, item(Material.BARRIER, "&cМеню", null));
        inv.setItem(53, item(Material.ARROW, "&eДальше", null));
        player.openInventory(inv);
    }

    public void openDetail(Player viewer, UUID target) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(target);
        String name = offline.getName() == null ? target.toString().substring(0, 8) : offline.getName();
        Inventory inv = Bukkit.createInventory(new Holder(Menu.DETAIL, 0, target, null), 54, title(name));
        Player online = Bukkit.getPlayer(target);
        List<String> lore = new ArrayList<>();
        lore.add("VL " + fmt(plugin.violations().total(target)));
        if (online != null) {
            lore.add("Пинг " + Compat.ping(online) + " · " + online.getGameMode().name().toLowerCase(Locale.ROOT));
            lore.add(online.getWorld().getName());
            String brand = plugin.data().peek(target) == null ? "" : plugin.data().peek(target).clientBrand;
            if (brand != null && !brand.isBlank()) lore.add(brand);
        } else lore.add("&8оффлайн");
        inv.setItem(4, head(target, "&f" + name, lore.toArray(String[]::new)));
        List<java.util.Map.Entry<String, Double>> vls = new ArrayList<>(plugin.violations().get(target).entrySet());
        vls.sort(Comparator.comparingDouble(e -> -e.getValue()));
        int slot = 9;
        for (var e : vls) {
            if (slot >= 36) break;
            inv.setItem(slot++, item(Material.PAPER, "&6" + e.getKey() + " &7" + fmt(e.getValue()), "Shift+ЛКМ сбросить этот чек"));
        }
        inv.setItem(45, item(Material.ENDER_PEARL, "&bТелепорт", null));
        inv.setItem(46, item(Material.PACKED_ICE, "&dFreeze", null));
        inv.setItem(47, item(Material.NAME_TAG, "&bBypass 24ч", null));
        inv.setItem(48, item(Material.BARRIER, "&cСбросить VL", "Shift+ЛКМ"));
        inv.setItem(50, item(Material.IRON_DOOR, "&cКик", null));
        inv.setItem(49, item(Material.ARROW, "&eНазад", null));
        viewer.openInventory(inv);
    }

    public void openChecks(Player player, int page, CheckInfo.Category category) {
        Inventory inv = Bukkit.createInventory(new Holder(Menu.CHECKS, page, null, category), 54,
                title(category == null ? "Чеки" : category.title()));
        List<CheckInfo> list = CheckCatalog.byCategory(category);
        int start = Math.max(0, page) * 36;
        for (int i = start, slot = 0; i < list.size() && slot < 36; i++, slot++) {
            CheckInfo info = list.get(i);
            boolean on = plugin.checks().enabled(info.id());
            inv.setItem(slot, item(on ? Material.LIME_DYE : Material.GRAY_DYE,
                    (on ? "&a" : "&c") + info.id() + (info.experimental() ? " &8exp" : ""),
                    info.description(), "ЛКМ переключить"));
        }
        inv.setItem(36, item(Material.ARROW, "&eНазад", null));
        inv.setItem(37, item(Material.LIME_DYE, "&aСтабильные", "Включить проверенные, выключить экспериментальные"));
        inv.setItem(38, item(Material.GREEN_DYE, "&aВсе ON", null));
        inv.setItem(39, item(Material.GRAY_DYE, "&cВсе OFF", null));
        inv.setItem(40, item(Material.BARRIER, "&cМеню", null));
        inv.setItem(41, item(category == CheckInfo.Category.MOVEMENT ? Material.FEATHER : Material.WHITE_DYE, "&fДвижение", null));
        inv.setItem(42, item(category == CheckInfo.Category.COMBAT ? Material.IRON_SWORD : Material.WHITE_DYE, "&fБой", null));
        inv.setItem(43, item(category == CheckInfo.Category.WORLD ? Material.GRASS_BLOCK : Material.WHITE_DYE, "&fМир", null));
        inv.setItem(44, item(Material.ARROW, "&eДальше", null));
        inv.setItem(45, item(category == CheckInfo.Category.PLAYER ? Material.PLAYER_HEAD : Material.WHITE_DYE, "&fИгрок", null));
        inv.setItem(46, item(category == null ? Material.LIME_DYE : Material.WHITE_DYE, "&fВсе", null));
        player.openInventory(inv);
    }

    public void openBypass(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(Menu.BYPASS, 0, null, null), 54, title("Bypass"));
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            boolean on = plugin.bypass().isBypassed(target.getUniqueId());
            inv.setItem(slot++, head(target.getUniqueId(), (on ? "&a" : "&c") + target.getName(), on ? "ЛКМ снять" : "ЛКМ выдать на 24ч"));
        }
        inv.setItem(49, item(Material.BARRIER, "&cМеню", null));
        player.openInventory(inv);
    }

    public void openHistory(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(Menu.HISTORY, 0, null, null), 54, title("Флаги"));
        List<FlagRecord> recent = plugin.history().recent(45);
        int slot = 0;
        for (FlagRecord record : recent) {
            inv.setItem(slot++, item(Material.PAPER, "&c" + record.check() + " &f" + record.name(),
                    "VL " + fmt(record.vl()) + " · ping " + record.ping(),
                    record.world(),
                    record.debug()));
        }
        inv.setItem(49, item(Material.BARRIER, "&cМеню", null));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!plugin.owner().isOwner(player)) {
            player.closeInventory();
            plugin.messages().send(player, "no-permission");
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) return;
        switch (holder.menu) {
            case MAIN -> main(player, event.getRawSlot());
            case PLAYERS -> players(player, holder, event.getRawSlot(), event.getClick());
            case DETAIL -> detail(player, holder, event.getRawSlot(), event.getClick(), event.getCurrentItem());
            case CHECKS -> checks(player, holder, event.getRawSlot(), event.getCurrentItem());
            case BYPASS -> bypass(player, event.getRawSlot());
            case HISTORY -> { if (event.getRawSlot() == 49) openMain(player); }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private void main(Player player, int slot) {
        switch (slot) {
            case 10 -> openPlayers(player, 0);
            case 12 -> openChecks(player, 0, null);
            case 14 -> openBypass(player);
            case 16 -> openHistory(player);
            case 28 -> {
                PenaltyManager.Mode next = switch (plugin.penalties().mode()) {
                    case OFF -> PenaltyManager.Mode.SIMULATE;
                    case SIMULATE -> PenaltyManager.Mode.SOFT;
                    case SOFT -> PenaltyManager.Mode.HARD;
                    case HARD -> PenaltyManager.Mode.OFF;
                };
                plugin.penalties().setMode(next);
                openMain(player);
            }
            case 30 -> {
                plugin.settings().setSetbackEnabled(!plugin.settings().setbackEnabled());
                openMain(player);
            }
            case 32 -> {
                plugin.alerts().toggle(player);
                openMain(player);
            }
            case 40 -> {
                plugin.reloadAll();
                plugin.messages().send(player, "reloaded");
                openMain(player);
            }
            default -> { }
        }
    }

    private void players(Player player, Holder holder, int slot, ClickType click) {
        if (slot == 49) { openMain(player); return; }
        if (slot == 45 && holder.page > 0) { openPlayers(player, holder.page - 1); return; }
        if (slot == 53) { openPlayers(player, holder.page + 1); return; }
        UUID id = headId(player, slot);
        if (id == null) return;
        if (click == ClickType.RIGHT) {
            plugin.freeze().toggle(id);
            openPlayers(player, holder.page);
        } else if (click == ClickType.SHIFT_RIGHT) {
            if (plugin.bypass().isBypassed(id)) plugin.bypass().removeBypass(id);
            else plugin.bypass().addBypass(id, plugin.getConfig().getInt("bypass.default-expire-mins", 1440));
            openPlayers(player, holder.page);
        } else openDetail(player, id);
    }

    private void detail(Player player, Holder holder, int slot, ClickType click, ItemStack current) {
        UUID target = holder.target;
        if (target == null || slot == 49) { openPlayers(player, 0); return; }
        Player online = Bukkit.getPlayer(target);
        if (slot == 45 && online != null) player.teleport(online.getLocation());
        else if (slot == 46) { plugin.freeze().toggle(target); openDetail(player, target); }
        else if (slot == 47) {
            if (plugin.bypass().isBypassed(target)) plugin.bypass().removeBypass(target);
            else plugin.bypass().addBypass(target, plugin.getConfig().getInt("bypass.default-expire-mins", 1440));
            openDetail(player, target);
        } else if (slot == 48 && click.isShiftClick()) {
            plugin.violations().reset(target);
            plugin.punishments().clear(target);
            plugin.messages().send(player, "vl-reset", "player", plugin.violations().name(target));
            openDetail(player, target);
        } else if (slot == 50 && online != null) {
            online.kickPlayer("AquaGuard");
            player.closeInventory();
        } else if (slot >= 9 && slot < 36 && click.isShiftClick() && current != null && current.hasItemMeta()) {
            String name = ChatColor.stripColor(current.getItemMeta().getDisplayName());
            if (name != null) {
                String check = name.split(" ")[0];
                plugin.violations().reset(target, check);
                openDetail(player, target);
            }
        }
    }

    private void checks(Player player, Holder holder, int slot, ItemStack current) {
        if (slot == 40) { openMain(player); return; }
        if (slot == 36 && holder.page > 0) { openChecks(player, holder.page - 1, holder.category); return; }
        if (slot == 44) { openChecks(player, holder.page + 1, holder.category); return; }
        if (slot == 37) { plugin.checks().setStableOnly(); openChecks(player, holder.page, holder.category); return; }
        if (slot == 38) { plugin.checks().setAll(true); openChecks(player, holder.page, holder.category); return; }
        if (slot == 39) { plugin.checks().setAll(false); openChecks(player, holder.page, holder.category); return; }
        if (slot == 41) { openChecks(player, 0, CheckInfo.Category.MOVEMENT); return; }
        if (slot == 42) { openChecks(player, 0, CheckInfo.Category.COMBAT); return; }
        if (slot == 43) { openChecks(player, 0, CheckInfo.Category.WORLD); return; }
        if (slot == 45) { openChecks(player, 0, CheckInfo.Category.PLAYER); return; }
        if (slot == 46) { openChecks(player, 0, null); return; }
        if (slot < 36 && current != null && current.hasItemMeta()) {
            String name = ChatColor.stripColor(current.getItemMeta().getDisplayName());
            if (name != null) plugin.checks().toggle(name.replace(" exp", "").trim());
            openChecks(player, holder.page, holder.category);
        }
    }

    private void bypass(Player player, int slot) {
        if (slot == 49) { openMain(player); return; }
        UUID id = headId(player, slot);
        if (id == null) return;
        if (plugin.bypass().isBypassed(id)) plugin.bypass().removeBypass(id);
        else plugin.bypass().addBypass(id, plugin.getConfig().getInt("bypass.default-expire-mins", 1440));
        openBypass(player);
    }

    private UUID headId(Player viewer, int slot) {
        ItemStack item = viewer.getOpenInventory().getTopInventory().getItem(slot);
        if (item == null || item.getType() != Material.PLAYER_HEAD || !(item.getItemMeta() instanceof SkullMeta meta)) return null;
        OfflinePlayer owner = meta.getOwningPlayer();
        return owner == null ? null : owner.getUniqueId();
    }

    private void fill(Inventory inv, Material material) {
        ItemStack pane = item(material, " ", null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Texts.color(name));
        if (lore != null && lore.length > 0 && lore[0] != null) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) if (line != null) lines.add(Texts.color("&7" + line));
            meta.setLore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack head(UUID uuid, String name, String... lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.setDisplayName(Texts.color(name));
        if (lore != null && lore.length > 0 && lore[0] != null) {
            List<String> lines = new ArrayList<>();
            for (String line : lore) if (line != null) lines.add(Texts.color("&7" + line));
            meta.setLore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String title(String text) {
        return ChatColor.DARK_AQUA + "AG " + ChatColor.WHITE + text;
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    private void click(Player player) {
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.2f);
        } catch (Throwable ignored) {
        }
    }
}
