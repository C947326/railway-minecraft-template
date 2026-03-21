package nest.fugitivebaron;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.trait.GameModeTrait;
import net.citizensnpcs.trait.SkinLayers;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class CitizensViceStaffSupport {
    private final FugitiveBaronPlugin plugin;

    CitizensViceStaffSupport(final FugitiveBaronPlugin plugin) {
        this.plugin = plugin;
    }

    boolean isAvailable() {
        return registry() != null;
    }

    void clearViceStaff(final Location center, final double radiusSquared, final String scoreboardTag) {
        final NPCRegistry registry = registry();
        if (registry == null || center.getWorld() == null) {
            return;
        }

        for (final NPC npc : registry) {
            if (!npc.isSpawned()) {
                continue;
            }
            final Entity entity = npc.getEntity();
            if (entity == null || entity.getWorld() != center.getWorld()) {
                continue;
            }
            if (!entity.getScoreboardTags().contains(scoreboardTag)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            npc.destroy();
        }
    }

    void spawnViceStaff(final Location location, final String displayName, final String skinName, final String scoreboardTag) {
        final NPCRegistry registry = registry();
        if (registry == null) {
            throw new IllegalStateException("Citizens is not available for vice-site staff.");
        }

        final NPC npc = registry.createNPC(EntityType.PLAYER, displayName);
        npc.setProtected(true);
        npc.setFlyable(false);
        npc.getOrAddTrait(GameModeTrait.class).setGameMode(GameMode.SURVIVAL);
        npc.getOrAddTrait(SkinLayers.class).show();

        final SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        if (skinName != null && !skinName.isBlank()) {
            skinTrait.setSkinName(skinName, true);
        } else {
            skinTrait.setFetchDefaultSkin(true);
        }

        if (!npc.spawn(location)) {
            npc.destroy();
            throw new IllegalStateException("Citizens failed to spawn vice-site staffer " + displayName + ".");
        }

        if (npc.getEntity() instanceof LivingEntity livingEntity) {
            livingEntity.customName(Component.text(displayName, NamedTextColor.LIGHT_PURPLE));
            livingEntity.setCustomNameVisible(true);
            livingEntity.setPersistent(true);
            livingEntity.setCanPickupItems(false);
            livingEntity.setCollidable(false);
            livingEntity.setSilent(true);
            livingEntity.addScoreboardTag(scoreboardTag);
            livingEntity.setRotation(location.getYaw(), location.getPitch());
            return;
        }

        npc.destroy();
        throw new IllegalStateException("Citizens spawned non-living vice-site staff.");
    }

    boolean isViceStaffEntity(final Entity entity, final String scoreboardTag) {
        if (entity == null || !entity.getScoreboardTags().contains(scoreboardTag)) {
            return false;
        }
        final NPCRegistry registry = registry();
        return registry != null && registry.getNPC(entity) != null;
    }

    void navigateTo(final Entity entity, final Location destination, final float speedModifier) {
        final NPCRegistry registry = registry();
        if (registry == null || entity == null) {
            return;
        }
        final NPC npc = registry.getNPC(entity);
        if (npc == null || !npc.isSpawned()) {
            return;
        }
        final Navigator navigator = npc.getNavigator();
        navigator.setTarget(destination);
        final NavigatorParameters parameters = navigator.getLocalParameters();
        parameters.speedModifier(speedModifier);
        parameters.distanceMargin(0.9D);
        parameters.stationaryTicks(20);
        parameters.updatePathRate(10);
        parameters.straightLineTargetingDistance(6.0F);
    }

    void face(final Entity entity, final Location target) {
        final NPCRegistry registry = registry();
        if (registry == null || entity == null) {
            return;
        }
        final NPC npc = registry.getNPC(entity);
        if (npc == null || !npc.isSpawned()) {
            return;
        }
        npc.faceLocation(target);
    }

    private NPCRegistry registry() {
        if (plugin.getServer().getPluginManager().getPlugin("Citizens") == null) {
            return null;
        }
        return CitizensAPI.getNPCRegistry();
    }
}
