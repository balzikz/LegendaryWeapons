package com.legendary.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LegendaryWeaponDefinition {

    private final String id;
    private final boolean enabled;
    private final boolean oncePerWorld;
    private final String type;
    private final String material;
    private final String displayName;
    private final List<String> lore;
    private final boolean unbreakable;
    private final RecipeDefinition recipe;
    private final AbilityDefinition ability;

    public LegendaryWeaponDefinition(
            String id,
            boolean enabled,
            boolean oncePerWorld,
            String type,
            String material,
            String displayName,
            List<String> lore,
            boolean unbreakable,
            RecipeDefinition recipe,
            AbilityDefinition ability
    ) {
        this.id = id;
        this.enabled = enabled;
        this.oncePerWorld = oncePerWorld;
        this.type = type;
        this.material = material;
        this.displayName = displayName;
        this.lore = lore == null ? Collections.emptyList() : Collections.unmodifiableList(lore);
        this.unbreakable = unbreakable;
        this.recipe = recipe;
        this.ability = ability;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isOncePerWorld() {
        return oncePerWorld;
    }

    public String getType() {
        return type;
    }

    public String getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return lore;
    }

    public boolean isUnbreakable() {
        return unbreakable;
    }

    public RecipeDefinition getRecipe() {
        return recipe;
    }

    public AbilityDefinition getAbility() {
        return ability;
    }

    public boolean isHarpoon() {
        return "HARPOON".equalsIgnoreCase(type) || "GRAPPLING_HOOK".equalsIgnoreCase(type);
    }

    public static class RecipeDefinition {
        private final boolean enabled;
        private final List<String> shape;
        private final Map<Character, String> ingredients;

        public RecipeDefinition(boolean enabled, List<String> shape, Map<Character, String> ingredients) {
            this.enabled = enabled;
            this.shape = shape == null ? Collections.emptyList() : Collections.unmodifiableList(shape);
            this.ingredients = ingredients == null
                    ? Collections.emptyMap()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(ingredients));
        }

        public boolean isEnabled() {
            return enabled;
        }

        public List<String> getShape() {
            return shape;
        }

        public Map<Character, String> getIngredients() {
            return ingredients;
        }
    }

    public static class AbilityDefinition {
        private final int cooldownTicks;
        private final double projectileSpeed;
        private final double pullSpeed;
        private final int maxFlightTicks;
        private final int maxPullTicks;
        private final double minDistance;
        private final double verticalCap;
        private final double stepBoost;
        private final int stuckTicks;
        private final int fallImmunityTicks;
        private final boolean pullPlayers;
        private final boolean pullMobs;

        public AbilityDefinition(
                int cooldownTicks,
                double projectileSpeed,
                double pullSpeed,
                int maxFlightTicks,
                int maxPullTicks,
                double minDistance,
                double verticalCap,
                double stepBoost,
                int stuckTicks,
                int fallImmunityTicks,
                boolean pullPlayers,
                boolean pullMobs
        ) {
            this.cooldownTicks = cooldownTicks;
            this.projectileSpeed = projectileSpeed;
            this.pullSpeed = pullSpeed;
            this.maxFlightTicks = maxFlightTicks;
            this.maxPullTicks = maxPullTicks;
            this.minDistance = minDistance;
            this.verticalCap = verticalCap;
            this.stepBoost = stepBoost;
            this.stuckTicks = stuckTicks;
            this.fallImmunityTicks = fallImmunityTicks;
            this.pullPlayers = pullPlayers;
            this.pullMobs = pullMobs;
        }

        public int getCooldownTicks() {
            return cooldownTicks;
        }

        public double getProjectileSpeed() {
            return projectileSpeed;
        }

        public double getPullSpeed() {
            return pullSpeed;
        }

        public int getMaxFlightTicks() {
            return maxFlightTicks;
        }

        public int getMaxPullTicks() {
            return maxPullTicks;
        }

        public double getMinDistance() {
            return minDistance;
        }

        public double getVerticalCap() {
            return verticalCap;
        }

        public double getStepBoost() {
            return stepBoost;
        }

        public int getStuckTicks() {
            return stuckTicks;
        }

        public int getFallImmunityTicks() {
            return fallImmunityTicks;
        }

        public boolean isPullPlayers() {
            return pullPlayers;
        }

        public boolean isPullMobs() {
            return pullMobs;
        }
    }
}
