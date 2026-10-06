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

package github.scarsz.discordsrv.platform;

import github.scarsz.discordsrv.util.MessageUtil;
import net.kyori.adventure.text.Component;

/**
 * Something that can run DiscordSRV commands and receive messages: a player, the server console or a
 * Discord-originated command source. Replaces Bukkit's CommandSender.
 */
public interface CommandSender {

    /**
     * @return the name of this sender (player name, or "CONSOLE")
     */
    String getName();

    /**
     * @param permission the permission node, eg. {@code discordsrv.link}
     * @return whether this sender has the given permission
     */
    boolean hasPermission(String permission);

    /**
     * Sends the given component to this sender
     */
    void sendMessage(Component message);

    /**
     * Sends a legacy (&amp;/§) or MiniMessage formatted message to this sender
     */
    default void sendMessage(String message) {
        MessageUtil.sendMessage(this, message);
    }

    /**
     * @return whether this sender is the server console
     */
    default boolean isConsole() {
        return false;
    }

}
