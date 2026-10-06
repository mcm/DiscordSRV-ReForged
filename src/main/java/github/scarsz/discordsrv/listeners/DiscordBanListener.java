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

package github.scarsz.discordsrv.listeners;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.platform.Platform;
import github.scarsz.discordsrv.util.LangUtil;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class DiscordBanListener extends ListenerAdapter {

    @Override
    public void onGuildBan(@NotNull GuildBanEvent event) {
        if (DiscordSRV.getPlugin().getAccountLinkManager() == null) return;
        UUID linkedUuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getUser().getId());
        if (linkedUuid == null) {
            DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Not handling ban for user " + event.getUser() + " because they didn't have a linked account");
            return;
        }

        Platform platform = DiscordSRV.getPlatform();
        String playerName = platform.getPlayerName(linkedUuid);
        if (playerName == null) return; // player hasn't played before

        if (!DiscordSRV.config().getBoolean("BanSynchronizationDiscordToMinecraft")) {
            DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Not handling ban for user " + event.getUser() + " because doing so is disabled in the config");
            return;
        }

        String reason = LangUtil.Message.BAN_DISCORD_TO_MINECRAFT.toString();
        if (platform.isBanned(linkedUuid)) return; // if they are already banned we don't want to overwrite the original ban reason
        // also kicks them if they're online, because adding them to the ban list isn't enough
        BanSynchronizer.ignoreNextChange(linkedUuid); // don't sync this ban back to Discord
        platform.ban(linkedUuid, playerName, reason, "Discord");
    }

    @Override
    public void onGuildUnban(@NotNull GuildUnbanEvent event) {
        if (DiscordSRV.getPlugin().getAccountLinkManager() == null) return;
        UUID linkedUuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(event.getUser().getId());
        if (linkedUuid == null) {
            DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Not handling unban for user " + event.getUser() + " because they didn't have a linked account");
            return;
        }

        String playerName = DiscordSRV.getPlatform().getPlayerName(linkedUuid);
        if (playerName == null) return; // player hasn't played before

        if (!DiscordSRV.config().getBoolean("BanSynchronizationDiscordToMinecraft")) {
            DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Not handling unban for user " + event.getUser() + " because doing so is disabled in the config");
            return;
        }

        if (StringUtils.isNotBlank(playerName) && DiscordSRV.getPlatform().isBanned(linkedUuid)) {
            BanSynchronizer.ignoreNextChange(linkedUuid); // don't sync this unban back to Discord
            DiscordSRV.getPlatform().unban(linkedUuid);
        }
    }

}
