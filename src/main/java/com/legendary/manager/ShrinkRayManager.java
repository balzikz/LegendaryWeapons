package com.legendary.manager;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.math.Vector3;
import cn.nukkit.potion.Effect;
import cn.nukkit.scheduler.NukkitRunnable;
import com.legendary.LegendaryItems;
import com.legendary.model.LegendaryWeaponDefinition;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ShrinkRayManager {

    private static final double VANILLA_PLAYER_HEIGHT = 1.8D;
    private static final double MIN_SCALE = 0.35D;
    private static final double MAX_SCALE = 2.4D;

    private static final String TAG_ACTIVE = "legendary_shrink_ray_active";
    private static final String TAG_SMALL = "legendary_shrink_ray_small";
    private static final String TAG_BIG = "legendary_shrink_ray_big";

    private final LegendaryItems plugin;
    private final CooldownManager cooldownManager;
    private final Map<UUID, ActiveScale> activeByTarget = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeTargetByCaster = new ConcurrentHashMap<>();
    private NukkitRunnable cleanupTask;

    public ShrinkRayManager(LegendaryItems plugin, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        startCleanupTask();
    }

    public void handleUse(Player player, LegendaryWeaponDefinition definition) {
        if (player == null || definition == null || !definition.isShrinkRay() || definition.getAbility() == null) {
            return;
        }

        LegendaryWeaponDefinition.AbilityDefinition ability = definition.getAbility();

        if (cooldownManager.isOnCooldown(player, definition.getId())) {
            player.sendActionBar("§cShrink Ray перезаряжается: "
                    + cooldownManager.getRemainingTicks(player, definition.getId()) + " тиков");
            return;
        }

        if (activeTargetByCaster.containsKey(player.getUniqueId())) {
            player.sendMessage("§cОдновременно можно держать активной только одну способность Shrink Ray.");
            return;
        }

        if (activeByTarget.containsKey(player.getUniqueId())) {
            player.sendMessage("§cПока на тебе уже висит изменение размера, Shrink Ray использовать нельзя.");
            return;
        }

        if (player.isSneaking()) {
            useSelfShrink(player, definition, ability);
            return;
        }

        fireGrowthRay(player, definition, ability);
    }

    public void cleanupPlayer(Player player) {
        if (player == null) {
            return;
        }

        ActiveScale selfState = activeByTarget.get(player.getUniqueId());
        if (selfState != null) {
            clearState(selfState, false, false, true);
        }

        UUID targetId = activeTargetByCaster.get(player.getUniqueId());
        if (targetId != null) {
            ActiveScale castState = activeByTarget.get(targetId);
            if (castState != null) {
                clearState(castState, false, false, true);
            } else {
                activeTargetByCaster.remove(player.getUniqueId(), targetId);
            }
        }
    }

    public void shutdown() {
        if (cleanupTask != null) {
            try {
                cleanupTask.cancel();
            } catch (Exception ignored) {
                // ignore
            }
        }

        List<ActiveScale> snapshot = new ArrayList<>(activeByTarget.values());
        for (ActiveScale state : snapshot) {
            clearState(state, false, false, true);
        }

        activeByTarget.clear();
        activeTargetByCaster.clear();
    }

    private void useSelfShrink(Player player,
                               LegendaryWeaponDefinition definition,
                               LegendaryWeaponDefinition.AbilityDefinition ability) {
        double targetScale = clampScale(scaleFromHeight(ability.getSelfHeightBlocks()));
        if (targetScale <= 0.0D) {
            player.sendMessage("§cВ config.yml у Shrink Ray некорректно настроен self-height-blocks.");
            return;
        }

        if (!applyScaleState(player, player, definition.getId(), targetScale, ability, ScaleMode.SELF_SMALL)) {
            player.sendMessage("§cНе удалось активировать уменьшение.");
            return;
        }

        cooldownManager.setCooldown(player, definition.getId(), ability.getCooldownTicks());
        player.sendMessage("§b§l[Shrink Ray] §fТы уменьшился до §e"
                + formatDouble(ability.getSelfHeightBlocks()) + "§f блока в высоту.");
    }

    private void fireGrowthRay(Player caster,
                               LegendaryWeaponDefinition definition,
                               LegendaryWeaponDefinition.AbilityDefinition ability) {
        Player target = findFirstHitPlayer(caster, ability);
        cooldownManager.setCooldown(caster, definition.getId(), ability.getCooldownTicks());

        if (target == null) {
            caster.sendActionBar("§7Shrink Ray: луч не нашёл цель.");
            return;
        }

        if (activeByTarget.containsKey(target.getUniqueId())) {
            caster.sendMessage("§cЭта цель уже находится под действием Shrink Ray.");
            return;
        }

        double targetScale = clampScale(scaleFromHeight(ability.getTargetHeightBlocks()));
        if (!hasClearanceForScale(target, targetScale)) {
            caster.sendMessage("§cЦель стоит под потолком — увеличить её сейчас нельзя.");
            return;
        }

        if (!applyScaleState(caster, target, definition.getId(), targetScale, ability, ScaleMode.TARGET_BIG)) {
            caster.sendMessage("§cНе удалось применить Shrink Ray к цели.");
            return;
        }

        caster.sendMessage("§b§l[Shrink Ray] §fТы увеличил игрока §e" + target.getName()
                + "§f до §e" + formatDouble(ability.getTargetHeightBlocks()) + "§f блоков в высоту.");
        target.sendMessage("§b§l[Shrink Ray] §fТвой размер увеличен до §e"
                + formatDouble(ability.getTargetHeightBlocks()) + "§f блоков на несколько секунд.");
    }

    private boolean applyScaleState(Player caster,
                                    Player target,
                                    String weaponId,
                                    double newScale,
                                    LegendaryWeaponDefinition.AbilityDefinition ability,
                                    ScaleMode mode) {
        if (caster == null || target == null) {
            return false;
        }
        if (activeTargetByCaster.containsKey(caster.getUniqueId())) {
            return false;
        }
        if (activeByTarget.containsKey(target.getUniqueId())) {
            return false;
        }

        Effect previousSpeed = cloneEffect(target, Effect.SPEED);
        Effect previousSlowness = cloneEffect(target, Effect.SLOWNESS);
        long now = System.currentTimeMillis();

        ActiveScale state = new ActiveScale(
                target.getUniqueId(),
                caster.getUniqueId(),
                weaponId,
                target.getLevel().getName(),
                caster.getLevel().getName(),
                safeScale(target),
                newScale,
                mode,
                now,
                now + (ability.getDurationTicks() * 50L),
                previousSpeed,
                previousSlowness,
                ability.isSelfSpeedEffect(),
                ability.getSelfSpeedAmplifier(),
                ability.isTargetSlownessEffect(),
                ability.getTargetSlownessAmplifier()
        );

        activeByTarget.put(target.getUniqueId(), state);
        activeTargetByCaster.put(caster.getUniqueId(), target.getUniqueId());

        applyTags(target, mode);
        applyScale(target, newScale);
        applyPotionState(target, state, ability.getDurationTicks());
        return true;
    }

    private Player findFirstHitPlayer(Player caster, LegendaryWeaponDefinition.AbilityDefinition ability) {
        Vector3 origin = caster.add(0, caster.getEyeHeight(), 0);
        Vector3 direction = caster.getDirectionVector();

        double length = Math.sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z);
        if (length <= 0.0001D) {
            return null;
        }

        double dx = direction.x / length;
        double dy = direction.y / length;
        double dz = direction.z / length;
        double step = Math.max(0.20D, ability.getRayStep());
        double range = Math.max(2.0D, ability.getRayRange());

        for (double traveled = 0.0D; traveled <= range; traveled += step) {
            Vector3 point = new Vector3(
                    origin.x + (dx * traveled),
                    origin.y + (dy * traveled),
                    origin.z + (dz * traveled)
            );

            if (traveled > 0.30D) {
                Block block = caster.getLevel().getBlock(point);
                if (block != null && !block.canPassThrough()) {
                    return null;
                }
            }

            Player hit = findPlayerAtPoint(caster, point);
            if (hit != null) {
                return hit;
            }
        }

        return null;
    }

    private Player findPlayerAtPoint(Player caster, Vector3 point) {
        for (Player candidate : plugin.getServer().getOnlinePlayers().values()) {
            if (candidate == null || candidate == caster) {
                continue;
            }
            if (!candidate.isOnline() || candidate.isClosed() || !candidate.isAlive()) {
                continue;
            }
            if (candidate.getLevel() != caster.getLevel()) {
                continue;
            }

            double candidateScale = Math.max(0.50D, safeScale(candidate));
            double height = Math.max(1.0D, candidateScale * VANILLA_PLAYER_HEIGHT);
            double radius = Math.max(0.45D, candidateScale * 0.35D);

            if (point.y < candidate.y || point.y > candidate.y + height) {
                continue;
            }

            double distX = candidate.x - point.x;
            double distZ = candidate.z - point.z;
            if ((distX * distX) + (distZ * distZ) <= radius * radius) {
                return candidate;
            }
        }
        return null;
    }

    private void applyPotionState(Player target, ActiveScale state, int durationTicks) {
        target.removeEffect(Effect.SPEED);
        target.removeEffect(Effect.SLOWNESS);

        if (state.mode == ScaleMode.SELF_SMALL && state.selfSpeedEffect) {
            Effect effect = Effect.getEffect(Effect.SPEED);
            if (effect != null) {
                effect.setDuration(durationTicks + 5)
                        .setAmplifier(Math.max(0, state.selfSpeedAmplifier))
                        .setVisible(false)
                        .setAmbient(false);
                target.addEffect(effect);
            }
        } else if (state.mode == ScaleMode.TARGET_BIG && state.targetSlownessEffect) {
            Effect effect = Effect.getEffect(Effect.SLOWNESS);
            if (effect != null) {
                effect.setDuration(durationTicks + 5)
                        .setAmplifier(Math.max(0, state.targetSlownessAmplifier))
                        .setVisible(false)
                        .setAmbient(false);
                target.addEffect(effect);
            }
        }
    }

    private void startCleanupTask() {
        cleanupTask = new NukkitRunnable() {
            @Override
            public void run() {
                sweepStates();
            }
        };
        cleanupTask.runTaskTimer(plugin, 10, 10);
    }

    private void sweepStates() {
        if (activeByTarget.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        List<ActiveScale> snapshot = new ArrayList<>(activeByTarget.values());
        for (ActiveScale state : snapshot) {
            Player target = getOnlinePlayer(state.targetId);
            Player caster = getOnlinePlayer(state.casterId);

            if (target == null || !target.isOnline() || target.isClosed()) {
                clearState(state, false, false, true);
                continue;
            }
            if (caster == null || !caster.isOnline() || caster.isClosed()) {
                clearState(state, false, false, true);
                continue;
            }
            if (!target.isAlive() || !caster.isAlive()) {
                clearState(state, false, false, true);
                continue;
            }
            if (!state.targetWorld.equalsIgnoreCase(target.getLevel().getName())) {
                clearState(state, false, false, true);
                continue;
            }
            if (!state.casterWorld.equalsIgnoreCase(caster.getLevel().getName())) {
                clearState(state, false, false, true);
                continue;
            }
            if (target.getLevel() != caster.getLevel()) {
                clearState(state, false, false, true);
                continue;
            }
            if (now >= state.expiresAtMillis) {
                clearState(state, true, true, false);
            }
        }
    }

    private void clearState(ActiveScale state,
                            boolean notifyCaster,
                            boolean notifyTarget,
                            boolean forceRestore) {
        if (state == null) {
            return;
        }

        Player target = getOnlinePlayer(state.targetId);
        Player caster = getOnlinePlayer(state.casterId);
        long now = System.currentTimeMillis();

        if (!forceRestore && target != null && target.isOnline() && !target.isClosed()) {
            if (!hasClearanceForScale(target, state.previousScale)) {
                state.expiresAtMillis = now + 500L;
                if (now - state.lastNoSpaceNoticeMillis >= 1000L) {
                    target.sendActionBar("§eShrink Ray ждёт, пока сверху появится место...");
                    state.lastNoSpaceNoticeMillis = now;
                }
                return;
            }
        }

        activeByTarget.remove(state.targetId, state);
        activeTargetByCaster.remove(state.casterId, state.targetId);

        if (target != null && target.isOnline() && !target.isClosed()) {
            restoreTarget(target, state, now);
        }

        if (notifyCaster && caster != null && caster.isOnline()) {
            if (state.mode == ScaleMode.SELF_SMALL) {
                caster.sendMessage("§b§l[Shrink Ray] §7Ты снова обычного размера.");
            } else {
                caster.sendMessage("§b§l[Shrink Ray] §7Эффект увеличения цели закончился.");
            }
        }

        if (notifyTarget && target != null && target.isOnline()) {
            if (state.mode == ScaleMode.TARGET_BIG) {
                target.sendMessage("§b§l[Shrink Ray] §7Твой размер вернулся к обычному.");
            }
        }
    }

    private void restoreTarget(Player target, ActiveScale state, long now) {
        target.removeTag(TAG_ACTIVE);
        target.removeTag(TAG_SMALL);
        target.removeTag(TAG_BIG);

        applyScale(target, state.previousScale);
        target.removeEffect(Effect.SPEED);
        target.removeEffect(Effect.SLOWNESS);

        Effect speed = restoreEffect(state.previousSpeed, now - state.startedAtMillis);
        Effect slowness = restoreEffect(state.previousSlowness, now - state.startedAtMillis);

        if (speed != null) {
            target.addEffect(speed);
        }
        if (slowness != null) {
            target.addEffect(slowness);
        }
    }

    private boolean hasClearanceForScale(Player player, double scale) {
        if (player == null || player.getLevel() == null) {
            return false;
        }

        double height = Math.max(1.0D, clampScale(scale) * VANILLA_PLAYER_HEIGHT);
        double radius = Math.max(0.30D, clampScale(scale) * 0.30D);
        double[] offsets = new double[] {0.0D, radius, -radius};

        for (double y = 0.10D; y <= height; y += 0.45D) {
            for (double ox : offsets) {
                for (double oz : offsets) {
                    Block block = player.getLevel().getBlock(new Vector3(player.x + ox, player.y + y, player.z + oz));
                    if (block != null && !block.canPassThrough()) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private void applyTags(Player target, ScaleMode mode) {
        target.addTag(TAG_ACTIVE);
        target.removeTag(TAG_SMALL);
        target.removeTag(TAG_BIG);
        if (mode == ScaleMode.SELF_SMALL) {
            target.addTag(TAG_SMALL);
        } else {
            target.addTag(TAG_BIG);
        }
    }

    private void applyScale(Player player, double scale) {
        double clampedScale = clampScale(scale);
        player.setScale((float) clampedScale);
        invokeOptional(player, "recalculateBoundingBox");
        invokeOptional(player, "updateMovement");
        invokeOptional(player, "scheduleUpdate");
    }

    private void invokeOptional(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.invoke(target);
        } catch (Throwable ignored) {
            // optional across forks
        }
    }

    private Player getOnlinePlayer(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            if (uuid.equals(player.getUniqueId())) {
                return player;
            }
        }
        return null;
    }

    private Effect cloneEffect(Player player, int effectId) {
        if (player == null || !player.hasEffect(effectId)) {
            return null;
        }
        Effect effect = player.getEffect(effectId);
        return effect == null ? null : effect.clone();
    }

    private Effect restoreEffect(Effect effect, long elapsedMillis) {
        if (effect == null) {
            return null;
        }

        Effect restored = effect.clone();
        int elapsedTicks = (int) Math.ceil(elapsedMillis / 50.0D);
        int remaining = restored.getDuration() - elapsedTicks;
        if (remaining <= 0) {
            return null;
        }

        restored.setDuration(remaining);
        return restored;
    }

    private double safeScale(Player player) {
        try {
            return player.getScale();
        } catch (Throwable ignored) {
            return 1.0D;
        }
    }

    private double scaleFromHeight(double heightBlocks) {
        if (heightBlocks <= 0.0D) {
            return 1.0D;
        }
        return heightBlocks / VANILLA_PLAYER_HEIGHT;
    }

    private double clampScale(double scale) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    }

    private String formatDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private enum ScaleMode {
        SELF_SMALL,
        TARGET_BIG
    }

    private static final class ActiveScale {
        private final UUID targetId;
        private final UUID casterId;
        private final String weaponId;
        private final String targetWorld;
        private final String casterWorld;
        private final double previousScale;
        private final double appliedScale;
        private final ScaleMode mode;
        private final long startedAtMillis;
        private final Effect previousSpeed;
        private final Effect previousSlowness;
        private final boolean selfSpeedEffect;
        private final int selfSpeedAmplifier;
        private final boolean targetSlownessEffect;
        private final int targetSlownessAmplifier;
        private long expiresAtMillis;
        private long lastNoSpaceNoticeMillis;

        private ActiveScale(UUID targetId,
                            UUID casterId,
                            String weaponId,
                            String targetWorld,
                            String casterWorld,
                            double previousScale,
                            double appliedScale,
                            ScaleMode mode,
                            long startedAtMillis,
                            long expiresAtMillis,
                            Effect previousSpeed,
                            Effect previousSlowness,
                            boolean selfSpeedEffect,
                            int selfSpeedAmplifier,
                            boolean targetSlownessEffect,
                            int targetSlownessAmplifier) {
            this.targetId = targetId;
            this.casterId = casterId;
            this.weaponId = weaponId;
            this.targetWorld = targetWorld;
            this.casterWorld = casterWorld;
            this.previousScale = previousScale;
            this.appliedScale = appliedScale;
            this.mode = mode;
            this.startedAtMillis = startedAtMillis;
            this.expiresAtMillis = expiresAtMillis;
            this.previousSpeed = previousSpeed;
            this.previousSlowness = previousSlowness;
            this.selfSpeedEffect = selfSpeedEffect;
            this.selfSpeedAmplifier = selfSpeedAmplifier;
            this.targetSlownessEffect = targetSlownessEffect;
            this.targetSlownessAmplifier = targetSlownessAmplifier;
            this.lastNoSpaceNoticeMillis = 0L;
        }
    }
}
