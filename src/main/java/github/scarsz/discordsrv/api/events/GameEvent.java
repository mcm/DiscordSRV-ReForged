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

package github.scarsz.discordsrv.api.events;

import github.scarsz.discordsrv.platform.GamePlayer;

@SuppressWarnings("LombokGetterMayBeUsed")
abstract class GameEvent<T extends github.scarsz.discordsrv.platform.event.GameEvent> extends Event {

    final private GamePlayer player;
    final private T triggeringGameEvent;

    GameEvent(GamePlayer player, T triggeringGameEvent) {
        this.player = player;
        this.triggeringGameEvent = triggeringGameEvent;
    }

    public GamePlayer getPlayer() {
        return this.player;
    }

    /**
     * @return the game event that triggered this event, may be null
     */
    public T getTriggeringGameEvent() {
        return this.triggeringGameEvent;
    }

    /**
     * @deprecated there are no Bukkit events on NeoForge, use {@link #getTriggeringGameEvent()}
     */
    @Deprecated
    public T getTriggeringBukkitEvent() {
        return this.triggeringGameEvent;
    }
}
