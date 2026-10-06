package github.scarsz.discordsrv.platform.event;

/**
 * Base class for game events that the platform (NeoForge) forwards to DiscordSRV.
 * These take the place of the Bukkit events the Spigot version listened to; their names match the Bukkit
 * event names where possible so that alerts.yml triggers keep working.
 */
public abstract class GameEvent {

    private final Object platformEvent;

    protected GameEvent(Object platformEvent) {
        this.platformEvent = platformEvent;
    }

    /**
     * @return the platform's own event object that triggered this event (eg. a NeoForge event), may be null
     */
    public Object getPlatformEvent() {
        return platformEvent;
    }

    /**
     * @return the event's name, used for matching alert triggers
     */
    public String getEventName() {
        return getClass().getSimpleName();
    }

}
