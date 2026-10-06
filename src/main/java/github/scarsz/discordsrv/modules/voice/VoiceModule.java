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
import github.scarsz.discordsrv.platform.GamePlayer;
import github.scarsz.discordsrv.platform.event.GameListener;
import github.scarsz.discordsrv.platform.event.PlayerJoinEvent;
import github.scarsz.discordsrv.platform.event.PlayerQuitEvent;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.PlayerUtil;
import github.scarsz.discordsrv.util.SchedulerUtil;
import lombok.Getter;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class VoiceModule extends ListenerAdapter implements GameListener {

    private static final List<Permission> LOBBY_REQUIRED_PERMISSIONS = Arrays.asList(Permission.VIEW_CHANNEL, Permission.VOICE_MOVE_OTHERS);
    private static final List<Permission> CATEGORY_REQUIRED_PERMISSIONS = Arrays.asList(Permission.VIEW_CHANNEL, Permission.VOICE_MOVE_OTHERS, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_CHANNEL);

    private final ReentrantLock lock = new ReentrantLock();
    private Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<Network> networks = ConcurrentHashMap.newKeySet();
    @Getter
    private final Set<String> mutedUsers = ConcurrentHashMap.newKeySet();
    private final Map<String, Pair<String, CompletableFuture<Void>>> awaitingMoves = new ConcurrentHashMap<>();

    private long lastLogTime;

    public VoiceModule() {
        if (DiscordSRV.config().getBoolean("Voice enabled")) {
            DiscordSRV.info("Enabling voice module");
            DiscordSRV.getPlugin().getJda().addEventListener(this);
            DiscordSRV.getPlugin().registerGameListener(this);
            SchedulerUtil.runTaskTimerAsynchronously(
                    this::tick,
                    2,
                    DiscordSRV.config().getInt("Tick speed")
            );
        }

        String categoryId = DiscordSRV.config().getString("Voice category");
        if (StringUtils.isBlank(categoryId)) return;

        Category category = DiscordSRV.getPlugin().getJda().getCategoryById(categoryId);
        if (category != null) {
            category.getVoiceChannels().stream()
                    .filter(channel -> {
                        try {
                            //noinspection ResultOfMethodCallIgnored
                            UUID.fromString(channel.getName());
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .forEach(channel -> {
                        // temporarily add it as a network so it can be emptied and deleted
                        networks.add(new Network(channel.getId()));
                    });
        }
    }

    private void tick() {
        if (!lock.tryLock()) {
            DiscordSRV.debug(Debug.VOICE, "Skipping voice module tick, a tick is already in progress");
            return;
        }
        try {
            Category category = getCategory();
            if (category == null) {
                DiscordSRV.debug(Debug.VOICE, "Skipping voice module tick, category is null");
                return;
            }

            VoiceChannel lobbyChannel = getLobbyChannel();
            if (lobbyChannel == null) {
                DiscordSRV.debug(Debug.VOICE, "Skipping voice module tick, lobby channel is null");
                return;
            }

            // check that the permissions are correct
            Member selfMember = lobbyChannel.getGuild().getSelfMember();
            Role publicRole = lobbyChannel.getGuild().getPublicRole();

            long currentTime = System.currentTimeMillis();
            boolean log = lastLogTime + TimeUnit.SECONDS.toMillis(30) < currentTime;

            boolean stop = false;
            for (Permission permission : LOBBY_REQUIRED_PERMISSIONS) {
                if (!selfMember.hasPermission(lobbyChannel, permission)) {
                    if (log) DiscordSRV.error("The bot doesn't have the \"" + permission.getName() + "\" permission in the voice lobby (" + lobbyChannel.getName() + ")");
                    stop = true;
                }
            }
            for (Permission permission : CATEGORY_REQUIRED_PERMISSIONS) {
                if (!selfMember.hasPermission(category, permission)) {
                    if (log) DiscordSRV.error("The bot doesn't have the \"" + permission.getName() + "\" permission in the voice category (" + category.getName() + ")");
                    stop = true;
                }
            }
            // we can't function & would throw exceptions
            if (stop) {
                lastLogTime = currentTime;
                return;
            }

            PermissionOverride lobbyPublicRoleOverride = lobbyChannel.getPermissionOverride(publicRole);
            if (lobbyPublicRoleOverride == null) {
                lobbyChannel.upsertPermissionOverride(publicRole).deny(Permission.VOICE_SPEAK).queue();
            } else if (!lobbyPublicRoleOverride.getDenied().contains(Permission.VOICE_SPEAK)) {
                lobbyPublicRoleOverride.getManager().deny(Permission.VOICE_SPEAK).queue();
            }

            // remove networks that have no voice channel
            networks.removeIf(network -> network.getChannel() == null && network.isInitialized());

            Set<GamePlayer> alivePlayers = new HashSet<>(PlayerUtil.getOnlinePlayers());

            // there are no player move/teleport events on this platform: any online player may have moved
            alivePlayers.forEach(this::markDirty);

            Set<UUID> oldDirtyPlayers = dirtyPlayers;
            dirtyPlayers = ConcurrentHashMap.newKeySet();
            for (UUID uuid : oldDirtyPlayers) {
                GamePlayer player = DiscordSRV.getPlatform().getPlayer(uuid);
                if (player == null) continue;

                Member member = getMember(player.getUniqueId());
                if (member == null) {
                    DiscordSRV.debug(Debug.VOICE, "Player " + player.getName() + " isn't linked, skipping voice checks");
                    continue;
                }

                if (member.getVoiceState() == null || member.getVoiceState().getChannel() == null) {
                    DiscordSRV.debug(Debug.VOICE, "Player " + player.getName() + " is not connected to voice");
                    continue;
                }

                AudioChannel playerChannel = member.getVoiceState().getChannel();
                boolean isLobby = playerChannel.getId().equals(getLobbyChannel().getId());
                if (!isLobby && (playerChannel.getParentCategory() == null || !playerChannel.getParentCategory().getId().equals(getCategory().getId()))) {
                    DiscordSRV.debug(Debug.VOICE, "Player " + player.getName() + " was not in the voice lobby or category");

                    // cancel existing moves if they changed to a different channel
                    Pair<String, CompletableFuture<Void>> pair = awaitingMoves.get(member.getId());
                    if (pair != null) pair.getRight().cancel(false);
                    continue;
                }

                // add player to networks that they may have came into contact with
                // and combine multiple networks if the player is connecting them together
                networks.stream()
                        .filter(network -> network.isPlayerInRangeToBeAdded(player))
                        // combine multiple networks if player is bridging both of them together
                        .reduce((network1, network2) -> network1.size() > network2.size() ? network1.engulf(network2) : network2.engulf(network1))
                        // add the player to the network if they aren't in it already
                        .filter(network -> !network.contains(player.getUniqueId()))
                        .ifPresent(network -> {
                            DiscordSRV.debug(Debug.VOICE, player.getName() + " has entered network " + network + "'s influence, connecting");
                            network.add(player.getUniqueId());
                        });

                // remove player from networks that they lost connection to
                networks.stream()
                        .filter(network -> network.contains(player.getUniqueId()))
                        .filter(network -> !network.isPlayerInRangeToStayConnected(player))
                        .forEach(network -> {
                            DiscordSRV.debug(Debug.VOICE, "Player " + player.getName() + " lost connection to " + network + ", disconnecting");
                            network.remove(player.getUniqueId());
                            if (network.size() == 1) network.clear();
                        });

                // create networks if two players are within activation distance
                Set<UUID> playersWithinRange = alivePlayers.stream()
                        .filter(p -> networks.stream().noneMatch(network -> network.contains(p)))
                        .filter(p -> !p.getUniqueId().equals(player.getUniqueId()))
                        .filter(p -> p.getWorldName().equals(player.getWorldName()))
                        .filter(p -> horizontalDistance(p, player) <= getHorizontalStrength()
                                && verticalDistance(p, player) <= getVerticalStrength())
                        .filter(p -> {
                            Member m = getMember(p.getUniqueId());
                            return m != null && m.getVoiceState() != null
                                    && m.getVoiceState().getChannel() != null
                                    && m.getVoiceState().getChannel().getParentCategory() != null
                                    && m.getVoiceState().getChannel().getParentCategory().equals(category);
                        })
                        .map(GamePlayer::getUniqueId)
                        .collect(Collectors.toCollection(ConcurrentHashMap::newKeySet));
                if (!playersWithinRange.isEmpty()) {
                    if (category.getChannels().size() == 50) {
                        DiscordSRV.debug(Debug.VOICE, "Can't create new voice network because category " + category.getName() + " is full of channels");
                        continue;
                    }

                    playersWithinRange.add(uuid);
                    networks.add(new Network(playersWithinRange));
                }
            }

            // handle moving players between channels
            Set<Member> members = new HashSet<>(lobbyChannel.getMembers());
            for (Network network : getNetworks()) {
                VoiceChannel voiceChannel = network.getChannel();
                if (voiceChannel == null) continue;
                members.addAll(voiceChannel.getMembers());
            }

            for (Member member : members) {
                UUID uuid = getUniqueId(member);
                AudioChannel playerChannel = member.getVoiceState().getChannel();
                if (playerChannel == null) continue;

                Network playerNetwork = uuid != null ? networks.stream()
                        .filter(n -> n.contains(uuid))
                        .findAny().orElse(null) : null;

                VoiceChannel shouldBeInChannel;
                if (playerNetwork != null) {
                    if (playerNetwork.getChannel() == null) {
                        // isn't yet created, we can wait until next tick
                        continue;
                    }

                    shouldBeInChannel = playerNetwork.getChannel();
                } else {
                    shouldBeInChannel = lobbyChannel;
                }

                Pair<String, CompletableFuture<Void>> awaitingMove = awaitingMoves.get(member.getId());
                // they're already where they're suppose to be
                if (awaitingMove != null && awaitingMove.getLeft().equals(shouldBeInChannel.getId())) continue;

                // if the cancel succeeded we can move them
                if (awaitingMove != null && !awaitingMove.getLeft().equals(shouldBeInChannel.getId())
                        && !awaitingMove.getRight().cancel(false)) continue;

                // schedule a move to the channel they're suppose to be in, if they aren't there yet
                if (!playerChannel.getId().equals(shouldBeInChannel.getId())) {
                    awaitingMoves.put(member.getId(), Pair.of(
                            shouldBeInChannel.getId(),
                            getGuild().moveVoiceMember(member, shouldBeInChannel)
                                    .submit().whenCompleteAsync((v, t) -> awaitingMoves.remove(member.getId()))
                    ));
                }
            }

            // delete empty networks
            for (Network network : new HashSet<>(networks)) {
                if (!network.isEmpty()) continue;

                VoiceChannel voiceChannel = network.getChannel();
                if (voiceChannel == null) continue;

                if (voiceChannel.getMembers().isEmpty()) {
                    voiceChannel.delete().reason("Lost communication").queue();
                    networks.remove(network);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        markDirty(event.getPlayer());
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        SchedulerUtil.runTaskAsynchronously(() -> {
            networks.stream()
                    .filter(network -> network.contains(uuid))
                    .forEach(network -> network.remove(uuid));
        });
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        VoiceChannel joined = asVoiceChannel(event.getChannelJoined());
        VoiceChannel left = asVoiceChannel(event.getChannelLeft());
        if (event.getChannelLeft() == null) {
            onGuildVoiceJoin(event.getMember(), joined);
        } else if (event.getChannelJoined() == null) {
            onGuildVoiceLeave(event.getMember(), left);
        } else {
            onGuildVoiceMove(event.getMember(), joined, left);
        }
    }

    private static VoiceChannel asVoiceChannel(AudioChannel channel) {
        return channel instanceof VoiceChannel ? (VoiceChannel) channel : null;
    }

    private void onGuildVoiceJoin(Member member, VoiceChannel channelJoined) {
        checkMutedUser(channelJoined, member);
        if (channelJoined == null || !channelJoined.equals(getLobbyChannel())) return;

        UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(member.getUser().getId());
        if (uuid == null) return;
        GamePlayer player = DiscordSRV.getPlatform().getPlayer(uuid);
        if (player != null) markDirty(player);
    }

    private void onGuildVoiceMove(Member member, VoiceChannel channelJoined, VoiceChannel channelLeft) {
        if (channelJoined != null && channelJoined.getParentCategory() != null && !channelJoined.getParentCategory().equals(getCategory()) &&
                channelLeft != null && channelLeft.getParentCategory() != null && channelLeft.getParentCategory().equals(getCategory())) {
            UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(member.getUser().getId());
            if (uuid == null) return;
            GamePlayer player = DiscordSRV.getPlatform().getPlayer(uuid);
            if (player != null) {
                networks.stream()
                        .filter(network -> network.contains(player.getUniqueId()))
                        .forEach(network -> network.remove(player));
            }
        }
        checkMutedUser(channelJoined, member);
    }

    private void onGuildVoiceLeave(Member member, VoiceChannel channelLeft) {
        checkMutedUser(null, member);
        if (channelLeft == null || channelLeft.getParentCategory() == null || !channelLeft.getParentCategory().equals(getCategory())) return;

        UUID uuid = DiscordSRV.getPlugin().getAccountLinkManager().getUuid(member.getUser().getId());
        if (uuid == null) return;
        GamePlayer player = DiscordSRV.getPlatform().getPlayer(uuid);
        if (player != null) {
            networks.stream()
                    .filter(network -> network.contains(player))
                    .forEach(network -> network.remove(player));
        }
    }

    @Override
    public void onChannelDelete(@NotNull ChannelDeleteEvent event) {
        networks.removeIf(network -> network.getChannel() != null && event.getChannel().getId().equals(network.getChannel().getId()));
    }

    private void markDirty(GamePlayer player) {
        dirtyPlayers.add(player.getUniqueId());
    }

    private void checkMutedUser(VoiceChannel channel, Member member) {
        if (channel == null || member.getVoiceState() == null || getLobbyChannel() == null || getCategory() == null) {
            return;
        }
        boolean isLobby = channel.getId().equals(getLobbyChannel().getId());
        if (isLobby && !member.getVoiceState().isGuildMuted()) {
            if (!DiscordSRV.config().getBoolean("Mute users who bypass speak permissions in the lobby")) return;
            PermissionOverride override = channel.getPermissionOverride(channel.getGuild().getPublicRole());
            if (override != null && override.getDenied().contains(Permission.VOICE_SPEAK)
                    && member.hasPermission(channel, Permission.VOICE_SPEAK, Permission.VOICE_MUTE_OTHERS)
                    && channel.getGuild().getSelfMember().hasPermission(channel, Permission.VOICE_MUTE_OTHERS)
                    && channel.getGuild().getSelfMember().hasPermission(getCategory(), Permission.VOICE_MOVE_OTHERS)) {
                member.mute(true).queue();
                mutedUsers.add(member.getId());
            }
        } else if (!isLobby) {
            if (mutedUsers.remove(member.getId())) {
                member.mute(false).queue();
            }
        }
    }

    public void shutdown() {
        for (Pair<String, CompletableFuture<Void>> value : awaitingMoves.values()) {
            value.getRight().cancel(true);
        }
        VoiceChannel lobbyChannel = getLobbyChannel();
        for (Network network : networks) {
            VoiceChannel channel = network.getChannel();
            if (channel != null) {
                if (lobbyChannel != null) {
                    for (Member member : channel.getMembers()) {
                        member.getGuild().moveVoiceMember(member, lobbyChannel).queue();
                    }
                }
                channel.delete().queue();
            }
            network.clear();
        }
        this.networks.clear();
    }

    public static VoiceModule get() {
        return DiscordSRV.getPlugin().getVoiceModule();
    }

    public static Guild getGuild() {
        return getCategory().getGuild();
    }

    public static Category getCategory() {
        if (DiscordUtil.getJda() == null) return null;
        String id = DiscordSRV.config().getString("Voice category");
        if (StringUtils.isBlank(id)) return null;
        return DiscordUtil.getJda().getCategoryById(id);
    }

    public static VoiceChannel getLobbyChannel() {
        if (DiscordUtil.getJda() == null) return null;
        String id = DiscordSRV.config().getString("Lobby channel");
        if (StringUtils.isBlank(id)) return null;
        return DiscordUtil.getJda().getVoiceChannelById(id);
    }

    public static Member getMember(UUID player) {
        String discordId = DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(player);
        return discordId != null ? getGuild().getMemberById(discordId) : null;
    }

    public static UUID getUniqueId(Member member) {
        return DiscordSRV.getPlugin().getAccountLinkManager().getUuid(member.getId());
    }

    public static double verticalDistance(GamePlayer player1, GamePlayer player2) {
        return Math.sqrt(square(player1.getY() - player2.getY()));
    }

    public static double horizontalDistance(GamePlayer player1, GamePlayer player2) {
        return Math.sqrt(square(player1.getX() - player2.getX()) + square(player1.getZ() - player2.getZ()));
    }

    private static double square(double num) {
        return num * num;
    }

    public static double getVerticalStrength() {
        return DiscordSRV.config().getDouble("Network.Vertical Strength");
    }

    public static double getHorizontalStrength() {
        return DiscordSRV.config().getDouble("Network.Horizontal Strength");
    }

    public static double getFalloff() {
        return DiscordSRV.config().getDouble("Network.Falloff");
    }

    public static boolean isVoiceChannelsVisible() {
        return DiscordSRV.config().getBoolean("Network.Channels are visible");
    }

}
