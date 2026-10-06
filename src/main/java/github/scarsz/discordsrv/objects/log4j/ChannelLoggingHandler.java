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

package github.scarsz.discordsrv.objects.log4j;

import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.MessageUtil;
import github.scarsz.discordsrv.util.PlaceholderUtil;
import github.scarsz.discordsrv.util.TimeUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Forwards the server's console output (log4j2) to the Discord console channel.
 * <p>
 * A self-contained replacement for the {@code me.scarsz.jdaappender} library used by the Spigot version of DiscordSRV:
 * a log4j2 appender is attached to the root logger, which only queues log events (it never blocks the logging thread;
 * events are dropped when the queue is full). A daemon thread periodically (every
 * {@code DiscordConsoleChannelLogRefreshRateInSeconds} seconds) formats the queued events and sends them to the
 * console channel, batched into as few messages as Discord's message length limit allows.
 */
public class ChannelLoggingHandler {

    /**
     * The levels that can be configured with DiscordConsoleChannelLevels
     */
    public enum LogLevel {
        DEBUG, INFO, WARN, ERROR;

        static LogLevel of(Level level) {
            if (level == null) return INFO;
            if (level.isMoreSpecificThan(Level.ERROR)) return ERROR; // ERROR & FATAL
            if (level.isMoreSpecificThan(Level.WARN)) return WARN;
            if (level.isMoreSpecificThan(Level.INFO)) return INFO;
            return DEBUG; // DEBUG & TRACE
        }
    }

    private static final String APPENDER_NAME = "DiscordSRV-ConsoleChannel";
    private static final int QUEUE_CAPACITY = 2500;
    private static final String CODE_BLOCK_START = "```\n";
    private static final String CODE_BLOCK_END = "\n```";
    private static final String[] JDA_LOGGER_PREFIXES = {"github.scarsz.discordsrv.dependencies.jda", "net.dv8tion.jda"};

    private final Supplier<TextChannel> channelSupplier;
    private final BlockingQueue<LogItem> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final Object flushLock = new Object();

    // configuration
    private final Set<LogLevel> logLevels;
    private final boolean useCodeBlocks;
    private final int loggerNamePadding;
    private final Map<String, Function<String, String>> loggerMappings = new LinkedHashMap<>();

    private volatile Appender appender;
    private volatile ScheduledExecutorService executor;
    private volatile Thread sendingThread;
    private volatile boolean droppedEvents = false;

    public ChannelLoggingHandler(Supplier<TextChannel> channelSupplier) {
        this.channelSupplier = channelSupplier;

        this.useCodeBlocks = DiscordSRV.config().getBooleanElse("DiscordConsoleChannelUseCodeBlocks", true);
        this.loggerNamePadding = Math.max(0, DiscordSRV.config().getIntElse("DiscordConsoleChannelPadding", 0));

        Set<LogLevel> configuredLevels = DiscordSRV.config().getStringList("DiscordConsoleChannelLevels").stream()
                .map(value -> value.toUpperCase(Locale.ROOT))
                .map(s -> {
                    try {
                        return LogLevel.valueOf(s);
                    } catch (IllegalArgumentException e) {
                        DiscordSRV.error("Invalid console logging level '" + s + "', valid options are " + Arrays.stream(LogLevel.values()).map(LogLevel::name).collect(Collectors.joining(", ")));
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        this.logLevels = !configuredLevels.isEmpty() ? EnumSet.copyOf(configuredLevels) : EnumSet.noneOf(LogLevel.class);

        mapLoggerName("net.minecraft.server.MinecraftServer", name -> "Server");
        mapLoggerNameFriendly("net.minecraft.server", s -> "Server/" + s);
        mapLoggerNameFriendly("net.minecraft", s -> "Minecraft/" + s);
        for (String jdaPrefix : JDA_LOGGER_PREFIXES) mapLoggerNameFriendly(jdaPrefix, s -> "DiscordSRV/JDA/" + s);
    }

    // --- logger name mapping ---

    private void mapLoggerName(String prefix, Function<String, String> mapper) {
        loggerMappings.put(prefix, mapper);
    }

    private void mapLoggerNameFriendly(String prefix, Function<String, String> mapper) {
        mapLoggerName(prefix, name -> mapper.apply(name.substring(name.lastIndexOf('.') + 1)));
    }

    public String resolveLoggerName(String loggerName) {
        if (loggerName == null) return "";
        for (Map.Entry<String, Function<String, String>> entry : loggerMappings.entrySet()) {
            String prefix = entry.getKey();
            if (loggerName.equals(prefix) || loggerName.startsWith(prefix + ".") || loggerName.startsWith(prefix + "$")) {
                return entry.getValue().apply(loggerName);
            }
        }
        return loggerName;
    }

    public String padLoggerName(String name) {
        if (name == null) name = "";
        if (loggerNamePadding <= 0 || StringUtils.isBlank(name)) return name;
        if (name.length() > loggerNamePadding) return name.substring(0, loggerNamePadding);
        return StringUtils.rightPad(name, loggerNamePadding);
    }

    public String padLevelName(String levelName) {
        if (loggerNamePadding <= 0) return levelName;
        int longest = Arrays.stream(LogLevel.values()).mapToInt(level -> level.name().length()).max().orElse(0);
        return StringUtils.rightPad(levelName, longest);
    }

    // --- lifecycle ---

    /**
     * Attaches the appender to the root logger
     */
    public void attach() {
        if (appender != null) return;
        Appender newAppender = new Appender();
        newAppender.start();

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LoggerConfig rootLoggerConfig = context.getConfiguration().getRootLogger();
        rootLoggerConfig.addAppender(newAppender, null, null);
        context.updateLoggers();
        appender = newAppender;
    }

    /**
     * Starts sending queued log lines to the console channel every DiscordConsoleChannelLogRefreshRateInSeconds seconds
     */
    public synchronized void schedule() {
        if (executor != null) return;
        int rate = Math.max(1, DiscordSRV.config().getIntElse("DiscordConsoleChannelLogRefreshRateInSeconds", 5));
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "DiscordSRV - Console Channel Sender");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::flushSafely, rate, rate, TimeUnit.SECONDS);
    }

    /**
     * Sends everything that's currently queued as soon as possible (asynchronously)
     */
    public void dumpStack() {
        ScheduledExecutorService executor = this.executor;
        if (executor != null && !executor.isShutdown()) {
            try {
                executor.execute(this::flushSafely);
            } catch (RejectedExecutionException ignored) {}
        }
    }

    /**
     * Stops the sending thread, sends what's left (best-effort) and detaches the appender
     */
    public void shutdown() {
        ScheduledExecutorService executor;
        synchronized (this) {
            executor = this.executor;
            this.executor = null;
        }
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // send whatever is left
        flushSafely();

        Appender appender = this.appender;
        this.appender = null;
        if (appender != null) {
            try {
                LoggerContext context = (LoggerContext) LogManager.getContext(false);
                context.getConfiguration().getRootLogger().removeAppender(appender.getName());
                context.updateLoggers();
            } catch (Throwable t) {
                DiscordSRV.debug(Debug.UNCATEGORIZED, "Failed to detach the console channel appender: " + t);
            }
            appender.stop();
        }
        queue.clear();
    }

    // --- receiving ---

    private static boolean isJdaLogger(String loggerName) {
        if (loggerName == null) return false;
        for (String prefix : JDA_LOGGER_PREFIXES) if (loggerName.startsWith(prefix)) return true;
        return false;
    }

    /**
     * Called on the logging thread: must be fast & must never block or log
     */
    private void receive(LogEvent event) {
        // ignore anything logged while sending to avoid feedback loops
        if (Thread.currentThread() == sendingThread) return;

        LogLevel level = LogLevel.of(event.getLevel());
        if (!logLevels.contains(level)) return;

        String loggerName = event.getLoggerName();
        // JDA logs a lot about the requests we make to send the console messages
        if (isJdaLogger(loggerName) && !event.getLevel().isMoreSpecificThan(Level.INFO)) return;

        String message = event.getMessage() != null ? event.getMessage().getFormattedMessage() : null;
        Throwable thrown = event.getThrown();
        if (thrown != null) {
            String stackTrace;
            try {
                stackTrace = ExceptionUtils.getStackTrace(thrown);
            } catch (Throwable t) {
                stackTrace = thrown.toString();
            }
            message = StringUtils.isNotEmpty(message) ? message + "\n" + stackTrace : stackTrace;
        }
        if (message == null) return;

        if (!queue.offer(new LogItem(loggerName, event.getTimeMillis(), level, message))) {
            droppedEvents = true;
        }
    }

    // --- sending ---

    private void flushSafely() {
        try {
            flush();
        } catch (Throwable t) {
            Thread previous = sendingThread;
            sendingThread = Thread.currentThread();
            try {
                DiscordSRV.debug(Debug.UNCATEGORIZED, "Failed to send console output to Discord: " + t);
            } finally {
                sendingThread = previous;
            }
        }
    }

    private void flush() {
        synchronized (flushLock) {
            if (queue.isEmpty()) return;
            Thread previous = sendingThread;
            sendingThread = Thread.currentThread();
            try {
                TextChannel channel = channelSupplier.get();
                if (channel == null) return; // keep the queued lines until the channel becomes available

                List<LogItem> items = new ArrayList<>();
                queue.drainTo(items);
                if (droppedEvents) {
                    droppedEvents = false;
                    DiscordSRV.debug(Debug.UNCATEGORIZED, "Some console lines were not forwarded to Discord because too many were logged at once");
                }

                List<String> lines = new ArrayList<>(items.size());
                for (LogItem item : items) {
                    String formatted = format(item);
                    if (formatted != null) lines.add(formatted);
                }

                for (String message : buildMessages(lines)) {
                    if (Thread.currentThread().isInterrupted()) break;
                    channel.sendMessage(message)
                            .setAllowedMentions(Collections.emptyList())
                            .complete();
                }
            } finally {
                sendingThread = previous;
            }
        }
    }

    private String format(LogItem item) {
        String line = item.message;

        // strip formatting
        line = MessageUtil.strip(DiscordUtil.aggressiveStrip(line));

        // apply console regex filters
        Map<Pattern, String> consoleRegexes = DiscordSRV.getPlugin().getConsoleRegexes();
        List<Map.Entry<Pattern, String>> regexes;
        synchronized (consoleRegexes) {
            regexes = new ArrayList<>(consoleRegexes.entrySet());
        }
        for (Map.Entry<Pattern, String> entry : regexes) {
            line = entry.getKey().matcher(line).replaceAll(entry.getValue());
            if (StringUtils.isBlank(line)) return null;
        }
        if (StringUtils.isBlank(line)) return null;

        String formatted = placeholders("DiscordConsoleChannelPrefix", item) + line + placeholders("DiscordConsoleChannelSuffix", item);
        if (useCodeBlocks) {
            // don't let lines break out of the code block
            formatted = formatted.replace("```", "`​`​`");
        }
        return formatted;
    }

    private String placeholders(String key, LogItem item) {
        String value = DiscordSRV.config().getString(key);

        // avoid processing placeholders if config value is empty
        if (StringUtils.isBlank(value)) {
            return "";
        }

        String name = padLoggerName(resolveLoggerName(item.logger));
        String timestamp = TimeUtil.consoleTimeStamp(item.timestamp);

        if (value.contains("%")) value = PlaceholderUtil.replacePlaceholdersToDiscord(value);
        return value
                .replace("{date}", timestamp)
                .replace("{datetime}", timestamp)
                .replace("{name}", StringUtils.isNotBlank(name) ? " " + name : "")
                .replace("{level}", padLevelName(item.level.name()));
    }

    /**
     * Joins the given lines into as few messages as possible, splitting lines that are too long by themselves
     */
    private List<String> buildMessages(List<String> lines) {
        int limit = Message.MAX_CONTENT_LENGTH - (useCodeBlocks ? CODE_BLOCK_START.length() + CODE_BLOCK_END.length() : 0);

        List<String> chunks = new ArrayList<>();
        for (String line : lines) {
            while (line.length() > limit) {
                int split = line.lastIndexOf('\n', limit);
                if (split <= 0) split = limit;
                chunks.add(line.substring(0, split));
                line = line.substring(split == limit ? split : split + 1);
            }
            if (!line.isEmpty()) chunks.add(line);
        }

        List<String> messages = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String chunk : chunks) {
            if (current.length() > 0 && current.length() + 1 + chunk.length() > limit) {
                messages.add(wrap(current.toString()));
                current.setLength(0);
            }
            if (current.length() > 0) current.append('\n');
            current.append(chunk);
        }
        if (current.length() > 0) messages.add(wrap(current.toString()));
        return messages;
    }

    private String wrap(String content) {
        return useCodeBlocks ? CODE_BLOCK_START + content + CODE_BLOCK_END : content;
    }

    private static class LogItem {
        private final String logger;
        private final long timestamp;
        private final LogLevel level;
        private final String message;

        private LogItem(String logger, long timestamp, LogLevel level, String message) {
            this.logger = logger;
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }
    }

    private class Appender extends AbstractAppender {

        protected Appender() {
            super(APPENDER_NAME, null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            try {
                receive(event);
            } catch (Throwable ignored) {
                // never let the console channel break logging
            }
        }
    }

}
