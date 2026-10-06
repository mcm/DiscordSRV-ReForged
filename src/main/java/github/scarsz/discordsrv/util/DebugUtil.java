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

import alexh.weak.Dynamic;
import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.events.DebugReportedEvent;
import github.scarsz.discordsrv.config.DynamicConfig;
import github.scarsz.discordsrv.config.Language;
import github.scarsz.discordsrv.hooks.permissions.LuckPermsHook;
import github.scarsz.discordsrv.listeners.DiscordDisconnectListener;
import github.scarsz.discordsrv.modules.voice.VoiceModule;
import github.scarsz.discordsrv.objects.Lag;
import github.scarsz.discordsrv.platform.Platform;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.requests.CloseCode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DebugUtil {

    public static final List<String> SENSITIVE_OPTIONS = Arrays.asList(
        "BotToken",
        "Experiment_JdbcAccountLinkBackend", "Experiment_JdbcUsername", "Experiment_JdbcPassword",
        "ProxyHost", "ProxyPort", "ProxyUser", "ProxyPassword"
    );
    public static int initializationCount = 0;

    private static final int LOG_TAIL_LINES = 500;

    /**
     * Generates a debug report and writes it to DiscordSRV's debug folder.
     * <p>
     * The Spigot version uploaded the (encrypted) report to bin.scarsz.me; this port writes it to disk instead.
     *
     * @param requester the name of whoever requested the report
     * @return a user-friendly message saying where the report has been written to (or why it failed)
     */
    public static String run(String requester) {
        List<Map<String, String>> files = new LinkedList<>();
        try {
            Platform platform = DiscordSRV.getPlatform();
            String debugInformation = getDebugInformation();
            boolean noIssues = debugInformation.contains("No issues detected automatically");
            Runnable addDebugInfo = () -> files.add(fileMap("debug-info.txt", "Potential issues in the installation", debugInformation));
            if (!noIssues) addDebugInfo.run();
            files.add(fileMap("discordsrv-info.txt", "general information about the mod", String.join("\n", new String[]{
                    "Report requested by " + requester + " at " + new Date(),
                    "Version information:",
                    "   mod version: " + DiscordSRV.version,
                    "   config version: " + DiscordSRV.config().getString("ConfigVersion"),
                    "   language: " + DiscordSRV.config().getLanguage().getName(),
                    "Plugin status:",
                    "   enabled: " + DiscordSRV.getPlugin().isEnabled(),
                    "   ready: " + DiscordSRV.isReady,
                    "   jda status: " + (DiscordUtil.getJda() != null && DiscordUtil.getJda().getGatewayPing() != -1 ? DiscordUtil.getJda().getStatus().name() + " / " + DiscordUtil.getJda().getGatewayPing() + "ms" : "build not finished"),
                    "   channels: " + DiscordSRV.getPlugin().getChannels(),
                    "   console channel: " + DiscordSRV.getPlugin().getConsoleChannel(),
                    "   main chat channel: " + DiscordSRV.getPlugin().getMainChatChannel() + " -> " + DiscordSRV.getPlugin().getMainTextChannel(),
                    "   main guild: " + DiscordSRV.getPlugin().getMainGuild(),
                    "   account link manager: " + (DiscordSRV.getPlugin().getAccountLinkManager() != null ? DiscordSRV.getPlugin().getAccountLinkManager().getClass().getSimpleName() : "null"),
                    "Environmental variables:",
                    "   discord main guild roles: " + (DiscordSRV.getPlugin().getMainGuild() == null ? "invalid main guild" : DiscordSRV.getPlugin().getMainGuild().getRoles().stream().map(Role::toString).collect(Collectors.toList())),
                    "   discord server owner: " + (DiscordSRV.getPlugin().getMainGuild() == null ? "invalid main guild" : DiscordSRV.getPlugin().getMainGuild().getOwner()),
                    "   luckperms: " + (platform.isModLoaded("luckperms") ? "loaded" : "not loaded") + (LuckPermsHook.isEnabled() ? ", hooked" : ", not hooked"),
                    "Threads:",
                    "   channel topic updater -> alive: " + (DiscordSRV.getPlugin().getChannelTopicUpdater() != null && DiscordSRV.getPlugin().getChannelTopicUpdater().isAlive()),
                    "   channel updater -> alive: " + (DiscordSRV.getPlugin().getChannelUpdater() != null && DiscordSRV.getPlugin().getChannelUpdater().isAlive()),
                    "   server watchdog -> alive: " + (DiscordSRV.getPlugin().getServerWatchdog() != null && DiscordSRV.getPlugin().getServerWatchdog().isAlive()),
                    "   nickname updater -> alive: " + (DiscordSRV.getPlugin().getNicknameUpdater() != null && DiscordSRV.getPlugin().getNicknameUpdater().isAlive()),
                    "   presence updater -> alive: " + (DiscordSRV.getPlugin().getPresenceUpdater() != null && DiscordSRV.getPlugin().getPresenceUpdater().isAlive()),
                    "Guilds:" + listGuilds()
            })));
            files.add(fileMap("relevant-lines-from-server.log", "lines from the server console containing \"discordsrv\"", getRelevantLinesFromServerLog()));
            files.add(fileMap("latest-log-tail.log", "the last " + LOG_TAIL_LINES + " lines of logs/latest.log", getServerLogTail()));
            for (DynamicConfig.Source source : DiscordSRV.config().getSources().values()) {
                File file = source.getFile();
                files.add(fileMap(file.getName(), "raw " + file.getPath(), file.exists() ? FileUtils.readFileToString(file, StandardCharsets.UTF_8) : "file does not exist"));
            }
            files.add(fileMap("config-active.yml", "active " + DiscordSRV.getPlugin().getConfigFile().getPath(), getActiveConfig()));
            files.add(fileMap("server-info.txt", null, getServerInfo()));
            files.add(fileMap("logger-details.txt", null, getLoggerInfo()));
            files.add(fileMap("permissions.txt", null, getPermissions()));
            files.add(fileMap("threads.txt", "Threads with DiscordSRV in the name or that have trace elements with DiscordSRV's classes", getThreads()));
            files.add(fileMap("system-info.txt", null, getSystemInfo()));
            if (noIssues) addDebugInfo.run();
        } catch (Exception e) {
            DiscordSRV.error(e);
            return "Failed to collect debug information: " + e.getMessage() + ". Check the console for further details.";
        }

        return writeReport(files, requester);
    }

    private static String listGuilds() {
        if (DiscordUtil.getJda() == null) return "\n   null JDA";
        StringBuilder list = new StringBuilder();
        for (Guild server : DiscordUtil.getJda().getGuilds()) {
            list.append("\n   ").append(server).append(":  [");
            for (TextChannel channel : server.getTextChannels()) list.append(channel).append(", ");
            list.append("]");
        }
        return list.toString();
    }

    private static Map<String, String> fileMap(String name, String description, String content) {
        Map<String, String> map = new HashMap<>();
        map.put("name", name);
        map.put("description", description);
        map.put("content", content);
        map.put("type", "text/plain");
        return map;
    }

    private static String getActiveConfig() {
        try {
            DynamicConfig.Source source = DiscordSRV.config().getProvider("config");
            Map<String, Object> values = new LinkedHashMap<>(source.getDefaults());
            values.putAll(source.getValues());
            Dynamic activeConfig = Dynamic.from(values);
            StringBuilder stringBuilder = new StringBuilder(500);
            Iterator<Dynamic> iterator = activeConfig.allChildren().iterator();
            while (iterator.hasNext()) {
                Dynamic child = iterator.next();
                if (child.allChildren().count() == 0) {
                    stringBuilder.append(child.key().asObject()).append(": ").append(child.asObject());
                } else {
                    StringJoiner childJoiner = new StringJoiner(", ");

                    Iterator<Dynamic> childIterator = child.allChildren().iterator();
                    while (childIterator.hasNext()) {
                        Dynamic grandchild = childIterator.next();
                        childJoiner.add("- " + grandchild.asObject());
                    }

                    stringBuilder.append(child.key().asObject()).append(": ").append(childJoiner);
                }
                stringBuilder.append("\n");
            }
            return stringBuilder.toString();
        } catch (Exception e) {
            return "Failed to get parsed config: " + e.getMessage() + "\n" + ExceptionUtils.getStackTrace(e);
        }
    }

    private static File getServerLogFile() {
        return new File("logs/latest.log");
    }

    private static String getRelevantLinesFromServerLog() {
        List<String> output = new LinkedList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(getServerLogFile()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (
                    line.toLowerCase().contains("discordsrv") && !line.toLowerCase().contains("[discordsrv] chat:")
                    || line.toLowerCase().contains(" /discord")
                ) output.add(DiscordUtil.aggressiveStrip(line));
            }
        } catch (IOException e) {
            DiscordSRV.error(e);
        }

        return String.join("\n", output);
    }

    private static String getServerLogTail() {
        Deque<String> lines = new ArrayDeque<>(LOG_TAIL_LINES);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(getServerLogFile()), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (lines.size() >= LOG_TAIL_LINES) lines.removeFirst();
                lines.addLast(DiscordUtil.aggressiveStrip(line));
            }
        } catch (IOException e) {
            return "Failed to read " + getServerLogFile() + ": " + e.getMessage();
        }
        return String.join("\n", lines);
    }

    private static String getServerInfo() {
        List<String> output = new LinkedList<>();
        Platform platform = DiscordSRV.getPlatform();

        output.add("server players: " + PlayerUtil.getOnlinePlayers().size() + "/" + platform.getMaxPlayers());
        output.add("server unique joins: " + platform.getTotalPlayerCount());
        output.add("server tps: " + Lag.getTPSString());
        output.add("");
        output.add("Minecraft version: " + platform.getMinecraftVersion());
        output.add("Server version: " + platform.getServerVersion());
        output.add("Server online mode: " + platform.isOnlineMode());
        output.add("");
        output.add("Integrations:");
        output.add("- luckperms: " + (platform.isModLoaded("luckperms") ? "loaded" : "not loaded"));

        return String.join("\n", output);
    }

    private static String getLoggerInfo() {
        List<String> output = new LinkedList<>();

        try {
            LoggerContext config = ((LoggerContext) LogManager.getContext(false));
            output.add("Log level: " + config.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME).getLevel());

            org.apache.logging.log4j.core.Logger rootLogger = ((org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger());

            List<String> filters = new ArrayList<>();
            Iterator<org.apache.logging.log4j.core.Filter> filterIterator = rootLogger.getFilters();
            while (filterIterator.hasNext()) {
                Object next = filterIterator.next();
                filters.add(next.getClass().getName() + ": " + next);
            }
            output.add("Filters: " + String.join(", ", filters));

            List<String> appenders = new ArrayList<>();
            for (Map.Entry<String, org.apache.logging.log4j.core.Appender> entry : rootLogger.getAppenders().entrySet()) {
                appenders.add(entry.getKey() + ": " + entry.getValue().getName() + " (" + entry.getValue().getClass().getName() + ")");
            }
            output.add("Appenders: " + String.join(", ", appenders));
        } catch (Throwable t) {
            output.add("Failed to log debug message for logging");
            output.add(ExceptionUtils.getMessage(t));
        }
        return String.join("\n", output);
    }

    private static String getDebugInformation() {
        List<Message> messages = new ArrayList<>();

        if (initializationCount > 1) {
            messages.add(new Message(Message.Type.PLUGIN_RELOADED));
        }

        if (DiscordUtil.getJda() == null) {
            if (DiscordSRV.invalidBotToken || DiscordDisconnectListener.mostRecentCloseCode == CloseCode.AUTHENTICATION_FAILED) {
                messages.add(new Message(Message.Type.INVALID_BOT_TOKEN));
            } else if (DiscordDisconnectListener.mostRecentCloseCode == CloseCode.DISALLOWED_INTENTS) {
                messages.add(new Message(Message.Type.DISALLOWED_INTENTS));
            } else {
                messages.add(new Message(Message.Type.NOT_CONNECTED));
            }
        } else if (DiscordUtil.getJda().getGuilds().isEmpty()) {
            messages.add(new Message(Message.Type.NOT_IN_ANY_SERVERS));
        }

        if (DiscordUtil.getJda() != null) {
            if (DiscordSRV.getPlugin().getMainTextChannel() == null) {
                if (DiscordSRV.getPlugin().getConsoleChannel() == null) {
                    messages.add(new Message(Message.Type.NO_CHANNELS_LINKED));
                } else {
                    messages.add(new Message(Message.Type.NO_CHAT_CHANNELS_LINKED));
                }
            }

            for (Map.Entry<String, String> entry : DiscordSRV.getPlugin().getChannels().entrySet()) {
                TextChannel textChannel = DiscordUtil.getTextChannelById(entry.getValue());
                if (textChannel == null || !textChannel.getId().equals(entry.getValue())) {
                    messages.add(new Message(Message.Type.INVALID_CHANNEL, "{" + entry.getKey() + ":" + entry.getValue() + "}"));
                    continue;
                }

                String configName = entry.getKey();
                String discordName = textChannel.getName();
                // contains non-alphanumeric & -whitespace characters (not a-z, 0-9 or whitespaces), "mc", "minecraft" or "chat" or is "global"
                if (configName.equals(discordName) && (!configName.replaceAll("[\\w\\d\\s]", "").isEmpty()
                        || configName.contains("mc") || configName.contains("minecraft") || configName.contains("chat")) && !configName.equals("global")) {
                    messages.add(new Message(Message.Type.SAME_CHANNEL_NAME, entry.getKey()));
                }
            }
        }

        String consoleChannelId = DiscordSRV.config().getString("DiscordConsoleChannelId");
        if (consoleChannelId != null && !consoleChannelId.matches("^0*$")
                && DiscordSRV.getPlugin().getChannels().values().stream().filter(Objects::nonNull).anyMatch(channelId -> channelId.equals(consoleChannelId))) {
            messages.add(new Message(Message.Type.CONSOLE_AND_CHAT_SAME_CHANNEL));
        }

        String roleName = DiscordSRV.config().getStringElse("MinecraftDiscordAccountLinkedRoleNameToAddUserTo", null);
        if (DiscordUtil.getJda() != null && roleName != null) {
            try {
                Role role = DiscordUtil.resolveRole(roleName);
                if (role != null && DiscordSRV.getPlugin().getGroupSynchronizables().values().stream().anyMatch(roleId -> roleId.equals(role.getId()))) {
                    messages.add(new Message(Message.Type.LINKED_ROLE_GROUP_SYNC));
                }
            } catch (Throwable ignored) {}
        }

        if (!DiscordSRV.config().getBooleanElse("RespectChatPlugins", true)) {
            messages.add(new Message(Message.Type.RESPECT_CHAT_PLUGINS));
        }

        if (!Debug.anyEnabled()) {
            messages.add(new Message(Message.Type.DEBUG_MODE_NOT_ENABLED));
        }

        StringBuilder stringBuilder = new StringBuilder();
        if (messages.isEmpty()) {
            stringBuilder.append("No issues detected automatically\n");
        } else {
            messages.stream().sorted((one, two) -> Boolean.compare(one.isWarning(), two.isWarning())).forEach(message ->
                    stringBuilder.append(message.isWarning() ? "[Warn] " : "[Error] ").append(message.getMessage()).append("\n"));
        }
        stringBuilder.append("\nFailedTests: [").append(messages.stream().map(Message::getTypeName).collect(Collectors.joining(", "))).append(']');
        stringBuilder.append("\nDebuggerCategories: [").append(String.join(", ", DiscordSRV.getPlugin().getDebuggerCategories())).append(']');

        return stringBuilder.toString();
    }

    private static String getPermissions() {
        List<String> output = new LinkedList<>();

        if (DiscordUtil.getJda() == null) {
            return "JDA == null";
        }

        Guild mainGuild = DiscordSRV.getPlugin().getMainGuild();
        if (mainGuild == null) {
            output.add("main guild -> null");
        } else {
            List<String> guildPermissions = new ArrayList<>();
            if (DiscordUtil.checkPermission(mainGuild, Permission.ADMINISTRATOR)) guildPermissions.add("administrator");
            if (DiscordUtil.checkPermission(mainGuild, Permission.MANAGE_ROLES)) guildPermissions.add("manage-roles");
            if (DiscordUtil.checkPermission(mainGuild, Permission.NICKNAME_MANAGE)) guildPermissions.add("nickname-manage");
            if (DiscordUtil.checkPermission(mainGuild, Permission.MANAGE_WEBHOOKS)) guildPermissions.add("manage-webhooks");
            if (DiscordUtil.checkPermission(mainGuild, Permission.BAN_MEMBERS)) guildPermissions.add("ban-members");
            output.add("main guild -> " + mainGuild + " [" + String.join(", ", guildPermissions) + "]");
        }

        VoiceChannel lobbyChannel = VoiceModule.getLobbyChannel();
        if (lobbyChannel == null) {
            output.add("voice lobby -> null");
        } else {
            List<String> channelPermissions = new ArrayList<>();
            if (DiscordUtil.checkPermission(lobbyChannel, Permission.VOICE_MOVE_OTHERS)) channelPermissions.add("move-members");
            output.add("voice lobby -> " + lobbyChannel + " [" + String.join(", ", channelPermissions) + "]");

            Category category = lobbyChannel.getParentCategory();
            if (category == null) {
                output.add("voice category -> null");
            } else {
                List<String> categoryPermissions = new ArrayList<>();
                if (DiscordUtil.checkPermission(category, Permission.VOICE_MOVE_OTHERS)) categoryPermissions.add("move-members");
                if (DiscordUtil.checkPermission(category, Permission.MANAGE_CHANNEL)) categoryPermissions.add("manage-channel");
                if (DiscordUtil.checkPermission(category, Permission.MANAGE_PERMISSIONS)) categoryPermissions.add("manage-permissions");
                output.add("voice category -> " + category + " [" + String.join(", ", categoryPermissions) + "]");
            }
        }

        TextChannel consoleChannel = DiscordSRV.getPlugin().getConsoleChannel();
        if (consoleChannel == null) {
            output.add("console channel -> null");
        } else {
            List<String> consolePermissions = new ArrayList<>();
            if (DiscordUtil.checkPermission(consoleChannel, Permission.VIEW_CHANNEL)) consolePermissions.add("read");
            if (DiscordUtil.checkPermission(consoleChannel, Permission.MESSAGE_SEND)) consolePermissions.add("write");
            if (DiscordUtil.checkPermission(consoleChannel, Permission.MANAGE_CHANNEL)) consolePermissions.add("channel-manage");
            output.add("console channel -> " + consoleChannel + " [" + String.join(", ", consolePermissions) + "]");
        }

        DiscordSRV.getPlugin().getChannels().forEach((channel, textChannelId) -> {
            TextChannel textChannel = StringUtils.isNotBlank(textChannelId) ? DiscordUtil.getTextChannelById(textChannelId) : null;
            if (textChannel != null) {
                List<String> outputForChannel = new LinkedList<>();
                if (DiscordUtil.checkPermission(textChannel, Permission.VIEW_CHANNEL)) outputForChannel.add("read");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_SEND)) outputForChannel.add("write");
                if (DiscordUtil.checkPermission(textChannel, Permission.MANAGE_CHANNEL)) outputForChannel.add("channel-manage");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_MANAGE)) outputForChannel.add("message-manage");
                if (DiscordUtil.checkPermission(textChannel, Permission.MANAGE_WEBHOOKS)) outputForChannel.add("manage-webhooks");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_ADD_REACTION)) outputForChannel.add("add-reactions");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_HISTORY)) outputForChannel.add("history");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_ATTACH_FILES)) outputForChannel.add("attach-files");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_MENTION_EVERYONE)) outputForChannel.add("mention-everyone");
                if (DiscordUtil.checkPermission(textChannel, Permission.MESSAGE_EXT_EMOJI)) outputForChannel.add("external-emotes");
                output.add(channel + " -> " + textChannel + " [" + String.join(", ", outputForChannel) + "]");
            } else {
                output.add(channel + " -> null");
            }
        });

        return String.join("\n", output);
    }

    private static String getThreads() {
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        Set<Thread> alreadyLoggedThreads = new HashSet<>();

        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<Thread, StackTraceElement[]> entry : stackTraces.entrySet()) {
            Thread thread = entry.getKey();
            String threadName = thread.getName();
            StackTraceElement[] traceElements = entry.getValue();
            if ((threadName.contains("DiscordSRV") || Arrays.stream(traceElements)
                    .anyMatch(trace -> trace.getClassName().startsWith("github.scarsz.discordsrv"))
            ) && alreadyLoggedThreads.add(thread)) {
                stringBuilder.append(threadName).append(":\n").append(PrettyUtil.beautify(traceElements)).append("\n");
            }
        }

        Thread serverThread = stackTraces.keySet().stream()
                .filter(thread -> thread.getName().equals("Server thread"))
                .findAny().orElse(null);
        if (serverThread != null && alreadyLoggedThreads.add(serverThread)) {
            stringBuilder.append("Server Thread:\n").append(PrettyUtil.beautify(serverThread.getStackTrace()));
        }

        stringBuilder.append("\nOther threads:\n");
        for (Thread thread : stackTraces.keySet()) {
            if (alreadyLoggedThreads.add(thread)) {
                stringBuilder.append("- ").append(thread.getName()).append('\n');
            }
        }

        return stringBuilder.toString();
    }

    private static String getSystemInfo() {
        List<String> output = new LinkedList<>();

        // total number of processors or cores available to the JVM
        output.add("Available processors (cores): " + Runtime.getRuntime().availableProcessors());
        output.add("");

        // memory
        output.add("Free memory for JVM (MB): " + Runtime.getRuntime().freeMemory() / 1024 / 1024);
        output.add("Maximum memory for JVM (MB): " + (Runtime.getRuntime().maxMemory() == Long.MAX_VALUE ? "no limit" : Runtime.getRuntime().maxMemory() / 1024 / 1024));
        output.add("Total memory available for JVM (MB): " + Runtime.getRuntime().totalMemory() / 1024 / 1024);
        output.add("");

        // drive space
        File serverRoot = new File(".").getAbsoluteFile();
        output.add("Server storage:");
        output.add("- total space (MB): " + serverRoot.getTotalSpace() / 1024 / 1024);
        output.add("- free space (MB): " + serverRoot.getFreeSpace() / 1024 / 1024);
        output.add("- usable space (MB): " + serverRoot.getUsableSpace() / 1024 / 1024);
        output.add("");

        // java version
        Map<String, String> systemProperties = ManagementFactory.getRuntimeMXBean().getSystemProperties();
        output.add("Java version: " + systemProperties.get("java.version"));
        output.add("Java vendor: " + systemProperties.get("java.vendor") + " " + systemProperties.get("java.vendor.url"));
        output.add("Java home: " + systemProperties.get("java.home"));
        output.add("Command line: " + systemProperties.get("sun.java.command"));
        output.add("Time zone: " + systemProperties.get("user.timezone"));
        output.add("OS: " + systemProperties.get("os.name") + " " + systemProperties.get("os.version") + " (" + systemProperties.get("os.arch") + ")");

        return String.join("\n", output);
    }

    /**
     * Writes the given files into a zip file in DiscordSRV's debug folder
     * @param files A Map representing a structure of file name &amp; its contents
     * @param requester Person who requested the debug report
     * @return A user-friendly message of how the report went
     */
    private static String writeReport(List<Map<String, String>> files, String requester) {
        if (files.size() == 0) {
            return "ERROR/Failed to collect debug information: files list == 0... How???";
        }

        // Remove sensitive data and set the file content to "blank" if the file is blank
        files.forEach(map -> {
            String content = map.get("content");
            if (StringUtils.isNotBlank(content)) {
                // remove sensitive options from files
                for (String option : DebugUtil.SENSITIVE_OPTIONS) {
                    String value = DiscordSRV.config().getString(option);
                    if (StringUtils.isNotBlank(value) && value.trim().length() > 2 && !value.equalsIgnoreCase("username")) {
                        content = content.replace(value, "REDACTED");
                    }
                }

                // extra regex replace for bot tokens
                content = content.replaceAll("[A-Za-z\\d]{24,}\\.[\\w-]{6}\\.[\\w-]{27,}", "TOKEN REDACTED");
            } else {
                // put "blank" for null file contents
                content = "blank";
            }
            map.put("content", content);
        });

        File debugFolder = DiscordSRV.getPlugin().getDebugFolder();
        if (!debugFolder.exists() && !debugFolder.mkdirs()) {
            return "ERROR/Failed to create the debug folder " + debugFolder.getPath();
        }

        String debugName = "debug-" + new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".zip";
        File zipFile = new File(debugFolder, debugName);

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Set<String> names = new HashSet<>();
            for (Map<String, String> file : files) {
                String name = file.get("name");
                if (!names.add(name)) continue;
                zipOutputStream.putNextEntry(new ZipEntry(name));

                StringBuilder content = new StringBuilder();
                if (StringUtils.isNotBlank(file.get("description")) && name.endsWith(".txt")) {
                    content.append("# ").append(file.get("description")).append("\n\n");
                }
                content.append(file.get("content"));
                byte[] data = content.toString().getBytes(StandardCharsets.UTF_8);
                zipOutputStream.write(data, 0, data.length);
                zipOutputStream.closeEntry();
            }
        } catch (IOException ex) {
            DiscordSRV.error(ex);
            return "ERROR/Failed to write the debug report to disk: " + ex.getClass().getName() + ": " + ex.getMessage();
        }

        String path = zipFile.getPath();
        DiscordSRV.api.callEvent(new DebugReportedEvent(requester, path));
        return path;
    }

    public static String getStackTrace() {
        List<String> stackTrace = new LinkedList<>();
        stackTrace.add("Stack trace @ debug call (THIS IS NOT AN ERROR)");
        Arrays.stream(ExceptionUtils.getStackTrace(new Throwable()).split("\n"))
                .filter(s -> s.toLowerCase().contains("discordsrv"))
                .filter(s -> !s.contains("DebugUtil.getStackTrace"))
                .forEach(stackTrace::add);
        return String.join("\n", stackTrace);
    }

    public static class Message {

        private final Type type;
        private final String[] args;

        public Message(Type type, String... args) {
            this.type = type;
            this.args = args;
        }

        private boolean isWarning() {
            return type.warning;
        }

        @SuppressWarnings("RedundantCast") // it in fact isn't
        public String getMessage() {
            return String.format(type.message, (Object[]) args);
        }

        public String getTypeName() {
            return type.name();
        }

        public enum Type {

            // Warnings
            NO_CHAT_CHANNELS_LINKED(true, "No chat channels linked"),
            NO_CHANNELS_LINKED(true, "No channels linked (chat & console)"),
            SAME_CHANNEL_NAME(true, "Channel %s has the same in-game and Discord channel name"),

            // Errors
            RESPECT_CHAT_PLUGINS(false, "You have RespectChatPlugins set to false. This means DiscordSRV will completely ignore " +
                    "any other plugin's attempts to cancel a chat message from being broadcasted to the server. " +
                    "Disabling this is NOT a valid solution to your chat messages not being sent to Discord."
            ),
            PLUGIN_RELOADED(false, "DiscordSRV has been initialized more than once (aka \"reloading\"). You will not receive support in this state."),
            INVALID_CHANNEL(false, "Invalid Channel %s (not found)"),
            CONSOLE_AND_CHAT_SAME_CHANNEL(false, LangUtil.InternalMessage.CONSOLE_CHANNEL_ASSIGNED_TO_LINKED_CHANNEL.getDefinitions().get(Language.EN)),
            NOT_IN_ANY_SERVERS(false, LangUtil.InternalMessage.BOT_NOT_IN_ANY_SERVERS.getDefinitions().get(Language.EN)),
            NOT_CONNECTED(false, "Not connected to Discord!"),
            INVALID_BOT_TOKEN(false, "Invalid bot token, not connected to Discord."),
            DISALLOWED_INTENTS(false, "Disallowed intents (Make sure you followed all installation instructions), not connected to Discord."),
            DEBUG_MODE_NOT_ENABLED(false, "You do not have debug mode on. Run /discordsrv debugger, " +
                    "try to reproduce your problem and then run /discordsrv debugger upload to generate another report."
            ),
            LINKED_ROLE_GROUP_SYNC(false, "Cannot have the role in MinecraftDiscordAccountLinkedRoleNameToAddUserTo as a role in GroupRoleSynchronizationGroupsAndRolesToSync");

            private final boolean warning;
            private final String message;

            Type(boolean warning, String message) {
                this.warning = warning;
                this.message = message;
            }

        }

    }

}
