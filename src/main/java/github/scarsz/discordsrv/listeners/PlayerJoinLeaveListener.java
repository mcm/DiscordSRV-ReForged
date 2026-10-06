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
import github.scarsz.discordsrv.objects.MessageFormat;
import github.scarsz.discordsrv.objects.managers.GroupSynchronizationManager;
import github.scarsz.discordsrv.platform.GamePlayer;
import github.scarsz.discordsrv.platform.event.GameListener;
import github.scarsz.discordsrv.platform.event.PlayerJoinEvent;
import github.scarsz.discordsrv.platform.event.PlayerQuitEvent;
import github.scarsz.discordsrv.util.*;

public class PlayerJoinLeaveListener implements GameListener {

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        final GamePlayer player = event.getPlayer();

        if (DiscordSRV.getPlugin().isGroupRoleSynchronizationEnabled()) {
            // trigger a synchronization for the player
            SchedulerUtil.runTaskAsynchronously(() ->
                    DiscordSRV.getPlugin().getGroupSynchronizationManager().resync(
                            player.getUniqueId(),
                            GroupSynchronizationManager.SyncDirection.AUTHORITATIVE,
                            true,
                            GroupSynchronizationManager.SyncCause.PLAYER_JOIN
                    )
            );
        }

        if (PlayerUtil.isVanished(player)) {
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not sending a join message for " + player.getName() + " because a vanish plugin reported them as vanished");
            return;
        }

        MessageFormat messageFormat = player.hasPlayedBefore()
                ? DiscordSRV.getPlugin().getMessageFromConfiguration("MinecraftPlayerJoinMessage")
                : DiscordSRV.getPlugin().getMessageFromConfiguration("MinecraftPlayerFirstJoinMessage");

        // make sure join messages enabled
        if (messageFormat == null) return;

        final String name = player.getName();

        // check if player has permission to not have join messages
        if (GamePermissionUtil.hasPermission(player, "discordsrv.silentjoin")) {
            DiscordSRV.info(LangUtil.InternalMessage.SILENT_JOIN.toString()
                    .replace("{player}", name)
            );
            return;
        }

        // player doesn't have silent join permission, send join message

        // schedule command to run in a second to be able to capture display name
        String message = event.getJoinMessage() != null ? MessageUtil.toLegacy(event.getJoinMessage()) : null;
        SchedulerUtil.runTaskLaterAsynchronously(() ->
                DiscordSRV.getPlugin().sendJoinMessage(player, message), 20);

        // if enabled, set the player's discord nickname as their ign
        if (DiscordSRV.config().getBoolean("NicknameSynchronizationEnabled")) {
            SchedulerUtil.runTaskAsynchronously(() -> {
                final String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId());
                DiscordSRV.getPlugin().getNicknameUpdater().setNickname(DiscordUtil.getMemberById(discordId), player);
            });
        }
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        final GamePlayer player = event.getPlayer();
        if (PlayerUtil.isVanished(player)) {
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not sending a quit message for " + player.getName() + " because a vanish plugin reported them as vanished");
            return;
        }

        MessageFormat messageFormat = DiscordSRV.getPlugin().getMessageFromConfiguration("MinecraftPlayerLeaveMessage");

        // make sure quit messages enabled
        if (messageFormat == null) return;

        final String name = player.getName();

        // no quit message, user shouldn't have one from permission
        if (GamePermissionUtil.hasPermission(player, "discordsrv.silentquit")) {
            DiscordSRV.info(LangUtil.InternalMessage.SILENT_QUIT.toString()
                    .replace("{player}", name)
            );
            return;
        }

        // player doesn't have silent quit, show quit message
        String message = event.getQuitMessage() != null ? MessageUtil.toLegacy(event.getQuitMessage()) : null;
        SchedulerUtil.runTaskAsynchronously(() -> DiscordSRV.getPlugin().sendLeaveMessage(player, message));
    }

}
