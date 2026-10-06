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
 * A player left the server
 */
public class PlayerQuitEvent extends PlayerGameEvent {

    private final Component quitMessage;

    public PlayerQuitEvent(GamePlayer player, Component quitMessage, Object platformEvent) {
        super(player, platformEvent);
        this.quitMessage = quitMessage;
    }

    /**
     * @return the quit message as it would be shown in game (eg. "Notch left the game")
     */
    public Component getQuitMessage() {
        return quitMessage;
    }

}
