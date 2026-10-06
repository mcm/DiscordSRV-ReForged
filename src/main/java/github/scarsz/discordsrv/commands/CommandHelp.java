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
import github.scarsz.discordsrv.util.GamePermissionUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.platform.CommandSender;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CommandHelp {

    // legacy color & formatting codes, in the same order as Bukkit's ChatColor values
    private static final String CHAT_COLORS = "0123456789abcdefklmnor";
    private static final char RESET = 'r';
    private static final String DARK_GRAY = "§8", GRAY = "§7", ITALIC = "§o";

    private static List<Character> disallowedChatColorCharacters = new ArrayList<Character>() {{
        add('0'); // black
        add('1'); // dark blue
        add('7'); // gray
        add('8'); // dark gray
        add('f'); // white
        add('k'); // magic
        add('l'); // bold
        add('m'); // strikethrough
        add('n'); // underline
        add('o'); // italic
        add(RESET);
    }};

    private static String color(char code) {
        return "§" + code;
    }

    @Command(commandNames = { "?", "help" },
            helpMessage = "Shows command help for DiscordSRV's commands",
            permission = "discordsrv.help",
            usageExample = "help [command]"
    )
    public static void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            help(sender);
        } else {
            help(sender, Arrays.asList(args));
        }
    }

    private static void help(CommandSender sender) {
        char titleColor = RESET, commandColor = RESET;
        while (disallowedChatColorCharacters.contains(titleColor))
            titleColor = CHAT_COLORS.charAt(ThreadLocalRandom.current().nextInt(CHAT_COLORS.length()));
        while (disallowedChatColorCharacters.contains(commandColor) || commandColor == titleColor)
            commandColor = CHAT_COLORS.charAt(ThreadLocalRandom.current().nextInt(CHAT_COLORS.length()));

        List<Method> commandMethods = new ArrayList<>();
        for (Method method : DiscordSRV.getPlugin().getCommandManager().getCommands().values())
            if (!commandMethods.contains(method)) commandMethods.add(method);

        MessageUtil.sendMessage(sender, DARK_GRAY + "================[ " + color(titleColor) + "DiscordSRV" + DARK_GRAY + " ]================");
        for (Method commandMethod : commandMethods) {
            Command commandAnnotation = commandMethod.getAnnotation(Command.class);

            // make sure sender has permission to run the commands before showing them permissions for it
            if (!GamePermissionUtil.hasPermission(sender, commandAnnotation.permission())) continue;

            MessageUtil.sendMessage(sender, GRAY + "- " + color(commandColor) + "/discord " + String.join("/", commandAnnotation.commandNames()));
            MessageUtil.sendMessage(sender, "    " + ITALIC + commandAnnotation.helpMessage());
            if (!commandAnnotation.usageExample().equals("")) MessageUtil.sendMessage(sender, "    " + GRAY + ITALIC + "ex. /discord " + commandAnnotation.usageExample());
        }
    }

    /**
     * Send help specific for the given commands
     * @param sender
     * @param commands
     */
    private static void help(CommandSender sender, List<String> commands) {
        char titleColor = RESET, commandColor = RESET;
        while (disallowedChatColorCharacters.contains(titleColor))
            titleColor = CHAT_COLORS.charAt(ThreadLocalRandom.current().nextInt(CHAT_COLORS.length() - 1));
        while (disallowedChatColorCharacters.contains(commandColor) || commandColor == titleColor)
            commandColor = CHAT_COLORS.charAt(ThreadLocalRandom.current().nextInt(CHAT_COLORS.length() - 1));

        List<Method> commandMethodsList = new LinkedList<>();
        Map<String, Method> commandMethods = DiscordSRV.getPlugin().getCommandManager().getCommands();
        for (String commandName : commands) {
            if (commandMethods.containsKey(commandName)) {
                commandMethodsList.add(DiscordSRV.getPlugin().getCommandManager().getCommands().get(commandName));
            }
        }

        if (commandMethodsList.isEmpty()) {
            MessageUtil.sendMessage(sender, LangUtil.Message.COMMAND_DOESNT_EXIST.toString());
            return;
        }

        MessageUtil.sendMessage(sender, DARK_GRAY + "===================[ " + color(titleColor) + "DiscordSRV" + DARK_GRAY + " ]===================");
        for (Method commandMethod : commandMethodsList) {
            Command commandAnnotation = commandMethod.getAnnotation(Command.class);

            // make sure sender has permission to run the commands before showing them permissions for it
            if (!GamePermissionUtil.hasPermission(sender, commandAnnotation.permission())) continue;

            MessageUtil.sendMessage(sender, GRAY + "- " + color(commandColor) + "/discord " + String.join("/", commandAnnotation.commandNames()));
            MessageUtil.sendMessage(sender, "   " + ITALIC + commandAnnotation.helpMessage());
            if (!commandAnnotation.usageExample().equals("")) MessageUtil.sendMessage(sender, "   " + GRAY + ITALIC + "ex. /discord " + commandAnnotation.usageExample());
        }
    }

}
