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

import github.scarsz.discordsrv.DiscordSRV;

import java.util.UUID;

/**
 * Access to the active {@link GroupProvider} (LuckPerms or FTB Ranks, see the {@code PermissionsProvider} config
 * option). All methods are safe to call when no provider is active: they return null/false/empty values.
 */
public final class GroupHook {

    private static volatile GroupProvider provider = null;

    private GroupHook() {}

    public static synchronized void setProvider(GroupProvider newProvider) {
        if (provider != null && provider != newProvider) {
            try {
                provider.disable();
            } catch (Throwable t) {
                DiscordSRV.debug("Failed to disable group provider " + provider.getName() + ": " + t);
            }
        }
        provider = newProvider;
    }

    public static GroupProvider getProvider() {
        return provider;
    }

    /**
     * @return whether a groups provider (LuckPerms or FTB Ranks) is available
     */
    public static boolean isEnabled() {
        return provider != null;
    }

    /**
     * @return the name of the active provider, or "none"
     */
    public static String getProviderName() {
        GroupProvider provider = GroupHook.provider;
        return provider != null ? provider.getName() : "none";
    }

    public static String getPrimaryGroup(UUID player) {
        GroupProvider provider = GroupHook.provider;
        if (provider == null || player == null) return null;
        try {
            return provider.getPrimaryGroup(player);
        } catch (Throwable t) {
            DiscordSRV.debug("Failed to get the primary group of " + player + " from " + provider.getName() + ": " + t);
            return null;
        }
    }

    public static String getPrimaryGroupLoading(UUID player) {
        GroupProvider provider = GroupHook.provider;
        if (provider == null || player == null) return null;
        return provider.getPrimaryGroupLoading(player);
    }

    public static String[] getGroups() {
        GroupProvider provider = GroupHook.provider;
        return provider != null ? provider.getGroups() : new String[0];
    }

    public static boolean groupExists(String group) {
        GroupProvider provider = GroupHook.provider;
        return provider != null && provider.groupExists(group);
    }

    public static String[] getPlayerGroups(UUID player) {
        GroupProvider provider = GroupHook.provider;
        if (provider == null || player == null) return null;
        return provider.getPlayerGroups(player);
    }

    public static boolean playerInGroup(UUID player, String group) {
        GroupProvider provider = GroupHook.provider;
        return provider != null && player != null && provider.playerInGroup(player, group);
    }

    public static boolean playerAddGroup(UUID player, String group) {
        GroupProvider provider = GroupHook.provider;
        return provider != null && player != null && provider.playerAddGroup(player, group);
    }

    public static boolean playerRemoveGroup(UUID player, String group) {
        GroupProvider provider = GroupHook.provider;
        return provider != null && player != null && provider.playerRemoveGroup(player, group);
    }

}
