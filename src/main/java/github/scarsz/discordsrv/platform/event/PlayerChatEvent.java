package github.scarsz.discordsrv.platform.event;

import github.scarsz.discordsrv.platform.GamePlayer;
import net.kyori.adventure.text.Component;

/**
 * A player sent a chat message
 */
public class PlayerChatEvent extends PlayerGameEvent {

    private final Component message;
    private final boolean cancelled;

    public PlayerChatEvent(GamePlayer player, Component message, boolean cancelled, Object platformEvent) {
        super(player, platformEvent);
        this.message = message;
        this.cancelled = cancelled;
    }

    /**
     * @return the message the player sent (without any chat formatting)
     */
    public Component getMessage() {
        return message;
    }

    public boolean isCancelled() {
        return cancelled;
    }

}
