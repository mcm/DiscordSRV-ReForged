package github.scarsz.discordsrv.platform.event;

import github.scarsz.discordsrv.platform.CommandSender;

/**
 * A non-player (the console, a command block, ...) ran a command
 */
public class ServerCommandEvent extends GameEvent {

    private final CommandSender sender;
    private final String command;

    public ServerCommandEvent(CommandSender sender, String command, Object platformEvent) {
        super(platformEvent);
        this.sender = sender;
        this.command = command;
    }

    public CommandSender getSender() {
        return sender;
    }

    /**
     * @return the command, without the leading slash
     */
    public String getCommand() {
        return command;
    }

}
