package nest.fugitivebaron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

final class HideoutService {
    private static final int REQUIRED_HIDEOUT_COUNT = 7;

    private final FugitiveBaronPlugin plugin;
    private List<Hideout> hideouts = List.of();
    private final Map<String, Location> locationOverrides = new HashMap<>();
    private int activeHideoutIndex;

    HideoutService(final FugitiveBaronPlugin plugin) {
        this.plugin = plugin;
        reload(plugin.getConfig());
    }

    void reload(final FileConfiguration config) {
        final ConfigurationSection section = config.getConfigurationSection("hideouts");
        if (section == null) {
            plugin.getLogger().warning("No hideouts section found in config. Baron radar will be inert.");
            this.hideouts = List.of();
            this.activeHideoutIndex = 0;
            return;
        }

        final List<Hideout> loaded = new ArrayList<>();
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection child = section.getConfigurationSection(key);
            if (child == null) {
                continue;
            }
            final boolean enabled = child.getBoolean("enabled", true);
            if (!enabled) {
                continue;
            }
            final String name = child.getString("name", key);
            final String world = child.getString("world", "world");
            loaded.add(new Hideout(
                key,
                enabled,
                name,
                world,
                child.getDouble("x"),
                child.getDouble("y"),
                child.getDouble("z"),
                child.getString("mood", "uneasy silence"),
                child.getString("clue", "Something here feels recently abandoned."),
                child.getString("ambient-line", "The air itself sounds suspicious."),
                child.getString("trust-intel", "John offers nothing but a thin smile."),
                child.getString("ambient-sound-id", "baron.hideout." + key)
            ));
        }

        loaded.sort(Comparator.comparing(Hideout::id));
        this.hideouts = Collections.unmodifiableList(loaded);
        final int configuredTotal = section.getKeys(false).size();
        if (configuredTotal != REQUIRED_HIDEOUT_COUNT) {
            plugin.getLogger().warning("Expected exactly 7 configured hideouts, found " + configuredTotal + ".");
        }
        if (hideouts.isEmpty()) {
            plugin.getLogger().warning("No enabled hideouts found. Baron radar will be inert.");
        } else if (hideouts.size() != configuredTotal) {
            plugin.getLogger().warning("Loaded " + hideouts.size() + " enabled hideout(s) out of " + configuredTotal + " configured.");
        }

        final int configuredActive = config.getInt("encounter.active-hideout-index", 0);
        if (hideouts.isEmpty()) {
            this.activeHideoutIndex = 0;
        } else {
            this.activeHideoutIndex = Math.floorMod(configuredActive, hideouts.size());
        }
    }

    List<Hideout> hideouts() {
        return hideouts;
    }

    Hideout activeHideout() {
        if (hideouts.isEmpty()) {
            return null;
        }
        return hideouts.get(activeHideoutIndex);
    }

    Location activeHideoutLocation() {
        final Hideout active = activeHideout();
        return active == null ? null : locationFor(active);
    }

    String activeHideoutId() {
        final Hideout active = activeHideout();
        return active == null ? null : active.id();
    }

    String activeHideoutName() {
        final Hideout active = activeHideout();
        return active == null ? null : active.name();
    }

    List<String> enabledHideoutIds() {
        return hideouts.stream().map(Hideout::id).toList();
    }

    List<HideoutSignal> nearestSignalsFor(final Player player, final int limit) {
        final Location playerLocation = player.getLocation();
        return hideouts.stream()
            .map(hideout -> toSignal(playerLocation, hideout))
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingDouble(HideoutSignal::distanceSquared))
            .limit(limit)
            .toList();
    }

    Hideout randomHideout() {
        if (hideouts.isEmpty()) {
            return null;
        }
        return hideouts.get(ThreadLocalRandom.current().nextInt(hideouts.size()));
    }

    void setActiveHideoutById(final String hideoutId) {
        for (int index = 0; index < hideouts.size(); index++) {
            if (hideouts.get(index).id().equalsIgnoreCase(hideoutId)) {
                this.activeHideoutIndex = index;
                return;
            }
        }
        throw new IllegalArgumentException("Unknown hideout id: " + hideoutId);
    }

    void chooseRandomActiveHideout() {
        if (hideouts.isEmpty()) {
            return;
        }
        this.activeHideoutIndex = ThreadLocalRandom.current().nextInt(hideouts.size());
    }

    boolean advanceToNextHideout() {
        if (hideouts.size() <= 1) {
            return false;
        }
        this.activeHideoutIndex = (activeHideoutIndex + 1) % hideouts.size();
        return true;
    }

    Component radarSummaryFor(final Player player) {
        final List<HideoutSignal> signals = nearestSignalsFor(player, 3);
        if (signals.isEmpty()) {
            return Component.text("The Brothel Radar hisses, but finds no John-network signals.", NamedTextColor.GRAY);
        }

        final Component prefix = Component.text("Brothel Radar picks up: ", NamedTextColor.AQUA);
        Component line = prefix;
        for (int index = 0; index < signals.size(); index++) {
            final HideoutSignal signal = signals.get(index);
            if (index > 0) {
                line = line.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            }
            line = line.append(Component.text(
                signal.hideout().name() + " " + signal.cardinal() + " " + Math.round(Math.sqrt(signal.distanceSquared())) + "m",
                NamedTextColor.WHITE
            ));
        }
        return line;
    }

    Hideout nearestHideoutWithin(final Player player, final double radius) {
        return nearestSignalsFor(player, 1).stream()
            .filter(signal -> signal.distanceSquared() <= radius * radius)
            .map(HideoutSignal::hideout)
            .findFirst()
            .orElse(null);
    }

    Component nearbyHideoutIntelFor(final Player player, final double radius) {
        final Hideout hideout = nearestHideoutWithin(player, radius);
        if (hideout == null) {
            return null;
        }

        final boolean active = activeHideout() != null && activeHideout().id().equals(hideout.id());
        Component line = Component.text(hideout.name() + ": ", NamedTextColor.GOLD)
            .append(Component.text(hideout.mood(), NamedTextColor.YELLOW))
            .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
            .append(Component.text(hideout.clue(), NamedTextColor.WHITE));

        if (active) {
            line = line.append(Component.text(" | The John-network signal terminates here.", NamedTextColor.GREEN));
        }
        return line;
    }

    String activeHideoutTrustIntel() {
        final Hideout hideout = activeHideout();
        return hideout == null ? "John offers nothing but a thin smile." : hideout.trustIntel();
    }

    String activeHideoutAmbientLine() {
        final Hideout hideout = activeHideout();
        return hideout == null ? null : hideout.ambientLine();
    }

    String activeHideoutAmbientSoundId() {
        final Hideout hideout = activeHideout();
        return hideout == null ? null : hideout.ambientSoundId();
    }

    void setLocationOverride(final String hideoutId, final Location location) {
        if (hideoutId == null || location == null) {
            return;
        }
        locationOverrides.put(hideoutId, location.clone());
    }

    void clearLocationOverride(final String hideoutId) {
        if (hideoutId == null) {
            return;
        }
        locationOverrides.remove(hideoutId);
    }

    Location locationForId(final String hideoutId) {
        return hideouts.stream()
            .filter(hideout -> hideout.id().equalsIgnoreCase(hideoutId))
            .findFirst()
            .map(this::locationFor)
            .map(Location::clone)
            .orElse(null);
    }

    private HideoutSignal toSignal(final Location playerLocation, final Hideout hideout) {
        final Location hideoutLocation = locationFor(hideout);
        if (hideoutLocation == null || playerLocation.getWorld() == null || !playerLocation.getWorld().equals(hideoutLocation.getWorld())) {
            return null;
        }

        final double dx = hideoutLocation.getX() - playerLocation.getX();
        final double dz = hideoutLocation.getZ() - playerLocation.getZ();
        final double distanceSquared = dx * dx + dz * dz;
        return new HideoutSignal(hideout, distanceSquared, cardinal(dx, dz));
    }

    private String cardinal(final double dx, final double dz) {
        final double angle = Math.toDegrees(Math.atan2(-dx, dz));
        final double normalized = (angle + 360.0D) % 360.0D;
        final String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        final int index = (int) Math.round(normalized / 45.0D) % directions.length;
        return directions[index];
    }

    private Location locationFor(final Hideout hideout) {
        final Location override = locationOverrides.get(hideout.id());
        if (override != null) {
            return override.clone();
        }
        return hideout.toLocation(plugin);
    }

    record HideoutSignal(Hideout hideout, double distanceSquared, String cardinal) {
    }
}
