package com.legendary.manager;

import cn.nukkit.item.Item;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import com.legendary.LegendaryItems;
import com.legendary.model.LegendaryWeaponDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ItemManager {

    private final LegendaryItems plugin;
    private final Map<String, LegendaryWeaponDefinition> definitions = new LinkedHashMap<>();

    public ItemManager(LegendaryItems plugin) {
        this.plugin = plugin;
    }

    public void loadDefinitions() {
        definitions.clear();

        Config config = plugin.getConfig();
        ConfigSection weaponsSection = config.getSection("weapons");
        if (weaponsSection == null) {
            plugin.getLogger().warning("В config.yml нет секции weapons.");
            return;
        }

        for (String id : weaponsSection.getKeys(false)) {
            String path = "weapons." + id;

            boolean enabled = config.getBoolean(path + ".enabled", true);
            boolean oncePerWorld = config.getBoolean(path + ".once-per-world", true);
            String type = config.getString(path + ".type", id).toUpperCase();
            String defaultMaterial = "HARPOON".equalsIgnoreCase(type)
                    ? "minecraft:fishing_rod"
                    : "minecraft:totem_of_undying";
            String material = config.getString(path + ".item.material", defaultMaterial);
            String displayName = config.getString(path + ".item.name", "§6Легендарное оружие");
            List<String> lore = new ArrayList<>(config.getStringList(path + ".item.lore"));
            boolean unbreakable = config.getBoolean(path + ".item.unbreakable", true);

            List<String> shape = new ArrayList<>(config.getStringList(path + ".recipe.shape"));
            ConfigSection ingredientsSection = config.getSection(path + ".recipe.ingredients");
            Map<Character, String> ingredients = new LinkedHashMap<>();
            if (ingredientsSection != null) {
                for (String symbol : ingredientsSection.getKeys(false)) {
                    if (symbol.length() != 1) {
                        plugin.getLogger().warning("Символ ингредиента должен быть одной буквой: " + path + ".recipe.ingredients." + symbol);
                        continue;
                    }
                    ingredients.put(symbol.charAt(0), ingredientsSection.getString(symbol));
                }
            }

            LegendaryWeaponDefinition.RecipeDefinition recipe = new LegendaryWeaponDefinition.RecipeDefinition(
                    config.getBoolean(path + ".recipe.enabled", true),
                    shape,
                    ingredients
            );

            List<String> mirroredCauses = new ArrayList<>(config.getStringList(path + ".ability.mirrored-causes"));
            if (mirroredCauses.isEmpty() && ("VOODOO_DOLL".equalsIgnoreCase(type) || "VOODOO".equalsIgnoreCase(type))) {
                mirroredCauses.addAll(Arrays.asList(
                        "ENTITY_ATTACK",
                        "PROJECTILE",
                        "FIRE",
                        "FIRE_TICK",
                        "LAVA",
                        "BLOCK_EXPLOSION",
                        "ENTITY_EXPLOSION",
                        "FALL",
                        "CONTACT",
                        "DROWNING",
                        "MAGIC",
                        "POISON",
                        "SUFFOCATION",
                        "THORNS",
                        "VOID",
                        "CUSTOM"
                ));
            }

            LegendaryWeaponDefinition.AbilityDefinition ability = new LegendaryWeaponDefinition.AbilityDefinition(
                    config.getInt(path + ".ability.cooldown-ticks", 60),
                    config.getDouble(path + ".ability.projectile-speed", 1.8),
                    config.getDouble(path + ".ability.pull-speed", 1.15),
                    config.getInt(path + ".ability.max-flight-ticks", 40),
                    config.getInt(path + ".ability.max-pull-ticks", 80),
                    config.getDouble(path + ".ability.min-distance", 1.75),
                    config.getDouble(path + ".ability.vertical-cap", 0.95),
                    config.getDouble(path + ".ability.step-boost", 0.42),
                    config.getInt(path + ".ability.stuck-ticks", 14),
                    config.getInt(path + ".ability.fall-immunity-ticks", 20),
                    config.getBoolean(path + ".ability.pull-players", true),
                    config.getBoolean(path + ".ability.pull-mobs", true),
                    config.getInt(path + ".ability.duration-ticks", 120),
                    config.getDouble(path + ".ability.bonus-damage-percent", 30.0D),
                    config.getBoolean(path + ".ability.cancel-hit-damage", true),
                    config.getBoolean(path + ".ability.require-same-world", true),
                    config.getBoolean(path + ".ability.break-on-owner-death", true),
                    config.getBoolean(path + ".ability.break-on-target-death", true),
                    config.getBoolean(path + ".ability.allow-retarget", true),
                    mirroredCauses
            );

            LegendaryWeaponDefinition definition = new LegendaryWeaponDefinition(
                    id.toLowerCase(),
                    enabled,
                    oncePerWorld,
                    type,
                    material,
                    displayName,
                    lore,
                    unbreakable,
                    recipe,
                    ability
            );

            definitions.put(definition.getId(), definition);
            plugin.getLogger().info("§aЗагружено оружие: " + definition.getId());
        }

        plugin.getLogger().info("§bВсего загружено легендарных оружий: " + definitions.size());
    }

    public LegendaryWeaponDefinition getDefinition(String id) {
        if (id == null) {
            return null;
        }
        return definitions.get(id.toLowerCase());
    }

    public Map<String, LegendaryWeaponDefinition> getDefinitions() {
        return Collections.unmodifiableMap(definitions);
    }

    public String getLegendaryId(Item item) {
        if (item == null || !item.hasCompoundTag()) {
            return null;
        }
        CompoundTag tag = item.getNamedTag();
        if (tag == null || !tag.contains("legendary_id")) {
            return null;
        }
        return tag.getString("legendary_id").toLowerCase();
    }

    public boolean isLegendary(Item item) {
        return getLegendaryId(item) != null;
    }

    public boolean isLegendary(Item item, String id) {
        String legendaryId = getLegendaryId(item);
        return legendaryId != null && legendaryId.equalsIgnoreCase(id);
    }

    public Item createItem(String id) {
        LegendaryWeaponDefinition definition = getDefinition(id);
        if (definition == null) {
            return null;
        }

        Item item;
        try {
            item = Item.fromString(definition.getMaterial());
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось создать предмет из material='" + definition.getMaterial() + "' для " + definition.getId());
            item = definition.isHarpoon() ? Item.get(346) : Item.get(450);
        }

        if (item == null || item.isNull()) {
            item = definition.isHarpoon() ? Item.get(346) : Item.get(450);
        }

        item.setCount(1);
        item.setCustomName(definition.getDisplayName());
        item.setLore(definition.getLore().toArray(new String[0]));

        CompoundTag tag = item.hasCompoundTag() ? item.getNamedTag() : new CompoundTag();
        tag.putBoolean("legendary_weapon", true);
        tag.putString("legendary_id", definition.getId());
        tag.putString("legendary_type", definition.getType());
        if (definition.isUnbreakable()) {
            tag.putBoolean("Unbreakable", true);
        }
        item.setNamedTag(tag);

        return item;
    }

    public String prettifyMaterial(String materialId) {
        if (materialId == null || materialId.trim().isEmpty()) {
            return "unknown";
        }

        String value = materialId.toLowerCase().replace("minecraft:", "").replace('_', ' ');
        String[] words = value.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }
}
