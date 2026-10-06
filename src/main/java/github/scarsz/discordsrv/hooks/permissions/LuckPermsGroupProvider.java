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

package github.scarsz.discordsrv.hooks.permissions;

import java.util.UUID;

/**
 * {@link GroupProvider} backed by LuckPerms (through {@link LuckPermsHook}, which also provides DiscordSRV's
 * LuckPerms contexts).
 */
public class LuckPermsGroupProvider implements GroupProvider {

    @Override
    public String getName() {
        return "LuckPerms";
    }

    @Override
    public String getPrimaryGroup(UUID player) {
        return LuckPermsHook.getPrimaryGroup(player);
    }

    @Override
    public String getPrimaryGroupLoading(UUID player) {
        return LuckPermsHook.getPrimaryGroupLoading(player);
    }

    @Override
    public String[] getGroups() {
        return LuckPermsHook.getGroups();
    }

    @Override
    public boolean groupExists(String group) {
        return LuckPermsHook.groupExists(group);
    }

    @Override
    public String[] getPlayerGroups(UUID player) {
        return LuckPermsHook.getPlayerGroups(player);
    }

    @Override
    public boolean playerInGroup(UUID player, String group) {
        return LuckPermsHook.playerInGroup(player, group);
    }

    @Override
    public boolean playerAddGroup(UUID player, String group) {
        return LuckPermsHook.playerAddGroup(player, group);
    }

    @Override
    public boolean playerRemoveGroup(UUID player, String group) {
        return LuckPermsHook.playerRemoveGroup(player, group);
    }

}
