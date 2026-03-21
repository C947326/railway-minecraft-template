package nest.fugitivebaron;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

final class RecognitionItemChecker {
    private boolean requireNamedPowder;
    private String powderName;

    RecognitionItemChecker(final FileConfiguration config) {
        reload(config);
    }

    void reload(final FileConfiguration config) {
        this.requireNamedPowder = config.getBoolean("encounter.require-named-powder", false);
        this.powderName = config.getString("encounter.powder-name", "Suspicious White Powder");
    }

    boolean isRecognitionItem(final ItemStack stack) {
        if (stack == null || stack.getType() != Material.GUNPOWDER) {
            return false;
        }
        if (!requireNamedPowder) {
            return true;
        }

        final ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        if (meta.displayName() == null) {
            return false;
        }

        final String actualName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        return powderName.equals(actualName);
    }
}
