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

package github.scarsz.discordsrv.platform.event;

import github.scarsz.discordsrv.platform.CommandSender;

/**
 * A non-player (the console, a command block, ...) ran a command
 */
public class ServerCommandEvent extends GameEvent {

    private final CommandSender sender;
    private final String command;

    public ServerCommandEvent(CommandSender sender, String command, Object platformEvent) {
        super(platformEvent);
        this.sender = sender;
        this.command = command;
    }

    public CommandSender getSender() {
        return sender;
    }

    /**
     * @return the command, without the leading slash
     */
    public String getCommand() {
        return command;
    }

}
