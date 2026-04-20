package com.legendary.manager;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerChatEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.player.PlayerQuitEvent;
import cn.nukkit.form.element.ElementInput;
import cn.nukkit.form.element.ElementLabel;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.item.Item;
import cn.nukkit.scheduler.NukkitRunnable;
import com.legendary.LegendaryItems;
import com.legendary.model.LegendaryWeaponDefinition;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DeathNoteManager {

    private final LegendaryItems plugin;
    private final CooldownManager cooldownManager;
    private final Map<Integer, PendingForm> pendingForms = new ConcurrentHashMap<>();
    private final Map<UUID, PendingSentence> sentencesByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, PendingSentence> sentencesByTarget = new ConcurrentHashMap<>();
    private NukkitRunnable cleanupTask;

    public DeathNoteManager(LegendaryItems plugin, CooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
        startCleanupTask();
    }

    public void handleUse(Player owner, LegendaryWeaponDefinition definition) {
        if (owner == null || definition == null || definition.getAbility() == null) {
            return;
        }

        if (cooldownManager.isOnCooldown(owner, definition.getId())) {
            owner.sendActionBar("§cТетрадь смерти перезаряжается: "
                    + cooldownManager.getRemainingTicks(owner, definition.getId()) + " тиков");
            return;
        }

        if (sentencesByOwner.containsKey(owner.getUniqueId())) {
            owner.sendMessage("§cУ тебя уже есть активный приговор. Дождись его завершения.");
            return;
        }

        Item inHand = owner.getInventory().getItemInHand();
        if (!plugin.getItemManager().isLegendary(inHand, definition.getId())) {
            return;
        }

        String currentWorld = owner.getLevel().getName();
        if (definition.getAbility().isBindToCraftWorld()) {
            String boundWorld = plugin.getItemManager().getBoundWorld(inHand);
            if (boundWorld == null) {
                bindHeldItemToWorld(owner, currentWorld);
                boundWorld = currentWorld;
                owner.sendMessage("§8[Death Note] §7Тетрадь привязалась к миру §e" + boundWorld + "§7.");
            }
            if (!currentWorld.equalsIgnoreCase(boundWorld)) {
                owner.sendMessage("§cЭта Тетрадь смерти принадлежит миру §e" + boundWorld + "§c и не работает здесь.");
                return;
            }
        }

        openForm(owner, definition);
    }

    public void handleFormResponse(PlayerFormRespondedEvent event) {
        int formId = extractFormId(event);
        if (formId < 0) {
            return;
        }

        PendingForm form = pendingForms.remove(formId);
        if (form == null) {
            return;
        }

        Player owner = event.getPlayer();
        if (owner == null || !form.ownerId.equals(owner.getUniqueId())) {
            return;
        }

        if (wasClosed(event)) {
            owner.sendMessage("§7Тетрадь смерти закрыта.");
            return;
        }

        LegendaryWeaponDefinition definition = plugin.getItemManager().getDefinition(form.legendaryId);
        if (definition == null || definition.getAbility() == null) {
            owner.sendMessage("§cНе удалось обработать Тетрадь смерти: определение оружия не найдено.");
            return;
        }

        String typedName = extractInputValue(event, 0);
        if (typedName == null || typedName.trim().isEmpty()) {
            owner.sendMessage("§cНужно ввести точное имя игрока.");
            return;
        }

        beginSentence(owner, typedName.trim(), definition);
    }

    public void handleChatGuess(PlayerChatEvent event) {
        if (event == null || event.getPlayer() == null) {
            return;
        }

        Player target = event.getPlayer();
        PendingSentence sentence = sentencesByTarget.get(target.getUniqueId());
        if (sentence == null) {
            return;
        }

        event.setCancelled();
        if (!validateSentence(sentence, true)) {
            return;
        }

        Player owner = getOnlinePlayer(sentence.ownerId);
        if (owner == null) {
            cancelSentence(sentence,
                    null,
                    "§7Приговор рассеялся: владелец тетради исчез.");
            return;
        }

        String guess = normalizeName(event.getMessage());
        if (guess.isEmpty()) {
            target.sendMessage("§cНапиши в чат имя владельца тетради.");
            return;
        }

        String ownerName = normalizeName(owner.getName());
        if (ownerName.equalsIgnoreCase(guess)) {
            cancelSentence(sentence,
                    "§e" + target.getName() + "§f разгадал твоё имя и сорвал приговор.",
                    "§aТы угадал владельца. Приговор Тетради смерти сорван.");
            return;
        }

        sentence.attemptsLeft--;
        if (sentence.attemptsLeft <= 0) {
            target.sendMessage("§4§l[Death Note] §cПопытки закончились.");
            executeSentence(sentence, "attempts");
            return;
        }

        target.sendMessage("§cНеверно. Осталось попыток: §e" + sentence.attemptsLeft + "§c.");
    }

    public void handleQuit(PlayerQuitEvent event) {
        if (event != null) {
            cleanupPlayer(event.getPlayer());
        }
    }

    public void handleDeath(PlayerDeathEvent event) {
        if (event != null) {
            cleanupPlayer(event.getEntity());
        }
    }

    public void cleanupPlayer(Player player) {
        if (player == null) {
            return;
        }

        PendingSentence byOwner = sentencesByOwner.get(player.getUniqueId());
        if (byOwner != null) {
            cancelSentence(byOwner, null, null);
        }

        PendingSentence byTarget = sentencesByTarget.get(player.getUniqueId());
        if (byTarget != null) {
            cancelSentence(byTarget, null, null);
        }

        removePendingForms(player.getUniqueId());
    }

    public void shutdown() {
        if (cleanupTask != null) {
            try {
                cleanupTask.cancel();
            } catch (Exception ignored) {
                // ignore
            }
        }
        pendingForms.clear();
        sentencesByOwner.clear();
        sentencesByTarget.clear();
    }

    private void openForm(Player owner, LegendaryWeaponDefinition definition) {
        FormWindowCustom window = new FormWindowCustom("§0Тетрадь смерти");
        window.addElement(new ElementInput("Введи точное имя игрока", "Ник жертвы"));
        window.addElement(new ElementLabel(
                "§7Сработает только по игрокам в выживании.\n"
                        + "§7Жертва получит время и попытки, чтобы написать имя владельца в чат.\n"
                        + "§7Подсказка для жертвы — мировой анонс крафта легендарки."
        ));

        int formId = owner.showFormWindow(window);
        pendingForms.put(formId, new PendingForm(owner.getUniqueId(), definition.getId()));
    }

    private void beginSentence(Player owner, String typedName, LegendaryWeaponDefinition definition) {
        LegendaryWeaponDefinition.AbilityDefinition ability = definition.getAbility();
        Item inHand = owner.getInventory().getItemInHand();
        if (!plugin.getItemManager().isLegendary(inHand, definition.getId())) {
            owner.sendMessage("§cДержи Тетрадь смерти в руке, пока вписываешь имя.");
            return;
        }

        if (cooldownManager.isOnCooldown(owner, definition.getId())) {
            owner.sendActionBar("§cТетрадь смерти перезаряжается: "
                    + cooldownManager.getRemainingTicks(owner, definition.getId()) + " тиков");
            return;
        }

        Player target = findOnlinePlayerExact(typedName);
        if (target == null) {
            owner.sendMessage("§cИгрок §e" + typedName + "§c не найден онлайн.");
            return;
        }

        if (target.getUniqueId().equals(owner.getUniqueId())) {
            owner.sendMessage("§cНельзя вписать в тетрадь самого себя.");
            return;
        }

        if (sentencesByOwner.containsKey(owner.getUniqueId())) {
            owner.sendMessage("§cУ тебя уже есть активный приговор.");
            return;
        }

        if (sentencesByTarget.containsKey(target.getUniqueId())) {
            owner.sendMessage("§cНа этого игрока уже действует чужая Тетрадь смерти.");
            return;
        }

        if (ability.isOnlySurvivalTargets() && !isSurvival(target)) {
            owner.sendMessage("§cТетрадь смерти работает только по игрокам в выживании.");
            return;
        }

        if (ability.isRequireSameWorld() && owner.getLevel() != target.getLevel()) {
            owner.sendMessage("§cЦель должна быть в том же мире.");
            return;
        }

        String boundWorld = owner.getLevel().getName();
        if (ability.isBindToCraftWorld()) {
            boundWorld = plugin.getItemManager().getBoundWorld(inHand);
            if (boundWorld == null) {
                bindHeldItemToWorld(owner, owner.getLevel().getName());
                boundWorld = owner.getLevel().getName();
            }

            if (!boundWorld.equalsIgnoreCase(owner.getLevel().getName())) {
                owner.sendMessage("§cЭта Тетрадь смерти принадлежит миру §e" + boundWorld + "§c.");
                return;
            }

            if (target.getLevel() == null || !boundWorld.equalsIgnoreCase(target.getLevel().getName())) {
                owner.sendMessage("§cЖертва должна быть в мире §e" + boundWorld + "§c, к которому привязана тетрадь.");
                return;
            }
        }

        long expiresAtMillis = System.currentTimeMillis() + (ability.getDurationTicks() * 50L);
        PendingSentence sentence = new PendingSentence(
                owner.getUniqueId(),
                target.getUniqueId(),
                definition.getId(),
                boundWorld,
                expiresAtMillis,
                Math.max(1, ability.getAttempts()),
                ability.isRequireSameWorld(),
                ability.isOnlySurvivalTargets()
        );

        sentencesByOwner.put(sentence.ownerId, sentence);
        sentencesByTarget.put(sentence.targetId, sentence);
        cooldownManager.setCooldown(owner, definition.getId(), ability.getCooldownTicks());

        double seconds = ability.getDurationTicks() / 20.0D;
        owner.sendMessage("§4§l[Death Note] §fИмя §c" + target.getName() + "§f записано. У цели есть §e"
                + formatSeconds(seconds) + " сек.§f и §e" + sentence.attemptsLeft + " попытки§f угадать твоё имя.");
        target.sendMessage("§4§l[Death Note] §fТвоё имя внесено в Тетрадь смерти.");
        target.sendMessage("§7Чтобы выжить, напиши в чат точное имя владельца тетради.");
        target.sendMessage("§7У тебя §e" + formatSeconds(seconds) + " сек.§7 и §e" + sentence.attemptsLeft + " попытки§7.");
        target.sendMessage("§8Подсказка: вспоминай, кто скрафтил легендарку в этом мире.");
    }

    private void executeSentence(PendingSentence sentence, String reason) {
        clearSentence(sentence);

        Player owner = getOnlinePlayer(sentence.ownerId);
        Player target = getOnlinePlayer(sentence.targetId);
        if (target == null || !target.isOnline() || target.isClosed()) {
            return;
        }
        if (sentence.onlySurvivalTargets && !isSurvival(target)) {
            return;
        }
        if (sentence.requireSameWorld && owner != null && owner.getLevel() != target.getLevel()) {
            return;
        }
        if (sentence.boundWorld != null && target.getLevel() != null
                && !sentence.boundWorld.equalsIgnoreCase(target.getLevel().getName())) {
            return;
        }

        if ("timeout".equalsIgnoreCase(reason)) {
            target.sendMessage("§4§l[Death Note] §cВремя вышло. Смерть настигла тебя.");
        } else {
            target.sendMessage("§4§l[Death Note] §cПоследняя ошибка оказалась роковой.");
        }
        if (owner != null && owner.isOnline() && !owner.isClosed()) {
            owner.sendMessage("§4§l[Death Note] §fПриговор над §c" + target.getName() + "§f исполнен.");
        }

        try {
            target.setHealth(0F);
        } catch (Throwable ignored) {
            try {
                target.attack(new EntityDamageEvent(target, EntityDamageEvent.DamageCause.CUSTOM, 9999F));
            } catch (Throwable ignoredToo) {
                // ignore
            }
        }
    }

    private boolean validateSentence(PendingSentence sentence, boolean notifyTarget) {
        if (sentence == null) {
            return false;
        }

        Player owner = getOnlinePlayer(sentence.ownerId);
        Player target = getOnlinePlayer(sentence.targetId);

        if (owner == null || !owner.isOnline() || owner.isClosed()) {
            cancelSentence(sentence, null, notifyTarget ? "§7Приговор рассеялся: владелец тетради исчез." : null);
            return false;
        }
        if (target == null || !target.isOnline() || target.isClosed()) {
            cancelSentence(sentence, "§7Приговор отменён: цель вышла из игры.", null);
            return false;
        }
        if (!owner.isAlive() || !target.isAlive()) {
            cancelSentence(sentence, null, null);
            return false;
        }
        if (sentence.requireSameWorld && owner.getLevel() != target.getLevel()) {
            cancelSentence(sentence,
                    "§7Приговор сорвался: вы больше не в одном мире.",
                    notifyTarget ? "§7Приговор сорвался: вы больше не в одном мире." : null);
            return false;
        }
        if (sentence.boundWorld != null) {
            if (owner.getLevel() == null || target.getLevel() == null
                    || !sentence.boundWorld.equalsIgnoreCase(owner.getLevel().getName())
                    || !sentence.boundWorld.equalsIgnoreCase(target.getLevel().getName())) {
                cancelSentence(sentence,
                        "§7Приговор сорвался: тетрадь работает только в привязанном мире.",
                        notifyTarget ? "§7Приговор сорвался: тетрадь больше не может достать тебя из другого мира." : null);
                return false;
            }
        }
        if (sentence.onlySurvivalTargets && !isSurvival(target)) {
            cancelSentence(sentence,
                    "§7Приговор снят: цель больше не в режиме выживания.",
                    notifyTarget ? "§7Приговор снят: Тетрадь смерти работает только по выживанию." : null);
            return false;
        }
        return true;
    }

    private void cancelSentence(PendingSentence sentence, String ownerMessage, String targetMessage) {
        clearSentence(sentence);

        Player owner = getOnlinePlayer(sentence.ownerId);
        Player target = getOnlinePlayer(sentence.targetId);
        if (ownerMessage != null && owner != null && owner.isOnline() && !owner.isClosed()) {
            owner.sendMessage("§8[Death Note] §f" + ownerMessage);
        }
        if (targetMessage != null && target != null && target.isOnline() && !target.isClosed()) {
            target.sendMessage("§8[Death Note] §f" + targetMessage);
        }
    }

    private void clearSentence(PendingSentence sentence) {
        if (sentence == null) {
            return;
        }
        sentencesByOwner.remove(sentence.ownerId);
        sentencesByTarget.remove(sentence.targetId);
    }

    private void startCleanupTask() {
        cleanupTask = new NukkitRunnable() {
            @Override
            public void run() {
                sweep();
            }
        };
        cleanupTask.runTaskTimer(plugin, 10, 10);
    }

    private void sweep() {
        if (sentencesByOwner.isEmpty()) {
            return;
        }

        for (PendingSentence sentence : new ArrayList<>(sentencesByOwner.values())) {
            if (!validateSentence(sentence, true)) {
                continue;
            }
            if (System.currentTimeMillis() >= sentence.expiresAtMillis) {
                executeSentence(sentence, "timeout");
            }
        }
    }

    private void bindHeldItemToWorld(Player player, String worldName) {
        int heldSlot = player.getInventory().getHeldItemIndex();
        Item updated = player.getInventory().getItem(heldSlot).clone();
        plugin.getItemManager().bindItemToWorld(updated, worldName);
        player.getInventory().setItem(heldSlot, updated);
    }

    private void removePendingForms(UUID ownerId) {
        if (ownerId == null || pendingForms.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, PendingForm> entry : new ArrayList<>(pendingForms.entrySet())) {
            if (ownerId.equals(entry.getValue().ownerId)) {
                pendingForms.remove(entry.getKey());
            }
        }
    }

    private Player findOnlinePlayerExact(String typedName) {
        String normalized = normalizeName(typedName);
        if (normalized.isEmpty()) {
            return null;
        }

        Server server = plugin.getServer();
        for (Player online : server.getOnlinePlayers().values()) {
            if (normalizeName(online.getName()).equalsIgnoreCase(normalized)) {
                return online;
            }
        }
        return null;
    }

    private Player getOnlinePlayer(UUID uniqueId) {
        if (uniqueId == null) {
            return null;
        }
        for (Player online : plugin.getServer().getOnlinePlayers().values()) {
            if (uniqueId.equals(online.getUniqueId())) {
                return online;
            }
        }
        return null;
    }

    private boolean isSurvival(Player player) {
        try {
            Method method = player.getClass().getMethod("isSurvival");
            Object result = method.invoke(player);
            if (result instanceof Boolean) {
                return (Boolean) result;
            }
        } catch (Throwable ignored) {
            // fallback below
        }
        try {
            return player.getGamemode() == Player.SURVIVAL;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int extractFormId(PlayerFormRespondedEvent event) {
        try {
            Method method = event.getClass().getMethod("getFormID");
            Object value = method.invoke(event);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
            // try alternative name
        }
        try {
            Method method = event.getClass().getMethod("getFormId");
            Object value = method.invoke(event);
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
            return -1;
        }
        return -1;
    }

    private boolean wasClosed(PlayerFormRespondedEvent event) {
        try {
            Method method = event.getClass().getMethod("wasClosed");
            Object value = method.invoke(event);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        } catch (Throwable ignored) {
            // fallback below
        }
        Object response = extractResponse(event);
        return response == null;
    }

    private Object extractResponse(PlayerFormRespondedEvent event) {
        try {
            Method method = event.getClass().getMethod("getResponse");
            return method.invoke(event);
        } catch (Throwable ignored) {
            // try window.getResponse()
        }
        try {
            Method getWindow = event.getClass().getMethod("getWindow");
            Object window = getWindow.invoke(event);
            if (window == null) {
                return null;
            }
            Method getResponse = window.getClass().getMethod("getResponse");
            return getResponse.invoke(window);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String extractInputValue(PlayerFormRespondedEvent event, int index) {
        Object response = extractResponse(event);
        if (response == null) {
            return null;
        }

        try {
            Method method = response.getClass().getMethod("getInputResponse", int.class);
            Object value = method.invoke(response, index);
            return value == null ? null : String.valueOf(value);
        } catch (Throwable ignored) {
            // fallback below
        }

        try {
            Method method = response.getClass().getMethod("getResponses");
            Object value = method.invoke(response);
            if (value instanceof java.util.List) {
                java.util.List list = (java.util.List) value;
                if (index >= 0 && index < list.size()) {
                    Object entry = list.get(index);
                    return entry == null ? null : String.valueOf(entry);
                }
            }
        } catch (Throwable ignored) {
            // ignore
        }

        return null;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String formatSeconds(double seconds) {
        if (Math.abs(seconds - Math.rint(seconds)) < 0.0001D) {
            return String.valueOf((long) Math.rint(seconds));
        }
        return String.format(Locale.US, "%.1f", seconds);
    }

    private static class PendingForm {
        private final UUID ownerId;
        private final String legendaryId;

        private PendingForm(UUID ownerId, String legendaryId) {
            this.ownerId = ownerId;
            this.legendaryId = legendaryId;
        }
    }

    private static class PendingSentence {
        private final UUID ownerId;
        private final UUID targetId;
        private final String legendaryId;
        private final String boundWorld;
        private final long expiresAtMillis;
        private final boolean requireSameWorld;
        private final boolean onlySurvivalTargets;
        private int attemptsLeft;

        private PendingSentence(UUID ownerId,
                                UUID targetId,
                                String legendaryId,
                                String boundWorld,
                                long expiresAtMillis,
                                int attemptsLeft,
                                boolean requireSameWorld,
                                boolean onlySurvivalTargets) {
            this.ownerId = ownerId;
            this.targetId = targetId;
            this.legendaryId = legendaryId;
            this.boundWorld = boundWorld;
            this.expiresAtMillis = expiresAtMillis;
            this.attemptsLeft = attemptsLeft;
            this.requireSameWorld = requireSameWorld;
            this.onlySurvivalTargets = onlySurvivalTargets;
        }
    }
}
