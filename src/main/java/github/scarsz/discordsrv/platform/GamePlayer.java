package github.scarsz.discordsrv.platform;

import github.scarsz.discordsrv.util.MessageUtil;
import net.kyori.adventure.text.Component;

import java.util.UUID;

/**
 * An online player on the Minecraft server. Replaces Bukkit's Player.
 */
public interface GamePlayer extends CommandSender {

    UUID getUniqueId();

    /**
     * @return the player's display name (eg. with team prefixes or nicknames applied)
     */
    Component getDisplayNameComponent();

    /**
     * @return the player's display name as a legacy (§) formatted string
     */
    default String getDisplayName() {
        return MessageUtil.toLegacy(getDisplayNameComponent());
    }

    /**
     * @return the name of the world (dimension) the player is in, eg. {@code overworld} or {@code the_nether}
     */
    String getWorldName();

    double getX();

    double getY();

    double getZ();

    /**
     * @return the player's latency in milliseconds
     */
    int getPing();

    /**
     * @return whether the player has joined the server before (false when this is their first join)
     */
    boolean hasPlayedBefore();

    /**
     * Disconnects the player from the server
     */
    void kick(Component reason);

    /**
     * Plays the "you've been mentioned" sound for this player
     */
    void playMentionSound();

    /**
     * @return the player's skin texture id (the last part of the textures.minecraft.net url), or null
     */
    String getSkinTexture();

    /**
     * @return whether the player is hidden from other players (vanished)
     */
    default boolean isVanished() {
        return false;
    }

    /**
     * @return the underlying platform player object (a {@code net.minecraft.server.level.ServerPlayer} on NeoForge)
     */
    Object getHandle();

}
