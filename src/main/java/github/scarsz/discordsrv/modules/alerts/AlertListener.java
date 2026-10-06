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

package github.scarsz.discordsrv.modules.alerts;

import alexh.weak.Dynamic;
import alexh.weak.Weak;
import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.objects.ExpiringDualHashBidiMap;
import github.scarsz.discordsrv.objects.Lag;
import github.scarsz.discordsrv.objects.MessageFormat;
import github.scarsz.discordsrv.platform.CommandSender;
import github.scarsz.discordsrv.platform.GamePlayer;
import github.scarsz.discordsrv.platform.event.*;
import github.scarsz.discordsrv.util.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.SpelEvaluationException;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sends configurable messages to Discord when events happen. Alerts can be triggered by
 * <ul>
 *     <li>game events forwarded by the platform ({@link GameEvent}, eg. PlayerJoinEvent, PlayerQuitEvent,
 *     PlayerChatEvent, PlayerDeathEvent, PlayerAdvancementDoneEvent, PlayerCommandEvent, ServerCommandEvent)</li>
 *     <li>commands ("/command" triggers)</li>
 *     <li>DiscordSRV API events</li>
 *     <li>JDA events</li>
 * </ul>
 */
public class AlertListener implements GameListener, EventListener {

    private static final Pattern VALID_CLASS_NAME_PATTERN = Pattern.compile("([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*");
    /**
     * Names of Bukkit events (used by alerts made for the Spigot version of DiscordSRV) mapped to the name of the
     * equivalent game event on this platform (all lower case)
     */
    private static final Map<String, String> EVENT_NAME_ALIASES = new HashMap<>();

    static {
        EVENT_NAME_ALIASES.put("asyncplayerchatevent", "playerchatevent");
        EVENT_NAME_ALIASES.put("playercommandpreprocessevent", "playercommandevent");
        EVENT_NAME_ALIASES.put("playerachievementawardedevent", "playeradvancementdoneevent");
    }

    private final Map<String, String> validClassNameCache = new ExpiringDualHashBidiMap<>(TimeUnit.MINUTES.toMillis(1));
    private final Set<String> activeTriggers = new HashSet<>();
    private boolean anyCommandTrigger = false;

    private final List<Dynamic> alerts = new ArrayList<>();
    private volatile boolean registered = false;

    public AlertListener() {
        reloadAlerts();
    }

    @SuppressWarnings("unchecked")
    public void reloadAlerts() {
        validClassNameCache.clear();
        activeTriggers.clear();
        anyCommandTrigger = false;
        alerts.clear();
        Optional<List<Map<?, ?>>> optionalAlerts = DiscordSRV.config().getOptional("Alerts")
                .filter(object -> object instanceof List)
                .map(object -> (List<Map<?, ?>>) object);
        if (registered) unregister();

        if (!optionalAlerts.isPresent() || optionalAlerts.get().isEmpty()) {
            return;
        }

        long count = optionalAlerts.get().size();

        for (Map<?, ?> map : optionalAlerts.get()) {
            Dynamic alert = Dynamic.from(map);
            alerts.add(alert);
            Set<String> triggers = getTriggers(alert);

            for (String trigger : triggers) {
                if (trigger == null) continue;
                if (trigger.startsWith("/")) {
                    anyCommandTrigger = true;
                    continue;
                }
                activeTriggers.add(trigger.toLowerCase(Locale.ROOT));
                activeTriggers.add(normalizeTrigger(trigger));

                if (!trigger.contains(".") || isBukkitEventName(trigger)) {
                    // game events are matched by name
                    continue;
                }

                try {
                    Class.forName(getEventClassName(trigger));
                } catch (ClassNotFoundException | LinkageError ignored) {
                    DiscordSRV.warning("Could not find event for alert trigger: " + trigger);
                }
            }
        }

        registered = true;
        DiscordSRV.info(optionalAlerts.get().size() + " alert" + (count > 1 ? "s" : "") + " registered");
    }

    public List<Dynamic> getAlerts() {
        return alerts;
    }

    /**
     * Stops alerts from being processed (until they're reloaded)
     */
    public void unregister() {
        registered = false;
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        runAlertsForEvent(event);
    }

    @Subscribe
    public void onDSRVEvent(github.scarsz.discordsrv.api.events.Event event) {
        runAlertsForEvent(event);
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        runAlertsForEvent(event);
    }

    @Override
    public void onPlayerQuit(PlayerQuitEvent event) {
        runAlertsForEvent(event);
    }

    @Override
    public void onPlayerChat(PlayerChatEvent event) {
        runAlertsForEvent(event);
    }

    @Override
    public void onPlayerDeath(PlayerDeathEvent event) {
        runAlertsForEvent(event);
    }

    @Override
    public void onPlayerAdvancementDone(PlayerAdvancementDoneEvent event) {
        runAlertsForEvent(event);
    }

    @Override
    public void onPlayerCommand(PlayerCommandEvent event) {
        runAlertsForEvent(event);
    }

    @Override
    public void onServerCommand(ServerCommandEvent event) {
        runAlertsForEvent(event);
    }

    private void runAlertsForEvent(Object event) {
        if (!registered) return;
        boolean command = event instanceof PlayerCommandEvent || event instanceof ServerCommandEvent;

        String eventClassName = getEventClassName(event);
        boolean active = (command && anyCommandTrigger)
                || activeTriggers.contains(eventClassName.toLowerCase(Locale.ROOT))
                || activeTriggers.contains(getEventName(event).toLowerCase(Locale.ROOT));
        if (!active) return;

        for (int i = 0; i < alerts.size(); i++) {
            Dynamic alert = alerts.get(i);
            Set<String> triggers = getTriggers(alert);
            boolean async = true;

            Dynamic asyncDynamic = alert.get("Async");
            if (asyncDynamic.isPresent()) {
                if (asyncDynamic.convert().intoString().equalsIgnoreCase("false")
                        || asyncDynamic.convert().intoString().equalsIgnoreCase("no")) {
                    async = false;
                }
            }

            if (async) {
                int alertIndex = i;
                SchedulerUtil.runTaskAsynchronously(() -> process(event, alert, triggers, alertIndex));
            } else {
                process(event, alert, triggers, i);
            }
        }
    }

    private Set<String> getTriggers(Dynamic alert) {
        Set<String> triggers = new HashSet<>();
        Dynamic triggerDynamic = alert.get("Trigger");
        if (triggerDynamic.isList()) {
            triggers.addAll(triggerDynamic.children()
                    .map(Weak::asString)
                    .collect(Collectors.toSet())
            );
        } else if (triggerDynamic.isString()) {
            triggers.add(triggerDynamic.asString());
        }

        Set<String> finalTriggers = new HashSet<>();
        for (String trigger : triggers) {
            if (!trigger.startsWith("/")) {
                String className = validClassNameCache.get(trigger);
                if (className == null) {
                    // event trigger, make sure it's a valid class name
                    Matcher matcher = VALID_CLASS_NAME_PATTERN.matcher(trigger);
                    if (matcher.find()) {
                        // valid class name found
                        className = matcher.group();
                    }
                    validClassNameCache.put(trigger, className);
                }
                finalTriggers.add(className);
                continue;
            }
            finalTriggers.add(trigger);
        }
        return finalTriggers;
    }

    private String getEventClassName(Object event) {
        String className = event instanceof String ? (String) event : event.getClass().getName();
        return className.replace("github.scarsz.discordsrv.dependencies.jda", "net.".concat("dv8tion.jda"));
    }

    /**
     * Converts the given (non-command) trigger to the lower case name of the event it should match,
     * translating the names of Bukkit events to the equivalent game event names of this platform
     */
    private static String normalizeTrigger(String trigger) {
        String name = trigger;
        if (isBukkitEventName(name)) name = name.substring(name.lastIndexOf('.') + 1);
        name = name.toLowerCase(Locale.ROOT);
        return EVENT_NAME_ALIASES.getOrDefault(name, name);
    }

    private static boolean isBukkitEventName(String trigger) {
        return trigger.startsWith("org.bukkit.") || trigger.startsWith("io.papermc.") || trigger.startsWith("com.destroystokyo.paper.");
    }

    private String getEventName(Object event) {
        return event instanceof GameEvent ? ((GameEvent) event).getEventName() : event.getClass().getSimpleName();
    }

    private void process(Object event, Dynamic alert, Set<String> triggers, int alertIndex) {
        GamePlayer player = event instanceof PlayerGameEvent ? ((PlayerGameEvent) event).getPlayer() : null;
        if (player == null) {
            // some things that do deal with players are not properly marked as a player event
            // this will check to see if a #getPlayer() method exists on events coming through
            try {
                Method getPlayerMethod = event.getClass().getMethod("getPlayer");
                if (GamePlayer.class.isAssignableFrom(getPlayerMethod.getReturnType())) {
                    player = (GamePlayer) getPlayerMethod.invoke(event);
                }
            } catch (Exception ignored) {
                // we tried ¯\_(ツ)_/¯
            }
        }

        CommandSender sender = null;
        String command = null;
        List<String> args = new LinkedList<>();

        if (event instanceof PlayerCommandEvent) {
            sender = player;
            command = ((PlayerCommandEvent) event).getCommand();
            if (command.startsWith("/")) command = command.substring(1);
        } else if (event instanceof ServerCommandEvent) {
            sender = ((ServerCommandEvent) event).getSender();
            command = ((ServerCommandEvent) event).getCommand();
        }
        if (StringUtils.isNotBlank(command)) {
            String[] split = command.split(" ", 2);
            String commandBase = split[0];
            if (split.length == 2) args.addAll(Arrays.asList(split[1].split(" ")));

            // transform "discordsrv:discord" to just "discord" for example
            if (commandBase.contains(":")) commandBase = commandBase.substring(commandBase.lastIndexOf(":") + 1);

            command = commandBase + (split.length == 2 ? (" " + split[1]) : "");
        }

        MessageFormat messageFormat = DiscordSRV.getPlugin().getMessageFromConfiguration("Alerts." + alertIndex);

        String eventClassName = getEventClassName(event);
        String eventName = getEventName(event);
        for (String trigger : triggers) {
            if (trigger.startsWith("/")) {
                if (StringUtils.isBlank(command) || !command.toLowerCase().split("\\s+|$", 2)[0].equals(trigger.substring(1))) continue;
            } else {
                // make sure the called event matches what this alert is supposed to trigger on
                if (!eventClassName.equals(trigger) && !eventName.equalsIgnoreCase(normalizeTrigger(trigger))) continue;
            }

            // make sure alert should run even if event is cancelled
            if (event instanceof PlayerChatEvent && ((PlayerChatEvent) event).isCancelled()) {
                Dynamic ignoreCancelledDynamic = alert.get("IgnoreCancelled");
                boolean ignoreCancelled = ignoreCancelledDynamic.isPresent() ? ignoreCancelledDynamic.as(Boolean.class) : true;
                if (ignoreCancelled) {
                    DiscordSRV.debug(Debug.ALERTS, "Not running alert for event " + eventName + ": event was cancelled");
                    return;
                }
            }

            Dynamic textChannelsDynamic = alert.get("Channel");
            if (textChannelsDynamic == null) {
                DiscordSRV.debug(Debug.ALERTS, "Not running alert for trigger " + trigger + ": no target channel was defined");
                return;
            }
            Set<String> channels = new HashSet<>();
            if (textChannelsDynamic.isList()) {
                textChannelsDynamic.children()
                        .map(Weak::asString)
                        .filter(Objects::nonNull)
                        .forEach(channels::add);
            } else if (textChannelsDynamic.isString()) {
                channels.add(textChannelsDynamic.asString());
            }
            Function<Function<String, Collection<TextChannel>>, Set<TextChannel>> channelResolver = converter -> {
                Set<TextChannel> textChannels = new HashSet<>();
                channels.forEach(channel -> textChannels.addAll(converter.apply(channel)));
                textChannels.removeIf(Objects::isNull);
                return textChannels;
            };

            Set<TextChannel> textChannels = channelResolver.apply(s -> {
                TextChannel target = DiscordSRV.getPlugin().getDestinationTextChannelForGameChannelName(s);
                return Collections.singleton(target);
            });
            if (textChannels.isEmpty()) {
                textChannels.addAll(channelResolver.apply(s ->
                        DiscordUtil.getJda().getTextChannelsByName(s, false)
                ));
            }
            if (textChannels.isEmpty()) {
                textChannels.addAll(channelResolver.apply(s -> NumberUtils.isDigits(s) ?
                        Collections.singleton(DiscordUtil.getJda().getTextChannelById(s)) : Collections.emptyList()));
            }

            if (textChannels.size() == 0) {
                DiscordSRV.debug(Debug.ALERTS, "Not running alert for trigger " + trigger + ": no target channel was defined/found (channels: " + channels + ")");
                return;
            }

            for (TextChannel textChannel : textChannels) {
                // check alert conditions
                boolean allConditionsMet = true;
                Dynamic conditionsDynamic = alert.dget("Conditions");
                if (conditionsDynamic.isPresent()) {
                    Iterator<Dynamic> conditions = conditionsDynamic.children().iterator();
                    while (conditions.hasNext()) {
                        Dynamic dynamic = conditions.next();
                        String expression = dynamic.convert().intoString();
                        try {
                            Boolean value = new SpELExpressionBuilder(expression)
                                    .withPluginVariables()
                                    .withVariable("event", event)
                                    .withVariable("server", DiscordSRV.getPlatform())
                                    .withVariable("discordsrv", DiscordSRV.getPlugin())
                                    .withVariable("player", player)
                                    .withVariable("sender", sender)
                                    .withVariable("command", command)
                                    .withVariable("args", args)
                                    .withVariable("allArgs", String.join(" ", args))
                                    .withVariable("channel", textChannel)
                                    .withVariable("jda", DiscordUtil.getJda())
                                    .evaluate(event, Boolean.class);
                            DiscordSRV.debug(Debug.ALERTS, "Condition \"" + expression + "\" -> " + value);
                            if (value != null && !value) {
                                allConditionsMet = false;
                                break;
                            }
                        } catch (ParseException e) {
                            DiscordSRV.error("Error while parsing expression \"" + expression + "\" for trigger \"" + trigger + "\" -> " + e.getMessage());
                        } catch (SpelEvaluationException e) {
                            DiscordSRV.error("Error while evaluating expression \"" + expression + "\" for trigger \"" + trigger + "\" -> " + e.getMessage());
                        }
                    }
                    if (!allConditionsMet) continue;
                }

                CommandSender finalSender = sender;
                String finalCommand = command;

                GamePlayer finalPlayer = player;
                BiFunction<String, Boolean, String> translator = (content, needsEscape) -> {
                    if (content == null) return null;

                    // evaluate any SpEL expressions
                    Map<String, Object> variables = new HashMap<>();
                    variables.put("event", event);
                    variables.put("server", DiscordSRV.getPlatform());
                    variables.put("discordsrv", DiscordSRV.getPlugin());
                    variables.put("player", finalPlayer);
                    variables.put("sender", finalSender);
                    variables.put("command", finalCommand);
                    variables.put("args", args);
                    variables.put("allArgs", String.join(" ", args));
                    variables.put("channel", textChannel);
                    variables.put("jda", DiscordUtil.getJda());
                    content = NamedValueFormatter.formatExpressions(content, event, variables);

                    // replace any normal placeholders
                    content = NamedValueFormatter.format(content, key -> {
                        switch (key) {
                            case "tps":
                                return Lag.getTPSString();
                            case "time":
                            case "date":
                                return TimeUtil.timeStamp();
                            case "ping":
                                return finalPlayer != null ? String.valueOf(PlayerUtil.getPing(finalPlayer)) : "-1";
                            case "name":
                            case "username":
                                return finalPlayer != null ? finalPlayer.getName() : "";
                            case "displayname":
                                return finalPlayer != null ? MessageUtil.strip(needsEscape ? DiscordUtil.escapeMarkdown(finalPlayer.getDisplayName()) : finalPlayer.getDisplayName()) : "";
                            case "world":
                                return finalPlayer != null ? finalPlayer.getWorldName() : "";
                            case "embedavatarurl":
                                return finalPlayer != null ? DiscordSRV.getAvatarUrl(finalPlayer) : DiscordUtil.getJda().getSelfUser().getEffectiveAvatarUrl();
                            case "botavatarurl":
                                return DiscordUtil.getJda().getSelfUser().getEffectiveAvatarUrl();
                            case "botname":
                                return DiscordSRV.getPlugin().getMainGuild() != null ? DiscordSRV.getPlugin().getMainGuild().getSelfMember().getEffectiveName() : DiscordUtil.getJda().getSelfUser().getName();
                            default:
                                return "{" + key + "}";
                        }
                    });

                    content = DiscordUtil.translateEmotes(content, textChannel.getGuild());
                    content = PlaceholderUtil.replacePlaceholdersToDiscord(content, finalPlayer);
                    return content;
                };

                MessageCreateData message = DiscordSRV.translateMessage(messageFormat, translator);
                if (message == null) {
                    DiscordSRV.debug(Debug.ALERTS, "Not sending alert because it is configured to have no message content");
                    return;
                }

                if (messageFormat.isUseWebhooks()) {
                    WebhookUtil.deliverMessage(textChannel,
                            translator.apply(messageFormat.getWebhookName(), false),
                            translator.apply(messageFormat.getWebhookAvatarUrl(), false),
                            message.getContent(), message.getEmbeds().stream().findFirst().orElse(null));
                } else {
                    DiscordUtil.queueMessage(textChannel, message);
                }
            }
        }
    }

}
