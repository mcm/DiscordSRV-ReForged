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

import java.util.UUID;

/**
 * An online player on the Minecraft server. Replaces Bukkit's Player.
 */
public interface GamePlayer extends CommandSender {

    UUID getUniqueId();

    /**
     * @return the player's display name (eg. with team prefixes or nicknames applied)
     */
    Component getDisplayNameComponent();

    /**
     * @return the player's display name as a legacy (§) formatted string
     */
    default String getDisplayName() {
        return MessageUtil.toLegacy(getDisplayNameComponent());
    }

    /**
     * @return the name of the world (dimension) the player is in, eg. {@code overworld} or {@code the_nether}
     */
    String getWorldName();

    double getX();

    double getY();

    double getZ();

    /**
     * @return the player's latency in milliseconds
     */
    int getPing();

    /**
     * @return whether the player has joined the server before (false when this is their first join)
     */
    boolean hasPlayedBefore();

    /**
     * Disconnects the player from the server
     */
    void kick(Component reason);

    /**
     * Plays the "you've been mentioned" sound for this player
     */
    void playMentionSound();

    /**
     * @return the player's skin texture id (the last part of the textures.minecraft.net url), or null
     */
    String getSkinTexture();

    /**
     * @return whether the player is hidden from other players (vanished)
     */
    default boolean isVanished() {
        return false;
    }

    /**
     * @return the underlying platform player object (a {@code net.minecraft.server.level.ServerPlayer} on NeoForge)
     */
    Object getHandle();

}
