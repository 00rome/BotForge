package dev.rome.botforge;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Standalone bot controller. The visual player entity is packet-driven; bot AI is
 * owned by this plugin rather than delegated to Citizens or another NPC plugin.
 *
 * NOTE: This first prototype is deliberately version-locked to Paper 1.21.11.
 * It is not a real authenticated/connected Minecraft client and cannot trigger
 * every Player-only server mechanic.
 */
public final class BotForgePlugin extends JavaPlugin {
    private final Map<String, BotAgent> bots = new LinkedHashMap<>();
    private BukkitTask aiTask;
    private FakePlayerPackets packets;

    @Override public void onEnable() {
        saveDefaultConfig();
        packets = new FakePlayerPackets(this);
        aiTask = Bukkit.getScheduler().runTaskTimer(this, this::tickBots, 1L,
                Math.max(1L, getConfig().getLong("ai-update-ticks", 4L)));
        getLogger().info("BotForge enabled. Standalone fake-player prototype; no Citizens dependency.");
    }

    @Override public void onDisable() {
        if (aiTask != null) aiTask.cancel();
        for (BotAgent bot : new ArrayList<>(bots.values())) packets.despawn(bot);
        bots.clear();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("botforge.admin")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "BotForge commands: /bf spawn <name>, remove <name>, list, follow <name>, wander <name>, stop <name>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("Run this command in-game."); return true; }
                if (args.length < 2) { sender.sendMessage("Usage: /bf spawn <name>"); return true; }
                if (bots.size() >= getConfig().getInt("max-bots-per-world", 8)) { sender.sendMessage("Bot limit reached."); return true; }
                String name = cleanName(args[1]);
                if (name.isBlank() || name.length() > 16 || bots.containsKey(name.toLowerCase(Locale.ROOT))) {
                    sender.sendMessage("Name must be 1-16 characters and unique."); return true;
                }
                BotAgent bot = new BotAgent(name, player.getLocation().clone(), player.getUniqueId());
                bots.put(name.toLowerCase(Locale.ROOT), bot);
                packets.spawn(bot);
                sender.sendMessage(ChatColor.GREEN + "Created bot " + name + ". Use /bf follow|wander|stop " + name);
            }
            case "remove" -> {
                if (args.length < 2) { sender.sendMessage("Usage: /bf remove <name>"); return true; }
                BotAgent bot = bots.remove(args[1].toLowerCase(Locale.ROOT));
                if (bot == null) sender.sendMessage("No bot with that name.");
                else { packets.despawn(bot); sender.sendMessage("Removed " + bot.name); }
            }
            case "list" -> sender.sendMessage(bots.isEmpty() ? "No bots." : "Bots: " + String.join(", ", bots.values().stream().map(b -> b.name + " (" + b.mode.name().toLowerCase(Locale.ROOT) + ")").toList()));
            case "follow", "wander", "stop" -> {
                if (args.length < 2) { sender.sendMessage("Usage: /bf " + args[0] + " <name>"); return true; }
                BotAgent bot = bots.get(args[1].toLowerCase(Locale.ROOT));
                if (bot == null) { sender.sendMessage("No bot with that name."); return true; }
                bot.mode = switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "follow" -> BotAgent.Mode.FOLLOW;
                    case "wander" -> BotAgent.Mode.WANDER;
                    default -> BotAgent.Mode.IDLE;
                };
                if (sender instanceof Player p && args[0].equalsIgnoreCase("follow")) bot.targetPlayer = p.getUniqueId();
                sender.sendMessage(bot.name + " mode: " + bot.mode.name().toLowerCase(Locale.ROOT));
            }
            default -> sender.sendMessage("Unknown subcommand. Use /bf.");
        }
        return true;
    }

    private void tickBots() {
        for (BotAgent bot : bots.values()) {
            if (bot.mode == BotAgent.Mode.IDLE) continue;
            Location next = bot.location.clone();
            if (bot.mode == BotAgent.Mode.FOLLOW) {
                Player target = bot.targetPlayer == null ? null : Bukkit.getPlayer(bot.targetPlayer);
                if (target == null || !target.getWorld().equals(bot.location.getWorld())) continue;
                double distance = target.getLocation().distance(bot.location);
                if (distance > getConfig().getDouble("follow-distance", 3.0)) {
                    next = stepToward(bot.location, target.getLocation(), 0.22);
                }
                bot.lookAt(target.getLocation());
            } else if (bot.mode == BotAgent.Mode.WANDER) {
                if (bot.wanderTarget == null || bot.location.distanceSquared(bot.wanderTarget) < 1.0) {
                    int radius = getConfig().getInt("wander-radius", 10);
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    bot.wanderTarget = bot.location.clone().add(r.nextInt(-radius, radius + 1), 0, r.nextInt(-radius, radius + 1));
                }
                next = stepToward(bot.location, bot.wanderTarget, 0.12);
            }
            next.setY(findGroundY(next));
            if (isWalkable(next)) {
                bot.location = next;
                packets.move(bot);
            }
            // Combat is intentionally a later milestone: packet-only fake players do not
            // have a server-authoritative hitbox, so attacking mobs needs a separate hitbox/entity layer.
        }
    }

    private Location stepToward(Location from, Location to, double speed) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.001) return from.clone();
        Location result = from.clone().add(dx / length * speed, 0, dz / length * speed);
        result.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        return result;
    }

    private double findGroundY(Location location) {
        World world = location.getWorld();
        int x = location.getBlockX(), z = location.getBlockZ();
        int y = Math.min(world.getMaxHeight() - 2, location.getBlockY());
        while (y > world.getMinHeight() && world.getBlockAt(x, y, z).isPassable()) y--;
        return y + 1;
    }

    private boolean isWalkable(Location location) {
        World world = location.getWorld();
        return world.getBlockAt(location).isPassable()
                && world.getBlockAt(location.clone().add(0, 1, 0)).isPassable()
                && !world.getBlockAt(location.clone().subtract(0, 1, 0)).isPassable();
    }

    private String cleanName(String input) { return input.replaceAll("[^A-Za-z0-9_]", ""); }

    public Collection<BotAgent> getBots() { return Collections.unmodifiableCollection(bots.values()); }

    public static final class BotAgent {
        enum Mode { IDLE, FOLLOW, WANDER }
        final String name;
        final UUID uuid = UUID.randomUUID();
        Location location;
        UUID targetPlayer;
        Location wanderTarget;
        Mode mode = Mode.IDLE;
        float yaw, pitch;
        net.minecraft.server.level.ServerPlayer nmsPlayer;
        BotAgent(String name, Location location, UUID targetPlayer) {
            this.name = name; this.location = location; this.targetPlayer = targetPlayer;
            this.yaw = location.getYaw(); this.pitch = location.getPitch();
        }
        void lookAt(Location target) {
            double dx = target.getX() - location.getX();
            double dy = target.getY() + 1.0 - (location.getY() + 1.6);
            double dz = target.getZ() - location.getZ();
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        }
    }
}
