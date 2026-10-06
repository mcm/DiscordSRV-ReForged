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

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DebugUtil;
import github.scarsz.discordsrv.platform.CommandSender;
import github.scarsz.discordsrv.util.SchedulerUtil;

import java.util.*;

public class CommandDebugger {

    @Command(commandNames = { "debugger" },
            helpMessage = "A toggleable timings-like command to dump debug information to a report file",
            permission = "discordsrv.debug"
    )
    public static void execute(CommandSender sender, String[] args) {
        List<String> arguments = new ArrayList<>(Arrays.asList(args));

        String subCommand;
        if (arguments.isEmpty()) {
            subCommand = "start";
        } else {
            subCommand = arguments.remove(0);
        }

        boolean upload = false;
        if (subCommand.equalsIgnoreCase("start") || subCommand.equalsIgnoreCase("on")) {
            Set<String> validArguments = new HashSet<>();
            for (String argument : arguments) {
                boolean anyValid = false;
                for (Debug value : Debug.values()) {
                    if (value.matches(argument)) {
                        anyValid = true;
                        break;
                    }
                }
                if (!anyValid) {
                    sender.sendMessage("§cInvalid debug category: §4" + argument);
                    continue;
                }

                validArguments.add(argument);
            }

            if (validArguments.isEmpty()) {
                DiscordSRV.getPlugin().getDebuggerCategories().add(Debug.UNCATEGORIZED.name());
            } else {
                DiscordSRV.getPlugin().getDebuggerCategories().addAll(validArguments);
            }
            sender.sendMessage("§3Debugger enabled, use "
                    + "§7/discordsrv debugger stop §3to stop debugging or "
                    + "§7/discordsrv debugger upload §3to stop debugging and generate a debug report");
            return;
        } else if (subCommand.equalsIgnoreCase("stop") || subCommand.equalsIgnoreCase("off")
                || (upload = subCommand.equalsIgnoreCase("upload"))) {
            if (upload) {
                // generate the report before clearing the debugger categories so the report includes them
                SchedulerUtil.runTaskAsynchronously(() -> {
                    report(sender);
                    DiscordSRV.getPlugin().getDebuggerCategories().clear();
                });
            } else {
                sender.sendMessage("§3Debugger disabled");
                DiscordSRV.getPlugin().getDebuggerCategories().clear();
            }
            return;
        }

        sender.sendMessage("§cInvalid subcommand §4" + subCommand);
    }

    @Command(commandNames = { "debug" },
            helpMessage = "Generates a debug report file with information about DiscordSRV's state",
            permission = "discordsrv.debug"
    )
    public static void debug(CommandSender sender, String[] args) {
        SchedulerUtil.runTaskAsynchronously(() -> report(sender));
    }

    private static void report(CommandSender sender) {
        String result = DebugUtil.run(sender.isConsole() ? "CONSOLE" : sender.getName());
        sender.sendMessage("§3Your debug report has been generated and is available at §b" + result);
    }

}
