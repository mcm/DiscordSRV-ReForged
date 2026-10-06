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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import github.scarsz.discordsrv.platform.GamePlayer;
import net.kyori.adventure.text.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link GamePlayer} backed by a {@link ServerPlayer}.
 * <p>
 * Instances are lightweight wrappers that may be created and thrown away freely; equality is based on the uuid.
 * Methods reading simple player state are safe to call from any thread (they read fields only), methods that
 * change state are executed on the server thread.
 */
public class NeoForgePlayer implements GamePlayer {

    private final NeoForgePlatform platform;
    private final ServerPlayer player;
    private final boolean firstJoin;

    public NeoForgePlayer(NeoForgePlatform platform, ServerPlayer player) {
        this(platform, player, false);
    }

    /**
     * @param firstJoin whether this player is joining the server for the first time (only meaningful during login)
     */
    public NeoForgePlayer(NeoForgePlatform platform, ServerPlayer player, boolean firstJoin) {
        this.platform = platform;
        this.player = player;
        this.firstJoin = firstJoin;
    }

    @Override
    public ServerPlayer getHandle() {
        return player;
    }

    @Override
    public UUID getUniqueId() {
        return player.getUUID();
    }

    @Override
    public String getName() {
        return player.getGameProfile().getName();
    }

    @Override
    public Component getDisplayNameComponent() {
        return ComponentConverter.toAdventure(player.getDisplayName());
    }

    @Override
    public boolean hasPermission(String permission) {
        return platform.getPermissions().hasPermission(player, permission);
    }

    @Override
    public void sendMessage(Component message) {
        MinecraftServer server = platform.getServer();
        net.minecraft.network.chat.Component converted = ComponentConverter.toMinecraft(message, server);
        platform.runOnMainThread(() -> player.sendSystemMessage(converted));
    }

    @Override
    public String getWorldName() {
        return player.level().dimension().location().getPath();
    }

    @Override
    public double getX() {
        return player.getX();
    }

    @Override
    public double getY() {
        return player.getY();
    }

    @Override
    public double getZ() {
        return player.getZ();
    }

    @Override
    public int getPing() {
        return player.connection != null ? player.connection.latency() : -1;
    }

    @Override
    public boolean hasPlayedBefore() {
        return !firstJoin;
    }

    @Override
    public void kick(Component reason) {
        net.minecraft.network.chat.Component converted = ComponentConverter.toMinecraft(reason, platform.getServer());
        platform.runOnMainThread(() -> {
            if (player.connection != null) player.connection.disconnect(converted);
        });
    }

    @Override
    public void playMentionSound() {
        platform.runOnMainThread(() -> player.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1F, 1F));
    }

    @Override
    public String getSkinTexture() {
        try {
            for (Property property : player.getGameProfile().getProperties().get("textures")) {
                String json = new String(Base64.getDecoder().decode(property.value()), StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
                if (textures == null || !textures.has("SKIN")) continue;
                JsonElement url = textures.getAsJsonObject("SKIN").get("url");
                if (url == null) continue;
                String value = url.getAsString();
                return value.substring(value.lastIndexOf('/') + 1);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public boolean isVanished() {
        // there's no common vanish API on NeoForge; spectators are not hidden from the player list either
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NeoForgePlayer)) return false;
        return Objects.equals(getUniqueId(), ((NeoForgePlayer) o).getUniqueId());
    }

    @Override
    public int hashCode() {
        return getUniqueId().hashCode();
    }

    @Override
    public String toString() {
        return "NeoForgePlayer{" + getName() + "/" + getUniqueId() + "}";
    }

}
