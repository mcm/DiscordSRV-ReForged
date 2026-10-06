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
import com.mojang.brigadier.StringReader;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.platform.CommandSender;
import github.scarsz.discordsrv.platform.GamePlayer;
import github.scarsz.discordsrv.platform.Platform;
import net.kyori.adventure.text.Component;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The NeoForge implementation of DiscordSRV's {@link Platform}.
 */
public class NeoForgePlatform implements Platform {

    private final String modVersion;
    private final NeoForgePermissions permissions = new NeoForgePermissions(this);
    private final Set<UUID> firstJoins = Collections.synchronizedSet(new HashSet<>());
    private final ConsoleSender consoleSender = new ConsoleSender();
    private volatile MinecraftServer server;

    public NeoForgePlatform(String modVersion) {
        this.modVersion = modVersion;
    }

    public MinecraftServer getServer() {
        return server;
    }

    void setServer(MinecraftServer server) {
        this.server = server;
    }

    public NeoForgePermissions getPermissions() {
        return permissions;
    }

    /**
     * Marks the player as joining for the first time (no player data existed when they logged in)
     */
    void markFirstJoin(UUID uuid) {
        firstJoins.add(uuid);
    }

    /**
     * Wraps the given player, consuming the first join marker
     */
    NeoForgePlayer wrapJoiningPlayer(ServerPlayer player) {
        return new NeoForgePlayer(this, player, firstJoins.remove(player.getUUID()));
    }

    public NeoForgePlayer wrap(ServerPlayer player) {
        return player != null ? new NeoForgePlayer(this, player) : null;
    }

    private MinecraftServer requireServer() {
        MinecraftServer server = this.server;
        if (server == null) throw new IllegalStateException("The server is not running");
        return server;
    }

    @Override
    public File getDataFolder() {
        return FMLPaths.CONFIGDIR.get().resolve("discordsrv").toFile();
    }

    @Override
    public String getModVersion() {
        return modVersion;
    }

    @Override
    public String getMinecraftVersion() {
        MinecraftServer server = this.server;
        return server != null ? server.getServerVersion() : "1.21.1";
    }

    @Override
    public String getServerVersion() {
        String neoVersion = ModList.get().getModContainerById("neoforge")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("?");
        return "NeoForge " + neoVersion + " (MC: " + getMinecraftVersion() + ")";
    }

    @Override
    public String getMotd() {
        MinecraftServer server = this.server;
        return server != null ? server.getMotd() : "";
    }

    @Override
    public int getMaxPlayers() {
        MinecraftServer server = this.server;
        return server != null ? server.getMaxPlayers() : 0;
    }

    @Override
    public boolean isOnlineMode() {
        MinecraftServer server = this.server;
        return server == null || server.usesAuthentication();
    }

    @Override
    public int getTotalPlayerCount() {
        MinecraftServer server = this.server;
        if (server == null) return 0;
        File[] playerFiles = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile().listFiles(f -> f.getName().endsWith(".dat"));
        return playerFiles != null ? playerFiles.length : 0;
    }

    private List<ServerPlayer> realPlayers() {
        MinecraftServer server = this.server;
        if (server == null) return Collections.emptyList();
        // copy: the player list may be modified concurrently by the server thread
        List<ServerPlayer> players;
        try {
            players = new ArrayList<>(server.getPlayerList().getPlayers());
        } catch (ConcurrentModificationException e) {
            players = callOnMainThread(() -> new ArrayList<>(server.getPlayerList().getPlayers()));
            if (players == null) players = Collections.emptyList();
        }
        players.removeIf(player -> player instanceof FakePlayer);
        return players;
    }

    @Override
    public Collection<? extends GamePlayer> getOnlinePlayers() {
        return realPlayers().stream().map(this::wrap).collect(Collectors.toList());
    }

    @Override
    public GamePlayer getPlayer(UUID uuid) {
        if (uuid == null) return null;
        for (ServerPlayer player : realPlayers()) {
            if (player.getUUID().equals(uuid)) return wrap(player);
        }
        return null;
    }

    @Override
    public GamePlayer getPlayer(String name) {
        if (name == null) return null;
        for (ServerPlayer player : realPlayers()) {
            if (player.getGameProfile().getName().equalsIgnoreCase(name)) return wrap(player);
        }
        return null;
    }

    @Override
    public String getPlayerName(UUID uuid) {
        if (uuid == null) return null;
        GamePlayer online = getPlayer(uuid);
        if (online != null) return online.getName();
        MinecraftServer server = this.server;
        if (server == null) return null;
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) return null;
        return cache.get(uuid).map(GameProfile::getName).orElse(null);
    }

    @Override
    public UUID getPlayerUuid(String name) {
        if (name == null || name.isEmpty()) return null;
        GamePlayer online = getPlayer(name);
        if (online != null) return online.getUniqueId();
        MinecraftServer server = this.server;
        if (server == null) return null;
        GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            Optional<GameProfile> profile = cache.get(name);
            if (profile.isPresent()) return profile.get().getId();
        }
        if (!server.usesAuthentication()) return UUIDUtil.createOfflinePlayerUUID(name);
        return null;
    }

    /**
     * @return a game profile for the given uuid (with the name from the profile cache if known)
     */
    private GameProfile profile(UUID uuid) {
        String name = getPlayerName(uuid);
        return new GameProfile(uuid, name != null ? name : "");
    }

    private GameProfile profile(UUID uuid, String name) {
        if (name == null || name.isEmpty()) return profile(uuid);
        return new GameProfile(uuid, name);
    }

    @Override
    public CommandSender getConsoleSender() {
        return consoleSender;
    }

    @Override
    public boolean hasPermission(UUID player, String permission) {
        return permissions.hasPermission(player, permission);
    }

    @Override
    public boolean isOp(UUID player) {
        MinecraftServer server = this.server;
        if (server == null || player == null) return false;
        return server.getPlayerList().isOp(profile(player));
    }

    @Override
    public boolean isWhitelisted(UUID player, String name) {
        MinecraftServer server = this.server;
        if (server == null || player == null) return false;
        return server.getPlayerList().getWhiteList().isWhiteListed(profile(player, name));
    }

    @Override
    public boolean isBanned(UUID player) {
        MinecraftServer server = this.server;
        if (server == null || player == null) return false;
        return server.getPlayerList().getBans().isBanned(profile(player));
    }

    @Override
    public boolean isIpBanned(String ip) {
        MinecraftServer server = this.server;
        if (server == null || ip == null) return false;
        return server.getPlayerList().getIpBans().isBanned(ip);
    }

    @Override
    public Set<UUID> getBannedPlayers() {
        MinecraftServer server = this.server;
        if (server == null) return Collections.emptySet();
        // the ban list isn't thread safe, read it on the server thread
        String[] names = callOnMainThread(() -> server.getPlayerList().getBans().getUserList());
        if (names == null) return Collections.emptySet();
        Set<UUID> banned = new HashSet<>();
        for (String name : names) {
            UUID uuid = getPlayerUuid(name);
            if (uuid != null) banned.add(uuid);
        }
        return banned;
    }

    @Override
    public void ban(UUID player, String name, String reason, String source) {
        runOnMainThread(() -> {
            MinecraftServer server = requireServer();
            PlayerList playerList = server.getPlayerList();
            GameProfile profile = profile(player, name);
            UserBanList bans = playerList.getBans();
            if (!bans.isBanned(profile)) {
                bans.add(new UserBanListEntry(profile, null, source, null, reason));
            }
            ServerPlayer online = playerList.getPlayer(player);
            if (online != null) {
                online.connection.disconnect(net.minecraft.network.chat.Component.translatable("multiplayer.disconnect.banned"));
            }
        });
    }

    @Override
    public void unban(UUID player) {
        runOnMainThread(() -> requireServer().getPlayerList().getBans().remove(profile(player)));
    }

    @Override
    public CompletableFuture<Boolean> executeConsoleCommand(String command, Consumer<Component> feedback) {
        return supplyOnMainThread(() -> {
            MinecraftServer server = requireServer();
            CommandSource output = new CommandSource() {
                @Override
                public void sendSystemMessage(net.minecraft.network.chat.Component component) {
                    // log to the console like the normal console source does
                    server.sendSystemMessage(component);
                    if (feedback != null) {
                        try {
                            feedback.accept(ComponentConverter.toAdventure(component));
                        } catch (Throwable t) {
                            DiscordSRV.error("Error while handling command feedback", t);
                        }
                    }
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return true;
                }
            };
            CommandSourceStack source = server.createCommandSourceStack().withSource(output);
            try {
                server.getCommands().performPrefixedCommand(source, command);
                return true;
            } catch (Throwable t) {
                DiscordSRV.error("Failed to execute console command \"" + command + "\"", t);
                return false;
            }
        });
    }

    @Override
    public boolean isMainThread() {
        MinecraftServer server = this.server;
        return server != null && server.isSameThread();
    }

    @Override
    public void runOnMainThread(Runnable runnable) {
        MinecraftServer server = this.server;
        if (server == null) {
            DiscordSRV.debug("Tried running a task on the main thread while the server isn't running");
            return;
        }
        if (server.isSameThread()) {
            runnable.run();
        } else {
            server.execute(runnable);
        }
    }

    /**
     * Runs the supplier on the main thread and waits (up to 10 seconds) for the result
     */
    <T> T callOnMainThread(Supplier<T> supplier) {
        if (isMainThread()) return supplier.get();
        try {
            return supplyOnMainThread(supplier).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            DiscordSRV.debug(e);
            return null;
        }
    }

    @Override
    public List<String> selectEntityNames(CommandSender sender, String selector) throws Exception {
        MinecraftServer server = requireServer();
        CommandSourceStack source;
        if (sender instanceof NeoForgePlayer) {
            source = ((NeoForgePlayer) sender).getHandle().createCommandSourceStack().withPermission(2);
        } else if (sender instanceof NeoForgeCommandSender) {
            source = ((NeoForgeCommandSender) sender).getSource();
        } else {
            source = server.createCommandSourceStack();
        }
        EntitySelector entitySelector = EntityArgument.entities().parse(new StringReader(selector));
        CommandSourceStack finalSource = source;
        List<String> names = callOnMainThread(() -> {
            try {
                return entitySelector.findEntities(finalSource).stream()
                        .map(Entity::getName)
                        .map(net.minecraft.network.chat.Component::getString)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                return null;
            }
        });
        if (names == null) throw new IllegalArgumentException("Invalid selector " + selector);
        return names;
    }

    private volatile FtbRanksBridge ftbRanks;

    /**
     * @return the FTB Ranks bridge, or null if FTB Ranks isn't installed
     */
    FtbRanksBridge getFtbRanks() {
        if (ftbRanks == null && isModLoaded("ftbranks")) {
            synchronized (this) {
                if (ftbRanks == null) ftbRanks = new FtbRanksBridge(this);
            }
        }
        return ftbRanks;
    }

    @Override
    public github.scarsz.discordsrv.hooks.permissions.GroupProvider createGroupProvider(String id) {
        if ("ftbranks".equals(id)) return getFtbRanks();
        return null;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    /**
     * The server console as a command sender
     */
    private class ConsoleSender implements CommandSender {

        @Override
        public String getName() {
            return "CONSOLE";
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }

        @Override
        public void sendMessage(Component message) {
            MinecraftServer server = NeoForgePlatform.this.server;
            if (server != null) {
                server.sendSystemMessage(ComponentConverter.toMinecraft(message, server));
            } else {
                DiscordSRV.info(github.scarsz.discordsrv.util.MessageUtil.strip(github.scarsz.discordsrv.util.MessageUtil.toLegacy(message)));
            }
        }

        @Override
        public boolean isConsole() {
            return true;
        }

    }

}
