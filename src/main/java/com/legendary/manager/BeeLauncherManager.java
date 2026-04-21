package com.legendary.manager;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityEgg;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.scheduler.NukkitRunnable;
import com.legendary.LegendaryItems;
import com.legendary.model.LegendaryWeaponDefinition;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BeeLauncherManager {

    private static final String BEE_PROJECTILE_TAG = "legendary_bee_launcher";
    private static final String BEE_MARKER_TAG = "legendary_bee_swarm";

    private final LegendaryItems plugin;
    private final CooldownManager cooldownManager;
    private final Map<UUID, EntityEgg> activeProjectiles = new ConcurrentHashMap<UUID, EntityEgg>();
    private final Map<Long, BeeRecord> activeBees = new ConcurrentHashMap<Long, BeeRecord>();
    private final Map<Long, Long> processedProjectiles = new ConcurrentHashMap<Long, Long>();
    private NukkitRunnable maintenanceTask;

    public BeeLauncherManager(LegendaryItems plugin, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        startMaintenanceTask();
    }

    public void useBeeLauncher(Player player, LegendaryWeaponDefinition definition) {
        LegendaryWeaponDefinition.AbilityDefinition ability = definition.getAbility();
        if (ability == null) {
            player.sendMessage("§cУ оружия не настроена секция ability.");
            return;
        }

        if (cooldownManager.isOnCooldown(player, definition.getId())) {
            player.sendActionBar("§cПчеломёт перезаряжается: "
                    + cooldownManager.getRemainingTicks(player, definition.getId()) + " тиков");
            return;
        }

        EntityEgg previous = activeProjectiles.remove(player.getUniqueId());
        if (previous != null && !previous.isClosed()) {
            previous.close();
        }

        CompoundTag nbt = Entity.getDefaultNBT(
                player.add(0, player.getEyeHeight(), 0),
                player.getDirectionVector().multiply(ability.getProjectileSpeed()),
                (float) player.getYaw(),
                (float) player.getPitch()
        );
        nbt.putBoolean(BEE_PROJECTILE_TAG, true);
        nbt.putString("legendary_id", definition.getId());
        nbt.putString("owner", player.getUniqueId().toString());

        EntityEgg egg = new EntityEgg(player.chunk, nbt, player);
        egg.spawnToAll();

        activeProjectiles.put(player.getUniqueId(), egg);
        cooldownManager.setCooldown(player, definition.getId(), ability.getCooldownTicks());

        player.getLevel().addSound(player, Sound.RANDOM_BOW, 1.0f, 0.6f);

        new NukkitRunnable() {
            @Override
            public void run() {
                EntityEgg current = activeProjectiles.get(player.getUniqueId());
                if (current == egg) {
                    activeProjectiles.remove(player.getUniqueId());
                }
                processedProjectiles.remove(egg.getId());
                if (!egg.isClosed()) {
                    egg.close();
                }
            }
        }.runTaskLater(plugin, Math.max(ability.getMaxFlightTicks(), 20));
    }

    public void handleProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof EntityProjectile)) {
            return;
        }

        EntityProjectile projectile = (EntityProjectile) event.getEntity();
        if (!isBeeProjectile(projectile)) {
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
        if (!markProjectileProcessed(projectile.getId())) {
            return;
        }

        Player shooter = (Player) projectile.shootingEntity;
        activeProjectiles.remove(shooter.getUniqueId(), projectile);

        LegendaryWeaponDefinition definition = resolveDefinition(projectile);
        if (definition == null || definition.getAbility() == null) {
            projectile.close();
            return;
        }

        Vector3 impact = mop != null && mop.hitVector != null ? mop.hitVector : projectile.getPosition();
        projectile.close();
        spawnSwarm(shooter, impact, definition);
    }

    public void handleProjectileDamage(EntityDamageByChildEntityEvent event) {
        if (!(event.getChild() instanceof EntityProjectile)) {
            return;
        }

        EntityProjectile projectile = (EntityProjectile) event.getChild();
        if (!isBeeProjectile(projectile)) {
            return;
        }
        if (!markProjectileProcessed(projectile.getId())) {
            return;
        }

        event.setCancelled();

        if (!(projectile.shootingEntity instanceof Player)) {
            projectile.close();
            return;
        }

        Player shooter = (Player) projectile.shootingEntity;
        activeProjectiles.remove(shooter.getUniqueId(), projectile);

        LegendaryWeaponDefinition definition = resolveDefinition(projectile);
        if (definition == null || definition.getAbility() == null) {
            projectile.close();
            return;
        }

        Vector3 impact = event.getEntity().add(0, 0.25D, 0);
        projectile.close();
        spawnSwarm(shooter, impact, definition);
    }

    public void handleBeeDamage(EntityDamageByEntityEvent event) {
        if (event == null || event.getDamager() == null) {
            return;
        }

        BeeRecord bee = activeBees.get(event.getDamager().getId());
        if (bee == null) {
            return;
        }

        if (!(event.getEntity() instanceof Player)) {
            event.setCancelled();
            return;
        }

        Player target = (Player) event.getEntity();
        if (target.getUniqueId().equals(bee.ownerId)) {
            event.setCancelled();
            return;
        }

        if (bee.expiresAtMillis <= System.currentTimeMillis()) {
            event.setCancelled();
            despawnBee((Entity) event.getDamager(), bee);
        }
    }

    public void cleanupPlayer(Player player) {
        if (player == null) {
            return;
        }

        EntityEgg projectile = activeProjectiles.remove(player.getUniqueId());
        if (projectile != null && !projectile.isClosed()) {
            processedProjectiles.remove(projectile.getId());
            projectile.close();
        }

        for (Iterator<Map.Entry<Long, BeeRecord>> it = activeBees.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, BeeRecord> entry = it.next();
            BeeRecord bee = entry.getValue();
            if (!bee.ownerId.equals(player.getUniqueId())) {
                continue;
            }

            Entity entity = plugin.getServer().getLevelByName(bee.worldName) != null
                    ? plugin.getServer().getLevelByName(bee.worldName).getEntity(entry.getKey())
                    : null;
            if (entity != null && !entity.isClosed()) {
                entity.close();
            }
            it.remove();
        }
    }

    public void shutdown() {
        if (maintenanceTask != null) {
            try {
                maintenanceTask.cancel();
            } catch (Exception ignored) {
                // ignore
            }
        }

        for (EntityEgg egg : activeProjectiles.values()) {
            if (egg != null && !egg.isClosed()) {
                egg.close();
            }
        }
        activeProjectiles.clear();

        for (Map.Entry<Long, BeeRecord> entry : activeBees.entrySet()) {
            BeeRecord bee = entry.getValue();
            Entity entity = plugin.getServer().getLevelByName(bee.worldName) != null
                    ? plugin.getServer().getLevelByName(bee.worldName).getEntity(entry.getKey())
                    : null;
            if (entity != null && !entity.isClosed()) {
                entity.close();
            }
        }
        activeBees.clear();
        processedProjectiles.clear();
    }

    private void spawnSwarm(Player owner, Vector3 impact, LegendaryWeaponDefinition definition) {
        if (owner == null || impact == null || definition == null || definition.getAbility() == null) {
            return;
        }

        LegendaryWeaponDefinition.AbilityDefinition ability = definition.getAbility();
        int min = Math.max(1, ability.getBeeCountMin());
        int max = Math.max(min, ability.getBeeCountMax());
        int count = ThreadLocalRandom.current().nextInt(min, max + 1);
        long expiresAt = System.currentTimeMillis() + (ability.getDurationTicks() * 50L);

        owner.getLevel().addSound(owner, Sound.RANDOM_POP, 1.0f, 1.0f);

        for (int i = 0; i < count; i++) {
            spawnSingleBee(owner, impact, ability, expiresAt);
        }
    }

    private void spawnSingleBee(Player owner,
                                Vector3 impact,
                                LegendaryWeaponDefinition.AbilityDefinition ability,
                                long expiresAt) {
        double radius = Math.max(0.3D, ability.getBeeSpawnRadius());
        double offsetX = ThreadLocalRandom.current().nextDouble(-radius, radius);
        double offsetY = ThreadLocalRandom.current().nextDouble(0.15D, 0.75D);
        double offsetZ = ThreadLocalRandom.current().nextDouble(-radius, radius);
        Vector3 spawnPos = impact.add(offsetX, offsetY, offsetZ);

        Entity bee = createBeeEntity(owner, spawnPos);
        if (bee == null) {
            plugin.getLogger().warning("Не удалось создать пчелу для Пчеломёта. Проверь ядро NukkitMOT и dependency в pom.xml.");
            return;
        }

        bee.namedTag.putBoolean(BEE_MARKER_TAG, true);
        bee.namedTag.putString("legendary_owner", owner.getUniqueId().toString());
        bee.spawnToAll();

        BeeRecord record = new BeeRecord(owner.getUniqueId(), owner.getLevel().getName(), expiresAt,
                Math.max(6.0D, ability.getBeeSearchRange()));
        activeBees.put(bee.getId(), record);
        retargetBee(bee, record);
    }

    private Entity createBeeEntity(Player owner, Vector3 spawnPos) {
        if (owner == null || owner.getLevel() == null || spawnPos == null) {
            return null;
        }

        FullChunk chunk = owner.getLevel().getChunk(spawnPos.getFloorX() >> 4, spawnPos.getFloorZ() >> 4);
        if (chunk == null) {
            return null;
        }

        CompoundTag nbt = Entity.getDefaultNBT(spawnPos);
        nbt.putBoolean(BEE_MARKER_TAG, true);
        nbt.putBoolean("Angry", true);

        try {
            Class<?> clazz = Class.forName("cn.nukkit.entity.passive.EntityBee");
            Constructor<?> constructor = clazz.getConstructor(FullChunk.class, CompoundTag.class);
            Object created = constructor.newInstance(chunk, nbt);
            if (created instanceof Entity) {
                return (Entity) created;
            }
        } catch (Exception ignored) {
            // fallback below
        }

        try {
            return Entity.createEntity("Bee", chunk, nbt);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void retargetBee(Entity bee, BeeRecord record) {
        if (bee == null || bee.isClosed()) {
            return;
        }

        Player target = findNearestTarget(bee, record.ownerId, record.searchRange);
        setBeeAngry(bee, true);
        if (target == null) {
            return;
        }

        try {
            Method setTarget = bee.getClass().getMethod("setTarget", Entity.class);
            setTarget.invoke(bee, target);
        } catch (Exception ignored) {
            // old or different API
        }

        try {
            Field angryTo = bee.getClass().getField("isAngryTo");
            angryTo.setLong(bee, target.getId());
        } catch (Exception ignored) {
            // optional field
        }
    }

    private void setBeeAngry(Entity bee, boolean value) {
        try {
            Method setAngry = bee.getClass().getMethod("setAngry", boolean.class);
            setAngry.invoke(bee, value);
        } catch (Exception ignored) {
            // optional method
        }
    }

    private Player findNearestTarget(Entity bee, UUID ownerId, double searchRange) {
        Player best = null;
        double bestDistanceSquared = searchRange * searchRange;

        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            if (player == null || !player.isOnline() || player.isClosed() || !player.isAlive()) {
                continue;
            }
            if (player.getUniqueId().equals(ownerId)) {
                continue;
            }
            if (player.getLevel() != bee.getLevel()) {
                continue;
            }

            double distanceSquared = player.distanceSquared(bee);
            if (distanceSquared > bestDistanceSquared) {
                continue;
            }

            bestDistanceSquared = distanceSquared;
            best = player;
        }

        return best;
    }

    private LegendaryWeaponDefinition resolveDefinition(EntityProjectile projectile) {
        if (projectile == null || projectile.namedTag == null || !projectile.namedTag.contains("legendary_id")) {
            return null;
        }
        return plugin.getItemManager().getDefinition(projectile.namedTag.getString("legendary_id"));
    }

    private boolean isBeeProjectile(EntityProjectile projectile) {
        return projectile != null
                && projectile.namedTag != null
                && projectile.namedTag.getBoolean(BEE_PROJECTILE_TAG);
    }

    private boolean markProjectileProcessed(long projectileId) {
        long now = System.currentTimeMillis();
        Long existing = processedProjectiles.get(projectileId);
        if (existing != null && existing.longValue() > now) {
            return false;
        }
        processedProjectiles.put(projectileId, now + 15000L);
        return true;
    }

    private void startMaintenanceTask() {
        maintenanceTask = new NukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                for (Iterator<Map.Entry<Long, Long>> it = processedProjectiles.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Long, Long> entry = it.next();
                    if (entry.getValue().longValue() <= now) {
                        it.remove();
                    }
                }

                for (Iterator<Map.Entry<Long, BeeRecord>> it = activeBees.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Long, BeeRecord> entry = it.next();
                    BeeRecord bee = entry.getValue();

                    if (bee.expiresAtMillis <= now) {
                        Entity entity = plugin.getServer().getLevelByName(bee.worldName) != null
                                ? plugin.getServer().getLevelByName(bee.worldName).getEntity(entry.getKey())
                                : null;
                        despawnBee(entity, bee);
                        it.remove();
                        continue;
                    }

                    Entity entity = plugin.getServer().getLevelByName(bee.worldName) != null
                            ? plugin.getServer().getLevelByName(bee.worldName).getEntity(entry.getKey())
                            : null;
                    if (entity == null || entity.isClosed() || !entity.isAlive()) {
                        it.remove();
                        continue;
                    }

                    retargetBee(entity, bee);
                }
            }
        };
        maintenanceTask.runTaskTimer(plugin, 10, 10);
    }

    private void despawnBee(Entity entity, BeeRecord bee) {
        if (entity != null && !entity.isClosed()) {
            entity.close();
        }
        if (entity != null) {
            activeBees.remove(entity.getId());
        }
    }

    private static final class BeeRecord {
        private final UUID ownerId;
        private final String worldName;
        private final long expiresAtMillis;
        private final double searchRange;

        private BeeRecord(UUID ownerId, String worldName, long expiresAtMillis, double searchRange) {
            this.ownerId = ownerId;
            this.worldName = worldName;
            this.expiresAtMillis = expiresAtMillis;
            this.searchRange = searchRange;
        }
    }
            }
