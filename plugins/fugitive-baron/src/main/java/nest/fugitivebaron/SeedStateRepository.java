package nest.fugitivebaron;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Location;
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
