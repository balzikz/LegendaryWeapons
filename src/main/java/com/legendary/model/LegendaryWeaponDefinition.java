package com.legendary.model;

import java.util.ArrayList;
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

    public LegendaryWeaponDefinition(String id,
                                     boolean enabled,
                                     boolean oncePerWorld,
                                     String type,
                                     String material,
                                     String displayName,
                                     List<String> lore,
                                     boolean unbreakable,
                                     RecipeDefinition recipe,
                                     AbilityDefinition ability) {
        this.id = id;
        this.enabled = enabled;
        this.oncePerWorld = oncePerWorld;
        this.type = type == null ? "UNKNOWN" : type.toUpperCase();
        this.material = material;
        this.displayName = displayName;
        this.lore = lore == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(lore));
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
        return "HARPOON".equalsIgnoreCase(type);
    }

    public boolean isVoodooDoll() {
        return "VOODOO_DOLL".equalsIgnoreCase(type) || "VOODOO".equalsIgnoreCase(type);
    }

    public boolean isShrinkRay() {
        return "SHRINK_RAY".equalsIgnoreCase(type) || "SHRINK".equalsIgnoreCase(type);
    }

    public boolean isDeathNote() {
        return "DEATH_NOTE".equalsIgnoreCase(type) || "NOTEBOOK".equalsIgnoreCase(type);
    }

    public static class RecipeDefinition {

        private final boolean enabled;
        private final List<String> shape;
        private final Map<Character, String> ingredients;

        public RecipeDefinition(boolean enabled, List<String> shape, Map<Character, String> ingredients) {
            this.enabled = enabled;
            this.shape = shape == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new ArrayList<>(shape));
            this.ingredients = ingredients == null
                    ? Collections.<Character, String>emptyMap()
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

        private final int durationTicks;
        private final double bonusDamagePercent;
        private final boolean cancelHitDamage;
        private final boolean requireSameWorld;
        private final boolean breakOnOwnerDeath;
        private final boolean breakOnTargetDeath;
        private final boolean allowRetarget;
        private final List<String> mirroredCauses;

        private final double rayRange;
        private final double rayStep;
        private final double selfHeightBlocks;
        private final double targetHeightBlocks;
        private final boolean selfSpeedEffect;
        private final int selfSpeedAmplifier;
        private final boolean targetSlownessEffect;
        private final int targetSlownessAmplifier;

        private final int attempts;
        private final boolean onlySurvivalTargets;
        private final boolean bindToCraftWorld;

        public AbilityDefinition(int cooldownTicks,
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
                                 boolean pullMobs,
                                 int durationTicks,
                                 double bonusDamagePercent,
                                 boolean cancelHitDamage,
                                 boolean requireSameWorld,
                                 boolean breakOnOwnerDeath,
                                 boolean breakOnTargetDeath,
                                 boolean allowRetarget,
                                 List<String> mirroredCauses,
                                 double rayRange,
                                 double rayStep,
                                 double selfHeightBlocks,
                                 double targetHeightBlocks,
                                 boolean selfSpeedEffect,
                                 int selfSpeedAmplifier,
                                 boolean targetSlownessEffect,
                                 int targetSlownessAmplifier,
                                 int attempts,
                                 boolean onlySurvivalTargets,
                                 boolean bindToCraftWorld) {
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
            this.durationTicks = durationTicks;
            this.bonusDamagePercent = bonusDamagePercent;
            this.cancelHitDamage = cancelHitDamage;
            this.requireSameWorld = requireSameWorld;
            this.breakOnOwnerDeath = breakOnOwnerDeath;
            this.breakOnTargetDeath = breakOnTargetDeath;
            this.allowRetarget = allowRetarget;
            this.mirroredCauses = mirroredCauses == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(mirroredCauses));
            this.rayRange = rayRange;
            this.rayStep = rayStep;
            this.selfHeightBlocks = selfHeightBlocks;
            this.targetHeightBlocks = targetHeightBlocks;
            this.selfSpeedEffect = selfSpeedEffect;
            this.selfSpeedAmplifier = selfSpeedAmplifier;
            this.targetSlownessEffect = targetSlownessEffect;
            this.targetSlownessAmplifier = targetSlownessAmplifier;
            this.attempts = attempts;
            this.onlySurvivalTargets = onlySurvivalTargets;
            this.bindToCraftWorld = bindToCraftWorld;
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

        public int getDurationTicks() {
            return durationTicks;
        }

        public double getBonusDamagePercent() {
            return bonusDamagePercent;
        }

        public boolean isCancelHitDamage() {
            return cancelHitDamage;
        }

        public boolean isRequireSameWorld() {
            return requireSameWorld;
        }

        public boolean isBreakOnOwnerDeath() {
            return breakOnOwnerDeath;
        }

        public boolean isBreakOnTargetDeath() {
            return breakOnTargetDeath;
        }

        public boolean isAllowRetarget() {
            return allowRetarget;
        }

        public List<String> getMirroredCauses() {
            return mirroredCauses;
        }

        public double getRayRange() {
            return rayRange;
        }

        public double getRayStep() {
            return rayStep;
        }

        public double getSelfHeightBlocks() {
            return selfHeightBlocks;
        }

        public double getTargetHeightBlocks() {
            return targetHeightBlocks;
        }

        public boolean isSelfSpeedEffect() {
            return selfSpeedEffect;
        }

        public int getSelfSpeedAmplifier() {
            return selfSpeedAmplifier;
        }

        public boolean isTargetSlownessEffect() {
            return targetSlownessEffect;
        }

        public int getTargetSlownessAmplifier() {
            return targetSlownessAmplifier;
        }

        public int getAttempts() {
            return attempts;
        }

        public boolean isOnlySurvivalTargets() {
            return onlySurvivalTargets;
        }

        public boolean isBindToCraftWorld() {
            return bindToCraftWorld;
        }
    }
}
