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
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;
import github.scarsz.discordsrv.util.*;
import net.dv8tion.jda.api.entities.User;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.commons.lang3.StringUtils;
import github.scarsz.discordsrv.platform.CommandSender;
import github.scarsz.discordsrv.platform.GamePlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CommandLink {

    @Command(commandNames = { "link" },
            helpMessage = "Generates a code to link your Minecraft account to your Discord account",
            permission = "discordsrv.link"
    )
    public static void execute(CommandSender sender, String[] args) {
        AccountLinkManager manager = DiscordSRV.getPlugin().getAccountLinkManager();
        if (manager == null) {
            MessageUtil.sendMessage(sender, LangUtil.Message.UNABLE_TO_LINK_ACCOUNTS_RIGHT_NOW.toString());
            return;
        }

        SchedulerUtil.runTaskAsynchronously(() -> executeAsync(sender, args, manager));
    }

    @SuppressWarnings({"deprecation", "ConstantConditions"})
    private static void executeAsync(CommandSender sender, String[] args, AccountLinkManager manager) {
        // assume manual link
        if (args.length >= 2) {
            if (!GamePermissionUtil.hasPermission(sender, "discordsrv.link.others")) {
                sender.sendMessage(LangUtil.Message.NO_PERMISSION.toString());
                return;
            }

            List<String> arguments = new ArrayList<>(Arrays.asList(args));
            String minecraft = arguments.remove(0);
            String discord = String.join(" ", arguments);

            UUID playerUuid = null;

            try {
                playerUuid = UUID.fromString(minecraft);
            } catch (IllegalArgumentException ignored) {}

            if (playerUuid == null) playerUuid = CommandLinked.findPlayer(minecraft);
            if (playerUuid == null) {
                MessageUtil.sendMessage(sender, "§cMinecraft player could not be found");
                return;
            }
            String playerName = DiscordSRV.getPlatform().getPlayerName(playerUuid);

            User user = null;
            try {
                user = DiscordUtil.getJda().getUserById(discord);
            } catch (IllegalArgumentException ignored) {}

            if (user == null) {
                // Discord no longer has discriminators, look up the user by their (unique) username
                String username = discord.contains("#") ? discord.substring(0, discord.indexOf('#')) : discord;
                user = DiscordUtil.getJda().getUsersByName(username, true).stream().findFirst().orElse(null);
            }

            if (user == null) {
                MessageUtil.sendMessage(sender, "§cDiscord user could not be found");
                return;
            }

            DiscordSRV.getPlugin().getAccountLinkManager().link(user.getId(), playerUuid);
            MessageUtil.sendMessage(sender, "§aLinked together §6" + (playerName != null ? playerName : minecraft)
                    + "§a and §6" + user.getName());
            return;
        }

        if (!(sender instanceof GamePlayer)) {
            sender.sendMessage("§c" + LangUtil.InternalMessage.PLAYER_ONLY_COMMAND.toString());
            return;
        }
        GamePlayer player = (GamePlayer) sender;

        // prevent people from generating multiple link codes then claiming them all at once to get multiple rewards
        new ArrayList<>(manager.getLinkingCodes().entrySet()).stream()
                .filter(entry -> entry.getValue().equals(player.getUniqueId()))
                .forEach(match -> manager.getLinkingCodes().remove(match.getKey()));

        if (manager.getDiscordId(player.getUniqueId()) != null) {
            MessageUtil.sendMessage(sender, LangUtil.Message.ACCOUNT_ALREADY_LINKED.toString());
        } else {
            String code = manager.generateCode(player.getUniqueId());

            // load message text
            String message = LangUtil.Message.CODE_GENERATED.toString()
                    .replace("%code%", code)
                    .replace("%botname%", DiscordSRV.getPlugin().getMainGuild().getSelfMember().getEffectiveName());
            // replace additional placeholders
            message = PlaceholderUtil.replacePlaceholders(message, player);
            // build message component
            Component component = LegacyComponentSerializer.builder().character('&').extractUrls().build().deserialize(message);

            String clickToCopyCode = LangUtil.Message.CLICK_TO_COPY_CODE.toString();
            if (StringUtils.isNotBlank(clickToCopyCode)) {
                component = component.clickEvent(ClickEvent.copyToClipboard(code))
                        .hoverEvent(HoverEvent.showText(
                                LegacyComponentSerializer.legacy('&').deserialize(clickToCopyCode)
                        ));
            }

            MessageUtil.sendMessage(sender, component);
        }
    }

}
