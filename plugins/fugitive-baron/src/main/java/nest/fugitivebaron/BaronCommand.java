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
    private final WorldSeedService worldSeedService;

    BaronCommand(
        final FugitiveBaronPlugin plugin,
        final FugitiveBaronController controller,
        final HideoutService hideoutService,
        final WorldSeedService worldSeedService
    ) {
        this.plugin = plugin;
        this.controller = controller;
        this.hideoutService = hideoutService;
        this.worldSeedService = worldSeedService;
    }

    @Override
    public boolean onCommand(
        @NotNull final CommandSender sender,
        @NotNull final Command command,
        @NotNull final String label,
        @NotNull final String[] args
    ) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /fugitivebaron <spawn|despawn|item|radar [reset [player]|resetall]|hideout|seed|reload>", NamedTextColor.RED));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "spawn" -> {
                try {
                    final LivingEntity baron = controller.spawnBaronAtActiveHideout();
                    final String activeHideoutId = hideoutService.activeHideoutId();
                    final String activeHideoutName = hideoutService.activeHideoutName();
                    sender.sendMessage(Component.text(
                        "Spawned the Baron at " + activeHideoutId + " (" + activeHideoutName + ") " + format(baron.getLocation()) + ".",
                        NamedTextColor.GREEN
                    ));
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
                if (args.length >= 2 && "resetall".equalsIgnoreCase(args[1])) {
                    sender.sendMessage(worldSeedService.resetAllRadarProgress());
                    for (final Player online : plugin.getServer().getOnlinePlayers()) {
                        plugin.refreshRadar(online);
                    }
                    return true;
                }
                if (args.length >= 2 && "reset".equalsIgnoreCase(args[1])) {
                    final Player target;
                    if (args.length >= 3) {
                        target = plugin.getServer().getPlayerExact(args[2]);
                        if (target == null) {
                            sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
                            return true;
                        }
                    } else if (sender instanceof Player player) {
                        target = player;
                    } else {
                        sender.sendMessage(Component.text("Console usage: /fugitivebaron radar reset <player>", NamedTextColor.RED));
                        return true;
                    }
                    sender.sendMessage(worldSeedService.resetRadarProgress(target));
                    plugin.refreshRadar(target);
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can receive the radar.", NamedTextColor.RED));
                    return true;
                }
                player.getInventory().addItem(plugin.createDragonRadar());
                plugin.refreshRadar(player);
                sender.sendMessage(Component.text("A replacement Brothel Radar crackles back to life.", NamedTextColor.GREEN));
                sender.sendMessage(worldSeedService.radarSummaryFor(player));
                return true;
            }
            case "hideout" -> {
                if (args.length == 1) {
                    sender.sendMessage(Component.text(
                        "Enabled hideouts: " + hideoutService.enabledHideoutIds() + " | active: " + hideoutService.activeHideoutId(),
                        NamedTextColor.YELLOW
                    ));
                    return true;
                }
                if ("random".equalsIgnoreCase(args[1])) {
                    hideoutService.chooseRandomActiveHideout();
                    sender.sendMessage(Component.text("Active hideout randomized to " + hideoutService.activeHideoutId() + ".", NamedTextColor.GREEN));
                    return true;
                }
                try {
                    hideoutService.setActiveHideoutById(args[1]);
                    sender.sendMessage(Component.text(
                        "Active hideout set to " + hideoutService.activeHideoutId() + " (" + hideoutService.activeHideoutName() + ").",
                        NamedTextColor.GREEN
                    ));
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
            case "seed" -> {
                if (args.length == 1 || "status".equalsIgnoreCase(args[1])) {
                    sender.sendMessage(worldSeedService.seedStatus());
                    return true;
                }
                if ("antenna".equalsIgnoreCase(args[1])) {
                    final boolean spawnBaron = args.length >= 3 && "spawn".equalsIgnoreCase(args[2]);
                    sender.sendMessage(worldSeedService.seedAntennaNest(spawnBaron));
                    return true;
                }
                if ("boards".equalsIgnoreCase(args[1])) {
                    sender.sendMessage(worldSeedService.seedBoards());
                    return true;
                }
                if ("vice".equalsIgnoreCase(args[1])) {
                    sender.sendMessage(worldSeedService.seedViceSites());
                    return true;
                }
                if ("all".equalsIgnoreCase(args[1])) {
                    final boolean spawnBaron = args.length >= 3 && "spawn".equalsIgnoreCase(args[2]);
                    sender.sendMessage(worldSeedService.seedAll(spawnBaron));
                    return true;
                }
                sender.sendMessage(Component.text("Usage: /fugitivebaron seed <status|antenna [spawn]|boards|vice|all [spawn]>", NamedTextColor.RED));
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
            return List.of("spawn", "despawn", "item", "radar", "hideout", "seed", "reload");
        }
        if (args.length == 2 && "radar".equalsIgnoreCase(args[0])) {
            return List.of("reset", "resetall");
        }
        if (args.length == 3 && "radar".equalsIgnoreCase(args[0]) && "reset".equalsIgnoreCase(args[1])) {
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).sorted().toList();
        }
        if (args.length == 2 && "hideout".equalsIgnoreCase(args[0])) {
            final List<String> options = hideoutService.hideouts().stream().map(Hideout::id).toList();
            return java.util.stream.Stream.concat(options.stream(), java.util.stream.Stream.of("random")).toList();
        }
        if (args.length == 2 && "seed".equalsIgnoreCase(args[0])) {
            return List.of("status", "antenna", "boards", "vice", "all");
        }
        if (args.length == 3
            && "seed".equalsIgnoreCase(args[0])
            && ("antenna".equalsIgnoreCase(args[1]) || "all".equalsIgnoreCase(args[1]))) {
            return List.of("spawn");
        }
        return List.of();
    }

    private String format(final Location location) {
        return location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }
}
