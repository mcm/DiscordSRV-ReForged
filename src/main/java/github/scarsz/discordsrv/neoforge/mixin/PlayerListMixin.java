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

package github.scarsz.discordsrv.neoforge.mixin;

import com.mojang.authlib.GameProfile;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.neoforge.ComponentConverter;
import github.scarsz.discordsrv.neoforge.DiscordSRVMod;
import github.scarsz.discordsrv.neoforge.NeoForgePlatform;
import github.scarsz.discordsrv.util.MessageUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * Hooks the vanilla login check (bans, whitelist, full server) to implement the "require linked account to play"
 * module. Runs after the vanilla checks so that bans/whitelist take precedence, like the Spigot version's
 * listener priority option allowed.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "canPlayerLogin", at = @At("RETURN"), cancellable = true)
    private void discordsrv$requireLink(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Component> cir) {
        if (cir.getReturnValue() != null) return; // already denied by vanilla
        NeoForgePlatform platform = DiscordSRVMod.getPlatform();
        DiscordSRV discordSRV = DiscordSRV.getPlugin();
        if (platform == null || discordSRV == null || profile.getId() == null) return;

        String ip = address instanceof InetSocketAddress && ((InetSocketAddress) address).getAddress() != null
                ? ((InetSocketAddress) address).getAddress().getHostAddress()
                : null;
        try {
            String kickMessage = discordSRV.checkLogin(profile.getId(), profile.getName(), ip);
            if (kickMessage != null) {
                cir.setReturnValue(ComponentConverter.toMinecraft(MessageUtil.toComponent(kickMessage), platform.getServer()));
            }
        } catch (Throwable t) {
            DiscordSRV.error("Failed to check if " + profile.getName() + " is allowed to join", t);
        }
    }

}
