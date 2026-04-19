package com.legendary.listeners;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
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
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        Player player = (Player) event.getEntity();
        if (plugin.getHarpoonManager().hasFallImmunity(player)) {
            event.setCancelled();
            plugin.getHarpoonManager().consumeFallImmunity(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getHarpoonManager().cleanupPlayer(event.getPlayer());
        plugin.getCooldownManager().clear(event.getPlayer());
    }
}
