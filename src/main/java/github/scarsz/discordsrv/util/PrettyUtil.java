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

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.platform.GamePlayer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public class PrettyUtil {

    public static String beautify(User user) {
        return beautify(user, "<Unknown>", true);
    }

    public static String beautify(User user, String noUsernameFormat, boolean includeId) {
        if (user == null) return noUsernameFormat;

        Guild mainGuild = DiscordSRV.getPlugin().getMainGuild();
        Member member = mainGuild != null ? mainGuild.getMember(user) : null;

        return member != null
                ? member.getEffectiveName() + (includeId ? " (#" + user.getId() + ")" : "")
                : user.getName() + (includeId ? " (#" + user.getId() + ")" : "");
    }

    public static String beautifyUsername(UUID player) {
        return beautifyUsername(player, "<Unknown>", true);
    }

    public static String beautifyUsername(UUID player, String noUsernameFormat, boolean includeUuid) {
        if (player == null) return noUsernameFormat;

        String name = DiscordSRV.getPlatform().getPlayerName(player);
        if (name == null) {
            // maybe this will work?
            GamePlayer onlinePlayer = DiscordSRV.getPlatform().getPlayer(player);
            if (onlinePlayer != null) {
                name = onlinePlayer.getName();
            }
        }
        return (name != null ? name : noUsernameFormat) + (includeUuid ? " (" + player + ")" : "");
    }

    public static String beautifyUsername(GamePlayer player) {
        return beautifyUsername(player, "<Unknown>", true);
    }

    public static String beautifyUsername(GamePlayer player, String noUsernameFormat, boolean includeUuid) {
        if (player == null) return noUsernameFormat;
        return beautifyUsername(player.getUniqueId(), noUsernameFormat, includeUuid);
    }

    /**
     * Turns a player uuid into Nickname/Username (UUID)
     * @param player the player's uuid
     * @return the player's nickname (if online) or username (if offline) and the UUID or if player is null "<Unknown>"
     */
    public static String beautifyNickname(UUID player) {
        return beautifyNickname(player, "<Unknown>", true);
    }

    public static String beautifyNickname(UUID player, String noUsernameFormat, boolean includeUuid) {
        if (player == null) return noUsernameFormat;

        GamePlayer onlinePlayer = DiscordSRV.getPlatform().getPlayer(player);
        if (onlinePlayer != null) {
            String displayName = onlinePlayer.getDisplayName();
            if (StringUtils.isBlank(displayName)) return beautifyUsername(player);
            return MessageUtil.strip(displayName) + (includeUuid ? " (" + player + ")" : "");
        } else {
            if (DiscordSRV.getPlatform().getPlayerName(player) == null) return noUsernameFormat;
            return beautifyUsername(player);
        }
    }

    public static String beautifyNickname(GamePlayer player) {
        return beautifyNickname(player, "<Unknown>", true);
    }

    public static String beautifyNickname(GamePlayer player, String noUsernameFormat, boolean includeUuid) {
        if (player == null) return noUsernameFormat;
        return beautifyNickname(player.getUniqueId(), noUsernameFormat, includeUuid);
    }

    /**
     * turn "ACHIEVEMENT_NAME" into "Achievement Name"
     * @param achievement achievement to beautify
     * @return pretty achievement name
     */
    public static String beautify(Enum<?> achievement) {
        if (achievement == null) return "<✗>";

        return Arrays.stream(achievement.name().toLowerCase().split("_"))
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                .collect(Collectors.joining(" "));
    }

    public static String beautify(StackTraceElement[] stackTraceElements) {
        return Arrays.stream(stackTraceElements).map(stackTraceElement -> "\t" + stackTraceElement.toString()).skip(1).collect(Collectors.joining("\n"));
    }

}
