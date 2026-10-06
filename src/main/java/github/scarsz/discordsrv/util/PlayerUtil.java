/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.util;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.platform.CommandSender;
import github.scarsz.discordsrv.platform.GamePlayer;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class PlayerUtil {

    public static List<GamePlayer> getOnlinePlayers() {
        return getOnlinePlayers(false);
    }

    /**
     * @param filterVanishedPlayers whether to filter out vanished players
     * @return {@code ArrayList} containing online players
     */
    public static List<GamePlayer> getOnlinePlayers(boolean filterVanishedPlayers) {
        List<GamePlayer> onlinePlayers = new ArrayList<>(DiscordSRV.getPlatform().getOnlinePlayers());

        if (!filterVanishedPlayers) {
            return onlinePlayers;
        } else {
            return onlinePlayers.stream()
                    .filter(player -> !isVanished(player))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Notify online players of mentions after a message was broadcasted to them
     * @param predicate predicate to determine whether the player got the message this ding was triggered for
     * @param message the message to be searched for players to ding
     */
    public static void notifyPlayersOfMentions(Predicate<? super GamePlayer> predicate, String message) {
        if (predicate == null) predicate = Objects::nonNull; // if null predicate given, that means everyone on the server would've gotten the message

        if (!DiscordSRV.config().getBoolean("MinecraftMentionSound")) return;

        if (StringUtils.isBlank(message)) {
            DiscordSRV.debug(Debug.DISCORD_TO_MINECRAFT, "Tried notifying players with null or blank message");
            return;
        }

        List<String> splitMessage =
                Arrays.stream(MessageUtil.strip(message).replaceAll("[^a-zA-Z0-9_@<>]", " ").split(" ")) // split message by groups of alphanumeric characters & underscores
                        .filter(StringUtils::isNotBlank)
                        .map(String::toLowerCase) // we don't care about case when finding player names
                        .map(s -> {
                            String possibleId = s.replace("<@", "").replace(">", "");
                            if (StringUtils.isNotBlank(possibleId) && StringUtils.isNumeric(possibleId) && s.startsWith("<@") && s.endsWith(">")) {
                                User possibleUser = DiscordUtil.getUserById(possibleId);
                                if (possibleUser == null || DiscordSRV.getPlugin().getMainGuild() == null) return s;
                                Member member = DiscordSRV.getPlugin().getMainGuild().getMember(possibleUser);
                                return member != null ? "@" + member.getEffectiveName().toLowerCase() : s;
                            } else {
                                return s;
                            }
                        })
                        .collect(Collectors.toList());

        getOnlinePlayers().stream()
                .filter(predicate) // filter out players that didn't get this message sent to them
                .filter(player -> // filter out players whose name nor display name is in the split message
                        splitMessage.contains("@" + player.getName().toLowerCase()) || splitMessage.contains("@" + MessageUtil.strip(player.getDisplayName()).toLowerCase())
                )
                .forEach(player -> DiscordSRV.getPlatform().runOnMainThread(player::playMentionSound));
    }

    /**
     * @param player Player to check
     * @return whether the player is vanished
     */
    public static boolean isVanished(GamePlayer player) {
        return player.isVanished();
    }

    public static int getPing(GamePlayer player) {
        return player.getPing();
    }

    private static final List<Character> VANILLA_TARGET_SELECTORS = Arrays.asList('p', 'r', 'a', 'e', 's', 'n');

    public static String convertTargetSelectors(String message, CommandSender sender) {
        for (int i = 0; i < message.length(); i++) {
            if (message.charAt(i) == '@') {
                int end = getSelectorEnd(message, i);
                if (end < 0 || end + 1 < message.length() && !canSeparateSelectors(message.charAt(end + 1))) {
                    continue;
                }
                String selector = message.substring(i, end + 1);

                try {
                    String target = sender == null ? "{TARGET}" : String.join(" ", DiscordSRV.getPlatform().selectEntityNames(sender, selector));
                    message = message.substring(0, i) + target + message.substring(end + 1);
                    i += target.length() - 1;
                } catch (Exception ignored) {
                    // invalid selector
                }
            }
        }
        return message;
    }

    /**
     * Seeks the position of the end character of the selector.
     *
     * @param message the full raw message
     * @param start the position of selector start
     * @return the index of the last character or -1 if invalid
     */
    private static int getSelectorEnd(String message, int start) {
        int end = start + 1;
        if (end >= message.length() || !VANILLA_TARGET_SELECTORS.contains(message.charAt(end))) {
            return -1; // Not a valid selector type
        }

        int argsPos = start + 2;
        if (argsPos < message.length() && message.charAt(argsPos) == '[') {
            for (int i = argsPos + 1; i < message.length(); i++) {
                char current = message.charAt(i);
                if (current == '[' || Character.isWhitespace(current)) {
                    return -1; // Selectors args cannot be recursive or contain spaces
                }
                if (current == ']') {
                    return i;
                }
            }
            return -1; // No end to the arguments
        }

        return end;
    }

    /**
     * Determines whether a character can separate two selectors.
     *
     * <p>Unlike the vanilla behavior, it is safer to not execute
     * target selectors like {@code @everyone} to avoid confusion.</p>
     *
     * @param character the character
     * @return if it could separate a selector from the rest
     */
    private static boolean canSeparateSelectors(char character) {
        return Character.isWhitespace(character) || character == '@';
    }

    /**
     * Returns whether the passed UUID is a v3 UUID. Offline UUIDs are v3, online are v4.
     * @param uuid the UUID to check
     * @return whether the UUID is a v3 UUID &amp; thus is offline
     */
    public static boolean uuidIsOffline(UUID uuid) {
        return uuid.version() == 3;
    }

}
