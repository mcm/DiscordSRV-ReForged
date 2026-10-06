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
 * A source of permission groups (ranks), used for group &lt;-&gt; role synchronization and the
 * {@code %primarygroup%} placeholder. Replaces Vault's Permission API from the Spigot version.
 * <p>
 * Implementations: LuckPerms ({@link LuckPermsGroupProvider}) and FTB Ranks (provided by the NeoForge platform).
 * Methods that may need to load data for offline players must not block the server thread.
 */
public interface GroupProvider {

    /**
     * @return the name of the mod providing the groups, eg. "LuckPerms"
     */
    String getName();

    /**
     * @return the primary group of the given player using only already loaded data (never blocks), or null
     */
    String getPrimaryGroup(UUID player);

    /**
     * @return the primary group of the given player, loading their data if needed (when not on the server thread), or null
     */
    String getPrimaryGroupLoading(UUID player);

    /**
     * @return the names of all groups
     */
    String[] getGroups();

    boolean groupExists(String group);

    /**
     * @return the groups the player is a member of, or null if the player's data couldn't be loaded
     */
    String[] getPlayerGroups(UUID player);

    boolean playerInGroup(UUID player, String group);

    /**
     * @return whether the player was added to the group
     */
    boolean playerAddGroup(UUID player, String group);

    /**
     * @return whether the player was removed from the group
     */
    boolean playerRemoveGroup(UUID player, String group);

    /**
     * Unregisters any listeners the provider registered
     */
    default void disable() {}

}
