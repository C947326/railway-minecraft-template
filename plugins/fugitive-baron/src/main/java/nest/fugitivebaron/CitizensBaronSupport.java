package nest.fugitivebaron;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.trait.GameModeTrait;
import net.citizensnpcs.trait.SkinLayers;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.HashSet;
import java.util.Set;

final class CitizensBaronSupport {
    private final FugitiveBaronPlugin plugin;

    private boolean preferred;
    private String skinName;
    private String skinTexture;
    private String skinSignature;
    private Integer npcId;

    CitizensBaronSupport(final FugitiveBaronPlugin plugin) {
        this.plugin = plugin;
        reload(plugin.getConfig());
    }

    void reload(final FileConfiguration config) {
        this.preferred = config.getBoolean("encounter.prefer-citizens-player-npc", true);
        this.skinName = config.getString("encounter.citizens-skin-name", "").trim();
        this.skinTexture = config.getString("encounter.citizens-skin-texture", "").trim();
        this.skinSignature = config.getString("encounter.citizens-skin-signature", "").trim();
    }

    boolean isPreferredAndAvailable() {
        return preferred && registry() != null;
    }

    LivingEntity spawnBaron(final Location location, final String displayName) {
        final NPCRegistry registry = registry();
        if (!preferred || registry == null) {
            return null;
        }

        despawnBaron();

        final NPC npc = registry.createNPC(EntityType.PLAYER, displayName);
        npc.setProtected(true);
        npc.getOrAddTrait(GameModeTrait.class).setGameMode(GameMode.SURVIVAL);
        npc.getOrAddTrait(SkinLayers.class).show();

        applySkin(npc);
        applyAppearance(npc);

        if (!npc.spawn(location)) {
            npc.destroy();
            throw new IllegalStateException("Citizens failed to spawn John at the active hideout.");
        }

        this.npcId = npc.getId();

        if (npc.getEntity() instanceof LivingEntity livingEntity) {
            livingEntity.getPersistentDataContainer().set(plugin.baronKey(), PersistentDataType.BYTE, (byte) 1);
            livingEntity.setPersistent(true);
            livingEntity.setCanPickupItems(false);
            livingEntity.setCollidable(true);
            livingEntity.setSilent(true);
            return livingEntity;
        }

        npc.destroy();
        this.npcId = null;
        throw new IllegalStateException("Citizens spawned a non-living John entity.");
    }

    void despawnBaron() {
        final NPC npc = npc();
        if (npc == null) {
            this.npcId = null;
            return;
        }
        npc.destroy();
        this.npcId = null;
    }

    int sweepSpawnedBarons() {
        final NPCRegistry registry = registry();
        if (registry == null) {
            return 0;
        }
        final Set<Integer> removedIds = new HashSet<>();
        for (final NPC npc : registry) {
            if (!npc.isSpawned() || npc.getEntity() == null) {
                continue;
            }
            if (!(npc.getEntity() instanceof LivingEntity livingEntity)) {
                continue;
            }
            final boolean tagged = livingEntity.getPersistentDataContainer().has(plugin.baronKey(), PersistentDataType.BYTE);
            final boolean namedJohn = npc.getName() != null && npc.getName().equalsIgnoreCase("John");
            if (!tagged && !namedJohn) {
                continue;
            }
            removedIds.add(npc.getId());
            npc.destroy();
        }
        if (npcId != null && removedIds.contains(npcId)) {
            npcId = null;
        }
        return removedIds.size();
    }

    LivingEntity getBaronEntity() {
        final NPC npc = npc();
        if (npc == null || !npc.isSpawned() || !(npc.getEntity() instanceof LivingEntity livingEntity)) {
            return null;
        }
        return livingEntity;
    }

    boolean isBaronEntity(final Entity entity) {
        final NPC npc = npc();
        return npc != null && npc.isSpawned() && npc.getEntity() != null && npc.getEntity().getUniqueId().equals(entity.getUniqueId());
    }

    void face(final Location target) {
        final NPC npc = npc();
        if (npc == null || !npc.isSpawned()) {
            return;
        }
        npc.faceLocation(target);
    }

    void stopNavigation() {
        final NPC npc = npc();
        if (npc == null || !npc.isSpawned()) {
            return;
        }
        npc.getNavigator().cancelNavigation();
    }

    void navigateTo(final Location destination, final float speedModifier) {
        final NPC npc = npc();
        if (npc == null || !npc.isSpawned()) {
            return;
        }

        final Navigator navigator = npc.getNavigator();
        navigator.setTarget(destination);
        final NavigatorParameters parameters = navigator.getLocalParameters();
        parameters.speedModifier(speedModifier);
        parameters.distanceMargin(1.25D);
        parameters.stationaryTicks(30);
        parameters.updatePathRate(10);
        parameters.straightLineTargetingDistance(8.0F);
    }

    private void applySkin(final NPC npc) {
        final SkinTrait skinTrait = npc.getOrAddTrait(SkinTrait.class);
        if (!skinTexture.isBlank() && !skinSignature.isBlank()) {
            final String cacheKey = skinName.isBlank() ? npc.getName() : skinName;
            skinTrait.setSkinPersistent(cacheKey, skinSignature, skinTexture);
            skinTrait.setShouldUpdateSkins(false);
            return;
        }
        if (!skinName.isBlank()) {
            skinTrait.setSkinName(skinName, true);
            return;
        }
        skinTrait.setFetchDefaultSkin(true);
    }

    private void applyAppearance(final NPC npc) {
        final Equipment equipment = npc.getOrAddTrait(Equipment.class);
        equipment.set(Equipment.EquipmentSlot.HELMET, null);
        equipment.set(Equipment.EquipmentSlot.CHESTPLATE, createOutfitPiece(Color.fromRGB(233, 229, 214), Material.LEATHER_CHESTPLATE));
        equipment.set(Equipment.EquipmentSlot.LEGGINGS, createOutfitPiece(Color.fromRGB(244, 239, 226), Material.LEATHER_LEGGINGS));
        equipment.set(Equipment.EquipmentSlot.BOOTS, createOutfitPiece(Color.fromRGB(210, 198, 174), Material.LEATHER_BOOTS));
    }

    private ItemStack createOutfitPiece(final Color color, final Material armorType) {
        final ItemStack item = new ItemStack(armorType);
        if (!(item.getItemMeta() instanceof LeatherArmorMeta meta)) {
            return item;
        }

        meta.setColor(color);
        item.setItemMeta(meta);
        return item;
    }

    private NPCRegistry registry() {
        if (plugin.getServer().getPluginManager().getPlugin("Citizens") == null) {
            return null;
        }
        return CitizensAPI.getNPCRegistry();
    }

    private NPC npc() {
        final NPCRegistry registry = registry();
        if (registry == null || npcId == null) {
            return null;
        }
        return registry.getById(npcId);
    }
}
