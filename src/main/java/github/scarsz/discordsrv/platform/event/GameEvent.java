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

/**
 * Base class for game events that the platform (NeoForge) forwards to DiscordSRV.
 * These take the place of the Bukkit events the Spigot version listened to; their names match the Bukkit
 * event names where possible so that alerts.yml triggers keep working.
 */
public abstract class GameEvent {

    private final Object platformEvent;

    protected GameEvent(Object platformEvent) {
        this.platformEvent = platformEvent;
    }

    /**
     * @return the platform's own event object that triggered this event (eg. a NeoForge event), may be null
     */
    public Object getPlatformEvent() {
        return platformEvent;
    }

    /**
     * @return the event's name, used for matching alert triggers
     */
    public String getEventName() {
        return getClass().getSimpleName();
    }

}
