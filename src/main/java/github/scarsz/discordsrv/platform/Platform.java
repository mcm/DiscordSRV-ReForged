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

package github.scarsz.discordsrv.platform;

import net.kyori.adventure.text.Component;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Everything DiscordSRV needs from the server platform (NeoForge). Replaces the Bukkit API.
 * All methods are safe to call from any thread unless noted otherwise.
 */
public interface Platform {

    /**
     * @return the folder DiscordSRV stores its configuration and data in
     */
    File getDataFolder();

    /**
     * @return DiscordSRV's own version
     */
    String getModVersion();

    /**
     * @return the Minecraft version, eg. 1.21.1
     */
    String getMinecraftVersion();

    /**
     * @return a human readable server version, eg. {@code NeoForge 21.1.252 (MC: 1.21.1)}
     */
    String getServerVersion();

    String getMotd();

    int getMaxPlayers();

    boolean isOnlineMode();

    /**
     * @return the amount of players that have ever joined (the amount of player data files)
     */
    int getTotalPlayerCount();

    /**
     * @return the currently online players (a snapshot)
     */
    Collection<? extends GamePlayer> getOnlinePlayers();

    GamePlayer getPlayer(UUID uuid);

    GamePlayer getPlayer(String name);

    /**
     * Looks up a player name for the given uuid from the server's profile cache
     * @return the name or null if unknown
     */
    String getPlayerName(UUID uuid);

    /**
     * Looks up a player uuid for the given name from the server's profile cache
     * @return the uuid or null if unknown
     */
    UUID getPlayerUuid(String name);

    /**
     * @return the server console as a command sender
     */
    CommandSender getConsoleSender();

    /**
     * Checks a permission for a player that may be offline
     */
    boolean hasPermission(UUID player, String permission);

    boolean isOp(UUID player);

    boolean isWhitelisted(UUID player, String name);

    boolean isBanned(UUID player);

    /**
     * @param ip an ip address, eg. {@code 127.0.0.1}
     * @return whether the ip is on the server's ip ban list
     */
    boolean isIpBanned(String ip);

    /**
     * @return the uuids of all players on the server's ban list
     */
    Set<UUID> getBannedPlayers();

    /**
     * Adds the player to the server's ban list (kicking them if they're online)
     */
    void ban(UUID player, String name, String reason, String source);

    void unban(UUID player);

    /**
     * Executes the given command as the server console, on the main thread.
     * @param command the command, without a leading slash
     * @param feedback receives any feedback the command produces (may be called from the main thread)
     * @return a future that completes when the command has been executed (with false if it threw)
     */
    CompletableFuture<Boolean> executeConsoleCommand(String command, Consumer<Component> feedback);

    /**
     * @return whether the current thread is the server thread
     */
    boolean isMainThread();

    /**
     * Runs the given task on the server thread (immediately if already on it)
     */
    void runOnMainThread(Runnable runnable);

    default <T> CompletableFuture<T> supplyOnMainThread(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        runOnMainThread(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    /**
     * Resolves a vanilla target selector (eg. {@code @a[distance=..10]}) from the perspective of the given sender
     * @return the names of the selected entities
     * @throws Exception if the selector is invalid
     */
    java.util.List<String> selectEntityNames(CommandSender sender, String selector) throws Exception;

    /**
     * @return whether a mod with the given id is loaded
     */
    boolean isModLoaded(String modId);

    /**
     * Sends the given message to everyone online
     */
    default void broadcast(Component message) {
        for (GamePlayer player : getOnlinePlayers()) player.sendMessage(message);
    }

}
