package github.scarsz.discordsrv.platform.event;

import github.scarsz.discordsrv.platform.GamePlayer;
import net.kyori.adventure.text.Component;

/**
 * A player left the server
 */
public class PlayerQuitEvent extends PlayerGameEvent {

    private final Component quitMessage;

    public PlayerQuitEvent(GamePlayer player, Component quitMessage, Object platformEvent) {
        super(player, platformEvent);
        this.quitMessage = quitMessage;
    }

    /**
     * @return the quit message as it would be shown in game (eg. "Notch left the game")
     */
    public Component getQuitMessage() {
        return quitMessage;
    }

}
