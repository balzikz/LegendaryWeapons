package com.legendary.manager;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.ShapedRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.scheduler.NukkitRunnable;
import cn.nukkit.utils.Config;
import com.legendary.LegendaryItems;
import com.legendary.model.LegendaryWeaponDefinition;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LegendaryCraftManager {

    private final LegendaryItems plugin;
    private final ItemManager itemManager;
    private final Config craftedData;

    public LegendaryCraftManager(LegendaryItems plugin, ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.craftedData = new Config(new File(plugin.getDataFolder(), "crafted.yml"), Config.YAML);
    }

    public void registerRecipes() {
        for (LegendaryWeaponDefinition definition : itemManager.getDefinitions().values()) {
            if (!definition.isEnabled() || definition.getRecipe() == null || !definition.getRecipe().isEnabled()) {
                continue;
            }

            List<String> shape = definition.getRecipe().getShape();
            if (shape.isEmpty()) {
                plugin.getLogger().warning("У оружия " + definition.getId() + " пустой recipe.shape, рецепт не зарегистрирован.");
                continue;
            }

            Item result = itemManager.createItem(definition.getId());
            if (result == null) {
                plugin.getLogger().warning("Не удалось создать result item для рецепта " + definition.getId());
                continue;
            }

            Map<Character, Item> ingredients = new LinkedHashMap<>();
            boolean invalid = false;
            for (Map.Entry<Character, String> entry : definition.getRecipe().getIngredients().entrySet()) {
                Item ingredient;
                try {
                    ingredient = Item.fromString(entry.getValue());
                } catch (Exception e) {
                    ingredient = null;
                }

                if (ingredient == null || ingredient.isNull()) {
                    plugin.getLogger().warning("Некорректный ингредиент '" + entry.getValue() + "' в рецепте " + definition.getId());
                    invalid = true;
                    break;
                }

                ingredient.setCount(1);
                ingredients.put(entry.getKey(), ingredient);
            }

            if (invalid) {
                continue;
            }

            try {
                ShapedRecipe recipe = new ShapedRecipe(
                        "legendary_" + definition.getId(),
                        0,
                        result,
                        shape.toArray(new String[0]),
                        ingredients,
                        Collections.<Item>emptyList()
                );
                plugin.getServer().addRecipe(recipe);
                plugin.getLogger().info("§aЗарегистрирован рецепт: " + definition.getId());
            } catch (Exception e) {
                plugin.getLogger().error("§cНе удалось зарегистрировать рецепт для " + definition.getId() + ": " + e.getMessage());
            }
        }
    }

    public boolean canCraftInWorld(String worldName, String legendaryId) {
        LegendaryWeaponDefinition definition = itemManager.getDefinition(legendaryId);
        if (definition == null) {
            return true;
        }
        if (!definition.isOncePerWorld()) {
            return true;
        }
        return !isCraftedInWorld(worldName, legendaryId);
    }

    public boolean isCraftedInWorld(String worldName, String legendaryId) {
        List<String> crafted = new ArrayList<>(craftedData.getStringList("crafted." + worldName));
        for (String entry : crafted) {
            if (entry.equalsIgnoreCase(legendaryId)) {
                return true;
            }
        }
        return false;
    }

    public void markCrafted(String worldName, String legendaryId) {
        List<String> crafted = new ArrayList<>(craftedData.getStringList("crafted." + worldName));
        String normalized = legendaryId.toLowerCase();
        if (!crafted.contains(normalized)) {
            crafted.add(normalized);
            craftedData.set("crafted." + worldName, crafted);
            craftedData.save();
        }
    }

    public boolean handleCraft(Player player, String legendaryId) {
        LegendaryWeaponDefinition definition = itemManager.getDefinition(legendaryId);
        if (definition == null) {
            return true;
        }

        String worldName = player.getLevel().getName();
        if (!canCraftInWorld(worldName, legendaryId)) {
            player.sendMessage("§cЭто легендарное оружие уже было скрафчено в мире §6" + worldName + "§c.");
            return false;
        }

        markCrafted(worldName, legendaryId);
        scheduleWorldBinding(player, definition, worldName);
        announceCraft(player, definition, worldName);
        return true;
    }

    private void announceCraft(Player crafter, LegendaryWeaponDefinition definition, String worldName) {
        String message = "§6§l[Legendary] §fВ мире §e" + worldName + "§f создано оружие §r"
                + definition.getDisplayName() + "§f. Владелец: §e" + crafter.getName();

        Server server = plugin.getServer();
        for (Player online : server.getOnlinePlayers().values()) {
            if (online.getLevel() != null && worldName.equalsIgnoreCase(online.getLevel().getName())) {
                online.sendMessage(message);
            }
        }
        plugin.getLogger().info("[Legendary] " + crafter.getName() + " скрафтил " + definition.getId() + " в мире " + worldName);
    }

    private void scheduleWorldBinding(final Player player, final LegendaryWeaponDefinition definition, final String worldName) {
        if (definition.getAbility() == null || !definition.getAbility().isBindToCraftWorld()) {
            return;
        }

        new NukkitRunnable() {
            @Override
            public void run() {
                if (!bindWorldTagInInventory(player, definition.getId(), worldName)) {
                    new NukkitRunnable() {
                        @Override
                        public void run() {
                            bindWorldTagInInventory(player, definition.getId(), worldName);
                        }
                    }.runTaskLater(plugin, 5);
                }
            }
        }.runTaskLater(plugin, 1);
    }

    private boolean bindWorldTagInInventory(Player player, String legendaryId, String worldName) {
        if (player == null || !player.isOnline() || player.isClosed()) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            Item item = inventory.getItem(slot);
            if (!itemManager.isLegendary(item, legendaryId)) {
                continue;
            }
            if (itemManager.getBoundWorld(item) != null) {
                return true;
            }

            Item updated = item.clone();
            itemManager.bindItemToWorld(updated, worldName);
            inventory.setItem(slot, updated);
            player.sendMessage("§8[Legendary] §7Предмет §r" + updated.getCustomName() + "§7 привязан к миру §e" + worldName + "§7.");
            return true;
        }

        return false;
    }
}
