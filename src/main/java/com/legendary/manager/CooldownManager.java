package com.legendary.manager;

import cn.nukkit.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {

    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public void setCooldown(Player player, String key, int ticks) {
        cooldowns
                .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(key.toLowerCase(), System.currentTimeMillis() + (ticks * 50L));
    }

    public boolean isOnCooldown(Player player, String key) {
        return getRemainingTicks(player, key) > 0;
    }

    public long getRemainingTicks(Player player, String key) {
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return 0L;
        }

        Long expiresAt = playerCooldowns.get(key.toLowerCase());
        if (expiresAt == null) {
            return 0L;
        }

        long diff = expiresAt - System.currentTimeMillis();
        if (diff <= 0L) {
            playerCooldowns.remove(key.toLowerCase());
            return 0L;
        }

        return (long) Math.ceil(diff / 50.0D);
    }

    public void clear(Player player) {
        cooldowns.remove(player.getUniqueId());
    }
}
