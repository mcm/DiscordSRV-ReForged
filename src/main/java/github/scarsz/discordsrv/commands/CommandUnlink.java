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
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.SchedulerUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import github.scarsz.discordsrv.platform.CommandSender;
import github.scarsz.discordsrv.platform.GamePlayer;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CommandUnlink {

    @Command(commandNames = { "unlink", "clearlinked" },
            helpMessage = "Unlinks your Minecraft account from your Discord account",
            permission = "discordsrv.unlink"
    )
    public static void execute(CommandSender sender, String[] args) {
        SchedulerUtil.runTaskAsynchronously(() -> executeAsync(sender, args));
    }

    private static void executeAsync(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof GamePlayer)) {
                MessageUtil.sendMessage(sender, "§c" + LangUtil.InternalMessage.NO_UNLINK_TARGET_SPECIFIED.toString());
                return;
            }

            GamePlayer player = (GamePlayer) sender;
            String linkedId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player.getUniqueId());
            boolean hasLinkedAccount = linkedId != null;

            if (hasLinkedAccount) {
                Member member = DiscordUtil.getMemberById(linkedId);
                String name = member != null ? member.getEffectiveName() : "Discord ID " + linkedId;

                DiscordSRV.getPlugin().getAccountLinkManager().unlink(player.getUniqueId());
                MessageUtil.sendMessage(sender, LangUtil.Message.UNLINK_SUCCESS.toString().replace("%name%", name));
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

            if (target.length() == 32 || target.length() == 36 && args.length == 1) {
                // target is UUID
                CommandLinked.notifyInterpret(sender, "UUID");
                UUID player = CommandLinked.parseUuid(target);
                CommandLinked.notifyPlayer(sender, player);
                String discordId = player != null ? DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player) : null;
                CommandLinked.notifyDiscord(sender, discordId);
                if (discordId != null) {
                    DiscordSRV.getPlugin().getAccountLinkManager().unlink(discordId);
                    notifyUnlinked(sender);
                }
                return;
            } else if (args.length == 1 && DiscordUtil.getUserById(target) != null ||
                    (StringUtils.isNumeric(target) && target.length() >= 17 && target.length() <= 20)) {
                // target is a Discord ID
                CommandLinked.notifyInterpret(sender, "Discord ID");
                UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(target);
                CommandLinked.notifyPlayer(sender, uuid);
                CommandLinked.notifyDiscord(sender, target);
                if (uuid != null) {
                    DiscordSRV.getPlugin().getAccountLinkManager().unlink(uuid);
                    notifyUnlinked(sender);
                }
                return;
            } else {
                if (args.length == 1 && target.length() >= 3 && target.length() <= 16) {
                    // target is probably a Minecraft player name
                    UUID player = CommandLinked.findPlayer(target);

                    if (player != null) {
                        // found them
                        CommandLinked.notifyInterpret(sender, "Minecraft player");
                        CommandLinked.notifyPlayer(sender, player);
                        CommandLinked.notifyDiscord(sender, DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player));

                        DiscordSRV.getPlugin().getAccountLinkManager().unlink(player);
                        notifyUnlinked(sender);
                        return;
                    }
                }

                if (joinedTarget.contains("#") || (joinedTarget.length() >= 2 && joinedTarget.length() <= 32 + 5)) {
                    // target is a discord name... probably.
                    // Discord no longer has discriminators, anything after a # is ignored
                    String targetUsername = joinedTarget.contains("#") ? joinedTarget.split("#")[0] : joinedTarget;

                    Set<User> matches = DiscordSRV.getPlugin().getMainGuild().getMembers().stream()
                            .filter(member -> member.getUser().getName().equalsIgnoreCase(targetUsername)
                                    || (member.getNickname() != null && member.getNickname().equalsIgnoreCase(targetUsername)))
                            .map(Member::getUser)
                            .collect(Collectors.toSet());

                    if (matches.size() != 0) {
                        CommandLinked.notifyInterpret(sender, "Discord name");

                        if (matches.size() == 1) {
                            User user = matches.iterator().next();
                            CommandLinked.notifyDiscord(sender, user.getId());
                            UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(user.getId());
                            if (uuid != null) {
                                CommandLinked.notifyPlayer(sender, uuid);
                                DiscordSRV.getPlugin().getAccountLinkManager().unlink(user.getId());
                                notifyUnlinked(sender);
                            } else {
                                CommandLinked.notifyPlayer(sender, null);
                            }
                        } else {
                            matches.stream().limit(5).forEach(user -> {
                                UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(user.getId());
                                CommandLinked.notifyPlayer(sender, uuid);
                                CommandLinked.notifyDiscord(sender, user.getId());
                            });

                            int remaining = matches.size() - 5;
                            if (remaining >= 1) {
                                MessageUtil.sendMessage(sender, String.format("%s+%s%d%s more result%s...",
                                        "§b", "§f", remaining, "§b",
                                        remaining > 1 ? "s" : "")
                                );
                            }

                            MessageUtil.sendMessage(sender, "§bBe more specific.");
                        }
                        return;
                    }
                }
            }

            // no matches at all found
            MessageUtil.sendMessage(sender, LangUtil.Message.LINKED_NOBODY_FOUND.toString().replace("%target%", joinedTarget));
        }
    }

    private static void notifyUnlinked(CommandSender sender) {
        MessageUtil.sendMessage(sender, "§f- §b Unlinked ✓");
    }

}
