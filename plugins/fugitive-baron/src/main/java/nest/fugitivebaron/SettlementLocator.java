package nest.fugitivebaron;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

final class SettlementLocator {
    private final FugitiveBaronPlugin plugin;

    SettlementLocator(final FugitiveBaronPlugin plugin) {
        this.plugin = plugin;
    }

    List<SettlementTarget> locateSettlements() {
        final List<SettlementTarget> targets = new ArrayList<>();
        targets.addAll(configuredSettlements());
        targets.addAll(loadedVillagerClusters());
        targets.sort(Comparator.comparing(SettlementTarget::name));
        return dedupe(targets);
    }

    private List<SettlementTarget> configuredSettlements() {
        final List<SettlementTarget> targets = new ArrayList<>();
        final FileConfiguration config = plugin.getConfig();
        final ConfigurationSection section = config.getConfigurationSection("settlements");
        if (section == null) {
            for (final World world : plugin.getServer().getWorlds()) {
                final Location spawn = world.getSpawnLocation();
                targets.add(new SettlementTarget("spawn_" + world.getName(), spawn, "spawn"));
            }
            return targets;
        }

        for (final String key : section.getKeys(false)) {
            final ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }
            final World world = plugin.getServer().getWorld(child.getString("world", "world"));
            if (world == null) {
                continue;
            }
            final Location location = new Location(world, child.getDouble("x"), child.getDouble("y"), child.getDouble("z"));
            final String style = child.getString("board-style", key);
            targets.add(new SettlementTarget(key, location, style));
        }
        return targets;
    }

    private List<SettlementTarget> loadedVillagerClusters() {
        final List<SettlementTarget> clustered = new ArrayList<>();
        for (final World world : plugin.getServer().getWorlds()) {
            for (final Entity entity : world.getEntitiesByClass(Villager.class)) {
                final Location location = entity.getLocation();
                SettlementTarget existing = null;
                for (final SettlementTarget target : clustered) {
                    if (!target.location().getWorld().equals(location.getWorld())) {
                        continue;
                    }
                    if (target.location().distanceSquared(location) <= 48 * 48) {
                        existing = target;
                        break;
                    }
                }
                if (existing == null) {
                    clustered.add(new SettlementTarget(
                        "village_" + clustered.size(),
                        location.clone(),
                        "village"
                    ));
                }
            }
        }
        return clustered;
    }

    private List<SettlementTarget> dedupe(final List<SettlementTarget> targets) {
        final List<SettlementTarget> deduped = new ArrayList<>();
        for (final SettlementTarget target : targets) {
            boolean duplicate = false;
            for (final SettlementTarget existing : deduped) {
                if (!existing.location().getWorld().equals(target.location().getWorld())) {
                    continue;
                }
                if (existing.location().distanceSquared(target.location()) <= 64 * 64) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                deduped.add(target);
            }
        }
        return deduped;
    }

    record SettlementTarget(String name, Location location, String style) {
    }
}
