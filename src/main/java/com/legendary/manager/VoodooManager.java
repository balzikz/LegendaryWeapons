package com.legendary.manager;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.item.Item;
import cn.nukkit.scheduler.NukkitRunnable;
import com.legendary.LegendaryItems;
import com.legendary.model.LegendaryWeaponDefinition;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VoodooManager {

    private final LegendaryItems plugin;
    private final CooldownManager cooldownManager;
    private final Map<UUID, VoodooLink> linksByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> ownerByTarget = new ConcurrentHashMap<>();
    private final Set<UUID> mirrorGuard = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private NukkitRunnable cleanupTask;

    public VoodooManager(LegendaryItems plugin, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        startCleanupTask();
    }

    public void handleMarkHit(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getDamager() instanceof Player) || !(event.getEntity() instanceof Player)) {
            return;
        }

        Player owner = (Player) event.getDamager();
        Player target = (Player) event.getEntity();
        Item inHand = owner.getInventory().getItemInHand();
        String legendaryId = plugin.getItemManager().getLegendaryId(inHand);
        if (legendaryId == null) {
            return;
        }

        LegendaryWeaponDefinition definition = plugin.getItemManager().getDefinition(legendaryId);
        if (definition == null || !definition.isEnabled() || !definition.isVoodooDoll() || definition.getAbility() == null) {
            return;
        }

        LegendaryWeaponDefinition.AbilityDefinition ability = definition.getAbility();

        if (owner.equals(target)) {
            event.setCancelled(true);
            owner.sendMessage("§cНельзя повесить Куклу Вуду на самого себя.");
            return;
        }

        if (cooldownManager.isOnCooldown(owner, definition.getId())) {
            event.setCancelled(true);
            owner.sendActionBar("§cКукла Вуду перезаряжается: "
                    + cooldownManager.getRemainingTicks(owner, definition.getId()) + " тиков");
            return;
        }

        UUID lockedBy = ownerByTarget.get(target.getUniqueId());
        if (lockedBy != null && !lockedBy.equals(owner.getUniqueId())) {
            event.setCancelled(true);
            owner.sendMessage("§cЭта цель уже связана другой Куклой Вуду.");
            return;
        }

        if (ability.isRequireSameWorld() && owner.getLevel() != target.getLevel()) {
            event.setCancelled(true);
            owner.sendMessage("§cЦель должна быть в том же мире.");
            return;
        }

        VoodooLink oldLink = linksByOwner.get(owner.getUniqueId());
        if (oldLink != null && !ability.isAllowRetarget()) {
            event.setCancelled(true);
            owner.sendMessage("§cСначала дождись завершения текущей связи Куклы Вуду.");
            return;
        }

        if (oldLink != null) {
            removeLink(oldLink, false, false);
        }

        if (ability.isCancelHitDamage()) {
            event.setCancelled(true);
        }

        long expiresAt = System.currentTimeMillis() + (ability.getDurationTicks() * 50L);
        VoodooLink link = new VoodooLink(owner.getUniqueId(), target.getUniqueId(), definition.getId(), expiresAt,
                ability.getBonusDamagePercent(), ability.isRequireSameWorld(),
                ability.isBreakOnOwnerDeath(), ability.isBreakOnTargetDeath(), ability.getMirroredCauses());

        linksByOwner.put(owner.getUniqueId(), link);
        ownerByTarget.put(target.getUniqueId(), owner.getUniqueId());
        cooldownManager.setCooldown(owner, definition.getId(), ability.getCooldownTicks());

        owner.sendMessage("§5§l[Voodoo] §fСвязь установлена с §d" + target.getName()
                + "§f на §e" + formatSeconds(ability.getDurationTicks()) + " сек.§f");
        target.sendMessage("§5§l[Voodoo] §fНа тебя наложена §dКукла Вуду§f игрока §e"
                + owner.getName() + "§f.");
    }

    public void handleOwnerDamage(EntityDamageEvent event) {
        if (event.isCancelled()) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player owner = (Player) event.getEntity();
        if (mirrorGuard.contains(owner.getUniqueId())) {
            return;
        }

        VoodooLink link = linksByOwner.get(owner.getUniqueId());
        if (link == null) {
            return;
        }

        if (!shouldMirror(link, event)) {
            return;
        }

        Player target = getOnlinePlayer(link.targetId);
        if (!validateLink(link, owner, target, true)) {
            return;
        }

        double takenDamage = resolveFinalDamage(event);
        if (takenDamage <= 0.0D) {
            return;
        }

        double mirroredDamage = takenDamage * (1.0D + (link.bonusDamagePercent / 100.0D));
        if (mirroredDamage <= 0.0D) {
            return;
        }

        EntityDamageEvent mirrorEvent = createMirrorDamageEvent(owner, target, mirroredDamage);
        if (mirrorEvent == null) {
            plugin.getLogger().warning("Не удалось создать EntityDamageEvent для Куклы Вуду.");
            return;
        }

        mirrorGuard.add(target.getUniqueId());
        try {
            target.attack(mirrorEvent);
        } finally {
            mirrorGuard.remove(target.getUniqueId());
        }

        if (!target.isAlive() && link.breakOnTargetDeath) {
            removeLink(link, owner.isOnline(), false);
        }
    }

    public void handleQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    public void handleDeath(PlayerDeathEvent event) {
        cleanupPlayer(event.getEntity());
    }

    public void cleanupPlayer(Player player) {
        if (player == null) {
            return;
        }

        VoodooLink owned = linksByOwner.get(player.getUniqueId());
        if (owned != null) {
            removeLink(owned, false, false);
        }

        UUID ownerId = ownerByTarget.get(player.getUniqueId());
        if (ownerId != null) {
            VoodooLink incoming = linksByOwner.get(ownerId);
            if (incoming != null) {
                removeLink(incoming, false, false);
            }
        }

        mirrorGuard.remove(player.getUniqueId());
    }

    public void shutdown() {
        if (cleanupTask != null) {
            try {
                cleanupTask.cancel();
            } catch (Exception ignored) {
                // ignore
            }
        }
        linksByOwner.clear();
        ownerByTarget.clear();
        mirrorGuard.clear();
    }

    private void startCleanupTask() {
        cleanupTask = new NukkitRunnable() {
            @Override
            public void run() {
                sweepInvalidLinks();
            }
        };
        cleanupTask.runTaskTimer(plugin, 20, 20);
    }

    private void sweepInvalidLinks() {
        if (linksByOwner.isEmpty()) {
            return;
        }

        List<VoodooLink> snapshot = new ArrayList<>(linksByOwner.values());
        for (VoodooLink link : snapshot) {
            Player owner = getOnlinePlayer(link.ownerId);
            Player target = getOnlinePlayer(link.targetId);
            validateLink(link, owner, target, true);
        }
    }

    private boolean validateLink(VoodooLink link, Player owner, Player target, boolean notify) {
        if (link == null) {
            return false;
        }
        if (System.currentTimeMillis() >= link.expiresAtMillis) {
            removeLink(link, notify, notify);
            return false;
        }
        if (owner == null || !owner.isOnline() || owner.isClosed()) {
            removeLink(link, false, false);
            return false;
        }
        if (target == null || !target.isOnline() || target.isClosed()) {
            removeLink(link, false, false);
            return false;
        }
        if (link.requireSameWorld && owner.getLevel() != target.getLevel()) {
            removeLink(link, notify, notify);
            return false;
        }
        if (link.breakOnOwnerDeath && !owner.isAlive()) {
            removeLink(link, false, false);
            return false;
        }
        if (link.breakOnTargetDeath && !target.isAlive()) {
            removeLink(link, false, false);
            return false;
        }
        return true;
    }

    private boolean shouldMirror(VoodooLink link, EntityDamageEvent event) {
        if (link == null || event == null) {
            return false;
        }
        List<String> causes = link.mirroredCauses;
        if (causes == null || causes.isEmpty()) {
            return true;
        }
        String causeName = event.getCause().name().toUpperCase(Locale.ROOT);
        for (String entry : causes) {
            if (causeName.equalsIgnoreCase(entry)) {
                return true;
            }
        }
        return false;
    }

    private Player getOnlinePlayer(UUID uniqueId) {
        if (uniqueId == null) {
            return null;
        }

        Server server = plugin.getServer();
        for (Player online : server.getOnlinePlayers().values()) {
            if (uniqueId.equals(online.getUniqueId())) {
                return online;
            }
        }
        return null;
    }

    private double resolveFinalDamage(EntityDamageEvent event) {
        try {
            Method finalDamageMethod = event.getClass().getMethod("getFinalDamage");
            Object value = finalDamageMethod.invoke(event);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        } catch (Throwable ignored) {
            // fallback to getDamage
        }

        try {
            return event.getDamage();
        } catch (Throwable ignored) {
            return 0.0D;
        }
    }

    private EntityDamageEvent createMirrorDamageEvent(Player owner, Player target, double damage) {
        EntityDamageEvent.DamageCause cause = EntityDamageEvent.DamageCause.CUSTOM;

        EntityDamageEvent byEntity = tryCreateDamageByEntity(owner, target, cause, damage);
        if (byEntity != null) {
            return byEntity;
        }
        return tryCreatePlainDamage(target, cause, damage);
    }

    private EntityDamageEvent tryCreateDamageByEntity(Player owner, Player target,
                                                      EntityDamageEvent.DamageCause cause,
                                                      double damage) {
        Constructor<?>[] constructors = EntityDamageByEntityEvent.class.getConstructors();
        for (Constructor<?> constructor : constructors) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 4) {
                continue;
            }
            if (!Entity.class.isAssignableFrom(parameterTypes[0])) {
                continue;
            }
            if (!Entity.class.isAssignableFrom(parameterTypes[1])) {
                continue;
            }
            if (!EntityDamageEvent.DamageCause.class.isAssignableFrom(parameterTypes[2])) {
                continue;
            }
            try {
                Object damageValue = castNumber(parameterTypes[3], damage);
                if (damageValue == null) {
                    continue;
                }
                Object created = constructor.newInstance(owner, target, cause, damageValue);
                if (created instanceof EntityDamageEvent) {
                    return (EntityDamageEvent) created;
                }
            } catch (Throwable ignored) {
                // try next constructor
            }
        }
        return null;
    }

    private EntityDamageEvent tryCreatePlainDamage(Player target,
                                                   EntityDamageEvent.DamageCause cause,
                                                   double damage) {
        Constructor<?>[] constructors = EntityDamageEvent.class.getConstructors();
        for (Constructor<?> constructor : constructors) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length != 3) {
                continue;
            }
            if (!Entity.class.isAssignableFrom(parameterTypes[0])) {
                continue;
            }
            if (!EntityDamageEvent.DamageCause.class.isAssignableFrom(parameterTypes[1])) {
                continue;
            }
            try {
                Object damageValue = castNumber(parameterTypes[2], damage);
                if (damageValue == null) {
                    continue;
                }
                Object created = constructor.newInstance(target, cause, damageValue);
                if (created instanceof EntityDamageEvent) {
                    return (EntityDamageEvent) created;
                }
            } catch (Throwable ignored) {
                // try next constructor
            }
        }
        return null;
    }

    private Object castNumber(Class<?> parameterType, double damage) {
        if (parameterType == double.class || parameterType == Double.class) {
            return damage;
        }
        if (parameterType == float.class || parameterType == Float.class) {
            return (float) damage;
        }
        if (parameterType == int.class || parameterType == Integer.class) {
            return Math.max(0, (int) Math.round(damage));
        }
        return null;
    }

    private void removeLink(VoodooLink link, boolean notifyOwner, boolean notifyTarget) {
        linksByOwner.remove(link.ownerId, link);
        ownerByTarget.remove(link.targetId, link.ownerId);

        if (notifyOwner) {
            Player owner = getOnlinePlayer(link.ownerId);
            if (owner != null && owner.isOnline()) {
                owner.sendMessage("§5§l[Voodoo] §7Связь Куклы Вуду завершена.");
            }
        }
        if (notifyTarget) {
            Player target = getOnlinePlayer(link.targetId);
            if (target != null && target.isOnline()) {
                target.sendMessage("§5§l[Voodoo] §7Действие Куклы Вуду закончилось.");
            }
        }
    }

    private String formatSeconds(int ticks) {
        return String.format(Locale.US, "%.1f", ticks / 20.0D);
    }

    private static final class VoodooLink {
        private final UUID ownerId;
        private final UUID targetId;
        private final String weaponId;
        private final long expiresAtMillis;
        private final double bonusDamagePercent;
        private final boolean requireSameWorld;
        private final boolean breakOnOwnerDeath;
        private final boolean breakOnTargetDeath;
        private final List<String> mirroredCauses;

        private VoodooLink(UUID ownerId,
                           UUID targetId,
                           String weaponId,
                           long expiresAtMillis,
                           double bonusDamagePercent,
                           boolean requireSameWorld,
                           boolean breakOnOwnerDeath,
                           boolean breakOnTargetDeath,
                           List<String> mirroredCauses) {
            this.ownerId = ownerId;
            this.targetId = targetId;
            this.weaponId = weaponId;
            this.expiresAtMillis = expiresAtMillis;
            this.bonusDamagePercent = bonusDamagePercent;
            this.requireSameWorld = requireSameWorld;
            this.breakOnOwnerDeath = breakOnOwnerDeath;
            this.breakOnTargetDeath = breakOnTargetDeath;
            this.mirroredCauses = mirroredCauses == null ? Collections.emptyList() : new ArrayList<>(mirroredCauses);
        }
    }
}
