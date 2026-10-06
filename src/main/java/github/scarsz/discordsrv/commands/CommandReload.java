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
import github.scarsz.discordsrv.api.events.ConfigReloadedEvent;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.platform.CommandSender;

public class CommandReload {

    @Command(commandNames = { "reload" },
            helpMessage = "Reloads the config of DiscordSRV",
            permission = "discordsrv.reload"
    )
    public static void execute(CommandSender sender, String[] args) {
        DiscordSRV.getPlugin().reloadConfig();
        DiscordSRV.getPlugin().reloadChannels();
        DiscordSRV.getPlugin().reloadRegexes();
        DiscordSRV.getPlugin().reloadRoleAliases();
        DiscordSRV.getPlugin().reloadAllowedMentions();
        DiscordSRV.api.updateSlashCommands();
        if (DiscordSRV.getPlugin().getChannelUpdater() != null) DiscordSRV.getPlugin().getChannelUpdater().reload();
        if (DiscordSRV.getPlugin().getAlertListener() != null) DiscordSRV.getPlugin().getAlertListener().reloadAlerts();

        MessageUtil.sendMessage(sender, "§b" + LangUtil.InternalMessage.RELOADED.toString());

        DiscordSRV.api.callEvent(new ConfigReloadedEvent(sender));
    }

}
