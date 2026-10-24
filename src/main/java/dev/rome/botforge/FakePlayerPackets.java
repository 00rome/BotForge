package dev.rome.botforge;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Packet-only fake-player renderer. Crucially, outgoing packets are sent via
 * each REAL viewer's connection; the fake player has no client connection.
 */
final class FakePlayerPackets {
    private final BotForgePlugin plugin;
    FakePlayerPackets(BotForgePlugin plugin) { this.plugin = plugin; }

    void spawn(BotForgePlugin.BotAgent bot) {
        Location loc = bot.location;
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel level = ((CraftWorld) loc.getWorld()).getHandle();
        GameProfile profile = new GameProfile(bot.uuid, bot.name);
        ServerPlayer fake = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        fake.setPos(loc.getX(), loc.getY(), loc.getZ());
        fake.setYRot(bot.yaw);
        fake.setXRot(bot.pitch);
        fake.setYHeadRot(bot.yaw);
        bot.nmsPlayer = fake;
        for (Player viewer : viewers(loc)) {
            ServerGamePacketListenerImpl connection = ((CraftPlayer) viewer).getHandle().connection;
            connection.send(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(fake)));
            connection.send(new ClientboundAddEntityPacket(fake));
        }
    }

    void move(BotForgePlugin.BotAgent bot) {
        ServerPlayer fake = bot.nmsPlayer;
        if (fake == null) return;
        Location loc = bot.location;
        fake.setPos(loc.getX(), loc.getY(), loc.getZ());
        fake.setYRot(bot.yaw);
        fake.setXRot(bot.pitch);
        fake.setYHeadRot(bot.yaw);
        for (Player viewer : viewers(loc)) {
            ServerGamePacketListenerImpl connection = ((CraftPlayer) viewer).getHandle().connection;
            connection.send(new ClientboundMoveEntityPacket.PosRot(fake.getId(), (short) 0, (short) 0, (short) 0, (byte) bot.yaw, (byte) bot.pitch, true));
            connection.send(new ClientboundRotateHeadPacket(fake, (byte) bot.yaw));
        }
    }

    void despawn(BotForgePlugin.BotAgent bot) {
        ServerPlayer fake = bot.nmsPlayer;
        if (fake == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            ServerGamePacketListenerImpl connection = ((CraftPlayer) viewer).getHandle().connection;
            connection.send(new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(fake.getId()));
            connection.send(new ClientboundPlayerInfoRemovePacket(List.of(bot.uuid)));
        }
        bot.nmsPlayer = null;
    }

    private List<Player> viewers(Location loc) {
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(loc.getWorld()) && p.getLocation().distanceSquared(loc) < 128 * 128)
                .toList();
    }
}
