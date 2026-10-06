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

package github.scarsz.discordsrv.listeners;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.events.AchievementMessagePostProcessEvent;
import github.scarsz.discordsrv.api.events.AchievementMessagePreProcessEvent;
import github.scarsz.discordsrv.objects.MessageFormat;
import github.scarsz.discordsrv.platform.GamePlayer;
import github.scarsz.discordsrv.platform.event.GameListener;
import github.scarsz.discordsrv.platform.event.PlayerAdvancementDoneEvent;
import github.scarsz.discordsrv.util.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class PlayerAdvancementDoneListener implements GameListener {

    @Override
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        GamePlayer player = event.getPlayer();
        // return if advancement or player objects are knackered because this can apparently happen for some reason
        if (event.getAdvancementId() == null || player == null) return;

        // respect invisibility plugins
        if (PlayerUtil.isVanished(player)) return;

        SchedulerUtil.runTaskAsynchronously(() -> runAsync(event));
    }

    private void runAsync(PlayerAdvancementDoneEvent event) {
        GamePlayer player = event.getPlayer();

        if (advancementIsHiddenInChat(event)) return;

        String channelName = DiscordSRV.getPlugin().getOptionalChannel("awards");

        MessageFormat messageFormat = DiscordSRV.getPlugin().getMessageFromConfiguration("MinecraftPlayerAchievementMessage");
        if (messageFormat == null) return;

        // turn "story/advancement_name" into "Advancement Name"
        String advancementTitle = getTitle(event);

        AchievementMessagePreProcessEvent preEvent = DiscordSRV.api.callEvent(new AchievementMessagePreProcessEvent(channelName, messageFormat, player, advancementTitle, event));
        if (preEvent.isCancelled()) {
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "AchievementMessagePreProcessEvent was cancelled, message send aborted");
            return;
        }
        // Update from event in case any listeners modified parameters
        advancementTitle = preEvent.getAchievementName();
        channelName = preEvent.getChannel();
        messageFormat = preEvent.getMessageFormat();

        if (messageFormat == null) return;

        String finalAchievementName = StringUtils.isNotBlank(advancementTitle) ? advancementTitle : "";
        String avatarUrl = DiscordSRV.getAvatarUrl(player);
        String botAvatarUrl = DiscordUtil.getJda().getSelfUser().getEffectiveAvatarUrl();
        String botName = DiscordSRV.getPlugin().getMainGuild() != null ? DiscordSRV.getPlugin().getMainGuild().getSelfMember().getEffectiveName() : DiscordUtil.getJda().getSelfUser().getName();
        String displayName = StringUtils.isNotBlank(player.getDisplayName()) ? MessageUtil.strip(player.getDisplayName()) : "";

        TextChannel destinationChannel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(channelName);
        BiFunction<String, Boolean, String> translator = (content, needsEscape) -> {
            if (content == null) return null;
            content = content
                    .replaceAll("%time%|%date%", TimeUtil.timeStamp())
                    .replace("%username%", needsEscape ? DiscordUtil.escapeMarkdown(player.getName()) : player.getName())
                    .replace("%displayname%", needsEscape ? DiscordUtil.escapeMarkdown(displayName) : displayName)
                    .replace("%usernamenoescapes%", player.getName())
                    .replace("%displaynamenoescapes%", displayName)
                    .replace("%world%", player.getWorldName())
                    .replace("%achievement%", MessageUtil.strip(needsEscape ? DiscordUtil.escapeMarkdown(finalAchievementName) : finalAchievementName))
                    .replace("%embedavatarurl%", avatarUrl)
                    .replace("%botavatarurl%", botAvatarUrl)
                    .replace("%botname%", botName);
            if (destinationChannel != null) content = DiscordUtil.translateEmotes(content, destinationChannel.getGuild());
            content = PlaceholderUtil.replacePlaceholdersToDiscord(content, player);
            return content;
        };
        MessageCreateData discordMessage = DiscordSRV.translateMessage(messageFormat, translator);
        if (discordMessage == null) return;

        String webhookName = translator.apply(messageFormat.getWebhookName(), false);
        String webhookAvatarUrl = translator.apply(messageFormat.getWebhookAvatarUrl(), false);

        AchievementMessagePostProcessEvent postEvent = DiscordSRV.api.callEvent(new AchievementMessagePostProcessEvent(channelName, discordMessage, player, advancementTitle, event, messageFormat.isUseWebhooks(), webhookName, webhookAvatarUrl, preEvent.isCancelled()));
        if (postEvent.isCancelled()) {
            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "AchievementMessagePostProcessEvent was cancelled, message send aborted");
            return;
        }
        // Update from event in case any listeners modified parameters
        channelName = postEvent.getChannel();
        discordMessage = postEvent.getDiscordMessage();

        TextChannel textChannel = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(channelName);
        if (postEvent.isUsingWebhooks()) {
            WebhookUtil.deliverMessage(textChannel, postEvent.getWebhookName(), postEvent.getWebhookAvatarUrl(),
                    discordMessage.getContent(), discordMessage.getEmbeds().stream().findFirst().orElse(null));
        } else {
            DiscordUtil.queueMessage(textChannel, discordMessage, true);
        }
    }

    private boolean advancementIsHiddenInChat(PlayerAdvancementDoneEvent event) {
        // don't send messages for advancements related to recipes
        String key = event.getAdvancementId();
        if (key.contains("recipe/") || key.contains("recipes/")) return true;

        // advancements without display properties are never announced
        if (event.getTitle() == null) return true;

        // the platform checks the advancement's announce_to_chat display property & the announce advancements gamerule
        return !event.isAnnounceToChat();
    }

    public static String getTitle(PlayerAdvancementDoneEvent event) {
        if (event.getTitle() != null) {
            String title = MessageUtil.strip(MessageUtil.toLegacy(event.getTitle()));
            if (StringUtils.isNotBlank(title)) return title;
        }

        DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Failed to get title of advancement " + event.getAdvancementId());
        String rawAdvancementName = event.getAdvancementId();
        return Arrays.stream(rawAdvancementName.substring(rawAdvancementName.lastIndexOf("/") + 1).toLowerCase().split("_"))
                .filter(StringUtils::isNotEmpty)
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                .collect(Collectors.joining(" "));
    }

}
