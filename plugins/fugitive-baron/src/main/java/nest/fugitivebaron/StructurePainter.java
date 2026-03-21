package nest.fugitivebaron;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

final class StructurePainter {
    void set(final Block block, final Material material) {
        block.setType(material, false);
    }

    void column(final Block origin, final int height, final Material material) {
        for (int y = 0; y < height; y++) {
            set(origin.getRelative(0, y, 0), material);
        }
    }

    void fill(final Block corner, final int width, final int depth, final Material material) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                set(corner.getRelative(x, 0, z), material);
            }
        }
    }

    Block topSolidBlock(final Block block) {
        Block cursor = block;
        while (cursor.getY() > cursor.getWorld().getMinHeight() && cursor.getType().isAir()) {
            cursor = cursor.getRelative(BlockFace.DOWN);
        }
        return cursor;
    }
}
