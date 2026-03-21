package nest.fugitivebaron;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.util.Vector;

final class FugitiveBaronController {
    private final FugitiveBaronPlugin plugin;
    private final HideoutService hideoutService;
    private final RecognitionItemChecker itemChecker;
    private final DialogueService dialogueService;

    private double awarenessRadius;
    private double suspicionRadius;
    private double trustRadius;
    private double fleeResetRadius;
    private double suspiciousSpeed;
    private double fleeSpeed;
    private long interactCooldownTicks;
    private String displayName;

    private UUID baronId;
    private BaronState state = BaronState.IDLE;
    private UUID currentTargetId;
    private long lastInteractionTick;

    FugitiveBaronController(
        final FugitiveBaronPlugin plugin,
        final HideoutService hideoutService,
        final RecognitionItemChecker itemChecker,
        final DialogueService dialogueService
    ) {
        this.plugin = plugin;
        this.hideoutService = hideoutService;
        this.itemChecker = itemChecker;
        this.dialogueService = dialogueService;
        reload(plugin.getConfig());
    }

    void reload(final FileConfiguration config) {
        this.awarenessRadius = config.getDouble("encounter.awareness-radius", 12.0D);
        this.suspicionRadius = config.getDouble("encounter.suspicion-radius", 7.0D);
        this.trustRadius = config.getDouble("encounter.trust-radius", 3.0D);
        this.fleeResetRadius = config.getDouble("encounter.flee-reset-radius", 16.0D);
        this.suspiciousSpeed = config.getDouble("encounter.suspicious-speed", 0.14D);
        this.fleeSpeed = config.getDouble("encounter.flee-speed", 0.34D);
        this.interactCooldownTicks = config.getLong("encounter.interact-cooldown-ticks", 40L);
        this.displayName = config.getString("encounter.display-name", "John");
    }

    boolean hasBaron() {
        return getBaronEntity() != null;
    }

    LivingEntity spawnBaron(final Location location) {
        Objects.requireNonNull(location.getWorld(), "Spawn world cannot be null.");
        despawnBaron();

        final EntityType type = parseEntityType();
        final Entity entity = location.getWorld().spawnEntity(location, type);
        if (!(entity instanceof LivingEntity livingEntity)) {
            entity.remove();
            throw new IllegalStateException("Configured entity type must be living.");
        }

        final PersistentDataContainer pdc = livingEntity.getPersistentDataContainer();
        pdc.set(plugin.baronKey(), PersistentDataType.BYTE, (byte) 1);

        livingEntity.customName(Component.text(displayName, NamedTextColor.GOLD));
        livingEntity.setCustomNameVisible(true);
        livingEntity.setPersistent(true);
        livingEntity.setRemoveWhenFarAway(false);
        livingEntity.setCanPickupItems(false);
        livingEntity.setCollidable(true);
        livingEntity.setAI(true);
        livingEntity.setSilent(true);
        tuneAttributes(livingEntity);
        applyAppearance(livingEntity);

        if (livingEntity instanceof WanderingTrader trader) {
            trader.setAI(true);
            trader.setInvulnerable(false);
            trader.setDespawnDelay(-1);
        }

        this.baronId = livingEntity.getUniqueId();
        this.state = BaronState.IDLE;
        this.currentTargetId = null;
        this.lastInteractionTick = 0L;
        plugin.debugLog("Spawned Baron at " + formatLocation(location));

        return livingEntity;
    }

    LivingEntity spawnBaronAtActiveHideout() {
        final Location location = hideoutService.activeHideoutLocation();
        if (location == null) {
            throw new IllegalStateException("Active hideout location is not available.");
        }
        return spawnBaron(location);
    }

    void despawnBaron() {
        final LivingEntity baron = getBaronEntity();
        if (baron != null) {
            plugin.debugLog("Despawning Baron at " + formatLocation(baron.getLocation()));
            baron.remove();
        }
        this.baronId = null;
        this.currentTargetId = null;
        this.state = BaronState.IDLE;
    }

    void tick(final long currentTick) {
        final LivingEntity baron = getBaronEntity();
        if (baron == null || baron.isDead()) {
            this.baronId = null;
            this.currentTargetId = null;
            this.state = BaronState.IDLE;
            return;
        }

        final Player target = findNearestPlayer(baron.getLocation(), awarenessRadius);
        if (target == null) {
            setState(BaronState.IDLE, null, currentTick);
            if (baron instanceof Mob mob) {
                mob.setTarget(null);
                stopPathfinding(mob);
                mob.setVelocity(new Vector(0, baron.getVelocity().getY(), 0));
            }
            return;
        }

        final double distance = baron.getLocation().distance(target.getLocation());
        final boolean hasRecognitionItem = itemChecker.isRecognitionItem(target.getInventory().getItemInMainHand());
        currentTargetId = target.getUniqueId();

        if (distance <= trustRadius) {
            if (hasRecognitionItem) {
                setState(BaronState.TRUST, target, currentTick);
                face(baron, target.getLocation());
                zeroHorizontalVelocity(baron);
            } else {
                setState(BaronState.FLEE, target, currentTick);
                flee(baron, target, fleeSpeed);
            }
            return;
        }

        if (distance <= suspicionRadius) {
            setState(BaronState.SUSPICIOUS, target, currentTick);
            retreat(baron, target, suspiciousSpeed);
            return;
        }

        if (state == BaronState.FLEE && distance < fleeResetRadius) {
            flee(baron, target, fleeSpeed);
            return;
        }

        setState(BaronState.OBSERVE, target, currentTick);
        face(baron, target.getLocation());
    }

    boolean isBaron(final Entity entity) {
        return entity.getPersistentDataContainer().has(plugin.baronKey(), PersistentDataType.BYTE);
    }

    void handleInteraction(final Player player, final Entity clicked, final long currentTick) {
        if (!isBaron(clicked) || !(clicked instanceof LivingEntity baron)) {
            return;
        }

        if (currentTick < lastInteractionTick + interactCooldownTicks) {
            return;
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        final boolean trusted = itemChecker.isRecognitionItem(held)
            && baron.getLocation().distance(player.getLocation()) <= trustRadius + 1.0D;

        if (!trusted) {
            plugin.debugLog("Rejected interaction from " + player.getName() + " without valid recognition item.");
            setState(BaronState.FLEE, player, currentTick);
            flee(baron, player, fleeSpeed);
            dialogueService.forceLine(player, BaronState.FLEE, currentTick);
            lastInteractionTick = currentTick;
            return;
        }

        plugin.debugLog("Accepted trusted interaction from " + player.getName() + " at active hideout.");
        setState(BaronState.TRUST, player, currentTick);
        player.sendMessage(Component.text(hideoutService.activeHideoutTrustIntel(), NamedTextColor.YELLOW));
        dialogueService.forceLine(player, BaronState.TRUST, currentTick);
        dialogueService.deliverAmbientHideoutLine(
            player,
            hideoutService.activeHideoutAmbientLine(),
            hideoutService.activeHideoutAmbientSoundId(),
            currentTick
        );
        lastInteractionTick = currentTick;
    }

    void handleDamage(final Entity entity, final Player attacker, final long currentTick) {
        if (!(entity instanceof LivingEntity baron) || !isBaron(entity)) {
            return;
        }

        plugin.debugLog("Baron damaged by " + attacker.getName() + "; triggering escape.");
        setState(BaronState.ESCAPE, attacker, currentTick);
        dialogueService.forceLine(attacker, BaronState.ESCAPE, currentTick);
        final World world = baron.getWorld();
        world.spawnParticle(Particle.SMOKE, baron.getLocation().add(0, 1, 0), 24, 0.4, 0.6, 0.4, 0.01);
        baron.remove();
        this.baronId = null;
        this.currentTargetId = null;
        this.state = BaronState.IDLE;
    }

    BaronState state() {
        return state;
    }

    Location getBaronLocation() {
        final LivingEntity baron = getBaronEntity();
        return baron == null ? null : baron.getLocation().clone();
    }

    private EntityType parseEntityType() {
        final String configured = plugin.getConfig().getString("encounter.spawn-entity", "WANDERING_TRADER");
        try {
            return EntityType.valueOf(configured);
        } catch (final IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid encounter.spawn-entity '" + configured + "', using WANDERING_TRADER.");
            return EntityType.WANDERING_TRADER;
        }
    }

    private LivingEntity getBaronEntity() {
        if (baronId == null) {
            return null;
        }
        for (final World world : plugin.getServer().getWorlds()) {
            final Entity entity = world.getEntity(baronId);
            if (entity instanceof LivingEntity livingEntity) {
                return livingEntity;
            }
        }
        return null;
    }

    private Player findNearestPlayer(final Location origin, final double radius) {
        final double radiusSquared = radius * radius;
        return origin.getWorld()
            .getPlayers()
            .stream()
            .filter(player -> !player.isDead())
            .filter(player -> player.getGameMode() != GameMode.SPECTATOR)
            .filter(player -> player.getLocation().distanceSquared(origin) <= radiusSquared)
            .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(origin)))
            .orElse(null);
    }

    private void setState(final BaronState nextState, final Player player, final long currentTick) {
        if (this.state == nextState) {
            if (player != null && nextState != BaronState.IDLE) {
                dialogueService.speakTo(player, nextState, currentTick);
            }
            return;
        }

        plugin.debugLog(
            "State change " + this.state + " -> " + nextState +
            (player == null ? "" : " for player " + player.getName())
        );
        this.state = nextState;
        if (player != null && nextState != BaronState.IDLE) {
            dialogueService.forceLine(player, nextState, currentTick);
        }
    }

    private String formatLocation(final Location location) {
        return location.getWorld().getName() + " " +
            location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private void face(final LivingEntity baron, final Location targetLocation) {
        final Location current = baron.getLocation();
        final Vector direction = targetLocation.toVector().subtract(current.toVector());
        direction.setY(0);
        if (direction.lengthSquared() <= 0.0001D) {
            return;
        }

        current.setDirection(direction.normalize());
        baron.teleport(current);
    }

    private void retreat(final LivingEntity baron, final Player player, final double speed) {
        face(baron, player.getLocation());
        final Vector away = escapeVector(baron, player);
        away.setY(0);
        if (away.lengthSquared() <= 0.0001D) {
            return;
        }

        if (baron instanceof Mob mob) {
            final Location destination = baron.getLocation().clone().add(away.normalize().multiply(14.0D));
            destination.setY(baron.getLocation().getY());
            tryMove(mob, destination, speed);
        }

        final Vector movement = away.normalize().multiply(speed);
        movement.setY(baron.getVelocity().getY());
        baron.setVelocity(movement);
    }

    private void flee(final LivingEntity baron, final Player player, final double speed) {
        retreat(baron, player, speed);
        baron.getWorld().spawnParticle(Particle.CLOUD, baron.getLocation().add(0, 0.6, 0), 8, 0.2, 0.2, 0.2, 0.01);
    }

    private void zeroHorizontalVelocity(final LivingEntity baron) {
        final Vector velocity = baron.getVelocity();
        baron.setVelocity(new Vector(0, velocity.getY(), 0));
    }

    private void applyAppearance(final LivingEntity baron) {
        final EntityEquipment equipment = baron.getEquipment();
        if (equipment == null) {
            return;
        }

        equipment.setHelmet(null);
        equipment.setChestplate(createOutfitPiece(Color.fromRGB(233, 229, 214), Material.LEATHER_CHESTPLATE));
        equipment.setLeggings(createOutfitPiece(Color.fromRGB(244, 239, 226), Material.LEATHER_LEGGINGS));
        equipment.setBoots(createOutfitPiece(Color.fromRGB(210, 198, 174), Material.LEATHER_BOOTS));
        equipment.setHelmetDropChance(0.0F);
        equipment.setChestplateDropChance(0.0F);
        equipment.setLeggingsDropChance(0.0F);
        equipment.setBootsDropChance(0.0F);
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

    private void tuneAttributes(final LivingEntity baron) {
        setBaseValue(baron, Attribute.MAX_HEALTH, 40.0D);
        setBaseValue(baron, Attribute.MOVEMENT_SPEED, 0.42D);
        baron.setHealth(baron.getAttribute(Attribute.MAX_HEALTH) == null ? baron.getHealth() : baron.getAttribute(Attribute.MAX_HEALTH).getValue());
    }

    private void setBaseValue(final LivingEntity entity, final Attribute attribute, final double value) {
        final AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.setBaseValue(value);
    }

    private Vector escapeVector(final LivingEntity baron, final Player player) {
        final Vector away = baron.getLocation().toVector().subtract(player.getLocation().toVector());
        if (away.lengthSquared() > 0.0001D) {
            return away;
        }
        final Vector fallback = player.getLocation().getDirection().multiply(-1.0D);
        fallback.setY(0);
        return fallback;
    }

    private void tryMove(final Mob mob, final Location destination, final double speed) {
        try {
            mob.getPathfinder().moveTo(destination, speed);
        } catch (final NoSuchMethodError ignored) {
            plugin.debugLog("Pathfinder.moveTo unavailable; falling back to velocity only.");
        }
    }

    private void stopPathfinding(final Mob mob) {
        try {
            mob.getPathfinder().stopPathfinding();
        } catch (final NoSuchMethodError ignored) {
            plugin.debugLog("Pathfinder.stopPathfinding unavailable; falling back to velocity only.");
        }
    }
}
