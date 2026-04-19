package com.legendary.listeners;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.item.Item;
import com.legendary.LegendaryItems;
import com.legendary.model.LegendaryWeaponDefinition;

public class LegendaryListener implements Listener {

    private final LegendaryItems plugin;

    public LegendaryListener(LegendaryItems plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
                && event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Item inHand = event.getPlayer().getInventory().getItemInHand();
        String legendaryId = plugin.getItemManager().getLegendaryId(inHand);
        if (legendaryId == null) {
            return;
        }

        LegendaryWeaponDefinition definition = plugin.getItemManager().getDefinition(legendaryId);
        if (definition == null || !definition.isEnabled()) {
            return;
        }

        if (definition.isHarpoon()) {
            event.setCancelled();
            plugin.getHarpoonManager().useHarpoon(event.getPlayer(), definition);
        }
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        plugin.getHarpoonManager().handleProjectileHit(event);
    }

    @EventHandler
    public void onProjectileDamage(EntityDamageByChildEntityEvent event) {
        plugin.getHarpoonManager().handleProjectileDamage(event);
    }

    @EventHandler
    public void onMelee(EntityDamageByEntityEvent event) {
        plugin.getVoodooManager().handleMarkHit(event);
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getRecipe() == null || event.getRecipe().getResult() == null) {
            return;
        }

        String legendaryId = plugin.getItemManager().getLegendaryId(event.getRecipe().getResult());
        if (legendaryId == null) {
            return;
        }

        if (!plugin.getCraftManager().handleCraft(event.getPlayer(), legendaryId)) {
            event.setCancelled();
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player
                && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            Player player = (Player) event.getEntity();
            if (plugin.getHarpoonManager().hasFallImmunity(player)) {
                event.setCancelled();
                plugin.getHarpoonManager().consumeFallImmunity(player);
            }
        }

        plugin.getVoodooManager().handleOwnerDamage(event);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom() == null || event.getTo() == null) {
            return;
        }
        if (event.getFrom().getLevel() == event.getTo().getLevel()) {
            return;
        }

        plugin.getHarpoonManager().cleanupPlayer(event.getPlayer());
        plugin.getVoodooManager().cleanupPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getHarpoonManager().cleanupPlayer(event.getPlayer());
        plugin.getVoodooManager().handleQuit(event);
        plugin.getCooldownManager().clear(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getHarpoonManager().cleanupPlayer(event.getEntity());
        plugin.getVoodooManager().handleDeath(event);
        plugin.getCooldownManager().clear(event.getEntity());
    }
}
