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
 * A player sent a chat message
 */
public class PlayerChatEvent extends PlayerGameEvent {

    private final Component message;
    private final boolean cancelled;

    public PlayerChatEvent(GamePlayer player, Component message, boolean cancelled, Object platformEvent) {
        super(player, platformEvent);
        this.message = message;
        this.cancelled = cancelled;
    }

    /**
     * @return the message the player sent (without any chat formatting)
     */
    public Component getMessage() {
        return message;
    }

    public boolean isCancelled() {
        return cancelled;
    }

}
