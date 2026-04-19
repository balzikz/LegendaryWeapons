package com.legendary.commands;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandExecutor;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import com.legendary.LegendaryItems;
import com.legendary.model.LegendaryWeaponDefinition;

import java.util.Map;

public class LegCommand implements CommandExecutor {

    private final LegendaryItems plugin;

    public LegCommand(LegendaryItems plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("legendaryitems.command.leg")) {
            sender.sendMessage("§cУ тебя нет прав на эту команду.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sendOverview(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            handleGive(sender, args);
            return true;
        }

        sendDetails(sender, args[0]);
        return true;
    }

    private void sendOverview(CommandSender sender) {
        sender.sendMessage("§6§lЛегендарные оружия");
        sender.sendMessage("§7/leg §8- §fсписок оружий");
        sender.sendMessage("§7/leg <id> §8- §fподробности и крафт");
        sender.sendMessage("§7/leg give <id> [игрок] §8- §fвыдать предмет для теста");
        sender.sendMessage(" ");

        Player player = sender instanceof Player ? (Player) sender : null;

        for (Map.Entry<String, LegendaryWeaponDefinition> entry : plugin.getItemManager().getDefinitions().entrySet()) {
            LegendaryWeaponDefinition definition = entry.getValue();
            if (!definition.isEnabled()) {
                continue;
            }

            String status = "§7(консоль)";
            if (player != null) {
                boolean canCraft = plugin.getCraftManager().canCraftInWorld(player.getLevel().getName(), definition.getId());
                status = canCraft ? "§aдоступен" : "§cуже скрафчен";
            }

            sender.sendMessage("§e- §f" + definition.getDisplayName() + " §8[§7id: §e" + definition.getId() + "§8] §7- " + status);
        }
    }

    private void sendDetails(CommandSender sender, String id) {
        LegendaryWeaponDefinition definition = plugin.getItemManager().getDefinition(id);
        if (definition == null || !definition.isEnabled()) {
            sender.sendMessage("§cОружие с id '" + id + "' не найдено.");
            return;
        }

        sender.sendMessage("§6§l" + definition.getDisplayName());
        sender.sendMessage("§7ID: §e" + definition.getId());
        sender.sendMessage("§7Тип: §e" + definition.getType());
        sender.sendMessage("§7База предмета: §e" + plugin.getItemManager().prettifyMaterial(definition.getMaterial()));
        sender.sendMessage("§7Кулдаун: §e" + definition.getAbility().getCooldownTicks() + " тиков");
        sender.sendMessage("§7Один раз на мир: " + (definition.isOncePerWorld() ? "§aда" : "§cнет"));

        if (sender instanceof Player) {
            Player player = (Player) sender;
            boolean canCraft = plugin.getCraftManager().canCraftInWorld(player.getLevel().getName(), definition.getId());
            sender.sendMessage("§7Статус в мире §e" + player.getLevel().getName() + "§7: " + (canCraft ? "§aещё не скрафчен" : "§cуже скрафчен"));
        }

        sender.sendMessage("§7Описание:");
        for (String line : definition.getLore()) {
            sender.sendMessage("§8 • §r" + line);
        }

        sender.sendMessage("§7Крафт:");
        if (definition.getRecipe() == null || !definition.getRecipe().isEnabled()) {
            sender.sendMessage("§cКрафт отключён.");
        } else {
            for (String row : definition.getRecipe().getShape()) {
                sender.sendMessage("§8  §f" + row);
            }
            for (Map.Entry<Character, String> entry : definition.getRecipe().getIngredients().entrySet()) {
                sender.sendMessage("§8  §e" + entry.getKey() + " §7= §f" + plugin.getItemManager().prettifyMaterial(entry.getValue()));
            }
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("legendaryitems.command.give")) {
            sender.sendMessage("§cУ тебя нет прав на выдачу легендарок.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /leg give <id> [игрок]");
            return;
        }

        LegendaryWeaponDefinition definition = plugin.getItemManager().getDefinition(args[1]);
        if (definition == null) {
            sender.sendMessage("§cОружие с id '" + args[1] + "' не найдено.");
            return;
        }

        Player target;
        if (args.length >= 3) {
            target = plugin.getServer().getPlayer(args[2]);
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage("§cИз консоли нужно указать игрока: /leg give <id> <игрок>");
            return;
        }

        if (target == null) {
            sender.sendMessage("§cИгрок не найден.");
            return;
        }

        Item item = plugin.getItemManager().createItem(definition.getId());
        if (item == null) {
            sender.sendMessage("§cНе удалось создать предмет.");
            return;
        }

        target.getInventory().addItem(item);
        target.sendMessage("§6[Legendary] §fТы получил §r" + definition.getDisplayName());
        if (target != sender) {
            sender.sendMessage("§aВыдал §r" + definition.getDisplayName() + "§a игроку §e" + target.getName());
        }
    }
}
