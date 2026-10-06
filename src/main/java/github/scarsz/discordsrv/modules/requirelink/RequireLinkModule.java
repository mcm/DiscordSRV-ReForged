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

package github.scarsz.discordsrv.modules.requirelink;

import alexh.weak.Dynamic;
import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.platform.GamePlayer;
import github.scarsz.discordsrv.platform.Platform;
import github.scarsz.discordsrv.platform.event.GameListener;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.SchedulerUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Requires players to have a linked Discord account (and optionally be in the Discord server / have a subscriber
 * role) to play. The platform calls {@link #check(String, UUID, String)} (via DiscordSRV#checkLogin) when a player
 * logs in, after the vanilla ban &amp; whitelist checks passed.
 * <p>
 * The Spigot version's "Listener priority" and "Listener event" options don't apply here and are ignored.
 */
public class RequireLinkModule implements GameListener {

    /**
     * Maximum amount of time to wait for Discord when checking whether a player's linked account is in a Discord server
     * and the member isn't cached. {@link #check(String, UUID, String)} runs on the server thread, so this must stay short.
     */
    private static final long MEMBER_LOOKUP_TIMEOUT_MILLIS = 3000;

    public RequireLinkModule() {
        // registered as a game listener by DiscordSRV
    }

    /**
     * Checks whether the given player is allowed to join
     *
     * @param playerName the name of the player logging in
     * @param playerUuid the uuid of the player logging in
     * @param ip the ip address the player is connecting from, may be null
     * @return the (legacy § formatted) kick message if the player should be denied, null if they're allowed to join
     */
    public String check(String playerName, UUID playerUuid, String ip) {
        if (!isEnabled()) return null;

        try {
            if (getBypassNames().contains(playerName)) {
                DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + playerName + " is on the bypass list, bypassing linking checks");
                return null;
            }

            Platform platform = DiscordSRV.getPlatform();
            if (checkWhitelist()) {
                boolean whitelisted = platform.isWhitelisted(playerUuid, playerName);
                if (whitelisted) {
                    DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + playerName + " is bypassing link requirement, player is whitelisted");
                    return null;
                }
            }
            boolean onlyCheckBannedPlayers = onlyCheckBannedPlayers();
            if (!checkBannedPlayers() || onlyCheckBannedPlayers) {
                boolean banned = false;
                if (platform.isBanned(playerUuid)) {
                    if (!onlyCheckBannedPlayers) {
                        DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + playerName + " is banned, skipping linked check");
                        return null;
                    }
                    banned = true;
                }
                if (!banned && ip != null && platform.isIpBanned(ip)) {
                    if (!onlyCheckBannedPlayers) {
                        DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + playerName + " connecting with banned IP " + ip + ", skipping linked check");
                        return null;
                    }
                    banned = true;
                }
                if (onlyCheckBannedPlayers && !banned) {
                    DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + playerName + " is bypassing link requirement because \"Only check banned players\" is enabled");
                    return null;
                }
            }

            if (!DiscordSRV.isReady) {
                DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + playerName + " connecting before DiscordSRV is ready, denying login");
                return MessageUtil.translateLegacy(getDiscordSRVStillStartingKickMessage());
            }

            String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordIdBypassCache(playerUuid);
            if (discordId == null) {
                Member botMember = DiscordSRV.getPlugin().getMainGuild().getSelfMember();
                String botName = botMember.getEffectiveName();
                String code = DiscordSRV.getPlugin().getAccountLinkManager().generateCode(playerUuid);
                String inviteLink = DiscordSRV.config().getString("DiscordInviteLink");

                DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + playerName + " is NOT linked to a Discord account, denying login");
                return MessageUtil.translateLegacy(DiscordSRV.config().getString("Require linked account to play.Not linked message"))
                        .replace("{BOT}", botName)
                        .replace("{CODE}", code)
                        .replace("{INVITE}", inviteLink);
            }

            Dynamic mustBeInDiscordServerOption = DiscordSRV.config().dget("Require linked account to play.Must be in Discord server");
            if (mustBeInDiscordServerOption.is(Boolean.class)) {
                boolean mustBePresent = mustBeInDiscordServerOption.as(Boolean.class);
                if (mustBePresent) {
                    boolean isPresent = false;
                    for (Guild guild : DiscordUtil.getJda().getGuilds()) {
                        if (isMember(guild, discordId)) {
                            isPresent = true;
                            break;
                        }
                    }
                    if (!isPresent) {
                        DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + playerName + "'s linked Discord account is NOT present, denying login");
                        return MessageUtil.translateLegacy(DiscordSRV.config().getString("Require linked account to play.Messages.Not in server"))
                                .replace("{INVITE}", DiscordSRV.config().getString("DiscordInviteLink"));
                    }
                }
            } else {
                Set<String> targets = new HashSet<>();

                if (mustBeInDiscordServerOption.isList()) {
                    mustBeInDiscordServerOption.children().forEach(dynamic -> targets.add(dynamic.toString()));
                } else {
                    targets.add(mustBeInDiscordServerOption.convert().intoString());
                }

                for (String guildId : targets) {
                    try {
                        Guild guild = DiscordUtil.getJda().getGuildById(guildId);
                        if (guild != null) {
                            boolean inServer = isMember(guild, discordId);
                            if (!inServer) {
                                DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + playerName + "'s linked Discord account is NOT present, denying login");
                                return MessageUtil.translateLegacy(DiscordSRV.config().getString("Require linked account to play.Messages.Not in server"))
                                        .replace("{INVITE}", DiscordSRV.config().getString("DiscordInviteLink"));
                            }
                        } else {
                            DiscordSRV.debug(Debug.REQUIRE_LINK, "Failed to get Discord server by ID " + guildId + ": bot is not in server");
                        }
                    } catch (NumberFormatException e) {
                        DiscordSRV.debug(Debug.REQUIRE_LINK, "Failed to get Discord server by ID " + guildId + ": not a parsable long");
                    }
                }
            }

            List<String> subRoleIds = DiscordSRV.config().getStringList("Require linked account to play.Subscriber role.Subscriber roles");
            if (isSubRoleRequired() && !subRoleIds.isEmpty()) {
                int failedRoleIds = 0;
                int matches = 0;

                for (String subRoleId : subRoleIds) {
                    if (StringUtils.isBlank(subRoleId)) {
                        failedRoleIds++;
                        continue;
                    }

                    Role role = null;
                    try {
                        role = DiscordUtil.getJda().getRoleById(subRoleId);
                    } catch (Throwable ignored) {}
                    if (role == null) {
                        failedRoleIds++;
                        continue;
                    }

                    Member member = role.getGuild().getMemberById(discordId);
                    if (member != null && member.getRoles().contains(role)) {
                        matches++;
                    }
                }

                if (failedRoleIds == subRoleIds.size()) {
                    DiscordSRV.error("Tried to authenticate " + playerName + " but no valid subscriber role IDs are found and thats a requirement; login will be denied until this is fixed.");
                    return MessageUtil.translateLegacy(getFailedToFindRoleKickMessage());
                }

                if (getAllSubRolesRequired() ? matches < subRoleIds.size() : matches == 0) {
                    DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + playerName + " does NOT match subscriber role requirements, denying login");
                    return MessageUtil.translateLegacy(getSubscriberRoleKickMessage());
                }
            }
        } catch (Exception exception) {
            DiscordSRV.error("Failed to check player: " + playerName, exception);
            return MessageUtil.translateLegacy(getUnknownFailureKickMessage());
        }
        return null;
    }

    /**
     * Checks whether the given Discord user is a member of the given guild, using JDA's member cache when possible
     * and falling back to a (time limited) request to Discord if the guild's members aren't fully cached
     */
    private boolean isMember(Guild guild, String discordId) throws Exception {
        if (guild.getMemberById(discordId) != null) return true;
        if (guild.isLoaded()) return false; // all members are cached, they aren't a member

        try {
            return guild.retrieveMemberById(discordId).submit().get(MEMBER_LOOKUP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) != null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ErrorResponseException) {
                ErrorResponse response = ((ErrorResponseException) e.getCause()).getErrorResponse();
                if (response == ErrorResponse.UNKNOWN_MEMBER || response == ErrorResponse.UNKNOWN_USER) return false;
            }
            throw e;
        }
    }

    public void noticePlayerUnlink(GamePlayer player) {
        if (!isEnabled()) return;
        if (getBypassNames().contains(player.getName())) return;
        Platform platform = DiscordSRV.getPlatform();
        if (checkWhitelist()) {
            boolean whitelisted = platform.isWhitelisted(player.getUniqueId(), player.getName());
            if (whitelisted) {
                DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + player.getName() + " is bypassing link requirement, player is whitelisted");
                return;
            }
        }
        // the ip of online players isn't available, only the player ban list is checked here
        if (onlyCheckBannedPlayers() && !platform.isBanned(player.getUniqueId())) {
            DiscordSRV.debug(Debug.REQUIRE_LINK, "Player " + player.getName() + " is bypassing link requirement because \"Only check banned players\" is enabled");
            return;
        }

        DiscordSRV.info("Kicking player " + player.getName() + " for unlinking their accounts");
        SchedulerUtil.runTask(() -> player.kick(MessageUtil.toComponent(MessageUtil.translateLegacy(getUnlinkedKickMessage()))));
    }

    private boolean checkWhitelist() {
        return DiscordSRV.config().getBoolean("Require linked account to play.Whitelisted players bypass check");
    }
    private boolean checkBannedPlayers() {
        return DiscordSRV.config().getBoolean("Require linked account to play.Check banned players");
    }
    private boolean onlyCheckBannedPlayers() {
        return DiscordSRV.config().getBoolean("Require linked account to play.Only check banned players");
    }
    private boolean getAllSubRolesRequired() {
        return DiscordSRV.config().getBoolean("Require linked account to play.Subscriber role.Require all of the listed roles");
    }
    public boolean isEnabled() {
        return DiscordSRV.config().getBoolean("Require linked account to play.Enabled");
    }
    private boolean isSubRoleRequired() {
        return DiscordSRV.config().getBoolean("Require linked account to play.Subscriber role.Require subscriber role to join");
    }
    private Set<String> getBypassNames() {
        return new HashSet<>(DiscordSRV.config().getStringList("Require linked account to play.Bypass names"));
    }
    private String getDiscordSRVStillStartingKickMessage() {
        return DiscordSRV.config().getString("Require linked account to play.Messages.DiscordSRV still starting");
    }
    private String getFailedToFindRoleKickMessage() {
        return DiscordSRV.config().getString("Require linked account to play.Messages.Failed to find subscriber role");
    }
    private String getSubscriberRoleKickMessage() {
        return DiscordSRV.config().getString("Require linked account to play.Subscriber role.Kick message");
    }
    private String getUnknownFailureKickMessage() {
        return DiscordSRV.config().getString("Require linked account to play.Messages.Failed for unknown reason");
    }
    private String getUnlinkedKickMessage() {
        return DiscordSRV.config().getString("Require linked account to play.Messages.Kicked for unlinking");
    }

}
