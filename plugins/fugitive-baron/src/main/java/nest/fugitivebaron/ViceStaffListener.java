package nest.fugitivebaron;

import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

final class ViceStaffListener implements Listener {
    private final WorldSeedService worldSeedService;

    ViceStaffListener(final WorldSeedService worldSeedService) {
        this.worldSeedService = worldSeedService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        final Entity clicked = event.getRightClicked();
        if (!worldSeedService.isViceStaff(clicked)) {
            return;
        }
        event.setCancelled(true);
        worldSeedService.handleViceStaffInteraction(event.getPlayer(), clicked);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onNpcRightClick(final NPCRightClickEvent event) {
        if (event.getNPC().getEntity() == null || !worldSeedService.isViceStaff(event.getNPC().getEntity())) {
            return;
        }
        worldSeedService.handleViceStaffInteraction(event.getClicker(), event.getNPC().getEntity());
    }
}
