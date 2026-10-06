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

package github.scarsz.discordsrv.modules.voice;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import net.dv8tion.jda.api.Permission;
import github.scarsz.discordsrv.platform.GamePlayer;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;

import java.util.*;

public class Network {

    private final Set<UUID> players;
    private String channel;
    private boolean initialized = false;

    public Network(String channel) {
        this.players = Collections.emptySet();
        this.channel = channel;
    }

    public Network(Set<UUID> players) {
        this.players = players;

        DiscordSRV.debug(Debug.VOICE, "Network being made for " + players);

        List<Permission> allowedPermissions = new ArrayList<>(Arrays.asList(Permission.VOICE_SPEAK));
        List<Permission> deniedPermissions = new ArrayList<>(Arrays.asList(Permission.VOICE_CONNECT));

        if (VoiceModule.isVoiceChannelsVisible()) {
            allowedPermissions.add(Permission.VIEW_CHANNEL);
        } else {
            deniedPermissions.add(Permission.VIEW_CHANNEL);
        }

        VoiceModule.getCategory().createVoiceChannel(UUID.randomUUID().toString())
                .addPermissionOverride(
                        VoiceModule.getGuild().getPublicRole(),
                        allowedPermissions,
                        deniedPermissions
                )
                .addPermissionOverride(
                        VoiceModule.getGuild().getSelfMember(),
                        Arrays.asList(Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.VOICE_MOVE_OTHERS),
                        Collections.emptyList()
                )
                .queue(channel -> {
                    this.channel = channel.getId();
                    initialized = true;
                }, e -> {
                    DiscordSRV.error("Failed to create network for " + players + ": " + e.getMessage());
                    VoiceModule.get().getNetworks().remove(this);
                });
    }

    public Network engulf(Network network) {
        DiscordSRV.debug(Debug.VOICE, "Network " + this + " is engulfing " + network);
        players.addAll(network.players);
        network.players.clear();
        return this;
    }

    /**
     * @return true if the player is within the network strength or falloff ranges
     */
    public boolean isPlayerInRangeToBeAdded(GamePlayer player) {
        return players.stream()
                .map(uuid -> DiscordSRV.getPlatform().getPlayer(uuid))
                .filter(Objects::nonNull)
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .filter(p -> p.getWorldName().equals(player.getWorldName()))
                .anyMatch(p -> VoiceModule.verticalDistance(p, player) <= VoiceModule.getVerticalStrength()
                        && VoiceModule.horizontalDistance(p, player) <= VoiceModule.getHorizontalStrength());
    }

    /**
     * @return true if the player is within the network strength and should be connected
     */
    public boolean isPlayerInRangeToStayConnected(GamePlayer player) {
        double falloff = VoiceModule.getFalloff();
        return players.stream()
                .map(uuid -> DiscordSRV.getPlatform().getPlayer(uuid))
                .filter(Objects::nonNull)
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .filter(p -> p.getWorldName().equals(player.getWorldName()))
                .anyMatch(p -> VoiceModule.verticalDistance(p, player) <= VoiceModule.getVerticalStrength() + falloff
                        && VoiceModule.horizontalDistance(p, player) <= VoiceModule.getHorizontalStrength() + falloff);
    }

    /**
     * @return true if the player is within the falloff range <strong>but not the strength range</strong>
     */
    public boolean isPlayerInsideFalloffZone(GamePlayer player) {
        double falloff = VoiceModule.getFalloff();
        double horizontalStrength = VoiceModule.getHorizontalStrength();
        double verticalStrength = VoiceModule.getHorizontalStrength();
        return players.stream()
                .map(uuid -> DiscordSRV.getPlatform().getPlayer(uuid))
                .filter(Objects::nonNull)
                .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                .filter(p -> p.getWorldName().equals(player.getWorldName()))
                .anyMatch(p -> {
                    double vertical = VoiceModule.verticalDistance(p, player);
                    double horizontal = VoiceModule.horizontalDistance(p, player);
                    return vertical > verticalStrength && vertical <= verticalStrength + falloff
                            && horizontal > horizontal && horizontal <= horizontalStrength + falloff;
                });
    }

    public void clear() {
        players.clear();
    }

    public void add(GamePlayer player) {
        players.add(player.getUniqueId());
    }

    public void add(UUID uuid) {
        players.add(uuid);
    }

    public void remove(GamePlayer player) {
        players.remove(player.getUniqueId());
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    public boolean contains(GamePlayer player) {
        return players.contains(player.getUniqueId());
    }

    public boolean contains(UUID uuid) {
        return players.contains(uuid);
    }

    public int size() {
        return players.size();
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public VoiceChannel getChannel() {
        if (channel == null || channel.isEmpty()) return null;
        return VoiceModule.getGuild().getVoiceChannelById(channel);
    }

    public boolean isInitialized() {
        return initialized;
    }

}
