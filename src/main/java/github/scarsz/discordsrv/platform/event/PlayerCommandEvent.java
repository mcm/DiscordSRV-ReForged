package github.scarsz.discordsrv.platform.event;

import github.scarsz.discordsrv.platform.GamePlayer;

/**
 * A player ran a command (named after Bukkit's PlayerCommandPreprocessEvent equivalent)
 */
public class PlayerCommandEvent extends PlayerGameEvent {

    private final String command;

    public PlayerCommandEvent(GamePlayer player, String command, Object platformEvent) {
        super(player, platformEvent);
        this.command = command;
    }

    /**
     * @return the command, without the leading slash
     */
    public String getCommand() {
        return command;
    }

}
