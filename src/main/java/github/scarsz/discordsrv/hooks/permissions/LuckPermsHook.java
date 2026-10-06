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

package github.scarsz.discordsrv.hooks.permissions;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;
import github.scarsz.discordsrv.objects.managers.GroupSynchronizationManager;
import github.scarsz.discordsrv.platform.GamePlayer;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.SchedulerUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * LuckPerms hook: the permissions &amp; groups provider for DiscordSRV on NeoForge (replaces Vault).
 * <p>
 * LuckPerms is an optional dependency, so this class must never reference {@code net.luckperms.api} classes
 * itself; everything touching LuckPerms lives in {@link Hook}, which is only loaded after {@link #enable()}
 * was called (when the LuckPerms mod is present).
 * <p>
 * All methods are safe to call when LuckPerms is absent: they return null/false/empty values.
 * Methods that may need to load a user from LuckPerms' storage (for offline players) only do so when not
 * called from the server thread; on the server thread only already loaded (online) users are used.
 */
public class LuckPermsHook {

    private static volatile Hook hook = null;

    private LuckPermsHook() {}

    /**
     * Hooks into LuckPerms. Must only be called when the LuckPerms mod is loaded.
     */
    public static synchronized void enable() {
        if (hook != null) hook.disable();
        hook = new Hook();
    }

    /**
     * Unregisters DiscordSRV's LuckPerms listeners & context calculators
     */
    public static synchronized void disable() {
        if (hook != null) {
            try {
                hook.disable();
            } catch (Throwable t) {
                DiscordSRV.debug("Failed to unhook LuckPerms: " + t);
            }
            hook = null;
        }
    }

    /**
     * @return whether LuckPerms is present and hooked
     */
    public static boolean isEnabled() {
        return hook != null;
    }

    /**
     * @return the primary group of the given (loaded/online) player, null if unavailable
     */
    public static String getPrimaryGroup(UUID uuid) {
        Hook hook = LuckPermsHook.hook;
        if (hook == null || uuid == null) return null;
        try {
            return hook.getPrimaryGroup(uuid, false);
        } catch (Throwable t) {
            DiscordSRV.debug("Failed to get primary group from LuckPerms for " + uuid + ": " + t);
            return null;
        }
    }

    /**
     * @return the primary group of the given player, loading them from LuckPerms' storage if they're offline
     * (when not on the server thread), null if unavailable
     */
    public static String getPrimaryGroupLoading(UUID uuid) {
        Hook hook = LuckPermsHook.hook;
        if (hook == null || uuid == null) return null;
        return hook.getPrimaryGroup(uuid, true);
    }

    /**
     * @return the names of all groups known to LuckPerms, empty if LuckPerms is unavailable
     */
    public static String[] getGroups() {
        Hook hook = LuckPermsHook.hook;
        if (hook == null) return new String[0];
        return hook.getGroups();
    }

    /**
     * @return whether a group with the given name exists
     */
    public static boolean groupExists(String group) {
        Hook hook = LuckPermsHook.hook;
        return hook != null && hook.groupExists(group);
    }

    /**
     * @return the groups the player is a direct member of (in their current contexts), null if the user couldn't be loaded
     */
    public static String[] getPlayerGroups(UUID uuid) {
        Hook hook = LuckPermsHook.hook;
        if (hook == null || uuid == null) return null;
        return hook.getPlayerGroups(uuid);
    }

    /**
     * @return whether the player is in (or inherits) the given group
     */
    public static boolean playerInGroup(UUID uuid, String group) {
        Hook hook = LuckPermsHook.hook;
        if (hook == null || uuid == null) return false;
        return hook.playerInGroup(uuid, group);
    }

    /**
     * Adds the player to the given group &amp; saves the user
     * @return whether the group was added
     */
    public static boolean playerAddGroup(UUID uuid, String group) {
        Hook hook = LuckPermsHook.hook;
        if (hook == null || uuid == null) return false;
        return hook.modifyGroup(uuid, group, true);
    }

    /**
     * Removes the player from the given group &amp; saves the user
     * @return whether the group was removed
     */
    public static boolean playerRemoveGroup(UUID uuid, String group) {
        Hook hook = LuckPermsHook.hook;
        if (hook == null || uuid == null) return false;
        return hook.modifyGroup(uuid, group, false);
    }

    /**
     * @return whether the player has the given permission according to LuckPerms (loading offline users when not
     * on the server thread), false if unavailable
     */
    public static boolean playerHas(UUID uuid, String permission) {
        Hook hook = LuckPermsHook.hook;
        if (hook == null || uuid == null) return false;
        return hook.playerHas(uuid, permission);
    }

    /**
     * The actual hook, the only class that touches the LuckPerms API.
     */
    private static class Hook implements net.luckperms.api.context.ContextCalculator<Object> {

        private static final String CONTEXT_LINKED = "discordsrv:linked";
        private static final String CONTEXT_BOOSTING = "discordsrv:boosting";
        private static final String CONTEXT_ROLE = "discordsrv:role";
        private static final String CONTEXT_ROLE_ID = "discordsrv:role_id";
        private static final String CONTEXT_SERVER_ID = "discordsrv:server_id";

        private static final long LOAD_TIMEOUT_SECONDS = 10;

        private final net.luckperms.api.LuckPerms luckPerms;
        private final Set<net.luckperms.api.event.EventSubscription<?>> subscriptions = new HashSet<>();
        private boolean contextsRegistered = false;
        private Method getUuidMethod = null;

        Hook() {
            luckPerms = net.luckperms.api.LuckPermsProvider.get();

            // update events
            if (!DiscordSRV.config().getStringList("DisabledPluginHooks").contains("LuckPerms-GroupUpdates")) {
                DiscordSRV.debug("Enabling LuckPerms' instant group updates");
                net.luckperms.api.event.EventBus eventBus = luckPerms.getEventBus();
                subscriptions.add(eventBus.subscribe(net.luckperms.api.event.user.track.UserTrackEvent.class, event -> handle(event.getUser().getUniqueId())));
                subscriptions.add(eventBus.subscribe(net.luckperms.api.event.node.NodeAddEvent.class, event -> handle(event, event.getNode(), true)));
                subscriptions.add(eventBus.subscribe(net.luckperms.api.event.node.NodeRemoveEvent.class, event -> handle(event, event.getNode(), false)));
            } else {
                DiscordSRV.debug("Not using LuckPerms' instant group updates because they are disabled in the config");
            }

            // contexts
            if (!DiscordSRV.config().getStringList("DisabledPluginHooks").contains("LuckPerms-Contexts")) {
                DiscordSRV.debug("Enabling LuckPerms' contexts");
                luckPerms.getContextManager().registerCalculator(this);
                contextsRegistered = true;
            } else {
                DiscordSRV.debug("Not using LuckPerms' contexts because they are disabled in the config");
            }
        }

        void disable() {
            subscriptions.forEach(net.luckperms.api.event.EventSubscription::close);
            subscriptions.clear();
            if (contextsRegistered) {
                luckPerms.getContextManager().unregisterCalculator(this);
                contextsRegistered = false;
            }
        }

        private void handle(net.luckperms.api.event.node.NodeMutateEvent event, net.luckperms.api.node.Node node, boolean add) {
            if (event.isUser() && node.getType() == net.luckperms.api.node.NodeType.INHERITANCE) {
                String groupName = net.luckperms.api.node.NodeType.INHERITANCE.cast(node).getGroupName();
                UUID uuid = ((net.luckperms.api.model.user.User) event.getTarget()).getUniqueId();
                Map<String, List<String>> justModified = DiscordSRV.getPlugin()
                        .getGroupSynchronizationManager().getJustModifiedGroups().getOrDefault(uuid, null);
                if (justModified != null && justModified.getOrDefault(add ? "add" : "remove", Collections.emptyList()).remove(groupName)) {
                    return;
                }
                handle(uuid);
            }
        }

        private void handle(UUID user) {
            if (!DiscordSRV.getPlugin().isGroupRoleSynchronizationEnabled()) return;
            SchedulerUtil.runTaskLaterAsynchronously(
                    () -> DiscordSRV.getPlugin().getGroupSynchronizationManager().resync(
                            user,
                            GroupSynchronizationManager.SyncDirection.TO_DISCORD,
                            GroupSynchronizationManager.SyncCause.MINECRAFT_GROUP_EDIT_API
                    ),
                    5
            );
        }

        // --- permissions & groups (replaces Vault's Permission) ---

        /**
         * Gets the LuckPerms user, loading it from storage if it isn't loaded and we're not on the server thread
         */
        private net.luckperms.api.model.user.User getUser(UUID uuid, boolean load) {
            net.luckperms.api.model.user.UserManager userManager = luckPerms.getUserManager();
            net.luckperms.api.model.user.User user = userManager.getUser(uuid);
            if (user != null || !load) return user;
            if (DiscordSRV.getPlatform().isMainThread()) {
                DiscordSRV.debug(Debug.GROUP_SYNC, "Not loading LuckPerms user " + uuid + " on the server thread");
                return null;
            }
            try {
                return userManager.loadUser(uuid).get(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                DiscordSRV.error("Failed to load LuckPerms user " + uuid, e);
                return null;
            }
        }

        private net.luckperms.api.query.QueryOptions getQueryOptions(net.luckperms.api.model.user.User user) {
            return luckPerms.getContextManager().getQueryOptions(user)
                    .orElseGet(() -> luckPerms.getContextManager().getStaticQueryOptions());
        }

        String getPrimaryGroup(UUID uuid, boolean load) {
            net.luckperms.api.model.user.User user = getUser(uuid, load);
            return user != null ? user.getPrimaryGroup() : null;
        }

        String[] getGroups() {
            return luckPerms.getGroupManager().getLoadedGroups().stream()
                    .map(net.luckperms.api.model.group.Group::getName)
                    .toArray(String[]::new);
        }

        boolean groupExists(String group) {
            return group != null && luckPerms.getGroupManager().getGroup(group) != null;
        }

        String[] getPlayerGroups(UUID uuid) {
            net.luckperms.api.model.user.User user = getUser(uuid, true);
            if (user == null) return null;
            net.luckperms.api.query.QueryOptions queryOptions = getQueryOptions(user);
            return user.getNodes(net.luckperms.api.node.NodeType.INHERITANCE).stream()
                    .filter(net.luckperms.api.node.Node::getValue)
                    .filter(node -> !node.hasExpired())
                    .filter(node -> queryOptions.satisfies(node.getContexts()))
                    .map(net.luckperms.api.node.types.InheritanceNode::getGroupName)
                    .distinct()
                    .toArray(String[]::new);
        }

        boolean playerInGroup(UUID uuid, String group) {
            if (group == null) return false;
            return playerHas(uuid, net.luckperms.api.node.types.InheritanceNode.builder(group).build().getKey());
        }

        boolean playerHas(UUID uuid, String permission) {
            net.luckperms.api.model.user.User user = getUser(uuid, true);
            if (user == null) return false;
            return user.getCachedData().getPermissionData(getQueryOptions(user)).checkPermission(permission).asBoolean();
        }

        boolean modifyGroup(UUID uuid, String group, boolean add) {
            if (group == null) return false;
            net.luckperms.api.model.user.User user = getUser(uuid, true);
            if (user == null) return false;
            net.luckperms.api.node.types.InheritanceNode node = net.luckperms.api.node.types.InheritanceNode.builder(group).build();
            net.luckperms.api.model.data.DataMutateResult result = add ? user.data().add(node) : user.data().remove(node);
            if (!result.wasSuccessful()) return false;
            luckPerms.getUserManager().saveUser(user).whenComplete((v, t) -> {
                if (t != null) DiscordSRV.error("Failed to save LuckPerms user " + uuid, t);
            });
            return true;
        }

        // --- contexts ---

        /**
         * Resolves the uuid of a LuckPerms context target (a platform player object)
         */
        private UUID getUniqueId(Object target) {
            if (target instanceof GamePlayer) return ((GamePlayer) target).getUniqueId();
            for (GamePlayer player : DiscordSRV.getPlatform().getOnlinePlayers()) {
                if (player.getHandle() == target) return player.getUniqueId();
            }
            // not (yet) in the online player list, eg. while logging in
            try {
                if (getUuidMethod == null || !getUuidMethod.getDeclaringClass().isInstance(target)) {
                    getUuidMethod = target.getClass().getMethod("getUUID");
                }
                Object uuid = getUuidMethod.invoke(target);
                if (uuid instanceof UUID) return (UUID) uuid;
            } catch (Exception ignored) {}
            return null;
        }

        @Override
        public void calculate(@NotNull Object target, net.luckperms.api.context.@NotNull ContextConsumer consumer) {
            UUID uuid = getUniqueId(target);
            if (uuid == null) {
                DiscordSRV.debug(Debug.LP_CONTEXTS, "Unable to determine the uuid of " + target + ", unable to provide contexts data");
                return;
            }
            AccountLinkManager accountLinkManager = DiscordSRV.getPlugin().getAccountLinkManager();
            if (accountLinkManager == null) return;
            if (!accountLinkManager.isInCache(uuid)) {
                // this *shouldn't* happen
                DiscordSRV.debug(Debug.LP_CONTEXTS, "Player " + target + " was not in cache when LP contexts were requested, unable to provide contexts data (online player: " + DiscordSRV.getPlatform().getPlayer(uuid) + ")");
                return;
            }
            String userId = accountLinkManager.getDiscordIdFromCache(uuid);
            consumer.accept(CONTEXT_LINKED, Boolean.toString(userId != null));

            if (userId == null) {
                return;
            }

            if (DiscordUtil.getJda() == null) return;
            User user = DiscordUtil.getJda().getUserById(userId);
            if (user == null) return;

            for (Guild guild : DiscordUtil.getJda().getGuilds()) {
                if (guild.getMember(user) == null) continue;

                consumer.accept(CONTEXT_SERVER_ID, guild.getId());
            }

            Guild mainGuild = DiscordSRV.getPlugin().getMainGuild();
            if (mainGuild == null) {
                return;
            }

            Member member = mainGuild.getMemberById(userId);
            if (member == null) {
                return;
            }

            consumer.accept(CONTEXT_BOOSTING, Boolean.toString(member.getTimeBoosted() != null));

            for (Role role : member.getRoles()) {
                if (StringUtils.isBlank(role.getName())) {
                    continue;
                }
                consumer.accept(CONTEXT_ROLE, role.getName());
                consumer.accept(CONTEXT_ROLE_ID, role.getId());
            }

        }

        @Override
        public net.luckperms.api.context.@NotNull ContextSet estimatePotentialContexts() {
            net.luckperms.api.context.ImmutableContextSet.Builder builder = net.luckperms.api.context.ImmutableContextSet.builder();

            builder.add(CONTEXT_LINKED, "true");
            builder.add(CONTEXT_LINKED, "false");

            builder.add(CONTEXT_BOOSTING, "true");
            builder.add(CONTEXT_BOOSTING, "false");

            if (DiscordUtil.getJda() == null) return builder.build();

            Guild mainGuild = DiscordSRV.getPlugin().getMainGuild();
            if (mainGuild != null) {
                for (Role role : mainGuild.getRoles()) {
                    if (StringUtils.isBlank(role.getName())) {
                        continue;
                    }
                    builder.add(CONTEXT_ROLE, role.getName());
                    builder.add(CONTEXT_ROLE_ID, role.getId());
                }
            }

            for (Guild guild : DiscordUtil.getJda().getGuilds()) {
                builder.add(CONTEXT_SERVER_ID, guild.getId());
            }

            return builder.build();
        }

    }

}
