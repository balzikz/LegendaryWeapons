package com.legendary.manager;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.potion.Effect;
import cn.nukkit.scheduler.Task;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ShrinkRayManager {

    private final PluginBase plugin;
    private final CooldownManager cooldownManager;
    private final ItemManager itemManager;

    private final Map<UUID, ScaleState> scaledPlayers = new HashMap<>();
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Set<UUID> shrunkPlayers = new HashSet<>();
    private final Set<UUID> giantPlayers = new HashSet<>();

    private int cooldownTicks;
    private int selfDurationTicks;
    private int targetDurationTicks;
    private double selfScale;
    private double targetScale;
    private int selfSpeedAmplifier;
    private int targetSlownessAmplifier;
    private double rayRange;
    private boolean revertOnWorldChange;
    private boolean revertOnQuit;
    private boolean revertOnDeath;

    public ShrinkRayManager(PluginBase plugin, CooldownManager cooldownManager, ItemManager itemManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        this.itemManager = itemManager;
        loadConfig();
        startTickTask();
    }

    public void reload() {
        loadConfig();
    }

    private void loadConfig() {
        this.cooldownTicks = plugin.getConfig().getInt("weapons.shrink_ray.cooldown-ticks", 60);
        this.selfDurationTicks = plugin.getConfig().getInt("weapons.shrink_ray.self-duration-ticks", 100);
        this.targetDurationTicks = plugin.getConfig().getInt("weapons.shrink_ray.target-duration-ticks", 100);
        this.selfScale = plugin.getConfig().getDouble("weapons.shrink_ray.self-scale", 0.55D);
        this.targetScale = plugin.getConfig().getDouble("weapons.shrink_ray.target-scale", 2.2D);
        this.selfSpeedAmplifier = plugin.getConfig().getInt("weapons.shrink_ray.self-speed-amplifier", 1);
        this.targetSlownessAmplifier = plugin.getConfig().getInt("weapons.shrink_ray.target-slowness-amplifier", 1);
        this.rayRange = plugin.getConfig().getDouble("weapons.shrink_ray.ray-range", 18.0D);
        this.revertOnWorldChange = plugin.getConfig().getBoolean("weapons.shrink_ray.revert-on-world-change", true);
        this.revertOnQuit = plugin.getConfig().getBoolean("weapons.shrink_ray.revert-on-quit", true);
        this.revertOnDeath = plugin.getConfig().getBoolean("weapons.shrink_ray.revert-on-death", true);

        if (selfScale <= 0.1D) selfScale = 0.1D;
        if (targetScale < 1.0D) targetScale = 1.0D;
        if (rayRange < 3.0D) rayRange = 3.0D;
    }

    public boolean handleUse(Player player, Item item) {
        if (!itemManager.isLegendary(item, "shrink_ray")) {
            return false;
        }

        if (cooldownManager.isOnCooldown(player, "shrink_ray")) {
            int left = cooldownManager.getRemaining(player, "shrink_ray");
            player.sendMessage("§cShrink Ray перезаряжается еще " + left + " тиков.");
            return true;
        }

        if (isScaled(player)) {
            player.sendMessage("§cПока эффект Shrink Ray активен, вторую способность использовать нельзя.");
            return true;
        }

        if (player.isSneaking()) {
            activateSelfShrink(player);
            return true;
        }

        Player target = findTargetPlayer(player, rayRange);
        if (target == null) {
            player.sendMessage("§cЦель не найдена.");
            return true;
        }

        if (isScaled(target)) {
            player.sendMessage("§cНа цели уже активен эффект изменения размера.");
            return true;
        }

        if (!canGrowAtLocation(target)) {
            player.sendMessage("§cЦель нельзя увеличить здесь: недостаточно места.");
            return true;
        }

        activateTargetGrow(player, target);
        return true;
    }

    private void activateSelfShrink(Player player) {
        ScaleState state = new ScaleState(
                player.getUniqueId(),
                safeGetScale(player),
                currentTick() + selfDurationTicks,
                true,
                selfSpeedAmplifier
        );

        applyScale(player, selfScale);
        applySpeed(player, selfSpeedAmplifier);
        scaledPlayers.put(player.getUniqueId(), state);
        markShrunk(player);
        cooldownManager.setCooldown(player, "shrink_ray", cooldownTicks);

        player.sendMessage("§aТы уменьшился.");
    }

    private void activateTargetGrow(Player owner, Player target) {
        ScaleState state = new ScaleState(
                target.getUniqueId(),
                safeGetScale(target),
                currentTick() + targetDurationTicks,
                false,
                targetSlownessAmplifier
        );

        applyScale(target, targetScale);
        applySlowness(target, targetSlownessAmplifier);
        scaledPlayers.put(target.getUniqueId(), state);
        markGiant(target);
        cooldownManager.setCooldown(owner, "shrink_ray", cooldownTicks);

        owner.sendMessage("§aТы увеличил игрока §e" + target.getName() + "§a.");
        target.sendMessage("§cТебя увеличили с помощью Shrink Ray.");
    }

    private Player findTargetPlayer(Player player, double range) {
        Vector3 direction = player.getDirectionVector().normalize();
        Location eye = player.add(0, player.getEyeHeight(), 0);

        Player best = null;
        double bestDistance = Double.MAX_VALUE;

        for (Player target : Server.getInstance().getOnlinePlayers().values()) {
            if (target == null || !target.isOnline() || target == player) {
                continue;
            }

            if (target.level != player.level) {
                continue;
            }

            double distance = target.distance(player);
            if (distance > range) {
                continue;
            }

            if (!hasLineOfSight(player, target, range)) {
                continue;
            }

            Vector3 toTarget = target.add(0, target.getEyeHeight() * 0.5, 0).subtract(eye.x, eye.y, eye.z);
            double dot = direction.dot(toTarget.normalize());

            if (dot < 0.965D) {
                continue;
            }

            if (distance < bestDistance) {
                bestDistance = distance;
                best = target;
            }
        }

        return best;
    }

    private boolean hasLineOfSight(Player from, Player to, double range) {
        Vector3 start = from.add(0, from.getEyeHeight(), 0);
        Vector3 end = to.add(0, to.getEyeHeight() * 0.5, 0);
        Vector3 diff = end.subtract(start.x, start.y, start.z);

        double length = diff.length();
        if (length <= 0.0D) {
            return false;
        }

        if (length > range) {
            return false;
        }

        Vector3 step = diff.normalize().multiply(0.35D);
        Vector3 current = start.clone();

        for (double walked = 0; walked < length; walked += 0.35D) {
            current = current.add(step);
            Block block = from.level.getBlock(current);
            if (block != null && block.getId() != 0 && block.isSolid()) {
                return false;
            }
        }

        return true;
    }

    private boolean canGrowAtLocation(Player player) {
        Position pos = player.getPosition();
        int baseX = pos.getFloorX();
        int baseY = pos.getFloorY();
        int baseZ = pos.getFloorZ();

        for (int y = 0; y < 4; y++) {
            Block block = player.level.getBlock(new Vector3(baseX, baseY + y, baseZ));
            if (block != null && block.getId() != 0 && block.isSolid()) {
                return false;
            }
        }
        return true;
    }

    private void startTickTask() {
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, new Task() {
            @Override
            public void onRun(int currentTick) {
                tickStates(currentTick);
            }
        }, 5);
    }

    private void tickStates(int tick) {
        Iterator<Map.Entry<UUID, ScaleState>> iterator = scaledPlayers.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, ScaleState> entry = iterator.next();
            UUID uuid = entry.getKey();
            ScaleState state = entry.getValue();

            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                iterator.remove();
                activePlayers.remove(uuid);
                shrunkPlayers.remove(uuid);
                giantPlayers.remove(uuid);
                continue;
            }

            if (tick < state.expireTick) {
                continue;
            }

            if (state.shrunk && !canReturnToNormal(player)) {
                state.expireTick = tick + 10;
                continue;
            }

            resetPlayer(player);
            iterator.remove();
        }
    }

    private boolean canReturnToNormal(Player player) {
        Position pos = player.getPosition();
        int baseX = pos.getFloorX();
        int baseY = pos.getFloorY();
        int baseZ = pos.getFloorZ();

        for (int y = 0; y < 2; y++) {
            Block block = player.level.getBlock(new Vector3(baseX, baseY + y, baseZ));
            if (block != null && block.getId() != 0 && block.isSolid()) {
                return false;
            }
        }
        return true;
    }

    public void handleQuit(Player player) {
        if (!revertOnQuit) {
            scaledPlayers.remove(player.getUniqueId());
            markNormal(player);
            return;
        }
        forceReset(player);
    }

    public void handleDeath(Player player) {
        if (!revertOnDeath) {
            scaledPlayers.remove(player.getUniqueId());
            markNormal(player);
            return;
        }
        forceReset(player);
    }

    public void handleTeleport(Player player, Position from, Position to) {
        if (!revertOnWorldChange) {
            return;
        }

        if (from == null || to == null) {
            return;
        }

        if (from.level != to.level) {
            forceReset(player);
        }
    }

    public void handleDisable() {
        for (Player player : plugin.getServer().getOnlinePlayers().values()) {
            if (player != null && isScaled(player)) {
                forceReset(player);
            }
        }
        scaledPlayers.clear();
        activePlayers.clear();
        shrunkPlayers.clear();
        giantPlayers.clear();
    }

    public boolean isScaled(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    public boolean isShrunk(Player player) {
        return shrunkPlayers.contains(player.getUniqueId());
    }

    public boolean isGiant(Player player) {
        return giantPlayers.contains(player.getUniqueId());
    }

    private void markNormal(Player target) {
        UUID id = target.getUniqueId();
        activePlayers.remove(id);
        shrunkPlayers.remove(id);
        giantPlayers.remove(id);
    }

    private void markShrunk(Player target) {
        UUID id = target.getUniqueId();
        activePlayers.add(id);
        shrunkPlayers.add(id);
        giantPlayers.remove(id);
    }

    private void markGiant(Player target) {
        UUID id = target.getUniqueId();
        activePlayers.add(id);
        shrunkPlayers.remove(id);
        giantPlayers.add(id);
    }

    public void forceReset(Player player) {
        if (player == null) {
            return;
        }

        resetPlayer(player);
        scaledPlayers.remove(player.getUniqueId());
    }

    private void resetPlayer(Player player) {
        ScaleState state = scaledPlayers.get(player.getUniqueId());
        double restoreScale = 1.0D;

        if (state != null) {
            restoreScale = state.originalScale;
        }

        applyScale(player, restoreScale);
        clearEffects(player);
        markNormal(player);
        player.sendMessage("§eТвой размер восстановлен.");
    }

    private void clearEffects(Player player) {
        try {
            player.removeEffect(Effect.SPEED);
        } catch (Throwable ignored) {
        }

        try {
            player.removeEffect(Effect.SLOWNESS);
        } catch (Throwable ignored) {
        }
    }

    private void applySpeed(Player player, int amplifier) {
        clearEffects(player);

        Effect effect = Effect.getEffect(Effect.SPEED);
        if (effect == null) {
            return;
        }

        effect.setDuration(Math.max(40, selfDurationTicks + 20));
        effect.setAmplifier(Math.max(0, amplifier));
        effect.setVisible(false);
        player.addEffect(effect);
    }

    private void applySlowness(Player player, int amplifier) {
        clearEffects(player);

        Effect effect = Effect.getEffect(Effect.SLOWNESS);
        if (effect == null) {
            return;
        }

        effect.setDuration(Math.max(40, targetDurationTicks + 20));
        effect.setAmplifier(Math.max(0, amplifier));
        effect.setVisible(false);
        player.addEffect(effect);
    }

    private void applyScale(Player player, double scale) {
        boolean success = false;

        try {
            Method setScale = Entity.class.getMethod("setScale", float.class);
            setScale.invoke(player, (float) scale);
            success = true;
        } catch (Throwable ignored) {
        }

        if (!success) {
            try {
                Method setScale = Entity.class.getMethod("setScale", double.class);
                setScale.invoke(player, scale);
                success = true;
            } catch (Throwable ignored) {
            }
        }

        if (!success) {
            try {
                Method setScale = player.getClass().getMethod("setScale", float.class);
                setScale.invoke(player, (float) scale);
                success = true;
            } catch (Throwable ignored) {
            }
        }

        if (!success) {
            try {
                Method setScale = player.getClass().getMethod("setScale", double.class);
                setScale.invoke(player, scale);
                success = true;
            } catch (Throwable ignored) {
            }
        }

        recalcBoundingBox(player);
    }

    private double safeGetScale(Player player) {
        try {
            Method getScale = Entity.class.getMethod("getScale");
            Object value = getScale.invoke(player);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        } catch (Throwable ignored) {
        }

        try {
            Method getScale = player.getClass().getMethod("getScale");
            Object value = getScale.invoke(player);
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
        } catch (Throwable ignored) {
        }

        return 1.0D;
    }

    private void recalcBoundingBox(Player player) {
        try {
            Method recalc = player.getClass().getMethod("reCalcOffsetBoundingBox");
            recalc.invoke(player);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Method recalc = player.getClass().getMethod("recalculateBoundingBox");
            recalc.invoke(player);
            return;
        } catch (Throwable ignored) {
        }

        try {
            Method setBoundingBox = Entity.class.getDeclaredMethod("setBoundingBox", AxisAlignedBB.class);
            setBoundingBox.setAccessible(true);

            double width = 0.6D * safeGetScale(player);
            double height = 1.8D * safeGetScale(player);

            AxisAlignedBB bb = new AxisAlignedBB(
                    player.x - width / 2.0D,
                    player.y,
                    player.z - width / 2.0D,
                    player.x + width / 2.0D,
                    player.y + height,
                    player.z + width / 2.0D
            );
            setBoundingBox.invoke(player, bb);
        } catch (Throwable ignored) {
        }
    }

    private int currentTick() {
        return (int) (System.currentTimeMillis() / 50L);
    }

    private static class ScaleState {
        private final UUID playerId;
        private final double originalScale;
        private int expireTick;
        private final boolean shrunk;
        private final int amplifier;

        private ScaleState(UUID playerId, double originalScale, int expireTick, boolean shrunk, int amplifier) {
            this.playerId = playerId;
            this.originalScale = originalScale;
            this.expireTick = expireTick;
            this.shrunk = shrunk;
            this.amplifier = amplifier;
        }
    }
}
