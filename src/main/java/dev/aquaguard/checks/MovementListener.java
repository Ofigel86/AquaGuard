package dev.aquaguard.checks;

import dev.aquaguard.AquaGuard;
import dev.aquaguard.checks.movement.MovementChecks;
import dev.aquaguard.checks.movement.MovementExtraChecks;
import dev.aquaguard.core.PlayerData;
import dev.aquaguard.util.BlockUtil;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class MovementListener implements Listener {
    private final AquaGuard plugin;
    private final MovementChecks physics;
    private final MovementExtraChecks extra;

    public MovementListener(AquaGuard plugin) {
        this.plugin = plugin;
        this.physics = new MovementChecks(plugin);
        this.extra = new MovementExtraChecks(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player player = event.getPlayer();
        if (plugin.freeze().isFrozen(player.getUniqueId())) {
            event.setTo(event.getFrom());
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != to.getWorld()) return;
        PlayerData data = plugin.data().get(player);
        if (plugin.exemption().bypass(player)) {
            data.lastLoc = to;
            data.lastMoveMs = System.currentTimeMillis();
            return;
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        boolean rotationOnly = dx == 0 && dy == 0 && dz == 0;
        if (rotationOnly) {
            if (plugin.checks().enabled("InvalidPitchA")) {
                float pitch = to.getPitch();
                if (pitch < -90.01f || pitch > 90.01f) {
                    plugin.flagger().flag(player, "InvalidPitchA", plugin.flagger().vl("InvalidPitchA", 2.0),
                            "pitch=" + pitch, "none");
                }
            }
            return;
        }
        long now = System.currentTimeMillis();
        boolean clientGround = player.isOnGround();
        boolean serverGround = BlockUtil.serverOnGround(player);
        MoveContext ctx = new MoveContext(player, data, from, to, dx, dy, dz, Math.hypot(dx, dz),
                clientGround, serverGround, to.getBlock(), to.clone().subtract(0, 1, 0).getBlock(),
                to.clone().add(0, 1, 0).getBlock(), now,
                plugin.exemption().lagging(player), plugin.exemption().bedrock(data));
        rememberSafe(ctx);
        physics.trackFall(ctx);
        physics.run(ctx);
        extra.run(ctx);
        plugin.combatChecks().afterMove(player, data, ctx.horizontal);
        plugin.setback().apply(event, player, data);
        data.lastClientGround = clientGround;
        data.lastServerGround = serverGround;
        data.lastH = ctx.horizontal;
        data.lastDx = dx;
        data.lastDz = dz;
        data.lastDy = dy;
        data.lastLoc = to.clone();
        data.lastMoveMs = now;
        if (serverGround) data.airTicks = 0;
        else if (!player.isFlying() && !player.isGliding()) data.airTicks++;
    }

    private void rememberSafe(MoveContext ctx) {
        if (!ctx.serverGround || plugin.exemption().creativeLike(ctx.player)) return;
        if (BlockUtil.isLiquidLike(ctx.feet) || BlockUtil.insideSolid(ctx.player)) return;
        if (ctx.data.hadRecentVelocity(300)) return;
        ctx.data.lastSafeGround = ctx.to.clone();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) physics.onLandDamage(player);
    }
}
