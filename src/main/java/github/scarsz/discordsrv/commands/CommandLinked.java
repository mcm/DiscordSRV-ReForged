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

package github.scarsz.discordsrv.commands;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.*;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import github.scarsz.discordsrv.platform.CommandSender;
import github.scarsz.discordsrv.platform.GamePlayer;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CommandLinked {

    @Command(commandNames = { "linked" },
            helpMessage = "Checks what Discord user your (or someone else's) MC account is linked to",
            permission = "discordsrv.linked"
    )
    public static void execute(CommandSender sender, String[] args) {
        SchedulerUtil.runTaskAsynchronously(() -> executeAsync(sender, args));
    }

    private static void executeAsync(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof GamePlayer)) {
                MessageUtil.sendMessage(sender, LangUtil.Message.LINKED_NOBODY_FOUND.toString()
                        .replace("%target%", "CONSOLE")
                );
                return;
            }

            String linkedId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(((GamePlayer) sender).getUniqueId());
            boolean hasLinkedAccount = linkedId != null;

            if (hasLinkedAccount) {
                Member member = DiscordUtil.getMemberById(linkedId);
                String name = member != null ? member.getEffectiveName() : "Discord ID " + linkedId;

                MessageUtil.sendMessage(sender, LangUtil.Message.LINKED_SUCCESS.toString().replace("%name%", name));
            } else {
                MessageUtil.sendMessage(sender, LangUtil.Message.LINK_FAIL_NOT_ASSOCIATED_WITH_AN_ACCOUNT.toString());
            }
        } else {
            if (!sender.hasPermission("discordsrv.linked.others")) {
                MessageUtil.sendMessage(sender, LangUtil.Message.NO_PERMISSION.toString());
                return;
            }

            String target = args[0];
            String joinedTarget = String.join(" ", args);

            if (args.length == 1 && target.length() == 32 || target.length() == 36) {
                // target is UUID
                notifyInterpret(sender, "UUID");
                UUID player = parseUuid(target);
                notifyPlayer(sender, player);
                notifyDiscord(sender, player != null ? DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player) : null);
                return;
            } else if (args.length == 1 && DiscordUtil.getUserById(target) != null ||
                    (StringUtils.isNumeric(target) && target.length() >= 17 && target.length() <= 20)) {
                // target is a Discord ID
                notifyInterpret(sender, "Discord ID");
                UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(target);
                notifyPlayer(sender, uuid);
                notifyDiscord(sender, target);
                return;
            } else {
                if (joinedTarget.contains("#") || (joinedTarget.length() >= 2 && joinedTarget.length() <= 32 + 5)) {
                    // target is a discord name... probably.
                    // Discord no longer has discriminators, anything after a # is ignored
                    String targetUsername = joinedTarget.contains("#") ? joinedTarget.split("#")[0] : joinedTarget;

                    Set<User> matches = DiscordUtil.getJda().getGuilds().stream()
                            .flatMap(guild -> guild.getMembers().stream())
                            .filter(member -> member.getUser().getName().equalsIgnoreCase(targetUsername)
                                    || (member.getNickname() != null && member.getNickname().equalsIgnoreCase(targetUsername)))
                            .map(Member::getUser)
                            .collect(Collectors.toSet());

                    if (matches.size() >= 1) {
                        notifyInterpret(sender, "Discord name");

                        matches.stream().limit(5).forEach(user -> {
                            UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(user.getId());
                            notifyPlayer(sender, uuid);
                            notifyDiscord(sender, user.getId());
                        });

                        int remaining = matches.size() - 5;
                        if (remaining >= 1) {
                            MessageUtil.sendMessage(sender, String.format("%s+%s%d%s more result%s...",
                                    "§b", "§f", remaining, "§b",
                                    remaining > 1 ? "s" : "")
                            );
                        }
                        return;
                    }
                }

                if (args.length == 1 && target.length() >= 3 && target.length() <= 16) {
                    // target is probably a Minecraft player name
                    UUID player = findPlayer(target);

                    if (player != null) {
                        // found them
                        notifyInterpret(sender, "Minecraft player");
                        notifyPlayer(sender, player);
                        notifyDiscord(sender, DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player));
                        return;
                    }
                }
            }

            // no matches at all found
            MessageUtil.sendMessage(sender, LangUtil.Message.LINKED_NOBODY_FOUND.toString().replace("%target%", joinedTarget));
        }
    }

    /**
     * @return the uuid parsed from the given (dashed or undashed) uuid string, or null if it's not a valid uuid
     */
    static UUID parseUuid(String target) {
        try {
            if (target.length() == 32) {
                target = target.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
            }
            return UUID.fromString(target);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Finds a player that is online or has played on the server before by name
     * @return the player's uuid or null if no such player was found
     */
    static UUID findPlayer(String name) {
        GamePlayer onlinePlayer = PlayerUtil.getOnlinePlayers().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst().orElse(null);
        if (onlinePlayer != null) return onlinePlayer.getUniqueId();

        UUID uuid = DiscordSRV.getPlatform().getPlayerUuid(name);
        if (uuid == null || DiscordSRV.getPlatform().getPlayerName(uuid) == null) {
            // player doesn't actually exist
            return null;
        }
        return uuid;
    }

    static void notifyInterpret(CommandSender sender, String type) {
        MessageUtil.sendMessage(sender, String.format("%sInterpreted target as %s%s",
                "§b", "§f", type)
        );
    }

    static void notifyPlayer(CommandSender sender, UUID player) {
        MessageUtil.sendMessage(sender, String.format("%s-%s Player: %s%s",
                "§f", "§b", "§f", PrettyUtil.beautifyNickname(player))
        );
    }

    static void notifyDiscord(CommandSender sender, String discordId) {
        User user = discordId != null ? DiscordUtil.getUserById(discordId) : null;
        String discordInfo = (user != null ? " (" + user.getName() + ")" : "") + " " + discordId;
        MessageUtil.sendMessage(sender, String.format("%s-%s Discord: %s%s%s",
                "§f", "§b", "§f", PrettyUtil.beautify(user), discordInfo)
        );
    }

}
