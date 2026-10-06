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
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.SchedulerUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

/**
 * Minecraft -> Discord ban synchronization (BanSynchronizationMinecraftToDiscord).
 * <p>
 * Replaces the Spigot version's PlayerBanListener: there is no ban event on NeoForge, so the server's ban list is
 * polled periodically and compared to the previous snapshot. Players that got banned get their linked Discord
 * account banned from the main guild, players that got unbanned get their linked Discord account unbanned.
 */
public class BanSynchronizer {

    private static final long POLL_INTERVAL_TICKS = 20 * 10; // 10 seconds
    private static final long IGNORE_EXPIRATION_MILLIS = TimeUnit.MINUTES.toMillis(1);

    /**
     * Players whose next ban list change originated from Discord (Discord -> Minecraft ban synchronization), with
     * the time they were marked. These changes must not be synchronized back to Discord.
     */
    private static final Map<UUID, Long> IGNORED_CHANGES = new ConcurrentHashMap<>();

    /**
     * Marks the next ban/unban of the given player as originating from Discord, so it's not synchronized back to
     * Discord. Call this right before banning/unbanning the player on the server due to a Discord (un)ban.
     *
     * @param uuid the player's uuid
     */
    public static void ignoreNextChange(UUID uuid) {
        if (uuid != null) IGNORED_CHANGES.put(uuid, System.currentTimeMillis());
    }

    private Set<UUID> previouslyBanned = null;
    private ScheduledFuture<?> task = null;

    public synchronized void start() {
        shutdown();
        task = SchedulerUtil.runTaskTimerAsynchronously(this::poll, POLL_INTERVAL_TICKS, POLL_INTERVAL_TICKS);
    }

    public synchronized void shutdown() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        previouslyBanned = null;
    }

    private synchronized void poll() {
        Set<UUID> banned;
        try {
            banned = new HashSet<>(DiscordSRV.getPlatform().getBannedPlayers());
        } catch (Exception e) {
            DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, e, "Failed to retrieve the server's ban list");
            return;
        }

        // expire stale ignore markers
        long now = System.currentTimeMillis();
        IGNORED_CHANGES.values().removeIf(time -> now - time > IGNORE_EXPIRATION_MILLIS);

        Set<UUID> previous = previouslyBanned;
        previouslyBanned = banned;
        if (previous == null) return; // first poll only records the current state

        Set<UUID> newlyBanned = new HashSet<>(banned);
        newlyBanned.removeAll(previous);
        Set<UUID> newlyUnbanned = new HashSet<>(previous);
        newlyUnbanned.removeAll(banned);

        for (UUID uuid : newlyBanned) {
            if (IGNORED_CHANGES.remove(uuid) != null) {
                DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Not handling ban for player " + name(uuid) + " (" + uuid + ") because it originated from Discord");
                continue;
            }
            handleBan(uuid);
        }
        for (UUID uuid : newlyUnbanned) {
            if (IGNORED_CHANGES.remove(uuid) != null) {
                DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Not handling unban for player " + name(uuid) + " (" + uuid + ") because it originated from Discord");
                continue;
            }
            handleUnban(uuid);
        }
    }

    private void handleBan(UUID uuid) {
        String name = name(uuid);
        if (!DiscordSRV.config().getBoolean("BanSynchronizationMinecraftToDiscord")) {
            DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Not handling ban for player " + name + " (" + uuid + ") because doing so is disabled in the config");
            return;
        }

        AccountLinkManager accountLinkManager = DiscordSRV.getPlugin().getAccountLinkManager();
        if (accountLinkManager == null) return;
        String discordId = accountLinkManager.getDiscordIdBypassCache(uuid);
        if (discordId == null) {
            DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Not handling ban for player " + name + " (" + uuid + ") because they didn't have a linked account");
            return;
        }

        Guild guild = DiscordSRV.getPlugin().getMainGuild();
        Member member = guild != null ? guild.getMemberById(discordId) : null;
        if (member == null) {
            DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Not handling ban for player " + name + " (" + uuid + ") because their linked Discord account (" + discordId + ") isn't in the main guild");
            return;
        }

        DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Handling ban for player " + name + " (" + uuid + ")");
        DiscordUtil.banMember(member);
    }

    private void handleUnban(UUID uuid) {
        String name = name(uuid);
        if (!DiscordSRV.config().getBoolean("BanSynchronizationMinecraftToDiscord")) {
            DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Not handling unban for player " + name + " (" + uuid + ") because doing so is disabled in the config");
            return;
        }

        AccountLinkManager accountLinkManager = DiscordSRV.getPlugin().getAccountLinkManager();
        if (accountLinkManager == null) return;
        String discordId = accountLinkManager.getDiscordIdBypassCache(uuid);
        if (discordId == null) return;

        Guild guild = DiscordSRV.getPlugin().getMainGuild();
        if (guild == null) return;

        guild.retrieveBan(User.fromId(discordId)).queue(ban -> {
            DiscordSRV.info("Unbanning player " + name + " from Discord (ID " + discordId + ") because they aren't banned on the server");
            DiscordUtil.unbanUser(guild, ban.getUser());
        }, failure -> DiscordSRV.debug(Debug.BAN_SYNCHRONIZATION, "Failed to check if player " + name + " is banned in Discord: " + failure.getMessage()));
    }

    private static String name(UUID uuid) {
        String name = DiscordSRV.getPlatform().getPlayerName(uuid);
        return name != null ? name : "<unknown>";
    }

}
