package nest.fugitivebaron;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class BaronCommand implements CommandExecutor, TabCompleter {
    private final FugitiveBaronPlugin plugin;
    private final FugitiveBaronController controller;
    private final HideoutService hideoutService;

    BaronCommand(
        final FugitiveBaronPlugin plugin,
        final FugitiveBaronController controller,
        final HideoutService hideoutService
    ) {
        this.plugin = plugin;
        this.controller = controller;
        this.hideoutService = hideoutService;
    }

    @Override
    public boolean onCommand(
        @NotNull final CommandSender sender,
        @NotNull final Command command,
        @NotNull final String label,
        @NotNull final String[] args
    ) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /fugitivebaron <spawn|despawn|item|radar|hideout|reload>", NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> {
                try {
                    final LivingEntity baron = controller.spawnBaronAtActiveHideout();
                    sender.sendMessage(Component.text("Spawned the Baron at active hideout " + format(baron.getLocation()) + ".", NamedTextColor.GREEN));
                    baron.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, baron.getLocation().add(0, 0.6, 0), 12, 0.3, 0.3, 0.3, 0.01);
                } catch (final IllegalStateException exception) {
                    sender.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
                }
                return true;
            }
            case "despawn" -> {
                controller.despawnBaron();
                sender.sendMessage(Component.text("The Baron has vanished.", NamedTextColor.YELLOW));
                return true;
            }
            case "item" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can receive the powder item.", NamedTextColor.RED));
                    return true;
                }
                player.getInventory().addItem(plugin.createRecognitionItem());
                sender.sendMessage(Component.text("A discreet packet has been placed in your hand.", NamedTextColor.GREEN));
                return true;
            }
            case "radar" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can receive the radar.", NamedTextColor.RED));
                    return true;
                }
                player.getInventory().addItem(plugin.createDragonRadar());
                sender.sendMessage(Component.text("The radar screen flickers with seven blips.", NamedTextColor.GREEN));
                return true;
            }
            case "hideout" -> {
                if (args.length == 1) {
                    sender.sendMessage(Component.text("Hideouts: " + hideoutService.hideouts().stream().map(Hideout::id).toList(), NamedTextColor.YELLOW));
                    return true;
                }
                if ("random".equalsIgnoreCase(args[1])) {
                    hideoutService.chooseRandomActiveHideout();
                    sender.sendMessage(Component.text("Active hideout randomized.", NamedTextColor.GREEN));
                    return true;
                }
                try {
                    hideoutService.setActiveHideoutById(args[1]);
                    sender.sendMessage(Component.text("Active hideout set to " + args[1] + ".", NamedTextColor.GREEN));
                } catch (final IllegalArgumentException exception) {
                    sender.sendMessage(Component.text(exception.getMessage(), NamedTextColor.RED));
                }
                return true;
            }
            case "reload" -> {
                plugin.reloadPluginConfig();
                sender.sendMessage(Component.text("Fugitive Baron config reloaded.", NamedTextColor.GREEN));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
                return true;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(
        @NotNull final CommandSender sender,
        @NotNull final Command command,
        @NotNull final String alias,
        @NotNull final String[] args
    ) {
        if (args.length == 1) {
            return List.of("spawn", "despawn", "item", "radar", "hideout", "reload");
        }
        if (args.length == 2 && "hideout".equalsIgnoreCase(args[0])) {
            final List<String> options = hideoutService.hideouts().stream().map(Hideout::id).toList();
            return java.util.stream.Stream.concat(options.stream(), java.util.stream.Stream.of("random")).toList();
        }
        return List.of();
    }

    private String format(final Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}
