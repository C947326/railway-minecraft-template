package nest.fugitivebaron;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class FugitiveBaronPlugin extends JavaPlugin {
    private RecognitionItemChecker itemChecker;
    private DialogueService dialogueService;
    private HideoutService hideoutService;
    private FugitiveBaronController controller;
    private DragonRadarService radarService;
    private NamespacedKey baronKey;
    private NamespacedKey powderKey;
    private NamespacedKey radarKey;
    private NamespacedKey receivedRadarKey;
    private long currentTick;
    private boolean debugLogging;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.baronKey = new NamespacedKey(this, "fugitive_baron");
        this.powderKey = new NamespacedKey(this, "recognition_powder");
        this.radarKey = new NamespacedKey(this, "dragon_radar");
        this.receivedRadarKey = new NamespacedKey(this, "received_dragon_radar");
        this.itemChecker = new RecognitionItemChecker(getConfig());
        this.dialogueService = new DialogueService(this, getConfig());
        this.hideoutService = new HideoutService(this);
        this.controller = new FugitiveBaronController(this, hideoutService, itemChecker, dialogueService);
        this.radarService = new DragonRadarService(this, hideoutService);
        this.debugLogging = getConfig().getBoolean("encounter.debug-logging", false);

        getServer().getPluginManager().registerEvents(new BaronListener(this, controller, radarService), this);

        final PluginCommand command = Objects.requireNonNull(getCommand("fugitivebaron"), "Command fugitivebaron missing from plugin.yml");
        final BaronCommand executor = new BaronCommand(this, controller, hideoutService);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        getServer().getScheduler().runTaskTimer(this, () -> {
            currentTick++;
            controller.tick(currentTick);
            radarService.maybeUpdateRadarTargets(currentTick);
        }, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (controller != null) {
            controller.despawnBaron();
        }
    }

    void reloadPluginConfig() {
        reloadConfig();
        this.debugLogging = getConfig().getBoolean("encounter.debug-logging", false);
        itemChecker.reload(getConfig());
        dialogueService.reload(getConfig());
        hideoutService.reload(getConfig());
        controller.reload(getConfig());
        radarService.reload(getConfig());
    }

    ItemStack createRecognitionItem() {
        final ItemStack item = new ItemStack(Material.GUNPOWDER);
        final ItemMeta meta = Objects.requireNonNull(item.getItemMeta(), "Gunpowder should always have item meta.");
        meta.displayName(Component.text(getConfig().getString("encounter.powder-name", "Suspicious White Powder")));
        meta.getPersistentDataContainer().set(powderKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack createDragonRadar() {
        return radarService.createRadar();
    }

    void ensureStartingRadar(final org.bukkit.entity.Player player) {
        radarService.ensurePlayerHasStarterRadar(player);
    }

    NamespacedKey baronKey() {
        return baronKey;
    }

    NamespacedKey radarKey() {
        return radarKey;
    }

    NamespacedKey receivedRadarKey() {
        return receivedRadarKey;
    }

    long currentTick() {
        return currentTick;
    }

    void debugLog(final String message) {
        if (!debugLogging) {
            return;
        }
        getLogger().info("[BaronDebug] " + message);
    }
}
