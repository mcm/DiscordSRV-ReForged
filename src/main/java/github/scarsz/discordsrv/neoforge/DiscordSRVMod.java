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

import github.scarsz.discordsrv.DiscordSRV;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge entrypoint. DiscordSRV is a server-side mod: it is only active on dedicated servers and isn't
 * required on clients.
 */
@Mod(value = DiscordSRVMod.MOD_ID, dist = Dist.DEDICATED_SERVER)
public class DiscordSRVMod {

    public static final String MOD_ID = "discordsrv";

    private static NeoForgePlatform platform;

    public DiscordSRVMod(IEventBus modEventBus, ModContainer container) {
        platform = new NeoForgePlatform(container.getModInfo().getVersion().toString());
        // loads the configuration
        DiscordSRV discordSRV = new DiscordSRV(platform);
        NeoForge.EVENT_BUS.register(new GameEventHandler(discordSRV, platform));
    }

    /**
     * @return the platform, or null if DiscordSRV isn't active (eg. on a client)
     */
    public static NeoForgePlatform getPlatform() {
        return platform;
    }

}
