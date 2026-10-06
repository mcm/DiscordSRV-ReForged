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

import github.scarsz.discordsrv.platform.GamePlayer;
import net.kyori.adventure.text.Component;

/**
 * A player joined the server
 */
public class PlayerJoinEvent extends PlayerGameEvent {

    private final Component joinMessage;

    public PlayerJoinEvent(GamePlayer player, Component joinMessage, Object platformEvent) {
        super(player, platformEvent);
        this.joinMessage = joinMessage;
    }

    /**
     * @return the join message as it would be shown in game (eg. "Notch joined the game")
     */
    public Component getJoinMessage() {
        return joinMessage;
    }

}
