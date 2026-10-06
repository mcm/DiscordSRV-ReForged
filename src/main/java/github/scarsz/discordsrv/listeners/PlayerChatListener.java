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

package github.scarsz.discordsrv.listeners;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.platform.event.GameListener;
import github.scarsz.discordsrv.platform.event.PlayerChatEvent;
import github.scarsz.discordsrv.util.SchedulerUtil;
import net.kyori.adventure.text.Component;

public class PlayerChatListener implements GameListener {

    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        Component message = event.getMessage();
        boolean isCancelled = event.isCancelled();
        SchedulerUtil.runTaskAsynchronously(() ->
                DiscordSRV.getPlugin().processChatMessage(
                        event.getPlayer(),
                        message,
                        DiscordSRV.getPlugin().getOptionalChannel("global"),
                        isCancelled,
                        event
                )
        );
    }

}
