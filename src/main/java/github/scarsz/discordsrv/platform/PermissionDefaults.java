package github.scarsz.discordsrv.platform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * DiscordSRV's permission nodes and who has them by default, taken from the Spigot version's plugin.yml.
 * Permission mods (eg. LuckPerms) can override these through the platform's permission system.
 */
public final class PermissionDefaults {

    public enum Default {
        /** everyone has the permission by default */
        TRUE,
        /** server operators have the permission by default */
        OP,
        /** nobody has the permission by default */
        FALSE
    }

    private static final Map<String, Default> NODES;
    private static final Map<String, String> DESCRIPTIONS;

    static {
        Map<String, Default> nodes = new LinkedHashMap<>();
        Map<String, String> descriptions = new LinkedHashMap<>();
        // player permissions (children of discordsrv.player)
        add(nodes, descriptions, "discordsrv.player", Default.TRUE, "parent permission of player-related functions of DiscordSRV");
        add(nodes, descriptions, "discordsrv.discord", Default.TRUE, "allows access to the discord command");
        add(nodes, descriptions, "discordsrv.chat", Default.TRUE, "whether the user is able to have their chat forwarded to Discord");
        add(nodes, descriptions, "discordsrv.help", Default.TRUE, "whether the player is able to display command help for DiscordSRV");
        add(nodes, descriptions, "discordsrv.nicknamesync", Default.TRUE, "whether the player should have their nickname synced with Discord, if doing so is enabled in synchronization.yml");
        add(nodes, descriptions, "discordsrv.link", Default.TRUE, "whether the player is able to link their Minecraft account to their Discord account");
        add(nodes, descriptions, "discordsrv.linked", Default.TRUE, "whether the player is able to check what Discord account their Minecraft account is linked to");
        // admin permissions (children of discordsrv.admin)
        add(nodes, descriptions, "discordsrv.admin", Default.OP, "parent permission of admin-related functions of DiscordSRV");
        add(nodes, descriptions, "discordsrv.updatenotification", Default.OP, "whether the player should be told if there's an update to DiscordSRV when joining");
        add(nodes, descriptions, "discordsrv.bcast", Default.OP, "whether the player is able to broadcast messages to the main text channel of DiscordSRV");
        add(nodes, descriptions, "discordsrv.reload", Default.OP, "whether the player is able to reload DiscordSRV's configuration");
        add(nodes, descriptions, "discordsrv.debug", Default.OP, "whether the player is able to run a debug report");
        add(nodes, descriptions, "discordsrv.link.others", Default.OP, "whether the player is able to link other peoples Minecraft accounts to Discord accounts");
        add(nodes, descriptions, "discordsrv.linked.others", Default.OP, "whether the player is able to check what Discord account other Minecraft accounts are linked to");
        add(nodes, descriptions, "discordsrv.unlink", Default.OP, "whether the player is able to unlink their Minecraft account from their Discord account");
        add(nodes, descriptions, "discordsrv.unlink.others", Default.OP, "whether the player is able to unlink other people's accounts");
        add(nodes, descriptions, "discordsrv.groupsyncwithcommands", Default.OP, "whether the player can run a permission mod command to force group sync to occur");
        add(nodes, descriptions, "discordsrv.resync", Default.OP, "whether the player can run /discord resync to force a resync of all groups/roles");
        add(nodes, descriptions, "discordsrv.language", Default.OP, "whether the player can run /discord language to change the mod's language");
        // off by default
        add(nodes, descriptions, "discordsrv.silentjoin", Default.FALSE, "whether to have join messages for players with this permission to be silenced");
        add(nodes, descriptions, "discordsrv.silentquit", Default.FALSE, "whether to have quit messages for players with this permission to be silenced");
        NODES = Collections.unmodifiableMap(nodes);
        DESCRIPTIONS = Collections.unmodifiableMap(descriptions);
    }

    private static void add(Map<String, Default> nodes, Map<String, String> descriptions, String node, Default def, String description) {
        nodes.put(node, def);
        descriptions.put(node, description);
    }

    private PermissionDefaults() {}

    /**
     * @return all static permission nodes with their defaults
     */
    public static Map<String, Default> getNodes() {
        return NODES;
    }

    public static String getDescription(String node) {
        return DESCRIPTIONS.getOrDefault(node, "");
    }

    /**
     * @return the default for the given node; group synchronization nodes ({@code discordsrv.sync.*}) and unknown
     * nodes default to {@link Default#FALSE}
     */
    public static Default getDefault(String node) {
        return NODES.getOrDefault(node.toLowerCase(Locale.ROOT), Default.FALSE);
    }

    /**
     * Converts a group name into a string usable in a permission node
     * (lowercase, only a-z, 0-9, _, - and .), eg. for {@code discordsrv.sync.<group>}
     */
    public static String sanitizeNodePart(String part) {
        return part.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }

}
