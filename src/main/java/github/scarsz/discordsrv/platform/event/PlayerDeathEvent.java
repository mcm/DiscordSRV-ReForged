package github.scarsz.discordsrv.platform.event;

import github.scarsz.discordsrv.platform.GamePlayer;
import net.kyori.adventure.text.Component;

/**
 * A player died
 */
public class PlayerDeathEvent extends PlayerGameEvent {

    private final Component deathMessage;

    public PlayerDeathEvent(GamePlayer player, Component deathMessage, Object platformEvent) {
        super(player, platformEvent);
        this.deathMessage = deathMessage;
    }

    /**
     * @return the death message, or null if death messages are not shown
     */
    public Component getDeathMessage() {
        return deathMessage;
    }

}
