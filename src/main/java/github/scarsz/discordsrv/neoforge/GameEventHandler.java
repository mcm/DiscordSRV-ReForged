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

import com.mojang.brigadier.ParseResults;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.platform.CommandSender;
import github.scarsz.discordsrv.platform.event.*;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

/**
 * Listens to NeoForge events and forwards them to DiscordSRV's platform independent core.
 * Registered on {@code NeoForge.EVENT_BUS}.
 */
public class GameEventHandler {

    private final DiscordSRV discordSRV;
    private final NeoForgePlatform platform;

    public GameEventHandler(DiscordSRV discordSRV, NeoForgePlatform platform) {
        this.discordSRV = discordSRV;
        this.platform = platform;
    }

    private static boolean isRealPlayer(Object entity) {
        return entity instanceof ServerPlayer && !(entity instanceof FakePlayer);
    }

    // --- lifecycle ---

    @SubscribeEvent
    public void onPermissionNodes(PermissionGatherEvent.Nodes event) {
        platform.getPermissions().onGatherNodes(event);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        platform.setServer(event.getServer());
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        platform.setServer(event.getServer());
        discordSRV.onEnable();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        discordSRV.onDisable();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        platform.setServer(null);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        discordSRV.onServerTick();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        DiscordCommand.register(event.getDispatcher(), discordSRV, platform);
    }

    // --- players ---

    @SubscribeEvent
    public void onPlayerLoad(PlayerEvent.LoadFromFile event) {
        // the player data file doesn't exist yet if this is the player's first time joining
        if (isRealPlayer(event.getEntity()) && !event.getPlayerFile("dat").exists()) {
            platform.markFirstJoin(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!isRealPlayer(event.getEntity())) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        NeoForgePlayer gamePlayer = platform.wrapJoiningPlayer(player);
        Component joinMessage = Component.translatable("multiplayer.player.joined", player.getDisplayName());
        discordSRV.callPlayerJoin(new PlayerJoinEvent(gamePlayer, ComponentConverter.toAdventure(joinMessage), event));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!isRealPlayer(event.getEntity())) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        Component quitMessage = Component.translatable("multiplayer.player.left", player.getDisplayName());
        discordSRV.callPlayerQuit(new PlayerQuitEvent(platform.wrap(player), ComponentConverter.toAdventure(quitMessage), event));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onChat(ServerChatEvent event) {
        if (!isRealPlayer(event.getPlayer())) return;
        discordSRV.callPlayerChat(new PlayerChatEvent(
                platform.wrap(event.getPlayer()),
                ComponentConverter.toAdventure(event.getMessage()),
                event.isCanceled(),
                event
        ));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (event.isCanceled() || !isRealPlayer(event.getEntity())) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        // only send death messages that would be shown in chat
        if (!player.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) return;
        Component deathMessage = player.getCombatTracker().getDeathMessage();
        discordSRV.callPlayerDeath(new PlayerDeathEvent(platform.wrap(player), ComponentConverter.toAdventure(deathMessage), event));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!isRealPlayer(event.getEntity())) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        AdvancementHolder advancement = event.getAdvancement();
        DisplayInfo display = advancement.value().display().orElse(null);
        if (display == null) return;

        boolean announce = display.shouldAnnounceChat()
                && player.level().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS);
        discordSRV.callPlayerAdvancementDone(new PlayerAdvancementDoneEvent(
                platform.wrap(player),
                advancement.id().toString(),
                ComponentConverter.toAdventure(display.getTitle()),
                ComponentConverter.toAdventure(display.getDescription()),
                display.getType().getSerializedName(),
                announce,
                event
        ));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCommand(CommandEvent event) {
        if (event.isCanceled()) return;
        ParseResults<CommandSourceStack> parseResults = event.getParseResults();
        CommandSourceStack source = parseResults.getContext().getSource();
        String command = parseResults.getReader().getString();
        if (command.startsWith("/")) command = command.substring(1);

        if (isRealPlayer(source.getEntity())) {
            discordSRV.callPlayerCommand(new PlayerCommandEvent(platform.wrap((ServerPlayer) source.getEntity()), command, event));
        } else {
            CommandSender sender = NeoForgeCommandSender.of(platform, source);
            discordSRV.callServerCommand(new ServerCommandEvent(sender, command, event));
        }
    }

}
