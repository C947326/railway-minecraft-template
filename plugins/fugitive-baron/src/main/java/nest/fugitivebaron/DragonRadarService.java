package nest.fugitivebaron;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

final class DragonRadarService {
    private final FugitiveBaronPlugin plugin;
    private final HideoutService hideoutService;
    private final WorldSeedService worldSeedService;
    private long updateTicks;
    private double maxDriftDegrees;
    private double pingRadius;
    private String radarName;
    private List<String> radarLore;

    DragonRadarService(final FugitiveBaronPlugin plugin, final HideoutService hideoutService, final WorldSeedService worldSeedService) {
        this.plugin = plugin;
        this.hideoutService = hideoutService;
        this.worldSeedService = worldSeedService;
        reload(plugin.getConfig());
    }

    void reload(final FileConfiguration config) {
        this.updateTicks = config.getLong("encounter.radar-update-ticks", 60L);
        this.maxDriftDegrees = config.getDouble("encounter.radar-max-drift-degrees", 18.0D);
        this.pingRadius = config.getDouble("encounter.radar-ping-radius", 40.0D);
        this.radarName = config.getString("encounter.radar-name", "Brothel Radar");
        this.radarLore = config.getStringList("encounter.radar-lore");
    }

    ItemStack createRadar() {
        final ItemStack item = new ItemStack(Material.COMPASS);
        final ItemMeta itemMeta = Objects.requireNonNull(item.getItemMeta(), "Compass must have item meta.");
        if (!(itemMeta instanceof CompassMeta meta)) {
            throw new IllegalStateException("Compass item meta must be CompassMeta.");
        }

        meta.displayName(Component.text(radarName, NamedTextColor.GREEN));
        if (!radarLore.isEmpty()) {
            meta.lore(radarLore.stream()
                .map(line -> Component.text(line, NamedTextColor.GRAY))
                .toList());
        }
        meta.setLodestoneTracked(false);
        meta.getPersistentDataContainer().set(plugin.radarKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    void ensurePlayerHasStarterRadar(final Player player) {
        final PersistentDataContainer playerData = player.getPersistentDataContainer();
        if (playerData.has(plugin.receivedRadarKey(), PersistentDataType.BYTE)) {
            return;
        }

        player.getInventory().addItem(createRadar());
        playerData.set(plugin.receivedRadarKey(), PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(Component.text("A Brothel Radar crackles with seven possible signals.", NamedTextColor.GREEN));
    }

    void maybeUpdateRadarTargets(final long currentTick) {
        if (updateTicks <= 0 || currentTick % updateTicks != 0) {
            return;
        }

        for (final Player player : plugin.getServer().getOnlinePlayers()) {
            updatePlayerRadar(player);
        }
    }

    void pingNearestSignals(final Player player) {
        player.sendActionBar(worldSeedService.radarSummaryFor(player));
        final Component clue = worldSeedService.pingAndDiscover(player, pingRadius);
        if (clue != null) {
            player.sendMessage(clue);
        }
        updatePlayerRadar(player);
    }

    void refreshRadarFor(final Player player) {
        updatePlayerRadar(player);
    }

    int resetRadarsForOnlinePlayers() {
        int reset = 0;
        for (final Player player : plugin.getServer().getOnlinePlayers()) {
            resetPlayerRadar(player);
            reset++;
        }
        return reset;
    }

    boolean isDragonRadar(final ItemStack stack) {
        if (stack == null || stack.getType() != Material.COMPASS) {
            return false;
        }
        final ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(plugin.radarKey(), PersistentDataType.BYTE);
    }

    private void resetPlayerRadar(final Player player) {
        final PlayerInventory inventory = player.getInventory();
        final ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isDragonRadar(contents[slot])) {
                inventory.setItem(slot, null);
            }
        }
        if (isDragonRadar(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
        player.getPersistentDataContainer().remove(plugin.receivedRadarKey());
        ensurePlayerHasStarterRadar(player);
        updatePlayerRadar(player);
    }

    private void updatePlayerRadar(final Player player) {
        final Location target = worldSeedService.nextRadarTarget(player);
        final Location signalTarget = target == null ? null : withDrift(player.getLocation(), target);

        final PlayerInventory inventory = player.getInventory();
        updateRadarStack(inventory.getItemInMainHand(), signalTarget);
        updateRadarStack(inventory.getItemInOffHand(), signalTarget);
        for (final ItemStack stack : inventory.getContents()) {
            updateRadarStack(stack, signalTarget);
        }
    }

    private void updateRadarStack(final ItemStack stack, final Location signalTarget) {
        if (!isDragonRadar(stack)) {
            return;
        }
        final ItemMeta itemMeta = stack.getItemMeta();
        if (!(itemMeta instanceof CompassMeta meta)) {
            return;
        }

        if (signalTarget == null) {
            meta.setLodestone(null);
            stack.setItemMeta(meta);
            return;
        }

        meta.setLodestone(signalTarget);
        meta.setLodestoneTracked(false);
        stack.setItemMeta(meta);
    }

    private Location withDrift(final Location playerLocation, final Location trueTarget) {
        if (trueTarget == null) {
            return null;
        }

        final World world = playerLocation.getWorld();
        if (world == null || trueTarget.getWorld() == null || !world.equals(trueTarget.getWorld())) {
            return trueTarget;
        }

        final Vector toTarget = trueTarget.toVector().subtract(playerLocation.toVector());
        toTarget.setY(0);
        if (toTarget.lengthSquared() <= 0.0001D) {
            return trueTarget.clone();
        }

        final double radians = Math.toRadians(ThreadLocalRandom.current().nextDouble(-maxDriftDegrees, maxDriftDegrees));
        final double cos = Math.cos(radians);
        final double sin = Math.sin(radians);
        final double x = toTarget.getX() * cos - toTarget.getZ() * sin;
        final double z = toTarget.getX() * sin + toTarget.getZ() * cos;

        final Vector drifted = new Vector(x, 0, z).normalize().multiply(toTarget.length());
        return new Location(world, playerLocation.getX() + drifted.getX(), trueTarget.getY(), playerLocation.getZ() + drifted.getZ());
    }

}
