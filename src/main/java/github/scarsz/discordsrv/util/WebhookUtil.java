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

package github.scarsz.discordsrv.util;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.platform.GamePlayer;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class WebhookUtil {

    private static final Predicate<Webhook> LEGACY = hook -> hook.getName().endsWith("#1") || hook.getName().endsWith("#2");
    private static boolean loggedBannedWords = false;

    static {
        try {
            // get rid of all previous webhooks created by DiscordSRV if they don't match a good channel
            for (Guild guild : DiscordSRV.getPlugin().getJda().getGuilds()) {
                Member selfMember = guild.getSelfMember();
                if (!selfMember.hasPermission(Permission.MANAGE_WEBHOOKS)) {
                    DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Unable to manage webhooks guild-wide in " + guild);
                    continue;
                }

                guild.retrieveWebhooks().queue(webhooks -> {
                    for (Webhook webhook : webhooks) {
                        Member owner = webhook.getOwner();
                        if (owner == null || !owner.getId().equals(selfMember.getId()) || !webhook.getName().startsWith("DiscordSRV")) {
                            continue;
                        }

                        TextChannel webhookChannel = webhook.getChannel() instanceof TextChannel ? (TextChannel) webhook.getChannel() : null;
                        if (DiscordSRV.getPlugin().getDestinationGameChannelNameForTextChannel(webhookChannel) == null) {
                            webhook.delete().reason("DiscordSRV: Purging webhook for unlinked channel").queue();
                        } else if (LEGACY.test(webhook)) {
                            webhook.delete().reason("DiscordSRV: Purging legacy formatted webhook").queue();
                        }
                    }
                });
            }
        } catch (Exception e) {
            DiscordSRV.warning("Failed to purge already existing webhooks: " + e.getMessage());
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, e);
        }
    }

    public static void deliverMessage(TextChannel channel, GamePlayer player, String message) {
        deliverMessage(channel, player, message, (Collection<? extends MessageEmbed>) null);
    }

    public static void deliverMessage(TextChannel channel, GamePlayer player, String message, MessageEmbed embed) {
        deliverMessage(channel, player, MessageUtil.strip(player.getDisplayName()), message, embed);
    }

    public static void deliverMessage(TextChannel channel, GamePlayer player, String message, Collection<? extends MessageEmbed> embeds) {
        deliverMessage(channel, player, MessageUtil.strip(player.getDisplayName()), message, embeds);
    }

    public static void deliverMessage(TextChannel channel, GamePlayer player, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions) {
        deliverMessage(channel, player, MessageUtil.strip(player.getDisplayName()), message, embed, attachments, interactions);
    }

    public static void deliverMessage(TextChannel channel, GamePlayer player, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions) {
        deliverMessage(channel, player, MessageUtil.strip(player.getDisplayName()), message, embeds, attachments, interactions);
    }

    public static void deliverMessage(TextChannel channel, GamePlayer player, String displayName, String message, MessageEmbed embed) {
        deliverMessage(channel, player.getUniqueId(), player, player.getName(), displayName, message, Collections.singletonList(embed), null, null);
    }

    public static void deliverMessage(TextChannel channel, GamePlayer player, String displayName, String message, Collection<? extends MessageEmbed> embeds) {
        deliverMessage(channel, player.getUniqueId(), player, player.getName(), displayName, message, embeds, null, null);
    }

    public static void deliverMessage(TextChannel channel, GamePlayer player, String displayName, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions) {
        deliverMessage(channel, player.getUniqueId(), player, player.getName(), displayName, message, Collections.singletonList(embed), attachments, interactions);
    }

    public static void deliverMessage(TextChannel channel, GamePlayer player, String displayName, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions) {
        deliverMessage(channel, player.getUniqueId(), player, player.getName(), displayName, message, embeds, attachments, interactions);
    }

    /**
     * Delivers a message as the given (possibly offline) player
     */
    public static void deliverMessage(TextChannel channel, UUID playerUuid, String displayName, String message, MessageEmbed embed) {
        deliverMessage(channel, playerUuid, displayName, message, Collections.singletonList(embed), null, null);
    }

    public static void deliverMessage(TextChannel channel, UUID playerUuid, String displayName, String message, Collection<? extends MessageEmbed> embeds) {
        deliverMessage(channel, playerUuid, displayName, message, embeds, null, null);
    }

    public static void deliverMessage(TextChannel channel, UUID playerUuid, String displayName, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions) {
        deliverMessage(channel, playerUuid, displayName, message, Collections.singletonList(embed), attachments, interactions);
    }

    public static void deliverMessage(TextChannel channel, UUID playerUuid, String displayName, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions) {
        GamePlayer player = DiscordSRV.getPlatform().getPlayer(playerUuid);
        String name = player != null ? player.getName() : DiscordSRV.getPlatform().getPlayerName(playerUuid);
        deliverMessage(channel, playerUuid, player, name, displayName, message, embeds, attachments, interactions);
    }

    private static void deliverMessage(TextChannel channel, UUID playerUuid, GamePlayer player, String playerName, String displayName, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions) {
        SchedulerUtil.runTaskAsynchronously(() -> {
            String avatarUrl;
            if (player != null) {
                avatarUrl = DiscordSRV.getAvatarUrl(player);
            } else {
                avatarUrl = DiscordSRV.getAvatarUrl(playerName, playerUuid);
            }

            String safeName = String.valueOf(playerName);
            String safeDisplayName = displayName != null ? displayName : safeName;
            String username = DiscordSRV.config().getString("Experiment_WebhookChatMessageUsernameFormat")
                    .replace("%displayname%", safeDisplayName)
                    .replace("%username%", safeName);
            String chatMessage = DiscordSRV.config().getString("Experiment_WebhookChatMessageFormat")
                    .replace("%displayname%", safeDisplayName)
                    .replace("%username%", safeName)
                    .replace("%message%", message.replace("[", "\\["));
            chatMessage = PlaceholderUtil.replacePlaceholdersToDiscord(chatMessage, player);
            chatMessage = DiscordUtil.translateEmotes(chatMessage, channel.getGuild());
            username = PlaceholderUtil.replacePlaceholdersToDiscord(username, player);
            username = MessageUtil.strip(username);

            for (Map.Entry<Pattern, String> entry : DiscordSRV.getPlugin().getGameRegexes().entrySet()) {
                username = entry.getKey().matcher(username).replaceAll(entry.getValue());
                chatMessage = entry.getKey().matcher(chatMessage).replaceAll(entry.getValue());

                if (StringUtils.isBlank(username)) {
                    DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not processing Minecraft message because the webhook username was cleared by a filter: " + entry.getKey().pattern());
                    return;
                }

                if (StringUtils.isBlank(chatMessage)) {
                    DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not processing Minecraft message because the webhook content was cleared by a filter: " + entry.getKey().pattern());
                    return;
                }
            }

            String userId = playerUuid != null && DiscordSRV.getPlugin().getAccountLinkManager() != null
                    ? DiscordSRV.getPlugin().getAccountLinkManager().getDiscordId(playerUuid)
                    : null;
            if (userId != null) {
                Member member = DiscordUtil.getMemberById(userId);
                username = username
                        .replace("%discordname%", member != null ? member.getEffectiveName() : "")
                        .replace("%discordusername%", member != null ? member.getUser().getName() : "");
                if (member != null) {
                    if (DiscordSRV.config().getBoolean("Experiment_WebhookChatMessageAvatarFromDiscord"))
                        avatarUrl = member.getUser().getEffectiveAvatarUrl();
                    if (DiscordSRV.config().getBoolean("Experiment_WebhookChatMessageUsernameFromDiscord"))
                        username = member.getEffectiveName();
                }
            } else {
                username = username
                        .replace("%discordname%", "")
                        .replace("%discordusername%", "");
            }

            if (username.length() > 80) {
                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "The webhook username in " + playerName + "'s message was too long! Reducing to 80 characters");
                username = username.substring(0, 80);
            }

            deliverMessage(channel, username, avatarUrl, chatMessage, embeds, attachments, interactions, false);
        });
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, MessageEmbed embed) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, Collections.singletonList(embed), null, null, true, true);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, MessageEmbed embed, boolean scheduleAsync) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, Collections.singletonList(embed), null, null, true, scheduleAsync);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, Collection<? extends MessageEmbed> embeds) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, embeds, null, null, true, true);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, Collection<? extends MessageEmbed> embeds, boolean scheduleAsync) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, embeds, null, null, true, scheduleAsync);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, Collections.singletonList(embed), attachments, interactions, true, true);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions, boolean scheduleAsync) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, Collections.singletonList(embed), attachments, interactions, true, scheduleAsync);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, embeds, attachments, interactions, true, true);
    }

    public static void deliverMessage(TextChannel channel, String webhookName, String webhookAvatarUrl, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions, boolean scheduleAsync) {
        executeWebhook(channel, webhookName, webhookAvatarUrl, null, message, embeds, attachments, interactions, true, scheduleAsync);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, MessageEmbed embed) {
        executeWebhook(channel, null, null, editMessageId, message, Collections.singletonList(embed), null, null, true, true);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, MessageEmbed embed, boolean scheduleAsync) {
        executeWebhook(channel, null, null, editMessageId, message, Collections.singletonList(embed), null, null, true, scheduleAsync);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, Collection<? extends MessageEmbed> embeds) {
        executeWebhook(channel, null, null, editMessageId, message, embeds, null, null, true, true);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, Collection<? extends MessageEmbed> embeds, boolean scheduleAsync) {
        executeWebhook(channel, null, null, editMessageId, message, embeds, null, null, true, scheduleAsync);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions) {
        executeWebhook(channel, null, null, editMessageId, message, Collections.singletonList(embed), attachments, interactions, true, true);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, MessageEmbed embed, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions, boolean scheduleAsync) {
        executeWebhook(channel, null, null, editMessageId, message, Collections.singletonList(embed), attachments, interactions, true, scheduleAsync);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions) {
        executeWebhook(channel, null, null, editMessageId, message, embeds, attachments, interactions, true, true);
    }

    public static void editMessage(TextChannel channel, String editMessageId, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions, boolean scheduleAsync) {
        executeWebhook(channel, null, null, editMessageId, message, embeds, attachments, interactions, true, scheduleAsync);
    }

    private static void closeAttachments(Map<String, InputStream> attachments) {
        if (attachments == null) return;
        attachments.values().forEach(inputStream -> {
            try {
                if (inputStream != null) inputStream.close();
            } catch (IOException ignore) {
            }
        });
    }

    private static void executeWebhook(TextChannel channel, String webhookName, String webhookAvatarUrl, String editMessageId, String message, Collection<? extends MessageEmbed> embeds, Map<String, InputStream> attachments, Collection<? extends MessageTopLevelComponent> interactions, boolean allowSecondAttempt, boolean scheduleAsync) {
        if (channel == null) {
            closeAttachments(attachments);
            return;
        }

        Runnable task = () -> {
            // resolving the webhook url can block (retrieving/creating webhooks), so it is done inside of the task
            String webhookUrl = getWebhookUrlToUseForChannel(channel);
            if (webhookUrl == null) {
                closeAttachments(attachments);
                return;
            }

            JDA jda = DiscordSRV.getPlugin().getJda();
            if (jda == null) {
                closeAttachments(attachments);
                return;
            }

            List<MessageEmbed> embedList = embeds != null
                    ? embeds.stream().filter(Objects::nonNull).collect(Collectors.toList())
                    : Collections.emptyList();
            List<FileUpload> files = new ArrayList<>();
            if (attachments != null) {
                attachments.forEach((name, data) -> {
                    if (data != null) files.add(FileUpload.fromData(data, name));
                });
            }

            try {
                IncomingWebhookClient client = WebhookClient.createClient(jda, webhookUrl);
                RestAction<?> action;
                if (editMessageId == null) {
                    String webName = webhookName != null ? webhookName : "";
                    for (Map.Entry<Pattern, String> entry : DiscordSRV.getPlugin().getWebhookUsernameRegexes().entrySet()) {
                        webName = entry.getKey().matcher(webName).replaceAll(entry.getValue());
                    }

                    // Handle Discord banned words in a way that isn't against their developer policy
                    String username = webName;
                    username = username
                            .replaceAll("(?i)(cly)d(e)", "$1*$2")
                            .replaceAll("(?i)(d)i(scord)", "$1*$2");
                    if (!username.equals(webName) && !loggedBannedWords) {
                        DiscordSRV.info("Some webhook usernames are being altered to remove blocked words (eg. Clyde and Discord)");
                        loggedBannedWords = true;
                    }
                    if (username.length() > 80) username = username.substring(0, 80);

                    MessageCreateBuilder createBuilder = new MessageCreateBuilder();
                    if (StringUtils.isNotBlank(message)) createBuilder.setContent(message);
                    if (!embedList.isEmpty()) createBuilder.setEmbeds(embedList);
                    if (interactions != null) createBuilder.setComponents(interactions);
                    if (!files.isEmpty()) createBuilder.setFiles(files);
                    if (createBuilder.isEmpty()) {
                        DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not sending webhook message to #" + channel.getName() + " because it has no content");
                        closeAttachments(attachments);
                        return;
                    }

                    // uses the default allowed mentions (MessageRequest#getDefaultMentions), same as normal messages
                    WebhookMessageCreateAction<Message> createAction = client.sendMessage(createBuilder.build());
                    if (StringUtils.isNotBlank(username)) createAction.setUsername(username);
                    if (StringUtils.isNotBlank(webhookAvatarUrl)) createAction.setAvatarUrl(webhookAvatarUrl);
                    action = createAction;
                } else {
                    MessageEditBuilder editBuilder = new MessageEditBuilder();
                    if (StringUtils.isNotBlank(message)) editBuilder.setContent(message);
                    if (embeds != null) editBuilder.setEmbeds(embedList);
                    if (interactions != null) editBuilder.setComponents(interactions);
                    if (!files.isEmpty()) editBuilder.setFiles(files);
                    WebhookMessageEditAction<Message> editAction = client.editMessageById(editMessageId, editBuilder.build());
                    action = editAction;
                }

                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Sending webhook " + (editMessageId == null ? "message" : "edit for message " + editMessageId)
                        + " to #" + channel.getName() + ": " + message + (embedList.isEmpty() ? "" : " (+" + embedList.size() + " embed(s))"));
                action.complete();
                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Received API response for webhook message delivery");
            } catch (ErrorResponseException e) {
                closeAttachments(attachments);
                if (e.getErrorResponse() == ErrorResponse.UNKNOWN_WEBHOOK || e.getErrorCode() == 404 || e.getErrorCode() == 10015) {
                    // 404 = Invalid Webhook (most likely to have been deleted), 10015 = unknown webhook
                    DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Webhook delivery returned " + e.getErrorCode() + " (Unknown Webhook), marking webhooks url's as invalid to let them regenerate" + (allowSecondAttempt ? " & trying again" : ""));
                    invalidWebhookUrlForChannel(channel); // tell it to get rid of the urls & get new ones
                    if (allowSecondAttempt && attachments == null)
                        executeWebhook(channel, webhookName, webhookAvatarUrl, editMessageId, message, embeds, null, interactions, false, false);
                    return;
                }
                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Received unexpected API response for webhook message delivery: " + e.getErrorCode() + " " + e.getMeaning());
                DiscordSRV.error("Failed to deliver webhook message to Discord: " + e.getMessage());
            } catch (Exception e) {
                DiscordSRV.error("Failed to deliver webhook message to Discord: " + e.getMessage());
                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, e);
                closeAttachments(attachments);
            }
        };

        if (scheduleAsync) {
            SchedulerUtil.runTaskAsynchronously(task);
        } else {
            task.run();
        }
    }

    private static final Map<String, String> channelWebhookUrls = new ConcurrentHashMap<>();

    public static void invalidWebhookUrlForChannel(TextChannel textChannel) {
        String channelId = textChannel.getId();
        channelWebhookUrls.remove(channelId);
    }

    public static String getWebhookUrlToUseForChannel(TextChannel channel) {
        final String channelId = channel.getId();
        return channelWebhookUrls.computeIfAbsent(channelId, cid -> {
            List<Webhook> hooks = new ArrayList<>();
            final Guild guild = channel.getGuild();
            final Member selfMember = guild.getSelfMember();

            String bannedWebhookFormat = "DiscordSRV " + cid; // This format is blocked by Discord
            String webhookFormat = "DSRV " + cid;

            // Check if we have permission guild-wide
            List<Webhook> result;
            try {
                if (guild.getSelfMember().hasPermission(Permission.MANAGE_WEBHOOKS)) {
                    result = guild.retrieveWebhooks().complete();
                } else {
                    result = channel.retrieveWebhooks().complete();
                }
            } catch (Exception e) {
                DiscordSRV.error("Failed to retrieve webhooks for message delivery: " + e.getMessage());
                return null;
            }

            result.stream()
                    .filter(webhook -> webhook.getName().startsWith(webhookFormat) || webhook.getName().startsWith(bannedWebhookFormat))
                    .filter(webhook -> {
                        // Filter to what we can modify
                        Member owner = webhook.getOwner();
                        return owner != null && selfMember.getId().equals(owner.getId());
                    })
                    .filter(webhook -> {
                        if (!webhook.getChannel().getId().equals(channel.getId())) {
                            webhook.delete().reason("DiscordSRV: Purging lost webhook").queue();
                            return false;
                        }
                        return true;
                    })
                    .filter(webhook -> {
                        if (LEGACY.test(webhook)) {
                            webhook.delete().reason("DiscordSRV: Purging legacy formatted webhook").queue();
                            return false;
                        }
                        return true;
                    })
                    .forEach(hooks::add);

            if (hooks.isEmpty()) {
                Webhook created = createWebhook(channel, webhookFormat);
                if (created != null) hooks.add(created);
            } else if (hooks.size() > 1) {
                for (int index = 1; index < hooks.size(); index++) {
                    hooks.get(index).delete().reason("DiscordSRV: Purging duplicate webhook").queue();
                }
            }

            return hooks.stream().map(Webhook::getUrl).findAny().orElse(null);
        });
    }

    public static Webhook createWebhook(TextChannel channel, String name) {
        try {
            Webhook webhook = channel.createWebhook(name).reason("DiscordSRV: Creating webhook").complete();
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Created webhook " + webhook.getName() + " to deliver messages to text channel #" + channel.getName());
            return webhook;
        } catch (Exception e) {
            DiscordSRV.error("Failed to create webhook " + name + " for message delivery: " + e.getMessage());
            return null;
        }
    }

    public static String getWebhookUrlFromCache(TextChannel channel) {
        return channelWebhookUrls.get(channel.getId());
    }

    /**
     * @return the id of the webhook DiscordSRV uses to deliver messages to the given channel, if one is cached
     */
    public static String getWebhookIdFromCache(TextChannel channel) {
        String url = getWebhookUrlFromCache(channel);
        if (url == null) return null;
        Matcher matcher = Webhook.WEBHOOK_URL.matcher(url);
        return matcher.matches() ? matcher.group("id") : null;
    }

}
