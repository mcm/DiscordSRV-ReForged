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

package github.scarsz.discordsrv.objects.managers.link;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.events.AccountLinkedEvent;
import github.scarsz.discordsrv.api.events.AccountUnlinkedEvent;
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;
import github.scarsz.discordsrv.objects.managers.GroupSynchronizationManager;
import github.scarsz.discordsrv.hooks.permissions.GroupHook;
import github.scarsz.discordsrv.platform.GamePlayer;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.PrettyUtil;
import lombok.Getter;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public abstract class AbstractAccountLinkManager implements AccountLinkManager {

    @Getter
    protected final Map<String, UUID> linkingCodes = new ConcurrentHashMap<>();

    @Override
    public String generateCode(UUID playerUuid) {
        String codeString;
        do {
            int code = ThreadLocalRandom.current().nextInt(10000);
            codeString = String.format("%04d", code);
        } while (linkingCodes.putIfAbsent(codeString, playerUuid) != null);
        return codeString;
    }

    private final Set<String> nagged = new HashSet<>();
    protected void ensureOffThread(boolean single) {
        if (!DiscordSRV.getPlatform().isMainThread()) return;

        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        String apiUser = elements[3].toString();
        if (!nagged.add(apiUser)) return;

        if (apiUser.startsWith("github.scarsz.discordsrv")) {
            DiscordSRV.warning("Linked account data requested on main thread, please report this to DiscordSRV: " + apiUser);
            for (StackTraceElement element : elements) DiscordSRV.debug(Debug.ACCOUNT_LINKING, element.toString());
            return;
        }

        DiscordSRV.warning("API user " + apiUser + " requested linked account information on the main thread while MySQL is enabled in DiscordSRV's settings");
        if (single) {
            DiscordSRV.warning("Requesting data for offline players on the main thread will lead to an exception in the future, if being on the main thread is explicitly required use getDiscordIdBypassCache / getUuidBypassCache");
        } else {
            DiscordSRV.warning("Managing / Requesting bulk linked account data on the main thread will lead to an exception in the future");
        }
        DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Full callstack:");
        for (StackTraceElement element : elements) DiscordSRV.debug(Debug.ACCOUNT_LINKING, element.toString());
    }

    protected void afterLink(String discordId, UUID uuid) {
        // call link event
        DiscordSRV.api.callEvent(new AccountLinkedEvent(DiscordUtil.getUserById(discordId), uuid));

        // trigger server commands
        String playerName = DiscordSRV.getPlatform().getPlayerName(uuid);
        User user = DiscordUtil.getUserById(discordId);
        for (String command : DiscordSRV.config().getStringList("MinecraftDiscordAccountLinkedConsoleCommands")) {
            DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Parsing command /" + command + " for linked commands...");
            command = command
                    .replace("%minecraftplayername%", PrettyUtil.beautifyUsername(uuid, "[Unknown Player]", false))
                    .replace("%minecraftdisplayname%", PrettyUtil.beautifyNickname(uuid, "[Unknown Player]", false))
                    .replace("%minecraftuuid%", uuid.toString())
                    .replace("%discordid%", discordId)
                    .replace("%discordname%", user != null ? user.getName() : "")
                    .replace("%discorddisplayname%", PrettyUtil.beautify(user, "", false));
            if (StringUtils.isBlank(command)) {
                DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Command was blank, skipping");
                continue;
            }
            command = PlaceholderUtil.replacePlaceholders(command, DiscordSRV.getPlatform().getPlayer(uuid));

            String finalCommand = command;
            DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Final command to be run: /" + finalCommand);
            DiscordSRV.getPlatform().executeConsoleCommand(finalCommand, feedback -> {});
        }

        // group sync using the authoritative side
        if (DiscordSRV.config().getBoolean("GroupRoleSynchronizationOnLink") && GroupHook.isEnabled()) {
            DiscordSRV.getPlugin().getGroupSynchronizationManager().resync(
                    uuid,
                    GroupSynchronizationManager.SyncDirection.AUTHORITATIVE,
                    true,
                    GroupSynchronizationManager.SyncCause.PLAYER_LINK
            );
        } else {
            String roleName = DiscordSRV.config().getString("MinecraftDiscordAccountLinkedRoleNameToAddUserTo");
            try {
                Role roleToAdd = DiscordUtil.resolveRole(roleName);
                if (roleToAdd != null) {
                    Member member = roleToAdd.getGuild().getMemberById(discordId);
                    if (member != null) {
                        DiscordUtil.addRoleToMember(member, roleToAdd);
                    } else {
                        DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Couldn't find member for " + playerName + " in " + roleToAdd.getGuild());
                    }
                } else {
                    DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Couldn't find \"account linked\" role " + roleName + " to add to " + playerName + "'s linked Discord account");
                }
            } catch (Throwable t) {
                DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Couldn't add \"account linked\" role \"" + roleName + "\" due to exception: " + ExceptionUtils.getMessage(t));
            }
        }

        // set user's discord nickname as their in-game name
        if (DiscordSRV.config().getBoolean("NicknameSynchronizationEnabled")) {
            DiscordSRV.getPlugin().getNicknameUpdater().setNickname(DiscordUtil.getMemberById(discordId), uuid);
        }
    }

    protected void beforeUnlink(UUID uuid, String discordId) {
        if (DiscordSRV.getPlugin().isGroupRoleSynchronizationEnabled()) {
            DiscordSRV.getPlugin().getGroupSynchronizationManager().removeSynchronizables(uuid);
        } else {
            try {
                // remove user from linked role
                Role role = DiscordUtil.resolveRole(DiscordSRV.config().getString("MinecraftDiscordAccountLinkedRoleNameToAddUserTo"));
                if (role != null) {
                    Member member = role.getGuild().getMemberById(discordId);
                    if (member != null) {
                        role.getGuild().removeRoleFromMember(member, role).queue();
                    } else {
                        DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Couldn't remove \"linked\" role from null member: " + uuid);
                    }
                } else {
                    DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Couldn't remove user from null \"linked\" role");
                }
            } catch (Throwable t) {
                DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Failed to remove \"linked\" role from [" + uuid + ":" + discordId + "] during unlink: " + ExceptionUtils.getMessage(t));
            }
        }
    }

    protected void afterUnlink(UUID uuid, String discordId) {
        Member member = DiscordUtil.getMemberById(discordId);

        DiscordSRV.api.callEvent(new AccountUnlinkedEvent(discordId, uuid));

        // run unlink console commands
        User user = DiscordUtil.getUserById(discordId);
        for (String command : DiscordSRV.config().getStringList("MinecraftDiscordAccountUnlinkedConsoleCommands")) {
            command = command
                    .replace("%minecraftplayername%", PrettyUtil.beautifyUsername(uuid, "[Unknown player]", false))
                    .replace("%minecraftdisplayname%", PrettyUtil.beautifyNickname(uuid, "<Unknown name>", false))
                    .replace("%minecraftuuid%", uuid.toString())
                    .replace("%discordid%", discordId)
                    .replace("%discordname%", user != null ? user.getName() : "")
                    .replace("%discorddisplayname%", PrettyUtil.beautify(user, "", false));
            if (StringUtils.isBlank(command)) continue;
            command = PlaceholderUtil.replacePlaceholders(command, DiscordSRV.getPlatform().getPlayer(uuid));

            String finalCommand = command;
            DiscordSRV.getPlatform().executeConsoleCommand(finalCommand, feedback -> {});
        }

        if (member != null && DiscordSRV.config().getBoolean("NicknameSynchronizationEnabled")) {
            if (member.getGuild().getSelfMember().canInteract(member)) {
                member.modifyNickname(null).queue();
            } else {
                DiscordSRV.debug(Debug.ACCOUNT_LINKING, "Can't remove nickname from " + member + ", bot is lower in hierarchy");
            }
        }

        GamePlayer player = DiscordSRV.getPlatform().getPlayer(uuid);
        if (player != null) {
            DiscordSRV.getPlugin().getRequireLinkModule().noticePlayerUnlink(player);
        }
    }

}
