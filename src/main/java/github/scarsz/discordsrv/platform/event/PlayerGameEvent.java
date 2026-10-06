package github.scarsz.discordsrv.platform.event;

import github.scarsz.discordsrv.platform.GamePlayer;

public abstract class PlayerGameEvent extends GameEvent {

    private final GamePlayer player;

    protected PlayerGameEvent(GamePlayer player, Object platformEvent) {
        super(platformEvent);
        this.player = player;
    }

    public GamePlayer getPlayer() {
        return player;
    }

}
