package nest.fugitivebaron;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.Lectern;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

final class WorldSeedService {
    private final FugitiveBaronPlugin plugin;
    private final HideoutService hideoutService;
    private final SeedStateRepository seedStateRepository;
    private final SettlementLocator settlementLocator;
    private final StructurePainter painter;

    WorldSeedService(final FugitiveBaronPlugin plugin, final HideoutService hideoutService) {
        this.plugin = plugin;
        this.hideoutService = hideoutService;
        this.seedStateRepository = new SeedStateRepository(plugin);
        this.settlementLocator = new SettlementLocator(plugin);
        this.painter = new StructurePainter();
    }

    Component seedStatus() {
        return Component.text(
            "Seed status: antenna="
                + seedStateRepository.isAntennaSeeded()
                + " boards="
                + seedStateRepository.rumorBoardCount()
                + " version="
                + seedStateRepository.seedVersion(),
            NamedTextColor.YELLOW
        );
    }

    Component seedAntennaNest() {
        final Hideout active = hideoutService.activeHideout();
        if (active == null) {
            return Component.text("No active hideout is configured.", NamedTextColor.RED);
        }
        final Location location = active.toLocation(plugin);
        if (location == null || location.getWorld() == null) {
            return Component.text("Active hideout world is unavailable.", NamedTextColor.RED);
        }

        final Location base = location.clone();
        base.setX(Math.floor(base.getX()));
        base.setZ(Math.floor(base.getZ()));
        seedAntenna(base);
        seedStateRepository.markAntennaSeeded(base);
        plugin.debugLog("Seeded Antenna Nest at " + format(base));
        return Component.text("Seeded Antenna Nest at " + format(base) + ".", NamedTextColor.GREEN);
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

        placeChest(world.getBlockAt(baseX + 6, baseY + 1, baseZ + 6), "Techtonic Crate", List.of(
            WorldContentLibrary.repaymentCertificate(),
            WorldContentLibrary.sponsorDraftPaper(),
            WorldContentLibrary.listenerNumbersPaper(),
            new ItemStack(Material.MUSIC_DISC_CAT, 1),
            new ItemStack(Material.GLASS_BOTTLE, 2),
            new ItemStack(Material.MAP, 1)
        ));

        placeBarrel(world.getBlockAt(baseX + 7, baseY + 1, baseZ + 3), "Liquor Shelf", List.of(
            WorldContentLibrary.liquorBook(),
            new ItemStack(Material.GLASS_BOTTLE, 4),
            new ItemStack(Material.POTION, 2),
            new ItemStack(Material.COOKED_BEEF, 2)
        ));

        placeBarrel(world.getBlockAt(baseX + 7, baseY, baseZ + 8), "Concealed Stash", List.of(
            new ItemStack(Material.COMPASS, 1),
            new ItemStack(Material.SPYGLASS, 1),
            new ItemStack(Material.GUNPOWDER, 16),
            WorldContentLibrary.escapeNotePaper(),
            new ItemStack(Material.IRON_INGOT, 2)
        ));

        painter.set(world.getBlockAt(baseX + 6, baseY + 1, baseZ + 1), Material.CAMPFIRE);
        painter.set(world.getBlockAt(baseX + 5, baseY + 1, baseZ + 1), Material.COAL_BLOCK);
        painter.set(world.getBlockAt(baseX + 6, baseY + 1, baseZ + 9), Material.BARREL);
        painter.set(world.getBlockAt(baseX + 1, baseY + 1, baseZ + 9), Material.RED_BED);
        painter.set(world.getBlockAt(baseX + 4, baseY + 1, baseZ + 9), Material.DARK_OAK_STAIRS);
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
            lectern.update(true, false);
        }
    }

    private void placeBarrel(final Block block, final String name, final List<ItemStack> items) {
        block.setType(Material.BARREL, false);
        if (block.getState() instanceof Barrel barrel) {
            populateContainer(barrel, name, items);
        }
    }

    private void placeChest(final Block block, final String name, final List<ItemStack> items) {
        block.setType(Material.CHEST, false);
        if (block.getState() instanceof Chest chest) {
            populateContainer(chest, name, items);
        }
    }

    private void populateContainer(final Container container, final String name, final List<ItemStack> items) {
        container.setCustomName(name);
        final Inventory inventory = container.getInventory();
        inventory.clear();
        for (final ItemStack item : items) {
            inventory.addItem(item.clone());
        }
        container.update(true, false);
    }

    private String format(final Location location) {
        return (location.getWorld() == null ? "world" : location.getWorld().getName()) + " "
            + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ();
    }
}
