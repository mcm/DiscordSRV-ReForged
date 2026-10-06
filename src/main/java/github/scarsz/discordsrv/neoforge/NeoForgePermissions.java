/*
 * DiscordSRV - https://github.com/DiscordSRV/DiscordSRV
 *
 * Copyright (C) 2016 - 2024 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */

package github.scarsz.discordsrv.neoforge;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.platform.PermissionDefaults;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.exceptions.UnregisteredPermissionException;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps DiscordSRV's permission nodes onto NeoForge's PermissionAPI, so permission mods that implement a NeoForge
 * permission handler (LuckPerms, FTB Ranks, ...) can manage them. Without such a mod, the defaults from
 * {@link PermissionDefaults} apply: "op" permissions are granted to server operators.
 */
public class NeoForgePermissions {

    private final NeoForgePlatform platform;
    private final Map<String, PermissionNode<Boolean>> nodes = new ConcurrentHashMap<>();

    public NeoForgePermissions(NeoForgePlatform platform) {
        this.platform = platform;
    }

    /**
     * Registers all of DiscordSRV's nodes. Called by NeoForge every time a server starts.
     */
    public void onGatherNodes(PermissionGatherEvent.Nodes event) {
        nodes.clear();
        for (Map.Entry<String, PermissionDefaults.Default> entry : PermissionDefaults.getNodes().entrySet()) {
            register(entry.getKey(), entry.getValue(), PermissionDefaults.getDescription(entry.getKey()));
        }
        // group synchronization permissions: discordsrv.sync.<group> & discordsrv.sync.deny.<group>
        if (DiscordSRV.getPlugin() != null) {
            for (String group : DiscordSRV.getPlugin().getGroupSynchronizables().keySet()) {
                if (group.isEmpty()) continue;
                String part = PermissionDefaults.sanitizeNodePart(group);
                register("discordsrv.sync." + part, PermissionDefaults.Default.FALSE, "group synchronization for " + group);
                register("discordsrv.sync.deny." + part, PermissionDefaults.Default.FALSE, "deny group synchronization for " + group);
            }
        }
        event.addNodes(nodes.values().toArray(new PermissionNode<?>[0]));
    }

    private void register(String node, PermissionDefaults.Default def, String description) {
        if (nodes.containsKey(node)) return;
        // node names are "modid.path"
        String path = node.substring("discordsrv.".length());
        PermissionNode<Boolean> permissionNode = new PermissionNode<>("discordsrv", path, PermissionTypes.BOOLEAN,
                (player, uuid, context) -> resolveDefault(def, uuid));
        permissionNode.setInformation(Component.literal(node), Component.literal(description));
        nodes.put(node, permissionNode);
    }

    private boolean resolveDefault(PermissionDefaults.Default def, UUID uuid) {
        switch (def) {
            case TRUE: return true;
            case OP: return uuid != null && platform.isOp(uuid);
            default: return false;
        }
    }

    private static String normalize(String permission) {
        String node = permission.toLowerCase(Locale.ROOT);
        if (node.startsWith("discordsrv.sync.")) {
            // group names are sanitized when registering
            boolean deny = node.startsWith("discordsrv.sync.deny.");
            String prefix = deny ? "discordsrv.sync.deny." : "discordsrv.sync.";
            node = prefix + PermissionDefaults.sanitizeNodePart(node.substring(prefix.length()));
        }
        return node;
    }

    public boolean hasPermission(ServerPlayer player, String permission) {
        String node = normalize(permission);
        PermissionNode<Boolean> permissionNode = nodes.get(node);
        if (permissionNode != null) {
            try {
                return Boolean.TRUE.equals(PermissionAPI.getPermission(player, permissionNode));
            } catch (UnregisteredPermissionException ignored) {
                // registered after the permission handler was created (eg. group added on reload)
            }
        }
        return fallback(node, player.getUUID());
    }

    public boolean hasPermission(UUID uuid, String permission) {
        MinecraftServer server = platform.getServer();
        if (server != null) {
            ServerPlayer online = server.getPlayerList().getPlayer(uuid);
            if (online != null) return hasPermission(online, permission);
        }
        String node = normalize(permission);
        PermissionNode<Boolean> permissionNode = nodes.get(node);
        if (permissionNode != null) {
            try {
                return Boolean.TRUE.equals(PermissionAPI.getOfflinePermission(uuid, permissionNode));
            } catch (UnregisteredPermissionException ignored) {}
        }
        return fallback(node, uuid);
    }

    private boolean fallback(String node, UUID uuid) {
        if (node.startsWith("discordsrv.")) return resolveDefault(PermissionDefaults.getDefault(node), uuid);
        // a permission from another mod: only operators
        return platform.isOp(uuid);
    }

}
