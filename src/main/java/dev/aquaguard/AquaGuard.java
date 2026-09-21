package dev.aquaguard;

import dev.aquaguard.bypass.BypassManager;
import dev.aquaguard.checks.CheckManager;
import dev.aquaguard.checks.CombatListener;
import dev.aquaguard.checks.Exemption;
import dev.aquaguard.checks.Flagger;
import dev.aquaguard.checks.MovementListener;
import dev.aquaguard.checks.PlayerListener;
import dev.aquaguard.checks.SetbackService;
import dev.aquaguard.checks.VelocityListener;
import dev.aquaguard.checks.WorldListener;
import dev.aquaguard.checks.combat.CombatChecks;
import dev.aquaguard.checks.player.PlayerChecks;
import dev.aquaguard.checks.world.WorldChecks;
import dev.aquaguard.cmd.AgCommand;
import dev.aquaguard.cmd.AgTabCompleter;
import dev.aquaguard.core.AlertManager;
import dev.aquaguard.core.DataManager;
import dev.aquaguard.core.History;
import dev.aquaguard.core.MessageService;
import dev.aquaguard.core.PunishmentManager;
import dev.aquaguard.core.RuntimeSettings;
import dev.aquaguard.core.Stats;
import dev.aquaguard.core.ViolationLog;
import dev.aquaguard.core.ViolationManager;
import dev.aquaguard.core.WatchManager;
import dev.aquaguard.freeze.FreezeManager;
import dev.aquaguard.gui.GuiManager;
import dev.aquaguard.owner.OwnerAccess;
import dev.aquaguard.penalty.PenaltyManager;
import dev.aquaguard.webhook.DiscordWebhook;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class AquaGuard extends JavaPlugin {
    private static AquaGuard instance;

    private ViolationManager violations;
    private DataManager data;
    private MessageService messages;
    private AlertManager alerts;
    private PunishmentManager punishments;
    private BypassManager bypass;
    private CheckManager checks;
    private FreezeManager freeze;
    private OwnerAccess owner;
    private PenaltyManager penalties;
    private ViolationLog violationLog;
    private DiscordWebhook webhook;
    private History history;
    private Stats stats;
    private RuntimeSettings settings;
    private Exemption exemption;
    private SetbackService setback;
    private Flagger flagger;
    private CombatChecks combatChecks;
    private WorldChecks worldChecks;
    private PlayerChecks playerChecks;
    private GuiManager gui;
    private WatchManager watch;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("messages.yml", false);
        applyConfigDefaults();

        this.settings = new RuntimeSettings(this);
        this.messages = new MessageService(this);
        this.violations = new ViolationManager(this);
        this.violations.load();
        this.data = new DataManager();
        this.alerts = new AlertManager(this);
        this.punishments = new PunishmentManager(this);
        this.bypass = new BypassManager(this);
        this.checks = new CheckManager(this);
        this.freeze = new FreezeManager(this);
        this.owner = new OwnerAccess(this);
        this.penalties = new PenaltyManager(this);
        this.violationLog = new ViolationLog(this);
        this.webhook = new DiscordWebhook(this);
        this.history = new History(getConfig().getInt("history.global", 300), getConfig().getInt("history.per-player", 40));
        this.stats = new Stats();
        this.exemption = new Exemption(this);
        this.setback = new SetbackService(this);
        this.flagger = new Flagger(this);
        this.combatChecks = new CombatChecks(this);
        this.worldChecks = new WorldChecks(this);
        this.playerChecks = new PlayerChecks(this);
        this.watch = new WatchManager(this);
        this.gui = new GuiManager(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(owner, this);
        pm.registerEvents(data, this);
        pm.registerEvents(freeze, this);
        pm.registerEvents(new MovementListener(this), this);
        pm.registerEvents(new VelocityListener(this), this);
        pm.registerEvents(new WorldListener(this), this);
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(penalties, this);
        pm.registerEvents(gui, this);

        long minute = 20L * 60L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            violations.decayAll(getConfig().getDouble("decay-per-minute", 0.5));
            violations.save();
        }, minute, minute);
        getServer().getScheduler().runTaskTimer(this, () -> {
            freeze.tick();
            watch.tick();
        }, 20L, 10L);

        AgCommand command = new AgCommand(this);
        if (getCommand("ag") != null) {
            getCommand("ag").setExecutor(command);
            getCommand("ag").setTabCompleter(new AgTabCompleter(this));
        }
        getLogger().info("AquaGuard " + getDescription().getVersion()
                + " enabled. Checks " + checks.enabledCount() + "/" + dev.aquaguard.checks.CheckCatalog.all().size()
                + ", penalties=" + penalties.mode().name().toLowerCase()
                + ", punishments=" + (punishments.enabled() ? punishments.mode() : "off"));
    }

    @Override
    public void onDisable() {
        if (violations != null) violations.save();
        getLogger().info("AquaGuard disabled.");
    }

    public void reloadAll() {
        reloadConfig();
        applyConfigDefaults();
        settings.reload();
        messages.reload();
        checks.reload();
        owner.reload();
        bypass.reloadCodes();
        punishments.reload();
    }

    private void applyConfigDefaults() {
        try (var in = getResource("config.yml")) {
            if (in == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            getConfig().setDefaults(defaults);
            getConfig().options().copyDefaults(true);
        } catch (Exception ex) {
            getLogger().warning("config defaults: " + ex.getMessage());
        }
    }

    public static AquaGuard get() { return instance; }
    public ViolationManager violations() { return violations; }
    public DataManager data() { return data; }
    public MessageService messages() { return messages; }
    public AlertManager alerts() { return alerts; }
    public PunishmentManager punishments() { return punishments; }
    public BypassManager bypass() { return bypass; }
    public CheckManager checks() { return checks; }
    public FreezeManager freeze() { return freeze; }
    public OwnerAccess owner() { return owner; }
    public PenaltyManager penalties() { return penalties; }
    public ViolationLog violationLog() { return violationLog; }
    public DiscordWebhook webhook() { return webhook; }
    public History history() { return history; }
    public Stats stats() { return stats; }
    public RuntimeSettings settings() { return settings; }
    public Exemption exemption() { return exemption; }
    public SetbackService setback() { return setback; }
    public Flagger flagger() { return flagger; }
    public CombatChecks combatChecks() { return combatChecks; }
    public WorldChecks worldChecks() { return worldChecks; }
    public PlayerChecks playerChecks() { return playerChecks; }
    public GuiManager gui() { return gui; }
    public WatchManager watch() { return watch; }
}
