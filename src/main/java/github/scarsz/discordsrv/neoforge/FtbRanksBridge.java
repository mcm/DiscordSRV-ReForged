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

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbranks.api.FTBRanksAPI;
import dev.ftb.mods.ftbranks.api.PermissionValue;
import dev.ftb.mods.ftbranks.api.Rank;
import dev.ftb.mods.ftbranks.api.RankManager;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.hooks.permissions.GroupProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * FTB Ranks integration: ranks are used as groups (by rank id) and FTB Ranks permission nodes are checked for
 * DiscordSRV's permissions. FTB Ranks doesn't hook into NeoForge's permission API, so it's queried directly.
 * <p>
 * This is the only class referencing the FTB Ranks API, it's only loaded when the mod is installed.
 * FTB Ranks' data isn't thread safe, so everything is done on the server thread.
 */
public class FtbRanksBridge implements GroupProvider {

    private final NeoForgePlatform platform;

    FtbRanksBridge(NeoForgePlatform platform) {
        this.platform = platform;
    }

    private static RankManager manager() {
        FTBRanksAPI api = FTBRanksAPI.getInstance();
        return api != null ? api.getManager() : null;
    }

    private <T> T onMainThread(Supplier<T> supplier, T fallback) {
        try {
            T value = platform.callOnMainThread(supplier);
            return value != null ? value : fallback;
        } catch (Throwable t) {
            DiscordSRV.debug("FTB Ranks call failed: " + t);
            return fallback;
        }
    }

    private ServerPlayer online(UUID uuid) {
        MinecraftServer server = platform.getServer();
        return server != null ? server.getPlayerList().getPlayer(uuid) : null;
    }

    private GameProfile profile(UUID uuid) {
        ServerPlayer player = online(uuid);
        if (player != null) return player.getGameProfile();
        String name = platform.getPlayerName(uuid);
        return new GameProfile(uuid, name != null ? name : "");
    }

    /**
     * The ranks of the player: all active ranks (including conditional ones, eg. by playtime) if they're online,
     * otherwise the ranks they were explicitly added to. Sorted by power, highest first.
     */
    private List<Rank> ranks(UUID uuid) {
        RankManager manager = manager();
        if (manager == null) return Collections.emptyList();
        ServerPlayer player = online(uuid);
        Collection<Rank> ranks = player != null ? manager.getRanks(player) : manager.getAddedRanks(profile(uuid));
        List<Rank> sorted = new ArrayList<>(ranks);
        sorted.sort(Comparator.comparingInt(Rank::getPower).reversed());
        return sorted;
    }

    // --- permissions ---

    /**
     * @return the value of the given node for the online player, empty if FTB Ranks doesn't define it
     */
    Optional<Boolean> getPermission(ServerPlayer player, String node) {
        return onMainThread(() -> {
            if (manager() == null) return Optional.<Boolean>empty();
            PermissionValue value = FTBRanksAPI.getPermissionValue(player, node);
            return value.isEmpty() ? Optional.<Boolean>empty() : value.asBoolean();
        }, Optional.empty());
    }

    /**
     * @return the value of the given node from the ranks an offline player was added to, empty if undefined
     */
    Optional<Boolean> getOfflinePermission(UUID uuid, String node) {
        return onMainThread(() -> {
            for (Rank rank : ranks(uuid)) {
                PermissionValue value = rank.getPermission(node);
                if (!value.isEmpty()) return value.asBoolean();
            }
            return Optional.<Boolean>empty();
        }, Optional.empty());
    }

    // --- groups ---

    @Override
    public String getName() {
        return "FTB Ranks";
    }

    @Override
    public String getPrimaryGroup(UUID player) {
        return onMainThread(() -> ranks(player).stream().findFirst().map(Rank::getId).orElse(null), null);
    }

    @Override
    public String getPrimaryGroupLoading(UUID player) {
        return getPrimaryGroup(player);
    }

    @Override
    public String[] getGroups() {
        return onMainThread(() -> {
            RankManager manager = manager();
            if (manager == null) return new String[0];
            return manager.getAllRanks().stream().map(Rank::getId).toArray(String[]::new);
        }, new String[0]);
    }

    @Override
    public boolean groupExists(String group) {
        return onMainThread(() -> {
            RankManager manager = manager();
            return manager != null && manager.getRank(group).isPresent();
        }, false);
    }

    @Override
    public String[] getPlayerGroups(UUID player) {
        return onMainThread(() -> {
            if (manager() == null) return null;
            return ranks(player).stream().map(Rank::getId).toArray(String[]::new);
        }, null);
    }

    @Override
    public boolean playerInGroup(UUID player, String group) {
        return onMainThread(() -> ranks(player).stream().anyMatch(rank -> rank.getId().equalsIgnoreCase(group)), false);
    }

    @Override
    public boolean playerAddGroup(UUID player, String group) {
        return onMainThread(() -> {
            RankManager manager = manager();
            if (manager == null) return false;
            Optional<Rank> rank = manager.getRank(group);
            if (!rank.isPresent()) {
                DiscordSRV.debug("Can't add " + player + " to FTB rank " + group + ": no such rank");
                return false;
            }
            return rank.get().add(profile(player));
        }, false);
    }

    @Override
    public boolean playerRemoveGroup(UUID player, String group) {
        return onMainThread(() -> {
            RankManager manager = manager();
            if (manager == null) return false;
            return manager.getRank(group).map(rank -> rank.remove(profile(player))).orElse(false);
        }, false);
    }

    @Override
    public String toString() {
        return "FtbRanksBridge{ranks=" + Arrays.stream(getGroups()).collect(Collectors.joining(", ")) + "}";
    }

}
