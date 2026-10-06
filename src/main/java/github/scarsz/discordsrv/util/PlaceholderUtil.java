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
import github.scarsz.discordsrv.hooks.permissions.LuckPermsHook;
import github.scarsz.discordsrv.objects.Lag;
import github.scarsz.discordsrv.platform.GamePlayer;
import github.scarsz.discordsrv.platform.Platform;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Placeholder replacement. The Spigot version delegated to PlaceholderAPI, which doesn't exist on NeoForge;
 * instead a small set of built-in placeholders (named like their PlaceholderAPI counterparts) is supported:
 * <ul>
 *     <li>{@code %player_name%}, {@code %player_displayname%}, {@code %player_uuid%}, {@code %player_world%},
 *     {@code %player_ping%}, {@code %luckperms_primary_group_name%} (when a player is available)</li>
 *     <li>{@code %server_online%}, {@code %server_max_players%}, {@code %server_tps%}, {@code %server_version%},
 *     {@code %server_motd%}, {@code %server_unique_joins%}</li>
 * </ul>
 */
public class PlaceholderUtil {

    private PlaceholderUtil() {}

    public static String replacePlaceholders(String input) {
        return replacePlaceholders(input, null);
    }

    public static String replacePlaceholders(String input, GamePlayer player) {
        if (input == null) return null;
        if (input.indexOf('%') == -1) return input;

        Platform platform = DiscordSRV.getPlatform();
        if (player != null) {
            if (input.contains("%player_")) {
                input = input
                        .replace("%player_name%", player.getName())
                        .replace("%player_displayname%", MessageUtil.strip(player.getDisplayName()))
                        .replace("%player_uuid%", player.getUniqueId().toString())
                        .replace("%player_world%", player.getWorldName())
                        .replace("%player_ping%", String.valueOf(player.getPing()));
            }
            if (input.contains("%luckperms_primary_group_name%")) {
                input = input.replace("%luckperms_primary_group_name%", notNull(LuckPermsHook.getPrimaryGroup(player.getUniqueId())));
            }
        }
        if (input.contains("%server_")) {
            input = input
                    .replace("%server_online%", String.valueOf(PlayerUtil.getOnlinePlayers(true).size()))
                    .replace("%server_max_players%", String.valueOf(platform.getMaxPlayers()))
                    .replace("%server_tps%", Lag.getTPSString())
                    .replace("%server_version%", notNull(platform.getServerVersion()))
                    .replace("%server_motd%", MessageUtil.strip(notNull(platform.getMotd())))
                    .replace("%server_unique_joins%", String.valueOf(platform.getTotalPlayerCount()));
        }
        return input;
    }

    /**
     * Important when the content may contain role mentions
     */
    public static String replacePlaceholdersToDiscord(String input) {
        return replacePlaceholdersToDiscord(input, null);
    }

    /**
     * Important when the content may contain role mentions
     */
    public static String replacePlaceholdersToDiscord(String input, GamePlayer player) {
        return replacePlaceholders(input, player);
    }

    /*
     * Placeholders for the channel topic updater & channel updater
     */
    @SuppressWarnings({"SpellCheckingInspection"})
    public static String replaceChannelUpdaterPlaceholders(String input) {
        if (StringUtils.isBlank(input)) return "";

        input = PlaceholderUtil.replacePlaceholdersToDiscord(input);

        final Map<String, String> mem = MemUtil.get();
        Platform platform = DiscordSRV.getPlatform();

        input = input.replaceAll("%time%|%date%", notNull(TimeUtil.timeStamp()))
                .replace("%playercount%", notNull(Integer.toString(PlayerUtil.getOnlinePlayers(true).size())))
                .replace("%playermax%", notNull(Integer.toString(platform.getMaxPlayers())))
                .replace("%totalplayers%", notNull(Integer.toString(DiscordSRV.getTotalPlayerCount())))
                .replace("%uptimemins%", notNull(Long.toString(TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - DiscordSRV.getPlugin().getStartTime()))))
                .replace("%uptimehours%", notNull(Long.toString(TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - DiscordSRV.getPlugin().getStartTime()))))
                .replace("%uptimedays%", notNull(Long.toString(TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - DiscordSRV.getPlugin().getStartTime()))))
                .replace("%timestamp%", notNull(Long.toString(System.currentTimeMillis() / 1000)))
                .replace("%starttimestamp%", notNull(Long.toString(TimeUnit.MILLISECONDS.toSeconds(DiscordSRV.getPlugin().getStartTime()))))
                .replace("%motd%", notNull(StringUtils.isNotBlank(platform.getMotd()) ? MessageUtil.strip(platform.getMotd()) : ""))
                .replace("%serverversion%", notNull(platform.getServerVersion()))
                .replace("%freememory%", notNull(mem.get("freeMB")))
                .replace("%usedmemory%", notNull(mem.get("usedMB")))
                .replace("%totalmemory%", notNull(mem.get("totalMB")))
                .replace("%maxmemory%", notNull(mem.get("maxMB")))
                .replace("%freememorygb%", notNull(mem.get("freeGB")))
                .replace("%usedmemorygb%", notNull(mem.get("usedGB")))
                .replace("%totalmemorygb%", notNull(mem.get("totalGB")))
                .replace("%maxmemorygb%", notNull(mem.get("maxGB")))
                .replace("%tps%", notNull(Lag.getTPSString()));

        return input;
    }

    public static String notNull(Object object) {
        return object != null ? object.toString() : "";
    }

}
