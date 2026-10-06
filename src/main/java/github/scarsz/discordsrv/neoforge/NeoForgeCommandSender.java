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

package github.scarsz.discordsrv.neoforge;

import github.scarsz.discordsrv.platform.CommandSender;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@link CommandSender} for a non-player command source (the server console, rcon, command blocks, ...).
 * Players are represented by {@link NeoForgePlayer}.
 */
public class NeoForgeCommandSender implements CommandSender {

    private final NeoForgePlatform platform;
    private final CommandSourceStack source;

    public NeoForgeCommandSender(NeoForgePlatform platform, CommandSourceStack source) {
        this.platform = platform;
        this.source = source;
    }

    /**
     * Wraps the given source: as a {@link NeoForgePlayer} if it's a player, otherwise as a generic sender
     */
    public static CommandSender of(NeoForgePlatform platform, CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? new NeoForgePlayer(platform, player) : new NeoForgeCommandSender(platform, source);
    }

    @Override
    public String getName() {
        return source.getTextName();
    }

    @Override
    public boolean hasPermission(String permission) {
        // the console & rcon have every permission, command blocks only "op" level permissions
        return source.hasPermission(4) || source.hasPermission(2) && !permission.startsWith("discordsrv.sync.");
    }

    @Override
    public void sendMessage(Component message) {
        net.minecraft.network.chat.Component converted = ComponentConverter.toMinecraft(message, platform.getServer());
        platform.runOnMainThread(() -> source.sendSystemMessage(converted));
    }

    @Override
    public boolean isConsole() {
        return source.hasPermission(4) && source.getEntity() == null;
    }

    public CommandSourceStack getSource() {
        return source;
    }

}
