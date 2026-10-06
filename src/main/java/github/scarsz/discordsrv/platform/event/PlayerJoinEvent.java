package github.scarsz.discordsrv.platform.event;

import github.scarsz.discordsrv.platform.GamePlayer;
import net.kyori.adventure.text.Component;

/**
 * A player joined the server
 */
public class PlayerJoinEvent extends PlayerGameEvent {

    private final Component joinMessage;

    public PlayerJoinEvent(GamePlayer player, Component joinMessage, Object platformEvent) {
        super(player, platformEvent);
        this.joinMessage = joinMessage;
    }

    /**
     * @return the join message as it would be shown in game (eg. "Notch joined the game")
     */
    public Component getJoinMessage() {
        return joinMessage;
    }

}
