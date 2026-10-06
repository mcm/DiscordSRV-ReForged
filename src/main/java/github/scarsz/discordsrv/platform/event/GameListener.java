package github.scarsz.discordsrv.platform.event;

/**
 * Receives game events forwarded by the platform. Register with
 * {@link github.scarsz.discordsrv.DiscordSRV#registerGameListener(GameListener)}.
 * This takes the place of Bukkit's Listener interface.
 */
public interface GameListener {

    default void onPlayerJoin(PlayerJoinEvent event) {}

    default void onPlayerQuit(PlayerQuitEvent event) {}

    default void onPlayerChat(PlayerChatEvent event) {}

    default void onPlayerDeath(PlayerDeathEvent event) {}

    default void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {}

    default void onPlayerCommand(PlayerCommandEvent event) {}

    default void onServerCommand(ServerCommandEvent event) {}

}
