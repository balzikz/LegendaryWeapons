package com.legendary.manager;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.entity.projectile.EntitySnowball;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.Sound;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.scheduler.NukkitRunnable;
import com.legendary.LegendaryItems;
import com.legendary.model.LegendaryWeaponDefinition;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HarpoonManager {

    private static final String HARPOON_TAG = "legendary_harpoon";

    private final LegendaryItems plugin;
    private final CooldownManager cooldownManager;
    private final Map<UUID, EntitySnowball> activeProjectiles = new ConcurrentHashMap<>();
    private final Map<Long, PullTask> activePulls = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fallImmunityUntil = new ConcurrentHashMap<>();

    public HarpoonManager(LegendaryItems plugin, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
    }

    public void useHarpoon(Player player, LegendaryWeaponDefinition definition) {
        LegendaryWeaponDefinition.AbilityDefinition ability = definition.getAbility();
        if (ability == null) {
            player.sendMessage("§cУ оружия не настроена секция ability.");
            return;
        }

        if (cooldownManager.isOnCooldown(player, definition.getId())) {
            player.sendActionBar("§cГарпун перезаряжается: " + cooldownManager.getRemainingTicks(player, definition.getId()) + " тиков");
            return;
        }

        EntitySnowball previous = activeProjectiles.remove(player.getUniqueId());
        if (previous != null && !previous.isClosed()) {
            previous.close();
        }

        CompoundTag nbt = Entity.getDefaultNBT(
                player.add(0, player.getEyeHeight(), 0),
                player.getDirectionVector().multiply(ability.getProjectileSpeed()),
                (float) player.getYaw(),
                (float) player.getPitch()
        );
        nbt.putBoolean(HARPOON_TAG, true);
        nbt.putString("legendary_id", definition.getId());
        nbt.putString("owner", player.getUniqueId().toString());

        EntitySnowball snowball = new EntitySnowball(player.chunk, nbt, player);
        snowball.setDataFlag(Entity.DATA_FLAGS, Entity.DATA_FLAG_INVISIBLE, true);
        snowball.spawnToAll();

        activeProjectiles.put(player.getUniqueId(), snowball);
        cooldownManager.setCooldown(player, definition.getId(), ability.getCooldownTicks());

        player.getLevel().addSound(player, Sound.RANDOM_BOW, 1.0f, 0.8f);

        new NukkitRunnable() {
            @Override
            public void run() {
                EntitySnowball current = activeProjectiles.get(player.getUniqueId());
                if (current == snowball) {
                    activeProjectiles.remove(player.getUniqueId());
                }
                if (!snowball.isClosed()) {
                    snowball.close();
                }
            }
        }.runTaskLater(plugin, ability.getMaxFlightTicks());
    }

    public void handleProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EntityProjectile)) {
            return;
        }

        EntityProjectile projectile = (EntityProjectile) event.getEntity();
        if (!isHarpoonProjectile(projectile)) {
            return;
        }
        if (!(projectile.shootingEntity instanceof Player)) {
            projectile.close();
            return;
        }

        MovingObjectPosition mop = event.getMovingObjectPosition();
        if (mop != null && mop.typeOfHit == 1) {
            return;
        }

        Player shooter = (Player) projectile.shootingEntity;
        activeProjectiles.remove(shooter.getUniqueId(), projectile);

        String legendaryId = getLegendaryId(projectile);
        LegendaryWeaponDefinition definition = plugin.getItemManager().getDefinition(legendaryId);
        if (definition == null || definition.getAbility() == null) {
            projectile.close();
            return;
        }

        Vector3 anchor = mop != null && mop.hitVector != null ? mop.hitVector : projectile.getPosition();
        projectile.close();
        startPullToPoint(shooter, anchor, definition.getAbility());
    }

    public void handleProjectileDamage(EntityDamageByChildEntityEvent event) {
        if (!(event.getChild() instanceof EntityProjectile) || !isHarpoonProjectile((EntityProjectile) event.getChild())) {
            return;
        }

        event.setCancelled();

        EntityProjectile projectile = (EntityProjectile) event.getChild();
        if (!(projectile.shootingEntity instanceof Player)) {
            projectile.close();
            return;
        }

        Player shooter = (Player) projectile.shootingEntity;
        activeProjectiles.remove(shooter.getUniqueId(), projectile);

        String legendaryId = getLegendaryId(projectile);
        LegendaryWeaponDefinition definition = plugin.getItemManager().getDefinition(legendaryId);
        if (definition == null || definition.getAbility() == null) {
            projectile.close();
            return;
        }

        if (!(event.getEntity() instanceof EntityLiving)) {
            projectile.close();
            return;
        }

        EntityLiving target = (EntityLiving) event.getEntity();
        if (target == shooter) {
            projectile.close();
            return;
        }

        if (target instanceof Player && !definition.getAbility().isPullPlayers()) {
            projectile.close();
            return;
        }

        if (!(target instanceof Player) && !definition.getAbility().isPullMobs()) {
            projectile.close();
            return;
        }

        projectile.close();
        startPullToEntity(target, shooter, definition.getAbility());
    }

    public boolean hasFallImmunity(Player player) {
        Long expiresAt = fallImmunityUntil.get(player.getUniqueId());
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < System.currentTimeMillis()) {
            fallImmunityUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public void consumeFallImmunity(Player player) {
        fallImmunityUntil.remove(player.getUniqueId());
    }

    public void cleanupPlayer(Player player) {
        EntitySnowball projectile = activeProjectiles.remove(player.getUniqueId());
        if (projectile != null && !projectile.isClosed()) {
            projectile.close();
        }

        PullTask ownPull = activePulls.remove(player.getId());
        if (ownPull != null) {
            ownPull.cancelTask();
        }
    }

    private void startPullToPoint(Entity entity, Vector3 anchor, LegendaryWeaponDefinition.AbilityDefinition ability) {
        cancelPull(entity);
        PullTask task = new PullTask(entity, null, anchor, ability);
        activePulls.put(entity.getId(), task);
        task.runTaskTimer(plugin, 1, 1);
    }

    private void startPullToEntity(Entity pulled, Entity anchorEntity, LegendaryWeaponDefinition.AbilityDefinition ability) {
        cancelPull(pulled);
        PullTask task = new PullTask(pulled, anchorEntity, null, ability);
        activePulls.put(pulled.getId(), task);
        task.runTaskTimer(plugin, 1, 1);
    }

    private void cancelPull(Entity entity) {
        PullTask existing = activePulls.remove(entity.getId());
        if (existing != null) {
            existing.cancelTask();
        }
    }

    private boolean isHarpoonProjectile(EntityProjectile projectile) {
        return projectile != null && projectile.namedTag != null && projectile.namedTag.getBoolean(HARPOON_TAG);
    }

    private String getLegendaryId(EntityProjectile projectile) {
        if (projectile == null || projectile.namedTag == null || !projectile.namedTag.contains("legendary_id")) {
            return null;
        }
        return projectile.namedTag.getString("legendary_id").toLowerCase();
    }

    private final class PullTask extends NukkitRunnable {

        private final Entity entity;
        private final Entity anchorEntity;
        private final Vector3 anchorPoint;
        private final LegendaryWeaponDefinition.AbilityDefinition ability;
        private double bestDistanceSquared = Double.MAX_VALUE;
        private int stuckTicks = 0;
        private int age = 0;

        private PullTask(Entity entity, Entity anchorEntity, Vector3 anchorPoint, LegendaryWeaponDefinition.AbilityDefinition ability) {
            this.entity = entity;
            this.anchorEntity = anchorEntity;
            this.anchorPoint = anchorPoint;
            this.ability = ability;
        }

        @Override
        public void run() {
            if (entity.isClosed() || !entity.isAlive()) {
                finish(false);
                return;
            }

            Vector3 target = resolveTarget();
            if (target == null) {
                finish(false);
                return;
            }

            if (anchorEntity != null && anchorEntity.getLevel() != entity.getLevel()) {
                finish(false);
                return;
            }

            double distanceSquared = entity.distanceSquared(target);
            if (distanceSquared <= ability.getMinDistance() * ability.getMinDistance()) {
                finish(true);
                return;
            }

            Vector3 delta = target.subtract(entity.x, entity.y, entity.z);
            double length = delta.length();
            if (length <= 0.0001D) {
                finish(true);
                return;
            }

            double speed = Math.min(ability.getPullSpeed(), Math.max(0.35D, length * 0.35D));
            Vector3 motion = delta.normalize().multiply(speed);

            if (motion.y > ability.getVerticalCap()) {
                motion.y = ability.getVerticalCap();
            }
            if (motion.y < -1.35D) {
                motion.y = -1.35D;
            }

            if (entity.isCollidedHorizontally && delta.y > 0.1D) {
                motion.y = Math.max(motion.y, ability.getStepBoost());
            }

            if (entity instanceof Player && ((Player) entity).isOnGround() && delta.y > 0.8D) {
                motion.y = Math.max(motion.y, ability.getStepBoost());
            }

            entity.setMotion(motion);

            if (distanceSquared + 0.05D < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                stuckTicks = 0;
            } else {
                stuckTicks++;
            }

            age++;
            if (stuckTicks >= ability.getStuckTicks() || age >= ability.getMaxPullTicks()) {
                finish(false);
            }
        }

        private Vector3 resolveTarget() {
            if (anchorEntity != null) {
                if (anchorEntity.isClosed() || !anchorEntity.isAlive()) {
                    return null;
                }
                return anchorEntity.add(0, 0.25D, 0);
            }
            return anchorPoint;
        }

        private void finish(boolean reachedTarget) {
            activePulls.remove(entity.getId(), this);

            if (entity instanceof Player) {
                Player player = (Player) entity;
                fallImmunityUntil.put(player.getUniqueId(), System.currentTimeMillis() + (ability.getFallImmunityTicks() * 50L));
                if (!reachedTarget) {
                    player.sendActionBar("§7Гарпун остановился безопасно.");
                }
            }

            cancelTask();
        }

        private void cancelTask() {
            try {
                this.cancel();
            } catch (Exception ignored) {
                // already cancelled
            }
        }
    }
}
