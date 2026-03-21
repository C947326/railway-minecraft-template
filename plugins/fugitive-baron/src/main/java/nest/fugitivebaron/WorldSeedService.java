package nest.fugitivebaron;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionType;

final class WorldSeedService {
    private static final int DEFAULT_MAX_HUNT_CYCLES = 3;
    private static final int REQUIRED_VICE_SITE_COUNT = 5;
    private static final String VICE_STAFF_TAG = "vice_staff";
    private static final String VICE_QUIP_TAG = "vice_quip";
    private static final long VICE_QUIP_LIFETIME_TICKS = 80L;
    private static final long VICE_STAFF_MOVE_INTERVAL_TICKS = 80L;
    private static final long VICE_STAFF_AMBIENT_INTERVAL_TICKS = 140L;

    private final FugitiveBaronPlugin plugin;
    private final HideoutService hideoutService;
    private final FugitiveBaronController controller;
    private final SeedStateRepository seedStateRepository;
    private final SettlementLocator settlementLocator;
    private final StructurePainter painter;
    private final List<ViceSite> viceSites = new ArrayList<>();
    private final Map<UUID, Long> viceStaffInteractionCooldowns = new HashMap<>();
    private final Map<UUID, Long> viceStaffMovementTicks = new HashMap<>();
    private final Map<UUID, Long> viceStaffAmbientTicks = new HashMap<>();
    private CitizensViceStaffSupport citizensViceStaffSupport;
    private boolean citizensViceStaffUnavailable;

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
        if (plugin.getServer().getPluginManager().getPlugin("Citizens") != null) {
            try {
                this.citizensViceStaffSupport = new CitizensViceStaffSupport(plugin);
            } catch (final Throwable throwable) {
                disableCitizensViceStaff("initialization", throwable);
            }
        }
        restoreOverrides();
        restoreViceSites();
    }

    Component seedStatus() {
        final int johnCount = controller.knownBaronCount();
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
                + " johns="
                + johnCount
                + " baron="
                + baronStatus
                + " version="
                + seedStateRepository.seedVersion(),
            NamedTextColor.YELLOW
        );
    }

    void tick(final long currentTick) {
        for (final ViceSite site : viceSites) {
            final World world = site.location().getWorld();
            if (world == null) {
                continue;
            }
            for (final org.bukkit.entity.LivingEntity entity : world.getLivingEntities()) {
                if (!isViceStaff(entity)) {
                    continue;
                }
                if (entity.getLocation().distanceSquared(site.location()) > 100.0D) {
                    continue;
                }
                maybeMoveViceStaff(entity, site, currentTick);
                maybePlayViceStaffAmbient(entity, currentTick);
            }
        }
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
        final Location persisted = "antenna_nest".equalsIgnoreCase(hideout.id()) ? seedStateRepository.antennaLocation(plugin) : null;
        if (persisted != null && persisted.getWorld() != null) {
            return persisted.clone();
        }

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
        final Component crorganResult = seedCrorgansNest();
        final Component boardsResult = seedBoards();
        final Component viceResult = seedViceSites();
        return Component.text()
            .append(Component.text("Seed all complete. ", NamedTextColor.GREEN))
            .append(antennaResult)
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(crorganResult)
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(boardsResult)
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(viceResult)
            .build();
    }

    Component seedCrorgansNest() {
        final Location configured = hideoutService.locationForId("crorgans_nest");
        if (configured == null || configured.getWorld() == null) {
            return Component.text("Crorgan's Nest is not enabled or has no valid world.", NamedTextColor.YELLOW);
        }
        final Location base = configured.clone();
        base.setX(Math.floor(base.getX()));
        base.setZ(Math.floor(base.getZ()));
        base.setY(configured.getWorld().getHighestBlockYAt(base) + 1);
        seedCrorgansNest(base);
        plugin.debugLog("Seeded Crorgan's Nest at " + format(base));
        return Component.text("Seeded Crorgan's Nest at " + format(base) + ".", NamedTextColor.GREEN);
    }

    Component fullResetAndSeed(final boolean spawnBaron) {
        controller.despawnBaron();
        hideoutService.clearLocationOverride("antenna_nest");
        seedStateRepository.resetAllState();
        viceSites.clear();
        final Component result = seedAll(spawnBaron);
        plugin.debugLog("Performed full Baron reset and reseed" + (spawnBaron ? " with John spawn." : "."));
        return Component.text("Full reset complete. ", NamedTextColor.GREEN).append(result);
    }

    Component seedViceSites() {
        final World world = plugin.getServer().getWorlds().isEmpty() ? null : plugin.getServer().getWorlds().getFirst();
        if (world == null) {
            return Component.text("No world available for vice-site seeding.", NamedTextColor.RED);
        }

        final Location spawn = world.getSpawnLocation();
        final List<WorldContentLibrary.ViceVariant> variants = WorldContentLibrary.viceVariants();
        int refreshed = 0;
        for (int index = 0; index < REQUIRED_VICE_SITE_COUNT; index++) {
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
        if (limit <= 0) {
            return List.of();
        }

        final List<ViceSite> undiscoveredVice = viceSites.stream()
            .filter(site -> site.location().getWorld() != null)
            .filter(site -> site.location().getWorld().equals(player.getWorld()))
            .filter(site -> !seedStateRepository.hasDiscoveredViceSite(player.getUniqueId(), site.id()))
            .sorted(Comparator.comparingDouble(site -> site.location().distanceSquared(player.getLocation())))
            .toList();

        if (!undiscoveredVice.isEmpty()) {
            final List<RadarSignal> signals = new ArrayList<>();
            for (final ViceSite site : undiscoveredVice) {
                if (signals.size() >= limit) {
                    break;
                }
                signals.add(toRadarSignal(player.getLocation(), site));
            }
            return signals;
        }

        final RadarSignal activeHideout = activeHideoutRadarSignal(player);
        return activeHideout == null ? List.of() : List.of(activeHideout);
    }

    Component radarSummaryFor(final Player player) {
        final List<RadarSignal> signals = radarSignalsFor(player, 3);
        if (signals.isEmpty()) {
            return Component.text("The Brothel Radar hisses, but finds no John-network signals.", NamedTextColor.GRAY);
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
        final ViceSite nearestVice = nearestUndiscoveredVice(player);
        if (nearestVice != null) {
            return nearestVice.location().clone();
        }

        final Location activeHideout = hideoutService.activeHideoutLocation();
        if (activeHideout == null || activeHideout.getWorld() == null || !activeHideout.getWorld().equals(player.getWorld())) {
            return null;
        }
        return activeHideout.clone();
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
        return activeHideoutArrivalIntel(player, radius);
    }

    boolean isViceStaff(final Entity entity) {
        if (entity == null) {
            return false;
        }
        if (!entity.getScoreboardTags().contains(VICE_STAFF_TAG)) {
            return looksLikeViceStaff(entity);
        }
        if (hasCitizensViceStaffSupport()) {
            try {
                if (citizensViceStaffSupport.isViceStaffEntity(entity, VICE_STAFF_TAG)) {
                    return true;
                }
            } catch (final Throwable throwable) {
                disableCitizensViceStaff("vice-staff identity", throwable);
            }
        }
        return entity instanceof Villager;
    }

    void handleViceStaffInteraction(final Player player, final Entity entity) {
        if (!(entity instanceof org.bukkit.entity.LivingEntity livingEntity) || !isViceStaff(entity)) {
            return;
        }
        final long currentTick = plugin.currentTick();
        final long nextAllowedTick = viceStaffInteractionCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (currentTick < nextAllowedTick) {
            return;
        }

        final ViceSite site = nearestViceSite(entity.getLocation());
        if (site == null) {
            return;
        }
        final WorldContentLibrary.ViceVariant variant = viceVariant(site.variant());
        if (variant == null || variant.npcQuips().isEmpty()) {
            return;
        }

        final String speaker = plainName(livingEntity.customName(), entity.getName());
        final String line = variant.npcQuips().get(ThreadLocalRandom.current().nextInt(variant.npcQuips().size()));
        plugin.dialogueService().deliverWitnessLine(player, speaker, line, null, currentTick);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 0.9F, randomPitch());
        showViceQuip(entity, line);
        faceViceStaff(entity, player.getLocation());
        viceStaffInteractionCooldowns.put(player.getUniqueId(), currentTick + 20L);
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

    Component advanceHuntAfterEscape(final Player player) {
        final int maxCycles = plugin.getConfig().getInt("encounter.max-hunt-cycles", DEFAULT_MAX_HUNT_CYCLES);
        final int nextCycle = seedStateRepository.huntCycle() + 1;

        if (nextCycle >= maxCycles) {
            controller.despawnBaron();
            seedStateRepository.resetAllViceSiteDiscoveries();
            seedStateRepository.setHuntCycle(nextCycle);
            plugin.debugLog("Baron escaped the hunt entirely at cycle " + nextCycle + ".");
            return Component.text(
                "John slips clean out of sight. By the time you crest the ridge, he has become folklore again.",
                NamedTextColor.GOLD
            );
        }

        if (!hideoutService.advanceToNextHideout()) {
            controller.despawnBaron();
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> controller.spawnBaronAtActiveHideout(), 20L);
            seedStateRepository.resetAllViceSiteDiscoveries();
            seedStateRepository.setHuntCycle(nextCycle);
            plugin.debugLog("John escaped line of sight, but only one hideout is enabled.");
            return Component.text(
                "John breaks line of sight and loops back into the same territory. The vice trail resets.",
                NamedTextColor.YELLOW
            );
        }

        controller.despawnBaron();
        seedStateRepository.resetAllViceSiteDiscoveries();
        seedStateRepository.setHuntCycle(nextCycle);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> controller.spawnBaronAtActiveHideout(), 20L);
        plugin.debugLog("John escaped to hideout " + hideoutService.activeHideoutId() + " after losing line of sight.");
        return Component.text(
            "John breaks line of sight and bolts for " + hideoutService.activeHideoutName() + ". The Brothel Radar goes dirty again.",
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

    private ViceSite nearestUndiscoveredVice(final Player player) {
        return viceSites.stream()
            .filter(site -> site.location().getWorld() != null)
            .filter(site -> site.location().getWorld().equals(player.getWorld()))
            .filter(site -> !seedStateRepository.hasDiscoveredViceSite(player.getUniqueId(), site.id()))
            .min(Comparator.comparingDouble(site -> site.location().distanceSquared(player.getLocation())))
            .orElse(null);
    }

    private RadarSignal activeHideoutRadarSignal(final Player player) {
        final Hideout activeHideout = hideoutService.activeHideout();
        final Location activeHideoutLocation = hideoutService.activeHideoutLocation();
        if (activeHideout == null || activeHideoutLocation == null || activeHideoutLocation.getWorld() == null) {
            return null;
        }
        if (!activeHideoutLocation.getWorld().equals(player.getWorld())) {
            return null;
        }
        final double dx = activeHideoutLocation.getX() - player.getLocation().getX();
        final double dz = activeHideoutLocation.getZ() - player.getLocation().getZ();
        return new RadarSignal(
            activeHideout.name(),
            activeHideoutLocation.clone(),
            dx * dx + dz * dz,
            cardinal(dx, dz)
        );
    }

    private Component activeHideoutArrivalIntel(final Player player, final double radius) {
        final Component line = hideoutService.nearbyHideoutIntelFor(player, radius);
        if (line == null) {
            return null;
        }

        final Hideout nearbyHideout = hideoutService.nearestHideoutWithin(player, radius);
        final Hideout activeHideout = hideoutService.activeHideout();
        if (nearbyHideout == null || activeHideout == null || !activeHideout.id().equalsIgnoreCase(nearbyHideout.id())) {
            return line;
        }

        final Location johnLocation = controller.getBaronLocation();
        if (controller.hasBaron() && johnLocation != null && johnLocation.getWorld() != null && johnLocation.getWorld().equals(player.getWorld())) {
            return line.append(Component.text(" | John is close. Approach with the white powder ready.", NamedTextColor.GOLD));
        }

        return line.append(Component.text(" | The trail is real, but John is not here now.", NamedTextColor.YELLOW));
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
            "Fragment " + (index + 1) + ": the whisper keeps returning to " + landmarkHint(activeHideout.id()) + ".",
            "Fragment " + (index + 1) + ": five houses agree at last. John's first hideout is " + activeHideout.name() + "."
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
            case "crorgans_nest" -> "stone rooms, hidden corners, and a nest built for someone too pleased with himself";
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
            case "crorgans_nest" -> "a private nest, stone-lined and smug enough to call itself strategic";
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

        prepareAntennaFootprint(world, baseX, baseY, baseZ);

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
        seedAntennaEscapeRoute(world, baseX, baseY, baseZ);

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
            enchantedHelmet("Surveillance Helmet"),
            enchantedHelmet("Surveillance Helmet"),
            enchantedBoots("Escape Boots"),
            enchantedBoots("Escape Boots"),
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

    private void prepareAntennaFootprint(final World world, final int baseX, final int baseY, final int baseZ) {
        for (int x = -1; x <= 9; x++) {
            for (int z = -1; z <= 15; z++) {
                for (int y = 1; y <= 9; y++) {
                    painter.set(world.getBlockAt(baseX + x, baseY + y, baseZ + z), Material.AIR);
                }
            }
        }
    }

    private void seedCrorgansNest(final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final int baseX = location.getBlockX() - 4;
        final int baseZ = location.getBlockZ() - 4;
        final int baseY = location.getBlockY();

        for (int x = -1; x <= 9; x++) {
            for (int z = -1; z <= 9; z++) {
                for (int y = 1; y <= 5; y++) {
                    painter.set(world.getBlockAt(baseX + x, baseY + y, baseZ + z), Material.AIR);
                }
            }
        }

        painter.fill(world.getBlockAt(baseX, baseY, baseZ), 9, 9, Material.STONE_BRICKS);
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                final boolean edge = x == 0 || x == 8 || z == 0 || z == 8;
                if (!edge) {
                    continue;
                }
                for (int y = 1; y <= 3; y++) {
                    final Material wallMaterial = (x == 0 || x == 8) && (z == 0 || z == 8)
                        ? Material.POLISHED_DEEPSLATE
                        : Material.DEEPSLATE_BRICKS;
                    painter.set(world.getBlockAt(baseX + x, baseY + y, baseZ + z), wallMaterial);
                }
            }
        }

        for (int y = 1; y <= 2; y++) {
            painter.set(world.getBlockAt(baseX + 4, baseY + y, baseZ + 8), Material.AIR);
        }
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                painter.set(world.getBlockAt(baseX + x, baseY + 4, baseZ + z), Material.DARK_OAK_SLAB);
            }
        }

        painter.set(world.getBlockAt(baseX + 2, baseY + 1, baseZ + 2), Material.CARTOGRAPHY_TABLE);
        placeLectern(world.getBlockAt(baseX + 3, baseY + 1, baseZ + 2), WorldContentLibrary.softwareBook());
        placeBarrel(world.getBlockAt(baseX + 6, baseY + 1, baseZ + 2), "Crorgan's Ledger", List.of(
            WorldContentLibrary.listenerNumbersPaper(),
            WorldContentLibrary.sponsorDraftPaper(),
            new ItemStack(Material.EMERALD, 6),
            new ItemStack(Material.REDSTONE, 14)
        ));
        placeChest(world.getBlockAt(baseX + 6, baseY + 1, baseZ + 6), "Crorgan's Cache", List.of(
            WorldContentLibrary.repaymentCertificate(),
            enchantedHelmet("Strategic Helmet"),
            enchantedBoots("Stairwell Boots"),
            new ItemStack(Material.GUNPOWDER, 12),
            new ItemStack(Material.COOKED_BEEF, 5)
        ));
        painter.set(world.getBlockAt(baseX + 2, baseY + 1, baseZ + 6), Material.RED_BED);
        painter.set(world.getBlockAt(baseX + 4, baseY + 1, baseZ + 6), Material.DARK_OAK_STAIRS);
        painter.set(world.getBlockAt(baseX + 4, baseY + 2, baseZ + 6), Material.LANTERN);
        painter.set(world.getBlockAt(baseX + 4, baseY + 1, baseZ + 7), Material.DARK_OAK_TRAPDOOR);

        seedCrorgansEscapeRoute(world, baseX, baseY, baseZ);
    }

    private void seedCrorgansEscapeRoute(final World world, final int baseX, final int baseY, final int baseZ) {
        final int centerX = baseX + 4;
        final int startZ = baseZ + 9;

        for (int step = 0; step < 8; step++) {
            final int z = startZ + step;
            final int groundY = world.getHighestBlockYAt(centerX, z);
            final int pathY = Math.min(baseY - 1, groundY);

            for (int width = -1; width <= 1; width++) {
                final int x = centerX + width;
                for (int clearY = pathY + 1; clearY <= pathY + 3; clearY++) {
                    painter.set(world.getBlockAt(x, clearY, z), Material.AIR);
                }
                painter.set(world.getBlockAt(x, pathY, z), step < 3 ? Material.COBBLED_DEEPSLATE_STAIRS : Material.COBBLED_DEEPSLATE);
            }
        }
    }

    private void seedAntennaEscapeRoute(final World world, final int baseX, final int baseY, final int baseZ) {
        final int centerX = baseX + 4;
        final int startZ = baseZ + 12;

        for (int step = 0; step < 10; step++) {
            final int z = startZ + step;
            final int groundY = world.getHighestBlockYAt(centerX, z);
            final int pathY = Math.min(baseY - 1, groundY);

            for (int width = -1; width <= 1; width++) {
                final int x = centerX + width;
                for (int clearY = pathY + 1; clearY <= pathY + 3; clearY++) {
                    painter.set(world.getBlockAt(x, clearY, z), Material.AIR);
                }
                painter.set(world.getBlockAt(x, pathY, z), step < 4 ? Material.COBBLESTONE_STAIRS : Material.COBBLESTONE);
            }
        }
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
        final double[] angles = {20.0D, 92.0D, 164.0D, 236.0D, 308.0D};
        final double distance = 120.0D + index * 45.0D;
        final double radians = Math.toRadians(angles[index % angles.length]);
        final int x = (int) Math.round(spawn.getX() + Math.cos(radians) * distance);
        final int z = (int) Math.round(spawn.getZ() + Math.sin(radians) * distance);
        return findViceSiteLocation(new Location(world, x, world.getHighestBlockYAt(x, z) + 1, z));
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

        prepareViceSiteFootprint(world, baseX, baseY, baseZ);

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
        if (hasCitizensViceStaffSupport()) {
            try {
                citizensViceStaffSupport.clearViceStaff(location, 64.0D, VICE_STAFF_TAG);
            } catch (final Throwable throwable) {
                disableCitizensViceStaff("vice-site cleanup", throwable);
            }
        }
        for (final Villager villager : world.getEntitiesByClass(Villager.class)) {
            if (villager.getLocation().distanceSquared(location) > 64.0D) {
                continue;
            }
            if (!villager.getScoreboardTags().contains(VICE_STAFF_TAG)) {
                continue;
            }
            villager.remove();
        }
        for (final TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
            if (display.getLocation().distanceSquared(location) > 64.0D) {
                continue;
            }
            if (!display.getScoreboardTags().contains(VICE_QUIP_TAG)) {
                continue;
            }
            display.remove();
        }
    }

    private Location findViceSiteLocation(final Location preferredCenter) {
        final World world = preferredCenter.getWorld();
        if (world == null) {
            return preferredCenter;
        }

        Location best = null;
        double bestVariance = Double.MAX_VALUE;
        for (int radius = 0; radius <= 12; radius += 2) {
            for (int dx = -radius; dx <= radius; dx += 2) {
                for (int dz = -radius; dz <= radius; dz += 2) {
                    final int x = preferredCenter.getBlockX() + dx;
                    final int z = preferredCenter.getBlockZ() + dz;
                    final SiteFit fit = viceSiteFit(world, x, z);
                    if (!fit.usable()) {
                        continue;
                    }
                    if (fit.variance() < bestVariance) {
                        bestVariance = fit.variance();
                        best = new Location(world, x, fit.floorY() + 1, z);
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return preferredCenter;
    }

    private SiteFit viceSiteFit(final World world, final int centerX, final int centerZ) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                final int x = centerX + dx;
                final int z = centerZ + dz;
                final int y = world.getHighestBlockYAt(x, z);
                final Material top = world.getBlockAt(x, y - 1, z).getType();
                if (!top.isSolid() || top == Material.WATER || top == Material.LAVA) {
                    return new SiteFit(false, y, Double.MAX_VALUE);
                }
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        return new SiteFit(maxY - minY <= 2, maxY, maxY - minY);
    }

    private void prepareViceSiteFootprint(final World world, final int baseX, final int baseY, final int baseZ) {
        for (int x = -1; x <= 7; x++) {
            for (int z = -1; z <= 7; z++) {
                for (int y = 1; y <= 5; y++) {
                    painter.set(world.getBlockAt(baseX + x, baseY + y, baseZ + z), Material.AIR);
                }
            }
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
            final Location npcLocation = safeViceStaffLocation(location.clone().add(pad[0], pad[1], pad[2]));
            final String skinName = variant.npcSkinNames().get(index % variant.npcSkinNames().size());
            final int styleIndex = index;
            npcLocation.setYaw((float) (ThreadLocalRandom.current().nextDouble(360.0D) - 180.0D));
            index++;
            if (!spawnViceStaffEntity(npcLocation, npcName, skinName)) {
                world.spawn(npcLocation, Villager.class, villager -> {
                    villager.customName(Component.text(npcName, NamedTextColor.LIGHT_PURPLE));
                    villager.setCustomNameVisible(true);
                    villager.setPersistent(true);
                    villager.setRemoveWhenFarAway(false);
                    villager.setVillagerExperience(0);
                    villager.setCanPickupItems(false);
                    villager.setProfession(styleIndex % 2 == 0 ? Villager.Profession.CLERIC : Villager.Profession.LIBRARIAN);
                    villager.setVillagerType(styleIndex % 2 == 0 ? Villager.Type.SAVANNA : Villager.Type.TAIGA);
                    villager.setAdult();
                    villager.setAI(false);
                    villager.setInvulnerable(true);
                    villager.setCollidable(false);
                    villager.setSilent(true);
                    villager.addScoreboardTag(VICE_STAFF_TAG);
                });
            }
        }
    }

    private void showViceQuip(final Entity entity, final String quip) {
        final World world = entity.getWorld();
        for (final TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
            if (display.getLocation().distanceSquared(entity.getLocation()) > 4.0D) {
                continue;
            }
            if (!display.getScoreboardTags().contains(VICE_QUIP_TAG)) {
                continue;
            }
            display.remove();
        }
        world.spawn(entity.getLocation().clone().add(0.0D, 2.35D, 0.0D), TextDisplay.class, display -> {
            display.text(Component.text('"' + quip + '"', NamedTextColor.WHITE));
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(true);
            display.setShadowed(false);
            display.setDefaultBackground(false);
            display.setLineWidth(220);
            display.addScoreboardTag(VICE_QUIP_TAG);
            plugin.getServer().getScheduler().runTaskLater(plugin, display::remove, VICE_QUIP_LIFETIME_TICKS);
        });
    }

    private Location safeViceStaffLocation(final Location preferred) {
        final World world = preferred.getWorld();
        if (world == null) {
            return preferred;
        }
        final Location candidate = preferred.clone();
        candidate.setY(Math.floor(candidate.getY()) + 0.05D);
        for (int dy = 0; dy <= 2; dy++) {
            final Location shifted = candidate.clone().add(0.0D, dy, 0.0D);
            if (isClearForViceStaff(shifted)) {
                return shifted;
            }
        }
        return candidate;
    }

    private boolean isClearForViceStaff(final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final Block feet = world.getBlockAt(location);
        final Block head = feet.getRelative(0, 1, 0);
        final Block ground = feet.getRelative(0, -1, 0);
        return (feet.getType().isAir() || feet.isPassable())
            && (head.getType().isAir() || head.isPassable())
            && ground.getType().isSolid();
    }

    private void maybeMoveViceStaff(final org.bukkit.entity.LivingEntity entity, final ViceSite site, final long currentTick) {
        final long nextMoveTick = viceStaffMovementTicks.getOrDefault(entity.getUniqueId(), 0L);
        if (currentTick < nextMoveTick) {
            return;
        }
        final Location siteCenter = site.location();
        final Location current = entity.getLocation();
        final Location destination = siteCenter.clone().add(
            ThreadLocalRandom.current().nextDouble(-1.75D, 1.75D),
            1.0D,
            ThreadLocalRandom.current().nextDouble(-1.75D, 1.75D)
        );
        destination.setYaw((float) (ThreadLocalRandom.current().nextDouble(360.0D) - 180.0D));

        if (hasCitizensViceStaffSupport()) {
            try {
                citizensViceStaffSupport.navigateTo(entity, destination, 1.05F);
            } catch (final Throwable throwable) {
                disableCitizensViceStaff("vice-staff navigation", throwable);
            }
        } else if (entity instanceof Villager villager) {
            villager.setAI(true);
            villager.getPathfinder().moveTo(destination);
        }

        if (current.distanceSquared(siteCenter) > 16.0D) {
            faceViceStaff(entity, siteCenter);
        }
        viceStaffMovementTicks.put(entity.getUniqueId(), currentTick + VICE_STAFF_MOVE_INTERVAL_TICKS + ThreadLocalRandom.current().nextLong(40L));
    }

    private void maybePlayViceStaffAmbient(final org.bukkit.entity.LivingEntity entity, final long currentTick) {
        final long nextAmbientTick = viceStaffAmbientTicks.getOrDefault(entity.getUniqueId(), 0L);
        if (currentTick < nextAmbientTick) {
            return;
        }
        final Player nearby = findNearbyPlayer(entity.getLocation(), 8.0D);
        if (nearby != null) {
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, SoundCategory.NEUTRAL, 0.65F, randomPitch());
        }
        viceStaffAmbientTicks.put(entity.getUniqueId(), currentTick + VICE_STAFF_AMBIENT_INTERVAL_TICKS + ThreadLocalRandom.current().nextLong(100L));
    }

    private boolean spawnViceStaffEntity(final Location npcLocation, final String npcName, final String skinName) {
        if (!hasCitizensViceStaffSupport()) {
            return false;
        }
        try {
            citizensViceStaffSupport.spawnViceStaff(npcLocation, npcName, skinName, VICE_STAFF_TAG);
            return true;
        } catch (final Throwable throwable) {
            disableCitizensViceStaff("vice-site spawn", throwable);
            return false;
        }
    }

    private boolean hasCitizensViceStaffSupport() {
        return !citizensViceStaffUnavailable && citizensViceStaffSupport != null && citizensViceStaffSupport.isAvailable();
    }

    private void disableCitizensViceStaff(final String phase, final Throwable throwable) {
        if (citizensViceStaffUnavailable) {
            return;
        }
        citizensViceStaffUnavailable = true;
        citizensViceStaffSupport = null;
        plugin.getLogger().warning(
            "Disabling Citizens vice-staff integration during " + phase + ": "
                + throwable.getClass().getSimpleName()
                + (throwable.getMessage() == null || throwable.getMessage().isBlank() ? "" : " - " + throwable.getMessage())
        );
    }

    private void faceViceStaff(final Entity entity, final Location target) {
        if (hasCitizensViceStaffSupport()) {
            try {
                citizensViceStaffSupport.face(entity, target);
                return;
            } catch (final Throwable throwable) {
                disableCitizensViceStaff("vice-staff facing", throwable);
            }
        }
        entity.setRotation(target.getYaw(), target.getPitch());
    }

    private Player findNearbyPlayer(final Location location, final double radius) {
        if (location.getWorld() == null) {
            return null;
        }
        return location.getWorld().getPlayers().stream()
            .filter(player -> player.getGameMode() == org.bukkit.GameMode.SURVIVAL || player.getGameMode() == org.bukkit.GameMode.ADVENTURE)
            .filter(player -> !controller.isBaron(player))
            .filter(player -> !isViceStaff(player))
            .filter(player -> player.getLocation().distanceSquared(location) <= radius * radius)
            .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(location)))
            .orElse(null);
    }

    private ViceSite nearestViceSite(final Location location) {
        return viceSites.stream()
            .filter(site -> site.location().getWorld() != null)
            .filter(site -> site.location().getWorld().equals(location.getWorld()))
            .min(Comparator.comparingDouble(site -> site.location().distanceSquared(location)))
            .orElse(null);
    }

    private boolean looksLikeViceStaff(final Entity entity) {
        if (!(entity instanceof org.bukkit.entity.LivingEntity livingEntity)) {
            return false;
        }
        if (!(entity instanceof Villager || entity.getType() == org.bukkit.entity.EntityType.PLAYER)) {
            return false;
        }
        if (livingEntity.customName() == null && (entity.getName() == null || entity.getName().isBlank())) {
            return false;
        }
        final ViceSite nearest = nearestViceSite(entity.getLocation());
        return nearest != null && nearest.location().distanceSquared(entity.getLocation()) <= 36.0D;
    }

    private WorldContentLibrary.ViceVariant viceVariant(final String id) {
        for (final WorldContentLibrary.ViceVariant variant : WorldContentLibrary.viceVariants()) {
            if (variant.id().equalsIgnoreCase(id)) {
                return variant;
            }
        }
        return null;
    }

    private String plainName(final Component component, final String fallback) {
        return fallback;
    }

    private float randomPitch() {
        return (float) ThreadLocalRandom.current().nextDouble(0.85D, 1.15D);
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
        fillLectern(block, book);
        plugin.getServer().getScheduler().runTask(plugin, () -> fillLectern(block, book));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> fillLectern(block, book), 2L);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> fillLectern(block, book), 10L);
    }

    private void fillLectern(final Block block, final ItemStack book) {
        if (block.getState() instanceof Lectern lectern) {
            lectern.getInventory().setItem(0, book.clone());
            lectern.update(true, true);
            plugin.debugLog("Filled lectern with '" + bookLabel(book) + "' at " + format(block.getLocation()));
        }
    }

    private String bookLabel(final ItemStack book) {
        if (book.getItemMeta() instanceof BookMeta meta) {
            return meta.getTitle() == null || meta.getTitle().isBlank() ? book.getType().name() : meta.getTitle();
        }
        return book.getType().name();
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

    private ItemStack enchantedHelmet(final String name) {
        final ItemStack item = namedItem(Material.TURTLE_HELMET, name);
        item.addUnsafeEnchantment(Enchantment.AQUA_AFFINITY, 1);
        item.addUnsafeEnchantment(Enchantment.RESPIRATION, 3);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return item;
    }

    private ItemStack enchantedBoots(final String name) {
        final ItemStack item = namedItem(Material.NETHERITE_BOOTS, name);
        item.addUnsafeEnchantment(Enchantment.DEPTH_STRIDER, 3);
        item.addUnsafeEnchantment(Enchantment.FEATHER_FALLING, 4);
        item.addUnsafeEnchantment(Enchantment.SOUL_SPEED, 3);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        item.addUnsafeEnchantment(Enchantment.MENDING, 1);
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

    record SiteFit(boolean usable, int floorY, double variance) {
    }
}
