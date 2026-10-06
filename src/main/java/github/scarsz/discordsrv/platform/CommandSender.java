package github.scarsz.discordsrv.platform;

import github.scarsz.discordsrv.util.MessageUtil;
import net.kyori.adventure.text.Component;

/**
 * Something that can run DiscordSRV commands and receive messages: a player, the server console or a
 * Discord-originated command source. Replaces Bukkit's CommandSender.
 */
public interface CommandSender {

    /**
     * @return the name of this sender (player name, or "CONSOLE")
     */
    String getName();

    /**
     * @param permission the permission node, eg. {@code discordsrv.link}
     * @return whether this sender has the given permission
     */
    boolean hasPermission(String permission);

    /**
     * Sends the given component to this sender
     */
    void sendMessage(Component message);

    /**
     * Sends a legacy (&amp;/§) or MiniMessage formatted message to this sender
     */
    default void sendMessage(String message) {
        MessageUtil.sendMessage(this, message);
    }

    /**
     * @return whether this sender is the server console
     */
    default boolean isConsole() {
        return false;
    }

}
