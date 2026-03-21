package nest.fugitivebaron;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

final class SeedStateRepository {
    private static final int SEED_VERSION = 1;

    private final FugitiveBaronPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    SeedStateRepository(final FugitiveBaronPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "seed-state.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        if (!file.exists()) {
            config.set("seed-version", SEED_VERSION);
            save();
        }
    }

    int seedVersion() {
        return config.getInt("seed-version", SEED_VERSION);
    }

    boolean isAntennaSeeded() {
        return config.getBoolean("antenna-nest.seeded", false);
    }

    Location antennaLocation(final FugitiveBaronPlugin plugin) {
        if (!isAntennaSeeded()) {
            return null;
        }
        final String worldName = config.getString("antenna-nest.world");
        if (worldName == null) {
            return null;
        }
        final World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(
            world,
            config.getDouble("antenna-nest.x"),
            config.getDouble("antenna-nest.y"),
            config.getDouble("antenna-nest.z")
        );
    }

    void markAntennaSeeded(final Location location) {
        config.set("seed-version", SEED_VERSION);
        config.set("antenna-nest.seeded", true);
        config.set("antenna-nest.world", location.getWorld() == null ? null : location.getWorld().getName());
        config.set("antenna-nest.x", location.getBlockX());
        config.set("antenna-nest.y", location.getBlockY());
        config.set("antenna-nest.z", location.getBlockZ());
        save();
    }

    Set<String> rumorBoardKeys() {
        return new HashSet<>(config.getStringList("rumor-boards"));
    }

    int rumorBoardCount() {
        return rumorBoardKeys().size();
    }

    boolean hasRumorBoard(final Location location) {
        final String locationKey = key(location);
        return rumorBoardKeys().stream().anyMatch(entry -> entry.endsWith("@" + locationKey));
    }

    void addRumorBoard(final String id, final Location location) {
        final Set<String> keys = rumorBoardKeys();
        keys.add(id + "@" + key(location));
        config.set("seed-version", SEED_VERSION);
        config.set("rumor-boards", keys.stream().sorted().toList());
        save();
    }

    List<Map<String, Object>> viceSiteMaps() {
        final ConfigurationSection section = config.getConfigurationSection("vice-sites");
        if (section == null) {
            return List.of();
        }
        final List<Map<String, Object>> sites = new ArrayList<>();
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }
            sites.add(Map.of(
                "id", key,
                "name", child.getString("name", key),
                "world", child.getString("world", "world"),
                "x", child.getInt("x"),
                "y", child.getInt("y"),
                "z", child.getInt("z"),
                "variant", child.getString("variant", "velvet_lantern"),
                "clue", child.getString("clue", "Someone here knew the Baron."),
                "nextLead", child.getString("next-lead", "The next whispers point outward.")
            ));
        }
        return sites;
    }

    void saveViceSite(
        final String id,
        final String name,
        final Location location,
        final String variant,
        final String clue,
        final String nextLead
    ) {
        final String path = "vice-sites." + id;
        config.set("seed-version", SEED_VERSION);
        config.set(path + ".name", name);
        config.set(path + ".world", location.getWorld() == null ? null : location.getWorld().getName());
        config.set(path + ".x", location.getBlockX());
        config.set(path + ".y", location.getBlockY());
        config.set(path + ".z", location.getBlockZ());
        config.set(path + ".variant", variant);
        config.set(path + ".clue", clue);
        config.set(path + ".next-lead", nextLead);
        save();
    }

    int viceSiteCount() {
        return viceSiteMaps().size();
    }

    Set<String> discoveredViceSites(final UUID playerId) {
        return new HashSet<>(config.getStringList("discoveries." + playerId + ".vice-sites"));
    }

    boolean hasDiscoveredViceSite(final UUID playerId, final String siteId) {
        return discoveredViceSites(playerId).contains(siteId);
    }

    void markViceSiteDiscovered(final UUID playerId, final String siteId) {
        final Set<String> discovered = discoveredViceSites(playerId);
        discovered.add(siteId);
        config.set("discoveries." + playerId + ".vice-sites", discovered.stream().sorted().toList());
        save();
    }

    void resetViceSiteDiscoveries(final UUID playerId) {
        config.set("discoveries." + playerId + ".vice-sites", null);
        save();
    }

    int resetAllViceSiteDiscoveries() {
        final ConfigurationSection discoveries = config.getConfigurationSection("discoveries");
        if (discoveries == null) {
            return 0;
        }

        int reset = 0;
        for (final String playerId : discoveries.getKeys(false)) {
            final String path = "discoveries." + playerId + ".vice-sites";
            if (config.isList(path)) {
                config.set(path, null);
                reset++;
            }
        }
        if (reset > 0) {
            save();
        }
        return reset;
    }

    private String key(final Location location) {
        final String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return world + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void save() {
        try {
            plugin.getDataFolder().mkdirs();
            config.save(file);
        } catch (final IOException exception) {
            plugin.getLogger().warning("Failed to save seed-state.yml: " + exception.getMessage());
        }
    }
}
