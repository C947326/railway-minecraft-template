package nest.fugitivebaron;

import org.bukkit.Location;

record Hideout(
    String id,
    boolean enabled,
    String name,
    String worldName,
    double x,
    double y,
    double z,
    String mood,
    String clue,
    String ambientLine,
    String trustIntel,
    String ambientSoundId
) {
    Location toLocation(final FugitiveBaronPlugin plugin) {
        final var world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }
}
