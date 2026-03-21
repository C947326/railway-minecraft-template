package nest.fugitivebaron;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

final class WorldSeedService {
    private static final int DEFAULT_MAX_HUNT_CYCLES = 3;

    private final FugitiveBaronPlugin plugin;
    private final HideoutService hideoutService;
    private final FugitiveBaronController controller;
    private final SeedStateRepository seedStateRepository;
    private final SettlementLocator settlementLocator;
    private final StructurePainter painter;
    private final List<ViceSite> viceSites = new ArrayList<>();

    WorldSeedService(
        final FugitiveBaronPlugin plugin,
        final HideoutService hideoutService,
        final FugitiveBaronController controller
    ) {
        this.plugin = plugin;
        this.hideoutService = hideoutService;
        this.controller = controller;
        this.seedStateRepository = new SeedStateRepository(plugin);
        this.settlementLocator = new SettlementLocator(plugin);
        this.painter = new StructurePainter();
        restoreOverrides();
        restoreViceSites();
    }

    Component seedStatus() {
        final boolean baronSpawned = controller.hasBaron();
        final Location baronLocation = controller.getBaronLocation();
        final String baronStatus = baronSpawned
            ? "spawned(" + controller.state().name().toLowerCase() + " at " + format(baronLocation) + ")"
            : "not-spawned";
        return Component.text(
            "Seed status: antenna="
                + seedStateRepository.isAntennaSeeded()
                + " boards="
                + seedStateRepository.rumorBoardCount()
                + " vice="
                + seedStateRepository.viceSiteCount()
                + " cycle="
                + seedStateRepository.huntCycle()
                + " baron="
                + baronStatus
                + " version="
                + seedStateRepository.seedVersion(),
            NamedTextColor.YELLOW
        );
    }

    Component seedAntennaNest() {
        return seedAntennaNest(false);
    }

    Component seedAntennaNest(final boolean spawnBaron) {
        final Hideout active = hideoutService.activeHideout();
        if (active == null) {
            return Component.text("No active hideout is configured.", NamedTextColor.RED);
        }
        final Location location = active.toLocation(plugin);
        if (location == null || location.getWorld() == null) {
            return Component.text("Active hideout world is unavailable.", NamedTextColor.RED);
        }

        final Location base = chooseSeedLocation(active, location);
        seedAntenna(base);
        hideoutService.setLocationOverride(active.id(), base);
        seedStateRepository.markAntennaSeeded(base);
        String suffix = "";
        if (spawnBaron) {
            controller.spawnBaronAtActiveHideout();
            suffix = " John spawned inside.";
            plugin.debugLog("Spawned Baron as part of antenna seeding.");
        }
        plugin.debugLog("Seeded Antenna Nest at " + format(base));
        return Component.text("Seeded Antenna Nest at " + format(base) + "." + suffix, NamedTextColor.GREEN);
    }

    private void restoreOverrides() {
        final Location antenna = seedStateRepository.antennaLocation(plugin);
        if (antenna != null) {
            hideoutService.setLocationOverride("antenna_nest", antenna);
        }
    }

    private Location chooseSeedLocation(final Hideout hideout, final Location configured) {
        final Location candidate = configured.clone();
        candidate.setX(Math.floor(candidate.getX()));
        candidate.setZ(Math.floor(candidate.getZ()));

        if (!"antenna_nest".equalsIgnoreCase(hideout.id()) || configured.getWorld() == null) {
            candidate.setY(configured.getWorld() == null ? configured.getY() : configured.getWorld().getHighestBlockYAt(candidate) + 1);
            return candidate;
        }

        final World world = configured.getWorld();
        final Location spawn = world.getSpawnLocation();
        final double dx = candidate.getX() - spawn.getX();
        final double dz = candidate.getZ() - spawn.getZ();
        final double distance = Math.sqrt(dx * dx + dz * dz);

        if (distance >= 500.0D && distance <= 1500.0D) {
            candidate.setY(world.getHighestBlockYAt(candidate) + 1);
            return candidate;
        }

        double nx = dx;
        double nz = dz;
        if (Math.abs(nx) < 0.01D && Math.abs(nz) < 0.01D) {
            nx = 1.0D;
            nz = -0.6D;
        }
        final double length = Math.sqrt(nx * nx + nz * nz);
        nx /= length;
        nz /= length;

        final double targetDistance = distance < 500.0D ? 900.0D : 1200.0D;
        final double x = spawn.getX() + nx * targetDistance;
        final double z = spawn.getZ() + nz * targetDistance;
        final Location relocated = new Location(world, Math.floor(x), 0, Math.floor(z));
        relocated.setY(world.getHighestBlockYAt(relocated) + 1);
        return relocated;
    }

    Component seedBoards() {
        final List<SettlementLocator.SettlementTarget> settlements = settlementLocator.locateSettlements();
        if (settlements.isEmpty()) {
            return Component.text("No settlements were found to seed rumor boards.", NamedTextColor.RED);
        }

        int seeded = 0;
        int skipped = 0;
        int variantIndex = 0;
        final List<WorldContentLibrary.BoardVariant> variants = WorldContentLibrary.boardVariants();

        for (final SettlementLocator.SettlementTarget settlement : settlements) {
            final Location boardLocation = findBoardLocation(settlement.location());
            if (boardLocation == null) {
                skipped++;
                continue;
            }
            if (seedStateRepository.hasRumorBoard(boardLocation)) {
                skipped++;
                continue;
            }
            final WorldContentLibrary.BoardVariant variant = variants.get(variantIndex % variants.size());
            variantIndex++;
            placeRumorBoard(boardLocation, variant);
            seedStateRepository.addRumorBoard(settlement.name(), boardLocation);
            plugin.debugLog("Seeded rumor board " + settlement.name() + " at " + format(boardLocation));
            seeded++;
        }

        return Component.text(
            "Seeded " + seeded + " rumor board(s), skipped " + skipped + ".",
            seeded > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        );
    }

    Component seedAll(final boolean spawnBaron) {
        final Component antennaResult = seedAntennaNest(spawnBaron);
        final Component boardsResult = seedBoards();
        final Component viceResult = seedViceSites();
        return Component.text()
            .append(Component.text("Seed all complete. ", NamedTextColor.GREEN))
            .append(antennaResult)
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(boardsResult)
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(viceResult)
            .build();
    }

    Component seedViceSites() {
        final World world = plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().getFirst();
        if (world == null) {
            return Component.text("No world available for vice-site seeding.", NamedTextColor.RED);
        }

        final Location spawn = world.getSpawnLocation();
        final List<WorldContentLibrary.ViceVariant> variants = WorldContentLibrary.viceVariants();
        int refreshed = 0;
        for (int index = 0; index < 4; index++) {
            final WorldContentLibrary.ViceVariant variant = variants.get(index % variants.size());
            final String id = "vice_" + (index + 1);
            final Location location = savedViceLocation(id, chooseViceLocation(spawn, index));
            if (location == null) {
                continue;
            }
            seedViceSite(id, variant, location);
            refreshed++;
        }
        return Component.text(
            "Seeded or refreshed " + refreshed + " vice site(s).",
            refreshed > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        );
    }

    List<RadarSignal> radarSignalsFor(final Player player, final int limit) {
        final List<ViceSite> undiscoveredVice = viceSites.stream()
            .filter(site -> site.location().getWorld() != null)
            .filter(site -> site.location().getWorld().equals(player.getWorld()))
            .filter(site -> !seedStateRepository.hasDiscoveredViceSite(player.getUniqueId(), site.id()))
            .sorted(Comparator.comparingDouble(site -> site.location().distanceSquared(player.getLocation())))
            .toList();

        if (!undiscoveredVice.isEmpty()) {
            return undiscoveredVice.stream()
                .limit(limit)
                .map(site -> toRadarSignal(player.getLocation(), site))
                .toList();
        }

        return hideoutService.nearestSignalsFor(player, limit).stream()
            .map(signal -> new RadarSignal(signal.hideout().id(), signal.hideout().name(), "hideout",
                hideoutService.locationForId(signal.hideout().id()), signal.distanceSquared(), signal.cardinal(),
                signal.hideout().clue()))
            .toList();
    }

    Component radarSummaryFor(final Player player) {
        final List<RadarSignal> signals = radarSignalsFor(player, 3);
        if (signals.isEmpty()) {
            return Component.text("The Brothel Radar hisses, but finds no Baron-network signals.", NamedTextColor.GRAY);
        }
        Component line = Component.text("Brothel Radar picks up: ", NamedTextColor.AQUA);
        for (int index = 0; index < signals.size(); index++) {
            final RadarSignal signal = signals.get(index);
            if (index > 0) {
                line = line.append(Component.text(" | ", NamedTextColor.DARK_GRAY));
            }
            line = line.append(Component.text(
                signal.name() + " " + signal.cardinal() + " " + Math.round(Math.sqrt(signal.distanceSquared())) + "m",
                NamedTextColor.WHITE
            ));
        }
        return line;
    }

    Location nextRadarTarget(final Player player) {
        final List<RadarSignal> signals = radarSignalsFor(player, 1);
        return signals.isEmpty() ? null : signals.getFirst().location();
    }

    Component pingAndDiscover(final Player player, final double radius) {
        final ViceSite discovered = nearestUndiscoveredViceWithin(player, radius);
        if (discovered != null) {
            seedStateRepository.markViceSiteDiscovered(player.getUniqueId(), discovered.id());
            final int discoveredCount = seedStateRepository.discoveredViceSites(player.getUniqueId()).size();
            plugin.debugLog("Player " + player.getName() + " discovered vice site " + discovered.id());
            final String fragment = activeHideoutClueFragment(discoveredCount - 1);
            return Component.text(discovered.name() + ": ", NamedTextColor.LIGHT_PURPLE)
                .append(Component.text(discovered.clue(), NamedTextColor.WHITE))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(discovered.nextLead(), NamedTextColor.YELLOW))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text(fragment, NamedTextColor.GREEN));
        }
        return hideoutService.nearbyHideoutIntelFor(player, radius);
    }

    Component advanceHuntAfterConfrontation(final Player player) {
        final int maxCycles = plugin.getConfig().getInt("encounter.max-hunt-cycles", DEFAULT_MAX_HUNT_CYCLES);
        final int nextCycle = seedStateRepository.huntCycle() + 1;

        if (nextCycle >= maxCycles) {
            controller.despawnBaron();
            seedStateRepository.resetAllViceSiteDiscoveries();
            seedStateRepository.setHuntCycle(nextCycle);
            plugin.debugLog("Baron hunt completed at cycle " + nextCycle + ".");
            return Component.text(
                "John vanishes in a cloud of lies and smoke. The trail is spent; whatever money remained has become folklore.",
                NamedTextColor.GOLD
            );
        }

        if (!hideoutService.advanceToNextHideout()) {
            controller.despawnBaron();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> controller.spawnBaronAtActiveHideout(), 20L);
            seedStateRepository.resetAllViceSiteDiscoveries();
            seedStateRepository.setHuntCycle(nextCycle);
            plugin.debugLog("Advanced Baron hunt to cycle " + nextCycle + " but only one hideout is enabled.");
            return Component.text(
                "John bolts, but your world only has one live hideout configured. The vice trail resets and the same scent returns.",
                NamedTextColor.YELLOW
            );
        }

        controller.despawnBaron();
        seedStateRepository.resetAllViceSiteDiscoveries();
        seedStateRepository.setHuntCycle(nextCycle);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> controller.spawnBaronAtActiveHideout(), 20L);
        plugin.debugLog("Advanced Baron hunt to cycle " + nextCycle + " at hideout " + hideoutService.activeHideoutId() + ".");
        return Component.text(
            "John bolts for " + hideoutService.activeHideoutName() + ". The Brothel Radar goes dirty again; the vice trail has reset.",
            NamedTextColor.GOLD
        );
    }

    Component resetRadarProgress(final Player player) {
        final int previousCount = seedStateRepository.discoveredViceSites(player.getUniqueId()).size();
        seedStateRepository.resetViceSiteDiscoveries(player.getUniqueId());
        plugin.debugLog("Reset Brothel Radar progression for " + player.getName() + " (" + previousCount + " cleared).");
        return Component.text(
            "Reset Brothel Radar progression for " + player.getName() + ". Cleared " + previousCount + " discovered vice site(s).",
            NamedTextColor.GREEN
        );
    }

    Component resetAllRadarProgress() {
        final int resetCount = seedStateRepository.resetAllViceSiteDiscoveries();
        plugin.debugLog("Reset Brothel Radar progression for " + resetCount + " player profile(s).");
        return Component.text(
            "Reset Brothel Radar progression for " + resetCount + " player profile(s).",
            resetCount > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW
        );
    }

    private ViceSite nearestUndiscoveredViceWithin(final Player player, final double radius) {
        return viceSites.stream()
            .filter(site -> site.location().getWorld() != null)
            .filter(site -> site.location().getWorld().equals(player.getWorld()))
            .filter(site -> !seedStateRepository.hasDiscoveredViceSite(player.getUniqueId(), site.id()))
            .filter(site -> site.location().distanceSquared(player.getLocation()) <= radius * radius)
            .min(Comparator.comparingDouble(site -> site.location().distanceSquared(player.getLocation())))
            .orElse(null);
    }

    private String activeHideoutClueFragment(final int index) {
        final Hideout activeHideout = hideoutService.activeHideout();
        final Location hideoutLocation = hideoutService.activeHideoutLocation();
        if (activeHideout == null || hideoutLocation == null || hideoutLocation.getWorld() == null) {
            return "The clues degrade into static.";
        }

        final Location spawn = hideoutLocation.getWorld().getSpawnLocation();
        final double dx = hideoutLocation.getX() - spawn.getX();
        final double dz = hideoutLocation.getZ() - spawn.getZ();
        final int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        final String direction = cardinal(dx, dz);

        final List<String> fragments = List.of(
            "Fragment " + (index + 1) + ": the next nest lies " + direction + " of spawn.",
            "Fragment " + (index + 1) + ": they kept saying the run from spawn was about " + nearestHundred(distance) + " metres.",
            "Fragment " + (index + 1) + ": the vice girls remembered " + biomeHint(activeHideout.id()) + ".",
            "Fragment " + (index + 1) + ": the final whisper mentions " + landmarkHint(activeHideout.id()) + "."
        );
        return fragments.get(Math.floorMod(index, fragments.size()));
    }

    private int nearestHundred(final int value) {
        return Math.max(100, Math.round(value / 100.0F) * 100);
    }

    private String biomeHint(final String hideoutId) {
        return switch (hideoutId) {
            case "antenna_nest" -> "high ground, copper, and a ridge cut by wind";
            case "beach_cache" -> "salt air, sand, and a line of open water";
            case "cave_server_room" -> "stone below ground and machine-noise in the dark";
            case "jungle_bunker" -> "thick leaves, wet heat, and something overgrown";
            case "ruined_dock" -> "rotted timber, water, and an old departure point";
            case "swamp_shack" -> "mud, reeds, and air that feels too warm to trust";
            case "watchpoint" -> "dry grass and a height advantage";
            default -> "terrain that encourages a coward to feel strategic";
        };
    }

    private String landmarkHint(final String hideoutId) {
        return switch (hideoutId) {
            case "antenna_nest" -> "copper rods and a rig pointed at the sky";
            case "beach_cache" -> "crates, a tide line, and somewhere to leave quickly";
            case "cave_server_room" -> "a hidden room that treats redstone like infrastructure";
            case "jungle_bunker" -> "camouflage, maps, and too much confidence in leaves";
            case "ruined_dock" -> "a ruined pier and one shipment that never left";
            case "swamp_shack" -> "warm lamp oil and boots that sink on the way out";
            case "watchpoint" -> "a spyglass stand watching too many routes";
            default -> "the sort of landmark a fugitive mistakes for strategy";
        };
    }

    private void seedAntenna(final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final int baseX = location.getBlockX() - 4;
        final int baseZ = location.getBlockZ() - 5;
        final int baseY = location.getBlockY();

        final Block floorCorner = world.getBlockAt(baseX, baseY, baseZ);
        painter.fill(floorCorner, 9, 11, Material.DARK_OAK_PLANKS);

        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 11; z++) {
                final boolean edge = x == 0 || x == 8 || z == 0 || z == 10;
                if (!edge) {
                    continue;
                }
                for (int y = 1; y <= 3; y++) {
                    final Block wall = world.getBlockAt(baseX + x, baseY + y, baseZ + z);
                    painter.set(wall, (x == 0 || x == 8) && (z == 0 || z == 10) ? Material.STRIPPED_SPRUCE_LOG : Material.STONE_BRICKS);
                }
            }
        }

        for (int y = 1; y <= 2; y++) {
            painter.set(world.getBlockAt(baseX + 4, baseY + y, baseZ + 10), Material.AIR);
        }
        painter.set(world.getBlockAt(baseX + 4, baseY + 1, baseZ + 9), Material.DARK_OAK_TRAPDOOR);
        painter.set(world.getBlockAt(baseX + 4, baseY, baseZ + 11), Material.DARK_OAK_STAIRS);
        painter.set(world.getBlockAt(baseX + 4, baseY - 1, baseZ + 12), Material.COBBLESTONE_STAIRS);
        painter.set(world.getBlockAt(baseX + 3, baseY, baseZ + 11), Material.SCAFFOLDING);
        painter.set(world.getBlockAt(baseX + 5, baseY, baseZ + 11), Material.SCAFFOLDING);
        painter.set(world.getBlockAt(baseX + 4, baseY + 2, baseZ + 10), Material.LANTERN);

        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 11; z++) {
                painter.set(world.getBlockAt(baseX + x, baseY + 4, baseZ + z), Material.DARK_OAK_SLAB);
            }
        }

        painter.fill(world.getBlockAt(baseX + 3, baseY + 5, baseZ + 3), 3, 3, Material.SCAFFOLDING);
        for (int i = 0; i < 4; i++) {
            painter.column(world.getBlockAt(baseX + 3 + i % 2, baseY + 6, baseZ + 3 + i / 2), 2, Material.LIGHTNING_ROD);
        }

        painter.set(world.getBlockAt(baseX + 1, baseY + 1, baseZ + 2), Material.CARTOGRAPHY_TABLE);
        placeLectern(world.getBlockAt(baseX + 2, baseY + 1, baseZ + 2), WorldContentLibrary.antennaCoreBook());
        painter.set(world.getBlockAt(baseX + 3, baseY + 1, baseZ + 2), Material.COPPER_BULB);
        painter.set(world.getBlockAt(baseX + 4, baseY + 1, baseZ + 2), Material.REDSTONE_WIRE);
        painter.set(world.getBlockAt(baseX + 5, baseY + 1, baseZ + 2), Material.REPEATER);

        placeBarrel(world.getBlockAt(baseX + 1, baseY + 1, baseZ + 6), "Ledger Barrel", List.of(
            WorldContentLibrary.techtonicSummaryPaper(),
            new ItemStack(Material.GOLD_NUGGET, 8),
            new ItemStack(Material.EMERALD, 2),
            new ItemStack(Material.REDSTONE, 12),
            new ItemStack(Material.STRING, 4),
            new ItemStack(Material.ROTTEN_FLESH, 2)
        ));

        placeChest(world.getBlockAt(baseX + 6, baseY + 1, baseZ + 6), "Techtonic Crate", createTechtonicCrateLoot());

        placeBarrel(world.getBlockAt(baseX + 7, baseY + 1, baseZ + 3), "Liquor Shelf", List.of(
            WorldContentLibrary.liquorBook(),
            new ItemStack(Material.GLASS_BOTTLE, 4),
            potion(Material.POTION, PotionType.STRENGTH, 2),
            new ItemStack(Material.COOKED_BEEF, 2)
        ));

        placeBarrel(world.getBlockAt(baseX + 7, baseY, baseZ + 8), "Concealed Stash", List.of(
            new ItemStack(Material.COMPASS, 1),
            new ItemStack(Material.SPYGLASS, 1),
            namedItem(Material.TURTLE_HELMET, "Surveillance Helmet"),
            new ItemStack(Material.GUNPOWDER, 16),
            WorldContentLibrary.escapeNotePaper(),
            new ItemStack(Material.IRON_INGOT, 2)
        ));

        painter.set(world.getBlockAt(baseX + 6, baseY + 1, baseZ + 1), Material.CAMPFIRE);
        painter.set(world.getBlockAt(baseX + 5, baseY + 1, baseZ + 1), Material.COAL_BLOCK);
        painter.set(world.getBlockAt(baseX + 6, baseY + 1, baseZ + 9), Material.BARREL);
        painter.set(world.getBlockAt(baseX + 1, baseY + 1, baseZ + 9), Material.RED_BED);
        painter.set(world.getBlockAt(baseX + 3, baseY + 1, baseZ + 9), Material.DARK_OAK_STAIRS);
        painter.set(world.getBlockAt(baseX + 4, baseY + 2, baseZ + 9), Material.LANTERN);
        painter.set(world.getBlockAt(baseX + 2, baseY + 1, baseZ + 10), Material.LANTERN);
        painter.set(world.getBlockAt(baseX + 8, baseY + 1, baseZ + 5), Material.LANTERN);
        painter.set(world.getBlockAt(baseX + 0, baseY + 1, baseZ + 5), Material.LANTERN);
    }

    private Location findBoardLocation(final Location center) {
        final World world = center.getWorld();
        if (world == null) {
            return null;
        }

        for (int radius = 2; radius <= 10; radius += 2) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    final int x = center.getBlockX() + dx;
                    final int z = center.getBlockZ() + dz;
                    final int y = world.getHighestBlockYAt(x, z);
                    final Location candidate = new Location(world, x, y, z);
                    if (isBoardSiteUsable(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private void restoreViceSites() {
        viceSites.clear();
        for (final Map<String, Object> map : seedStateRepository.viceSiteMaps()) {
            final World world = plugin.getServer().getWorld((String) map.get("world"));
            if (world == null) {
                continue;
            }
            viceSites.add(new ViceSite(
                (String) map.get("id"),
                (String) map.get("name"),
                new Location(world, (int) map.get("x"), (int) map.get("y"), (int) map.get("z")),
                (String) map.get("variant"),
                (String) map.get("clue"),
                (String) map.get("nextLead")
            ));
        }
    }

    private Location savedViceLocation(final String siteId, final Location fallback) {
        for (final ViceSite viceSite : viceSites) {
            if (viceSite.id().equalsIgnoreCase(siteId)) {
                return viceSite.location().clone();
            }
        }
        return fallback;
    }

    private Location chooseViceLocation(final Location spawn, final int index) {
        final World world = spawn.getWorld();
        if (world == null) {
            return null;
        }
        final double[] angles = {30.0D, 120.0D, 220.0D, 310.0D};
        final double distance = 120.0D + index * 45.0D;
        final double radians = Math.toRadians(angles[index % angles.length]);
        final int x = (int) Math.round(spawn.getX() + Math.cos(radians) * distance);
        final int z = (int) Math.round(spawn.getZ() + Math.sin(radians) * distance);
        final int y = world.getHighestBlockYAt(x, z);
        return new Location(world, x, y + 1, z);
    }

    private void seedViceSite(final String id, final WorldContentLibrary.ViceVariant variant, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        removeExistingViceSite(id);
        clearViceStaff(location);
        final int baseX = location.getBlockX() - 3;
        final int baseZ = location.getBlockZ() - 3;
        final int baseY = location.getBlockY();

        painter.fill(world.getBlockAt(baseX, baseY, baseZ), 7, 7, Material.DARK_OAK_PLANKS);
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                final boolean edge = x == 0 || x == 6 || z == 0 || z == 6;
                if (!edge) continue;
                for (int y = 1; y <= 3; y++) {
                    painter.set(world.getBlockAt(baseX + x, baseY + y, baseZ + z),
                        (x == 0 || x == 6) && (z == 0 || z == 6) ? Material.STRIPPED_CHERRY_LOG : Material.CHERRY_PLANKS);
                }
            }
        }
        for (int y = 1; y <= 2; y++) {
            painter.set(world.getBlockAt(baseX + 3, baseY + y, baseZ), Material.AIR);
        }
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                painter.set(world.getBlockAt(baseX + x, baseY + 4, baseZ + z), Material.DARK_OAK_SLAB);
            }
        }
        painter.set(world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1), Material.BARREL);
        placeBarrel(world.getBlockAt(baseX + 1, baseY + 1, baseZ + 1), variant.name() + " Ledger", List.of(
            WorldContentLibrary.sponsorDraftPaper(),
            WorldContentLibrary.listenerNumbersPaper(),
            new ItemStack(Material.EMERALD, ThreadLocalRandom.current().nextInt(4, 11)),
            new ItemStack(Material.GOLD_NUGGET, ThreadLocalRandom.current().nextInt(10, 21)),
            new ItemStack(Material.GLASS_BOTTLE, 3)
        ));
        placeChest(world.getBlockAt(baseX + 5, baseY + 1, baseZ + 1), variant.name() + " Back Room", List.of(
            WorldContentLibrary.repaymentCertificate(),
            new ItemStack(Material.DIAMOND, ThreadLocalRandom.current().nextInt(1, 4)),
            namedItem(Material.NETHERITE_SWORD, "Collector's Measure"),
            namedItem(Material.NETHERITE_AXE, "Negotiation Aid"),
            new ItemStack(Material.GUNPOWDER, ThreadLocalRandom.current().nextInt(4, 10)),
            potion(Material.POTION, PotionType.STRONG_HEALING, 1),
            potion(Material.SPLASH_POTION, PotionType.STRONG_SWIFTNESS, 1),
            potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE, 1)
        ));
        placeLectern(world.getBlockAt(baseX + 3, baseY + 1, baseZ + 4), WorldContentLibrary.softwareBook());
        painter.set(world.getBlockAt(baseX + 2, baseY + 1, baseZ + 4), Material.JUKEBOX);
        painter.set(world.getBlockAt(baseX + 4, baseY + 1, baseZ + 4), Material.LANTERN);
        painter.set(world.getBlockAt(baseX + 3, baseY + 1, baseZ + 6), Material.DARK_OAK_STAIRS);
        painter.set(world.getBlockAt(baseX + 2, baseY + 1, baseZ + 6), Material.RED_CARPET);
        painter.set(world.getBlockAt(baseX + 3, baseY + 1, baseZ + 5), Material.RED_CARPET);
        painter.set(world.getBlockAt(baseX + 4, baseY + 1, baseZ + 6), Material.RED_CARPET);

        spawnViceStaff(location, variant);
        final ViceSite viceSite = new ViceSite(id, variant.name(), location.clone(), variant.id(), variant.clue(), variant.nextLead());
        viceSites.add(viceSite);
        seedStateRepository.saveViceSite(id, variant.name(), location, variant.id(), variant.clue(), variant.nextLead());
        plugin.debugLog("Seeded vice site " + id + " at " + format(location));
    }

    private void removeExistingViceSite(final String id) {
        viceSites.removeIf(site -> site.id().equalsIgnoreCase(id));
    }

    private void clearViceStaff(final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        for (final Villager villager : world.getEntitiesByClass(Villager.class)) {
            if (villager.getLocation().distanceSquared(location) > 64.0D) {
                continue;
            }
            final Component customName = villager.customName();
            if (customName == null) {
                continue;
            }
            villager.remove();
        }
    }

    private void spawnViceStaff(final Location location, final WorldContentLibrary.ViceVariant variant) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final double[][] pads = {
            {-1.25D, 1.0D, -1.25D},
            {1.25D, 1.0D, -1.25D},
            {-1.25D, 1.0D, 1.25D},
            {1.25D, 1.0D, 1.25D},
            {0.0D, 1.0D, 0.25D}
        };
        int index = 0;
        for (final String npcName : variant.npcNames()) {
            final double[] pad = pads[index % pads.length];
            final Location npcLocation = location.clone().add(pad[0], pad[1], pad[2]);
            index++;
            world.spawn(npcLocation, Villager.class, villager -> {
                villager.customName(Component.text(npcName, NamedTextColor.LIGHT_PURPLE));
                villager.setCustomNameVisible(true);
                villager.setPersistent(true);
                villager.setRemoveWhenFarAway(false);
                villager.setVillagerExperience(0);
                villager.setCanPickupItems(false);
                villager.setProfession(Villager.Profession.NONE);
                villager.setAdult();
                villager.setAI(false);
                villager.setInvulnerable(true);
                villager.setCollidable(false);
            });
        }
    }

    private RadarSignal toRadarSignal(final Location playerLocation, final ViceSite site) {
        final double dx = site.location().getX() - playerLocation.getX();
        final double dz = site.location().getZ() - playerLocation.getZ();
        final double distanceSquared = dx * dx + dz * dz;
        return new RadarSignal(site.id(), site.name(), "vice", site.location(), distanceSquared, cardinal(dx, dz), site.clue());
    }

    private String cardinal(final double dx, final double dz) {
        final double angle = Math.toDegrees(Math.atan2(-dx, dz));
        final double normalized = (angle + 360.0D) % 360.0D;
        final String[] directions = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        final int index = (int) Math.round(normalized / 45.0D) % directions.length;
        return directions[index];
    }

    private boolean isBoardSiteUsable(final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                final Block block = world.getBlockAt(location.getBlockX() + dx, location.getBlockY(), location.getBlockZ() + dz);
                final Block above = block.getRelative(0, 1, 0);
                if (!block.getType().isSolid() || !above.getType().isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    private void placeRumorBoard(final Location location, final WorldContentLibrary.BoardVariant variant) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final int x = location.getBlockX();
        final int y = location.getBlockY();
        final int z = location.getBlockZ();

        painter.column(world.getBlockAt(x - 1, y, z), 3, Material.STRIPPED_SPRUCE_LOG);
        painter.column(world.getBlockAt(x + 1, y, z), 3, Material.STRIPPED_SPRUCE_LOG);
        painter.set(world.getBlockAt(x, y + 2, z), Material.LANTERN);
        painter.set(world.getBlockAt(x - 1, y + 1, z), Material.DARK_OAK_PLANKS);
        painter.set(world.getBlockAt(x, y + 1, z), Material.DARK_OAK_PLANKS);
        painter.set(world.getBlockAt(x + 1, y + 1, z), Material.DARK_OAK_PLANKS);
        painter.set(world.getBlockAt(x - 1, y + 2, z), Material.DARK_OAK_PLANKS);
        painter.set(world.getBlockAt(x + 1, y + 2, z), Material.DARK_OAK_PLANKS);

        placeSign(world.getBlockAt(x - 1, y + 2, z - 1), variant.header());
        placeSign(world.getBlockAt(x, y + 1, z - 1), variant.sighting());
        placeSign(world.getBlockAt(x + 1, y + 1, z - 1), variant.warning());
        placeSign(world.getBlockAt(x, y + 2, z - 1), variant.comic());
        placeSign(world.getBlockAt(x - 1, y + 1, z - 1), variant.witness());
    }

    private void placeSign(final Block block, final String text) {
        block.setType(Material.OAK_WALL_SIGN, false);
        if (block.getBlockData() instanceof Directional directional) {
            directional.setFacing(org.bukkit.block.BlockFace.NORTH);
            block.setBlockData(directional, false);
        }
        if (block.getState() instanceof Sign sign) {
            final List<String> lines = wrapSignText(text);
            for (int index = 0; index < Math.min(4, lines.size()); index++) {
                sign.setLine(index, lines.get(index));
            }
            sign.update(true, false);
        }
    }

    private List<String> wrapSignText(final String text) {
        final List<String> lines = new java.util.ArrayList<>();
        String remaining = text;
        while (!remaining.isBlank() && lines.size() < 4) {
            if (remaining.length() <= 15) {
                lines.add(remaining);
                break;
            }
            int split = remaining.lastIndexOf(' ', 15);
            if (split <= 0) {
                split = 15;
            }
            lines.add(remaining.substring(0, split));
            remaining = remaining.substring(Math.min(split + 1, remaining.length()));
        }
        while (lines.size() < 4) {
            lines.add("");
        }
        return lines;
    }

    private void placeLectern(final Block block, final ItemStack book) {
        block.setType(Material.LECTERN, false);
        if (block.getState() instanceof Lectern lectern) {
            lectern.getInventory().setItem(0, book);
            lectern.update(true, true);
        }
    }

    private void placeBarrel(final Block block, final String name, final List<ItemStack> items) {
        block.setType(Material.BARREL, false);
        fillBarrel(block, name, items);
        plugin.getServer().getScheduler().runTask(plugin, () -> fillBarrel(block, name, items));
    }

    private void placeChest(final Block block, final String name, final List<ItemStack> items) {
        block.setType(Material.CHEST, false);
        fillChest(block, name, items);
        plugin.getServer().getScheduler().runTask(plugin, () -> fillChest(block, name, items));
    }

    private void fillBarrel(final Block block, final String name, final List<ItemStack> items) {
        if (block.getState() instanceof Barrel barrel) {
            barrel.setCustomName(name);
            barrel.update(true, true);
            if (block.getState() instanceof Barrel placedBarrel) {
                populateInventory(placedBarrel.getInventory(), items);
                plugin.debugLog("Filled barrel '" + name + "' with " + items.size() + " item stack(s) at " + format(block.getLocation()));
            }
        }
    }

    private void fillChest(final Block block, final String name, final List<ItemStack> items) {
        if (block.getState() instanceof Chest chest) {
            chest.setCustomName(name);
            chest.update(true, true);
            if (block.getState() instanceof Chest placedChest) {
                populateInventory(placedChest.getBlockInventory(), items);
                plugin.debugLog("Filled chest '" + name + "' with " + items.size() + " item stack(s) at " + format(block.getLocation()));
            }
        }
    }

    private void populateInventory(final Inventory inventory, final List<ItemStack> items) {
        inventory.clear();
        for (final ItemStack item : items) {
            inventory.addItem(item.clone());
        }
    }

    private ItemStack potion(final Material material, final PotionType potionType, final int amount) {
        final ItemStack item = new ItemStack(material, amount);
        if (item.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(potionType);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack namedItem(final Material material, final String name) {
        final ItemStack item = new ItemStack(material, 1);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<ItemStack> createTechtonicCrateLoot() {
        final ThreadLocalRandom random = ThreadLocalRandom.current();
        final java.util.ArrayList<ItemStack> loot = new java.util.ArrayList<>();

        loot.add(WorldContentLibrary.repaymentCertificate());
        loot.add(WorldContentLibrary.sponsorDraftPaper());
        loot.add(WorldContentLibrary.listenerNumbersPaper());
        loot.add(new ItemStack(Material.MAP, 1));
        loot.add(new ItemStack(Material.GLASS_BOTTLE, 4));
        loot.add(new ItemStack(Material.MUSIC_DISC_CAT, 1));
        loot.add(new ItemStack(Material.MUSIC_DISC_PIGSTEP, 1));

        loot.add(new ItemStack(Material.EMERALD, random.nextInt(12, 25)));
        loot.add(new ItemStack(Material.GOLD_INGOT, random.nextInt(20, 37)));
        loot.add(new ItemStack(Material.DIAMOND, random.nextInt(6, 13)));

        if (random.nextDouble() < 0.75D) {
            loot.add(new ItemStack(Material.DIAMOND, random.nextInt(4, 9)));
        }
        if (random.nextDouble() < 0.55D) {
            loot.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
        }
        if (random.nextDouble() < 0.45D) {
            loot.add(new ItemStack(Material.NETHERITE_SCRAP, random.nextInt(1, 3)));
        }
        if (random.nextDouble() < 0.20D) {
            loot.add(new ItemStack(Material.NETHERITE_INGOT, 1));
        }
        if (random.nextDouble() < 0.40D) {
            loot.add(new ItemStack(Material.ANCIENT_DEBRIS, random.nextInt(1, 3)));
        }
        if (random.nextDouble() < 0.50D) {
            loot.add(new ItemStack(Material.GUNPOWDER, random.nextInt(8, 17)));
        }

        return loot;
    }

    private String format(final Location location) {
        return (location.getWorld() == null ? "world" : location.getWorld().getName()) + " "
            + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }

    record ViceSite(String id, String name, Location location, String variant, String clue, String nextLead) {
    }

    record RadarSignal(String id, String name, String type, Location location, double distanceSquared, String cardinal, String clue) {
    }
}
