package com.legendary;

import cn.nukkit.command.PluginCommand;
import cn.nukkit.plugin.PluginBase;
import com.legendary.commands.LegCommand;
import com.legendary.listeners.LegendaryListener;
import com.legendary.manager.CooldownManager;
import com.legendary.manager.HarpoonManager;
import com.legendary.manager.ItemManager;
import com.legendary.manager.LegendaryCraftManager;
import com.legendary.manager.ShrinkRayManager;
import com.legendary.manager.VoodooManager;

public 1 LegendaryItems extends PluginBase {

    private static LegendaryItems instance;

    private ItemManager itemManager;
    private LegendaryCraftManager craftManager;
    private CooldownManager cooldownManager;
    private HarpoonManager harpoonManager;
    private VoodooManager voodooManager;
    private ShrinkRayManager shrinkRayManager;

    @Override
    public void onEnable() {
        instance = this;

        this.saveDefaultConfig();
        this.reloadConfig();

        this.itemManager = new ItemManager(this);
        this.itemManager.loadDefinitions();

        this.cooldownManager = new CooldownManager();
        this.harpoonManager = new HarpoonManager(this, cooldownManager);
        this.voodooManager = new VoodooManager(this, cooldownManager);
        this.shrinkRayManager = new ShrinkRayManager(this, cooldownManager);

        this.craftManager = new LegendaryCraftManager(this, itemManager);
        this.craftManager.registerRecipes();

        this.getServer().getPluginManager().registerEvents(new LegendaryListener(this), this);

        PluginCommand legCommand = (PluginCommand) this.getServer().getPluginCommand("leg");
        if (legCommand != null) {
            legCommand.setExecutor(new LegCommand(this));
        } else {
            this.getLogger().warning("Команда /leg не найдена в plugin.yml");
        }

        this.getLogger().info("§aLegendaryItems включён. Оружия: Гарпун, Кукла Вуду, Shrink Ray.");
    }

    @Override
    public void onDisable() {
        if (voodooManager != null) {
            voodooManager.shutdown();
        }
        if (shrinkRayManager != null) {
            shrinkRayManager.shutdown();
        }
    }

    public static LegendaryItems getInstance() {
        return instance;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public LegendaryCraftManager getCraftManager() {
        return craftManager;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public HarpoonManager getHarpoonManager() {
        return harpoonManager;
    }

    public VoodooManager getVoodooManager() {
        return voodooManager;
    }

    public ShrinkRayManager getShrinkRayManager() {
        return shrinkRayManager;
    }
}
