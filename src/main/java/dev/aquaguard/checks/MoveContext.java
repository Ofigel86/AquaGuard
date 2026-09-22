package dev.aquaguard.checks;

import dev.aquaguard.core.PlayerData;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class MoveContext {
    public final Player player;
    public final PlayerData data;
    public final Location from;
    public final Location to;
    public final double dx;
    public final double dy;
    public final double dz;
    public final double horizontal;
    public final boolean clientGround;
    public final boolean serverGround;
    public final Block feet;
    public final Block below;
    public final Block above;
    public final long now;
    public final boolean lagging;
    public final boolean bedrock;

    public MoveContext(Player player, PlayerData data, Location from, Location to,
                       double dx, double dy, double dz, double horizontal,
                       boolean clientGround, boolean serverGround,
                       Block feet, Block below, Block above,
                       long now, boolean lagging, boolean bedrock) {
        this.player = player;
        this.data = data;
        this.from = from;
        this.to = to;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.horizontal = horizontal;
        this.clientGround = clientGround;
        this.serverGround = serverGround;
        this.feet = feet;
        this.below = below;
        this.above = above;
        this.now = now;
        this.lagging = lagging;
        this.bedrock = bedrock;
    }
}
