package nest.fugitivebaron;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

final class BaronListener implements Listener {
    private final FugitiveBaronController controller;
    private final FugitiveBaronPlugin plugin;
    private final DragonRadarService radarService;

    BaronListener(
        final FugitiveBaronPlugin plugin,
        final FugitiveBaronController controller,
        final DragonRadarService radarService
    ) {
        this.plugin = plugin;
        this.controller = controller;
        this.radarService = radarService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onPlayerInteractEntity(final PlayerInteractEntityEvent event) {
        final Entity clicked = event.getRightClicked();
        if (!controller.isBaron(clicked)) {
            return;
        }
        event.setCancelled(true);
        controller.handleInteraction(event.getPlayer(), clicked, plugin.currentTick());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onEntityDamage(final EntityDamageByEntityEvent event) {
        if (!controller.isBaron(event.getEntity())) {
            return;
        }
        final Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null) {
            controller.handleDamage(event.getEntity(), attacker, plugin.currentTick());
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    void onEnvironmentalDamage(final EntityDamageEvent event) {
        if (!controller.isBaron(event.getEntity())) {
            return;
        }
        if (event instanceof EntityDamageByEntityEvent) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    void onPlayerJoin(final PlayerJoinEvent event) {
        plugin.ensureStartingRadar(event.getPlayer());
        radarService.pingNearestSignals(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    void onPlayerInteract(final PlayerInteractEvent event) {
        final Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!radarService.isDragonRadar(event.getItem())) {
            return;
        }

        radarService.pingNearestSignals(event.getPlayer());
    }

    private Player resolveAttacker(final Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
