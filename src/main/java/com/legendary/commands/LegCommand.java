package com.legendary.command;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandExecutor;
import cn.nukkit.command.CommandSender;
import cn.nukkit.item.Item;
import com.legendary.LegendaryItems;
import com.legendary.model.LegendaryWeaponDefinition;

import java.util.Locale;
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
        sender.sendMessage("§7/leg <id> §8- §fподробности, эффекты и крафт");
        sender.sendMessage("§7/leg give <id> [игрок] §8- §fвыдать предмет для теста");
        sender.sendMessage(" ");

        Player player = sender instanceof Player ? (Player) sender : null;
        for (Map.Entry<String, LegendaryWeaponDefinition> entry : plugin.getItemManager().getDefinitions().entrySet()) {
            LegendaryWeaponDefinition definition = entry.getValue();
            if (definition == null || !definition.isEnabled()) {
                continue;
            }

            String state = "§7(консоль)";
            if (player != null) {
                boolean canCraft = plugin.getCraftManager().canCraftInWorld(player.getLevel().getName(), definition.getId());
                state = canCraft ? "§aдоступен" : "§cуже скрафчен";
            }

            sender.sendMessage("§e- §r" + definition.getDisplayName()
                    + " §8[§7id: §e" + definition.getId() + "§8]"
                    + " §7тип: §e" + readableType(definition)
                    + " §7- " + state);
        }
    }

    private void sendDetails(CommandSender sender, String id) {
        LegendaryWeaponDefinition definition = plugin.getItemManager().getDefinition(id);
        if (definition == null || !definition.isEnabled()) {
            sender.sendMessage("§cОружие с id '" + id + "' не найдено.");
            return;
        }

        LegendaryWeaponDefinition.AbilityDefinition ability = definition.getAbility();

        sender.sendMessage("§6§l" + definition.getDisplayName());
        sender.sendMessage("§7ID: §e" + definition.getId());
        sender.sendMessage("§7Тип: §e" + readableType(definition));
        sender.sendMessage("§7База предмета: §e" + plugin.getItemManager().prettifyMaterial(definition.getMaterial()));
        sender.sendMessage("§7Кулдаун: §e" + (ability != null ? ability.getCooldownTicks() : 0) + " тиков");
        sender.sendMessage("§7Один раз на мир: " + (definition.isOncePerWorld() ? "§aда" : "§cнет"));

        if (sender instanceof Player) {
            Player player = (Player) sender;
            boolean canCraft = plugin.getCraftManager().canCraftInWorld(player.getLevel().getName(), definition.getId());
            sender.sendMessage("§7Статус в мире §e" + player.getLevel().getName() + "§7: "
                    + (canCraft ? "§aещё не скрафчен" : "§cуже скрафчен"));
        }

        if (definition.isHarpoon() && ability != null) {
            sender.sendMessage("§7Эффект: §fПритягивает тебя к точке попадания или тянет цель к тебе.");
            sender.sendMessage("§7Сила притягивания: §e" + formatDouble(ability.getPullSpeed()));
            sender.sendMessage("§7Макс. время подтягивания: §e" + ability.getMaxPullTicks() + " тиков");
        } else if (definition.isVoodooDoll() && ability != null) {
            sender.sendMessage("§7Эффект: §fПосле удара связывает цель с владельцем.");
            sender.sendMessage("§7Длительность связи: §e" + ability.getDurationTicks() + " тиков §8("
                    + formatDouble(ability.getDurationTicks() / 20.0D) + " сек)");
            sender.sendMessage("§7Бонус зеркального урона: §e+" + formatDouble(ability.getBonusDamagePercent()) + "%");
            sender.sendMessage("§7Прямой урон ударом: " + (ability.isCancelHitDamage() ? "§cотменяется" : "§aнаносится"));
        } else if (definition.isShrinkRay() && ability != null) {
            sender.sendMessage("§7Эффект: §fShift + ПКМ уменьшает тебя, обычный ПКМ стреляет лучом по игроку.");
            sender.sendMessage("§7Длительность: §e" + ability.getDurationTicks() + " тиков §8("
                    + formatDouble(ability.getDurationTicks() / 20.0D) + " сек)");
            sender.sendMessage("§7Высота владельца: §e" + formatDouble(ability.getSelfHeightBlocks()) + " блока");
            sender.sendMessage("§7Высота цели: §e" + formatDouble(ability.getTargetHeightBlocks()) + " блока");
            sender.sendMessage("§7Дальность луча: §e" + formatDouble(ability.getRayRange()) + " блоков");
            sender.sendMessage("§7Себе эффект: " + readablePotionBoost(ability.isSelfSpeedEffect(), "Скорость", ability.getSelfSpeedAmplifier()));
            sender.sendMessage("§7Цели эффект: " + readablePotionBoost(ability.isTargetSlownessEffect(), "Замедление", ability.getTargetSlownessAmplifier()));
        } else if (definition.isDeathNote() && ability != null) {
            sender.sendMessage("§7Эффект: §fПКМ открывает форму, где владелец вписывает имя жертвы.");
            sender.sendMessage("§7Шанс жертвы: §e" + formatDouble(ability.getDurationTicks() / 20.0D) + " сек §7и §e" + ability.getAttempts() + " попытки");
            sender.sendMessage("§7Только выживание: " + (ability.isOnlySurvivalTargets() ? "§aда" : "§cнет"));
            sender.sendMessage("§7Привязка к миру крафта: " + (ability.isBindToCraftWorld() ? "§aда" : "§cнет"));
            sender.sendMessage("§7Требуется один мир: " + (ability.isRequireSameWorld() ? "§aда" : "§cнет"));
        }

        sender.sendMessage("§7Описание:");
        for (String line : definition.getLore()) {
            sender.sendMessage("§8 • §r" + line);
        }

        sender.sendMessage("§7Крафт:");
        if (definition.getRecipe() == null || !definition.getRecipe().isEnabled()) {
            sender.sendMessage("§cКрафт отключён.");
            return;
        }

        for (String row : definition.getRecipe().getShape()) {
            sender.sendMessage("§8 §f" + row);
        }
        for (Map.Entry<Character, String> entry : definition.getRecipe().getIngredients().entrySet()) {
            sender.sendMessage("§8 §e" + entry.getKey() + " §7= §f"
                    + plugin.getItemManager().prettifyMaterial(entry.getValue()));
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

    private String readableType(LegendaryWeaponDefinition definition) {
        if (definition.isHarpoon()) {
            return "Гарпун";
        }
        if (definition.isVoodooDoll()) {
            return "Кукла Вуду";
        }
        if (definition.isShrinkRay()) {
            return "Shrink Ray";
        }
        if (definition.isDeathNote()) {
            return "Тетрадь смерти";
        }
        return definition.getType().toLowerCase(Locale.ROOT);
    }

    private String readablePotionBoost(boolean enabled, String name, int amplifier) {
        if (!enabled) {
            return "§7нет";
        }
        return "§e" + name + " " + roman(amplifier + 1);
    }

    private String roman(int value) {
        if (value <= 1) {
            return "I";
        }
        if (value == 2) {
            return "II";
        }
        if (value == 3) {
            return "III";
        }
        if (value == 4) {
            return "IV";
        }
        if (value == 5) {
            return "V";
        }
        return String.valueOf(value);
    }

    private String formatDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.US, "%.2f", value);
    }
}
