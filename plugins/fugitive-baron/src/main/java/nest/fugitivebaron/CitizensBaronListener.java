package nest.fugitivebaron;

import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

final class CitizensBaronListener implements Listener {
    private final FugitiveBaronController controller;
    private final FugitiveBaronPlugin plugin;

    CitizensBaronListener(final FugitiveBaronPlugin plugin, final FugitiveBaronController controller) {
        this.plugin = plugin;
        this.controller = controller;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onNpcRightClick(final NPCRightClickEvent event) {
        if (event.getNPC().getEntity() == null || !controller.isBaron(event.getNPC().getEntity())) {
            return;
        }
        controller.handleInteraction(event.getClicker(), event.getNPC().getEntity(), plugin.currentTick());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onNpcLeftClick(final NPCLeftClickEvent event) {
        if (event.getNPC().getEntity() == null || !controller.isBaron(event.getNPC().getEntity())) {
            return;
        }
        controller.handleDamage(event.getNPC().getEntity(), event.getClicker(), plugin.currentTick());
    }
}
