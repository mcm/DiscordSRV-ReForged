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

package github.scarsz.discordsrv;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.neovisionaries.ws.client.DualStackMode;
import com.neovisionaries.ws.client.ProxySettings;
import com.neovisionaries.ws.client.WebSocketFactory;
import github.scarsz.discordsrv.api.ApiManager;
import github.scarsz.discordsrv.api.events.*;
import github.scarsz.discordsrv.config.DynamicConfig;
import github.scarsz.discordsrv.config.Language;
import github.scarsz.discordsrv.hooks.permissions.LuckPermsHook;
import github.scarsz.discordsrv.listeners.*;
import github.scarsz.discordsrv.modules.alerts.AlertListener;
import github.scarsz.discordsrv.modules.requirelink.RequireLinkModule;
import github.scarsz.discordsrv.modules.voice.VoiceModule;
import github.scarsz.discordsrv.objects.Lag;
import github.scarsz.discordsrv.objects.MessageFormat;
import github.scarsz.discordsrv.objects.PluginLogger;
import github.scarsz.discordsrv.objects.log4j.ChannelLoggingHandler;
import github.scarsz.discordsrv.objects.log4j.JdaFilter;
import github.scarsz.discordsrv.objects.managers.AccountLinkManager;
import github.scarsz.discordsrv.objects.managers.CommandManager;
import github.scarsz.discordsrv.objects.managers.GroupSynchronizationManager;
import github.scarsz.discordsrv.objects.managers.link.JdbcAccountLinkManager;
import github.scarsz.discordsrv.objects.managers.link.file.AppendOnlyFileAccountLinkManager;
import github.scarsz.discordsrv.objects.threads.*;
import github.scarsz.discordsrv.platform.CommandSender;
import github.scarsz.discordsrv.platform.GamePlayer;
import github.scarsz.discordsrv.platform.Platform;
import github.scarsz.discordsrv.platform.event.*;
import github.scarsz.discordsrv.util.*;
import lombok.Getter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import okhttp3.ConnectionPool;
import okhttp3.Credentials;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * DiscordSRV's main class, can be accessed via {@link #getPlugin()}.
 * <p>
 * In the NeoForge port this class is platform independent: the NeoForge mod creates it with a {@link Platform}
 * implementation and forwards server lifecycle and game events to it.
 *
 * @see #getAccountLinkManager()
 * @see #sendJoinMessage(GamePlayer, String)
 * @see #sendLeaveMessage(GamePlayer, String)
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class DiscordSRV {

    public static final ApiManager api = new ApiManager();
    public static boolean isReady = false;
    public static boolean shuttingDown = false;
    public static boolean invalidBotToken = false;
    private static boolean offlineUuidAvatarUrlNagged = false;
    public static String version = "";

    private static DiscordSRV instance;

    @Getter private final Platform platformInstance;
    @Getter private final PluginLogger logger = new PluginLogger("DiscordSRV");
    private volatile boolean enabled = false;

    // Managers
    private AccountLinkManager accountLinkManager;
    @Getter private final CommandManager commandManager = new CommandManager();
    @Getter private final GroupSynchronizationManager groupSynchronizationManager = new GroupSynchronizationManager();

    // Threads
    @Getter private ChannelTopicUpdater channelTopicUpdater;
    @Getter private ChannelUpdater channelUpdater;
    @Getter private NicknameUpdater nicknameUpdater;
    @Getter private PresenceUpdater presenceUpdater;
    @Getter private ServerWatchdog serverWatchdog;

    // Modules
    @Getter private AlertListener alertListener = null;
    @Getter private RequireLinkModule requireLinkModule;
    @Getter private VoiceModule voiceModule;
    @Getter private BanSynchronizer banSynchronizer;

    // Game event listeners (replaces Bukkit's event registration)
    private final List<GameListener> gameListeners = new CopyOnWriteArrayList<>();

    // Config
    @Getter private final Map<String, String> channels = new LinkedHashMap<>(); // <in-game channel name, discord channel>
    @Getter private final Map<String, String> roleAliases = new LinkedHashMap<>(); // key always lowercase
    @Getter private final Map<Pattern, String> consoleRegexes = new LinkedHashMap<>();
    @Getter private final Map<Pattern, String> gameRegexes = new LinkedHashMap<>();
    @Getter private final Map<Pattern, String> discordRegexes = new LinkedHashMap<>();
    @Getter private final Map<Pattern, String> webhookUsernameRegexes = new LinkedHashMap<>();
    private final DynamicConfig config;

    // Debugger
    @Getter private final Set<String> debuggerCategories = new CopyOnWriteArraySet<>();

    @Getter private final long startTime = System.currentTimeMillis();
    @Getter private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Files
    @Getter private final File dataFolder;
    @Getter private final File configFile;
    @Getter private final File messagesFile;
    @Getter private final File voiceFile;
    @Getter private final File linkingFile;
    @Getter private final File synchronizationFile;
    @Getter private final File alertsFile;
    @Getter private final File debugFolder;
    @Getter private final File logFolder;

    // JDA & JDA related
    @Getter private JDA jda = null;
    private ExecutorService callbackThreadPool;
    @Getter private ChannelLoggingHandler consoleAppender;
    private JdaFilter jdaFilter;

    public static DiscordSRV getPlugin() {
        return instance;
    }
    public static Platform getPlatform() {
        return instance.platformInstance;
    }
    public static DynamicConfig config() {
        return getPlugin().config;
    }
    public void reloadConfig() {
        try {
            config().loadAll();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    public void reloadAllowedMentions() {
        // set default mention types to never ping everyone/here
        MessageRequest.setDefaultMentions(config().getStringList("DiscordChatChannelAllowedMentions").stream()
                .map(s -> {
                    String name = s.toUpperCase(Locale.ROOT);
                    if (name.equals("EMOTE")) name = "EMOJI"; // renamed in JDA 5
                    try {
                        return Message.MentionType.valueOf(name);
                    } catch (IllegalArgumentException e) {
                        DiscordSRV.error("Unknown mention type \"" + s + "\" defined in DiscordChatChannelAllowedMentions");
                        return null;
                    }
                }).filter(Objects::nonNull).collect(Collectors.toSet()));
        DiscordSRV.debug("Allowed chat mention types: " + MessageRequest.getDefaultMentions().stream().map(Enum::name).collect(Collectors.joining(", ")));
    }

    public void reloadChannels() {
        synchronized (channels) {
            channels.clear();
            config().getMap("Channels").forEach(channels::put);
        }
    }
    public void reloadRoleAliases() {
        synchronized (roleAliases) {
            roleAliases.clear();
            config().getMap("DiscordChatChannelRoleAliases").forEach((role, alias) -> roleAliases.put(role.toLowerCase(), alias));
        }
    }
    public void reloadRegexes() {
        synchronized (consoleRegexes) {
            consoleRegexes.clear();
            loadRegexesFromConfig("DiscordConsoleChannelFilters", consoleRegexes);
        }
        synchronized (gameRegexes) {
            gameRegexes.clear();
            loadRegexesFromConfig("DiscordChatChannelGameFilters", gameRegexes);
        }
        synchronized (discordRegexes) {
            discordRegexes.clear();
            loadRegexesFromConfig("DiscordChatChannelDiscordFilters", discordRegexes);
        }
        synchronized (webhookUsernameRegexes) {
            webhookUsernameRegexes.clear();
            loadRegexesFromConfig("Experiment_WebhookChatMessageUsernameFilters", webhookUsernameRegexes);
        }
    }
    private void loadRegexesFromConfig(final String key, final Map<Pattern, String> map) {
        config().getMap(key).forEach((regex, replacement) -> {
            if (StringUtils.isEmpty(regex)) return;
            try {
                Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
                map.put(pattern, replacement);
            } catch (PatternSyntaxException e) {
                error("Invalid regex pattern: " + regex + " (" + e.getDescription() + ")");
            }
        });
    }
    public String getMainChatChannel() {
        return !channels.isEmpty() ? channels.keySet().iterator().next() : null;
    }
    public TextChannel getMainTextChannel() {
        if (channels.isEmpty() || jda == null) return null;
        String firstChannel = channels.values().iterator().next();
        if (StringUtils.isBlank(firstChannel)) return null;
        return DiscordUtil.getTextChannelById(firstChannel);
    }
    public Guild getMainGuild() {
        if (jda == null) return null;

        TextChannel mainTextChannel = getMainTextChannel();
        if (mainTextChannel != null) return mainTextChannel.getGuild();
        TextChannel consoleChannel = getConsoleChannel();
        if (consoleChannel != null) return consoleChannel.getGuild();
        return !jda.getGuilds().isEmpty() ? jda.getGuilds().get(0) : null;
    }
    public TextChannel getConsoleChannel() {
        if (jda == null) return null;

        String consoleChannel = config.getString("DiscordConsoleChannelId");
        return StringUtils.isNotBlank(consoleChannel) && StringUtils.isNumeric(consoleChannel)
                ? DiscordUtil.getTextChannelById(consoleChannel)
                : null;
    }
    public TextChannel getDestinationTextChannelForGameChannelName(String gameChannelName) {
        if (jda == null || gameChannelName == null) return null;
        Map.Entry<String, String> entry = channels.entrySet().stream().filter(e -> e.getKey().equals(gameChannelName)).findFirst().orElse(null);
        String value = entry != null ? entry.getValue() : null;
        if (!StringUtils.isBlank(value)) {
            return DiscordUtil.getTextChannelById(value); // found case-sensitive channel
        }

        // no case-sensitive channel found, try case in-sensitive
        entry = channels.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(gameChannelName)).findFirst().orElse(null);
        value = entry != null ? entry.getValue() : null;
        if (!StringUtils.isBlank(value)) {
            return DiscordUtil.getTextChannelById(value); // found case-insensitive channel
        }

        return null; // no channel found, case-insensitive or not
    }
    public String getDestinationGameChannelNameForTextChannel(TextChannel source) {
        if (source == null) return null;
        return channels.entrySet().stream()
                .filter(entry -> source.getId().equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);
    }
    public File getLogFile() {
        String fileName = config().getString("DiscordConsoleChannelUsageLog");
        if (StringUtils.isBlank(fileName)) return null;
        fileName = fileName.replace("%date%", TimeUtil.date());
        return new File(this.getLogFolder(), fileName);
    }

    // log messages
    public static void logThrowable(Throwable throwable, Consumer<String> logger) {
        StringWriter stringWriter = new StringWriter();
        throwable.printStackTrace(new PrintWriter(stringWriter));

        for (String line : stringWriter.toString().split("\n")) logger.accept(line);
    }
    private static PluginLogger log() {
        return instance != null ? instance.logger : FALLBACK_LOGGER;
    }
    private static final PluginLogger FALLBACK_LOGGER = new PluginLogger("DiscordSRV");
    public static void info(LangUtil.InternalMessage message) {
        info(message.toString());
    }
    public static void info(String message) {
        log().info(message);
    }
    public static void warning(LangUtil.InternalMessage message) {
        warning(message.toString());
    }
    public static void warning(String message) {
        log().warning(message);
    }
    public static void error(LangUtil.InternalMessage message) {
        error(message.toString());
    }
    public static void error(String message) {
        log().severe(message);
    }
    public static void error(Throwable throwable) {
        logThrowable(throwable, DiscordSRV::error);
    }
    public static void error(String message, Throwable throwable) {
        error(message);
        error(throwable);
    }
    public static void debug(String message) {
        debug(Debug.UNCATEGORIZED, message);
    }
    public static void debug(Debug type, String message) {
        if (type.isVisible()) {
            log().info("[" + type.name() + " DEBUG] " + message
                    + (Debug.CALLSTACKS.isVisible() ? "\n" + DebugUtil.getStackTrace() : ""));
        }
    }
    public static void debug(Throwable throwable) {
        debug(Debug.UNCATEGORIZED, throwable);
    }
    public static void debug(Debug type, Throwable throwable) {
        logThrowable(throwable, line -> debug(type, line));
    }
    public static void debug(Throwable throwable, String message) {
        debug(Debug.UNCATEGORIZED, throwable, message);
    }
    public static void debug(Debug type, Throwable throwable, String message) {
        debug(type, throwable);
        debug(type, message);
    }
    public static void debug(Collection<String> message) {
        message.forEach(DiscordSRV::debug);
    }
    public static void debug(Debug type, Collection<String> message) {
        message.forEach(msg -> debug(type, msg));
    }

    /**
     * Creates DiscordSRV and loads its configuration. Called by the platform when the mod is constructed.
     */
    public DiscordSRV(Platform platform) {
        instance = this;
        this.platformInstance = platform;
        version = platform.getModVersion();

        dataFolder = platform.getDataFolder();
        configFile = new File(dataFolder, "config.yml");
        messagesFile = new File(dataFolder, "messages.yml");
        voiceFile = new File(dataFolder, "voice.yml");
        linkingFile = new File(dataFolder, "linking.yml");
        synchronizationFile = new File(dataFolder, "synchronization.yml");
        alertsFile = new File(dataFolder, "alerts.yml");
        debugFolder = new File(dataFolder, "debug");
        logFolder = new File(dataFolder, "discord-console-logs");

        // load config
        if (!dataFolder.exists() && !dataFolder.mkdirs()) error("Failed to create data folder " + dataFolder);
        config = new DynamicConfig();
        config.addSource(DiscordSRV.class, "config", getConfigFile());
        config.addSource(DiscordSRV.class, "messages", getMessagesFile());
        config.addSource(DiscordSRV.class, "voice", getVoiceFile());
        config.addSource(DiscordSRV.class, "linking", getLinkingFile());
        config.addSource(DiscordSRV.class, "synchronization", getSynchronizationFile());
        config.addSource(DiscordSRV.class, "alerts", getAlertsFile());
        String languageCode = System.getProperty("user.language", "en").toUpperCase(Locale.ROOT);
        Language language = null;
        try {
            Language lang = Language.valueOf(languageCode);
            if (config.isLanguageAvailable(lang)) {
                language = lang;
            } else {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            logger.info("Unknown user language " + languageCode + ".");
            logger.info("If you fluently speak " + languageCode + " as well as English, see the GitHub repo to translate it!");
        }
        if (language == null) language = Language.EN;
        config.setLanguage(language);
        try {
            config.saveAllDefaults();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save default config files", e);
        }
        try {
            config.loadAll();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config", e);
        }
        String forcedLanguage = config.getString("ForcedLanguage");
        if (StringUtils.isNotBlank(forcedLanguage) && !forcedLanguage.equalsIgnoreCase("none")) {
            Arrays.stream(Language.values())
                    .filter(lang -> lang.getCode().equalsIgnoreCase(forcedLanguage) ||
                            lang.getName().equalsIgnoreCase(forcedLanguage)
                    )
                    .findFirst().ifPresent(config::setLanguage);
        }
    }

    /**
     * @return whether DiscordSRV is enabled (started and not disabled due to an error)
     */
    public boolean isEnabled() {
        return enabled;
    }

    public String getVersion() {
        return version;
    }

    /**
     * Called by the platform when the server is starting
     */
    public void onEnable() {
        if (++DebugUtil.initializationCount > 1) {
            DiscordSRV.error(LangUtil.InternalMessage.PLUGIN_RELOADED.toString());
        }
        enabled = true;
        shuttingDown = false;
        requireLinkModule = new RequireLinkModule();

        ConfigUtil.migrate();
        ConfigUtil.logMissingOptions();
        DiscordSRV.debug("Language is " + config.getLanguage().getName());

        Thread initThread = new Thread(this::init, "DiscordSRV - Initialization");
        initThread.setUncaughtExceptionHandler((t, e) -> {
            disablePlugin();
            error(e);
            logger.severe("DiscordSRV failed to load properly: " + e.getMessage() + ". Can't figure it out? Go to https://github.com/mcm/DiscordSRV-ReForged/issues for help");
        });
        initThread.start();
    }

    /**
     * Marks DiscordSRV as disabled. Mods can't be unloaded, so DiscordSRV stays loaded but inactive.
     */
    public void disablePlugin() {
        enabled = false;
    }

    public void init() {

        // shutdown previously existing jda if plugin gets reloaded
        if (jda != null) try { jda.shutdown(); jda = null; } catch (Exception e) { error(e); }

        reloadAllowedMentions();

        // add log4j filter for JDA messages
        if (jdaFilter == null) {
            try {
                jdaFilter = new JdaFilter();
                ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).addFilter(jdaFilter);
                debug("JdaFilter applied");
            } catch (Throwable e) {
                jdaFilter = null;
                error("Failed to attach JDA message filter to root logger", e);
            }
        }

        if (Debug.JDA.isVisible()) {
            LoggerContext context = ((LoggerContext) LogManager.getContext(false));
            context.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME).setLevel(Level.ALL);
            context.updateLoggers();
        }

        if (Debug.JDA_REST_ACTIONS.isVisible()) {
            RestAction.setPassContext(true);
        }

        // Limit okhttp to 20 concurrent requests to avoid hogging every available thread
        Dispatcher dispatcher = new Dispatcher(
                new ThreadPoolExecutor(
                        2, 20, 5, TimeUnit.SECONDS,
                        new SynchronousQueue<>(), runnable -> {
                            Thread thread = new Thread(runnable, "DiscordSRV - OkHttp Dispatcher");
                            thread.setDaemon(true);
                            return thread;
                        })
        );
        dispatcher.setMaxRequests(20);
        dispatcher.setMaxRequestsPerHost(20); // most requests are to discord.com
        ConnectionPool connectionPool = new ConnectionPool(5, 10, TimeUnit.SECONDS);

        String proxyHost = config.getString("ProxyHost");
        int proxyPort = config.getInt("ProxyPort");
        String authUser = config.getString("ProxyUser");
        String authPassword = config.getString("ProxyPassword");

        WebSocketFactory websocketFactory = new WebSocketFactory()
                .setDualStackMode(DualStackMode.IPV4_ONLY);

        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                // more lenient timeouts (normally 10 seconds for these 3)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS);
        if (config.getBooleanElse("NoopHostnameVerifier", false)) {
            httpClientBuilder.hostnameVerifier((hostname, sslSession) -> true);
        }

        if (!proxyHost.isEmpty() && !proxyHost.equals("example.com")) {
            try {
                // This had to be set to empty string to avoid issue with basic auth
                // Reference: https://stackoverflow.com/questions/41806422/java-web-start-unable-to-tunnel-through-proxy-since-java-8-update-111
                System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost.trim(), proxyPort));
                httpClientBuilder.proxy(proxy);

                ProxySettings proxySettings = websocketFactory.getProxySettings();
                proxySettings.setHost(proxyHost.trim());
                proxySettings.setPort(proxyPort);

                if (!authPassword.isEmpty()) {
                    String trimmedUsername = authUser.trim();
                    String trimmedPassword = authPassword.trim();

                    httpClientBuilder.proxyAuthenticator((route, response) -> {
                        String credential = Credentials.basic(trimmedUsername, trimmedPassword);
                        return response.request().newBuilder().header("Proxy-Authorization", credential).build();
                    });

                    proxySettings.setCredentials(trimmedUsername, trimmedPassword);
                }
            } catch (Exception e) {
                DiscordSRV.error("Failed to generate a proxy from config options.", e);
            }
        }

        OkHttpClient httpClient = httpClientBuilder.build();

        // set custom RestAction failure handler
        RestAction.setDefaultFailure(throwable -> {
            if (shuttingDown) {
                Throwable t = throwable;
                while (t != null) {
                    if (t instanceof InterruptedException || t instanceof InterruptedIOException) {
                        // Ignore interrupts when shutting down
                        return;
                    }
                    t = t.getCause();
                }
            }

            if (throwable instanceof HierarchyException) {
                DiscordSRV.error("DiscordSRV failed to perform an action due to being lower in hierarchy than the action's target: " + throwable.getMessage());
            } else if (throwable instanceof PermissionException) {
                DiscordSRV.error("DiscordSRV failed to perform an action because the bot is missing the " + ((PermissionException) throwable).getPermission().name() + " permission: " + throwable.getMessage());
            } else if (throwable instanceof RateLimitedException) {
                DiscordSRV.error("DiscordSRV encountered rate limiting. If you are running multiple DiscordSRV instances on the same token, this is considered API abuse and risks your server being IP banned from Discord. Make one bot per server.");
            } else if (throwable instanceof ErrorResponseException) {
                if (((ErrorResponseException) throwable).getErrorCode() == 50013) {
                    // Missing Permissions, too bad we don't know which one
                    DiscordSRV.error("DiscordSRV received a permission error response (50013) from Discord. Unfortunately the specific error isn't provided in that response.");
                    DiscordSRV.debug(Debug.JDA_REST_ACTIONS, throwable.getCause() != null ? throwable.getCause() : throwable);
                    return;
                }

                Throwable cause = throwable.getCause();
                if (cause instanceof InterruptedIOException && jda != null) {
                    JDA.Status status = jda.getStatus();
                    if (status == JDA.Status.SHUTDOWN || status == JDA.Status.SHUTTING_DOWN) {
                        // Ignore InterruptedIOException's during shutdown, we can't hold up the server from stopping forever,
                        // so some requests are cancelled during shutdown. Logging errors for those request failures isn't important.
                        return;
                    }
                }
                DiscordSRV.error("DiscordSRV encountered an unknown Discord error: " + throwable.getMessage());
            } else {
                DiscordSRV.error("DiscordSRV encountered an unknown exception: " + throwable.getMessage() + "\n" + ExceptionUtils.getStackTrace(throwable));
            }

            if (Debug.JDA_REST_ACTIONS.isVisible() && throwable.getCause() != null) {
                error(throwable.getCause());
            }
        });

        File tokenFile = new File(getDataFolder(), ".token");
        String token;
        if (StringUtils.isNotBlank(System.getProperty("DISCORDSRV_TOKEN"))) {
            token = System.getProperty("DISCORDSRV_TOKEN");
            DiscordSRV.debug("Using bot token supplied from JVM property DISCORDSRV_TOKEN");
        } else if (StringUtils.isNotBlank(System.getenv("DISCORDSRV_TOKEN"))) {
            token = System.getenv("DISCORDSRV_TOKEN");
            DiscordSRV.debug("Using bot token supplied from environment variable DISCORDSRV_TOKEN");
        } else if (tokenFile.exists()) {
            try {
                token = new String(Files.readAllBytes(tokenFile.toPath()), StandardCharsets.UTF_8);
                DiscordSRV.debug("Using bot token supplied from " + tokenFile.getPath());
            } catch (IOException e) {
                error(".token file could not be read: " + e.getMessage());
                token = null;
            }
        } else {
            token = config.getString("BotToken");
            DiscordSRV.debug("Using bot token supplied from config");
        }

        if (StringUtils.isBlank(token) || "BOTTOKEN".equalsIgnoreCase(token)) {
            disablePlugin();
            error("No bot token has been set in the config; a bot token is required to connect to Discord.");
            invalidBotToken = true;
            return;
        } else if (token.length() < 59) {
            disablePlugin();
            error("An invalid length bot token (" + token.length() + ") has been set in the config; a valid bot token is required to connect to Discord."
                    + (token.length() == 32 ? " Did you copy the \"Client Secret\" instead of the \"Bot Token\" into the config?" : ""));
            invalidBotToken = true;
            return;
        } else {
            // remove invalid characters
            token = token.replaceAll("[^\\w\\d-_.]", "");
        }

        callbackThreadPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors(), pool -> {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("DiscordSRV - JDA Callback " + worker.getPoolIndex());
            return worker;
        }, null, true);

        // log in to discord
        if (config.getBooleanElse("EnablePresenceInformation", false)) {
            DiscordSRV.api.requireIntent(GatewayIntent.GUILD_PRESENCES);
            DiscordSRV.api.requireCacheFlag(CacheFlag.ACTIVITY);
            DiscordSRV.api.requireCacheFlag(CacheFlag.CLIENT_STATUS);
        }
        try {
            // see ApiManager for our default intents & cache flags
            jda = JDABuilder.create(token, api.getIntents())
                    // we disable anything that isn't enabled (everything is enabled by default)
                    .disableCache(Arrays.stream(CacheFlag.values()).filter(cacheFlag -> !api.getCacheFlags().contains(cacheFlag)).collect(Collectors.toList()))
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setCallbackPool(callbackThreadPool, false)
                    .setWebsocketFactory(websocketFactory)
                    .setHttpClient(httpClient)
                    .setAutoReconnect(true)
                    .setBulkDeleteSplittingEnabled(false)
                    .setEnableShutdownHook(false)
                    .addEventListeners(new DiscordBanListener())
                    .addEventListeners(new DiscordChatListener())
                    .addEventListeners(new DiscordConsoleListener())
                    .addEventListeners(new DiscordAccountLinkListener())
                    .addEventListeners(new DiscordDisconnectListener())
                    .addEventListeners(api)
                    .addEventListeners(groupSynchronizationManager)
                    .setContextEnabled(false)
                    .build();
            jda.awaitReady(); // let JDA be assigned as soon as we can, but wait until it's ready

            for (Guild guild : jda.getGuilds()) {
                guild.retrieveOwner().queue();
                guild.loadMembers()
                        .onSuccess(members -> DiscordSRV.debug("Loaded " + members.size() + " members in guild " + guild))
                        .onError(throwable -> DiscordSRV.error("Failed to retrieve members of guild " + guild, throwable))
                        .get(); // block DiscordSRV startup until members are loaded
            }
        } catch (InvalidTokenException e) {
            disablePlugin();
            invalidBotToken = true;
            DiscordDisconnectListener.printDisconnectMessage(true, "The bot token is invalid");
            return;
        } catch (Exception e) {
            if (e instanceof IllegalStateException && "Was shutdown trying to await status".equals(e.getMessage())) {
                // already logged by JDA
                disablePlugin();
                return;
            }
            disablePlugin();
            DiscordSRV.error("An unknown error occurred building JDA...", e);
            return;
        }

        // start presence updater thread
        if (presenceUpdater != null && presenceUpdater.getState() != Thread.State.NEW) presenceUpdater.interrupt();
        presenceUpdater = new PresenceUpdater();
        presenceUpdater.start();

        // start nickname updater thread
        if (nicknameUpdater != null && nicknameUpdater.getState() != Thread.State.NEW) nicknameUpdater.interrupt();
        nicknameUpdater = new NicknameUpdater();
        nicknameUpdater.start();

        // show warning if bot wasn't in any guilds
        if (jda.getGuilds().isEmpty()) {
            DiscordSRV.error(LangUtil.InternalMessage.BOT_NOT_IN_ANY_SERVERS);
            DiscordSRV.error(jda.getInviteUrl(Permission.ADMINISTRATOR));
            return;
        }

        // see if console channel exists; if it does, tell user where it's been assigned & add console appender
        TextChannel consoleChannel = getConsoleChannel();
        if (consoleChannel != null) {
            DiscordSRV.info(LangUtil.InternalMessage.CONSOLE_FORWARDING_ASSIGNED_TO_CHANNEL + " " + consoleChannel);
            try {
                consoleAppender = new ChannelLoggingHandler(() -> {
                    TextChannel textChannel = DiscordSRV.getPlugin().getConsoleChannel();
                    return textChannel != null && textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND) ? textChannel : null;
                });
                consoleAppender.attach();
                consoleAppender.schedule();
            } catch (Throwable t) {
                consoleAppender = null;
                error("Failed to attach the console channel log appender", t);
            }
        } else {
            DiscordSRV.info(LangUtil.InternalMessage.NOT_FORWARDING_CONSOLE_OUTPUT.toString());
        }

        reloadChannels();
        reloadRegexes();
        reloadRoleAliases();

        // update slash commands once everything has had a chance to register
        SchedulerUtil.runTaskLaterAsynchronously(api::updateSlashCommands, 20);

        // warn if the console channel is connected to a chat channel
        if (getMainTextChannel() != null && getConsoleChannel() != null && getMainTextChannel().getId().equals(getConsoleChannel().getId())) DiscordSRV.warning(LangUtil.InternalMessage.CONSOLE_CHANNEL_ASSIGNED_TO_LINKED_CHANNEL);

        // send server startup message
        SchedulerUtil.runTaskLaterAsynchronously(() -> DiscordUtil.queueMessage(
                getOptionalTextChannel("status"),
                PlaceholderUtil.replacePlaceholdersToDiscord(LangUtil.Message.SERVER_STARTUP_MESSAGE.toString()),
                true
        ), 20);

        // extra enabled check before doing game stuff
        if (!isEnabled()) return;

        // start server watchdog
        if (serverWatchdog != null && serverWatchdog.getState() != Thread.State.NEW) serverWatchdog.interrupt();
        serverWatchdog = new ServerWatchdog();
        serverWatchdog.start();

        // load account links
        if (JdbcAccountLinkManager.shouldUseJdbc()) {
            try {
                accountLinkManager = new JdbcAccountLinkManager();
                ((JdbcAccountLinkManager) accountLinkManager).migrateFile();
            } catch (SQLException e) {
                StringBuilder stringBuilder = new StringBuilder("JDBC account link backend failed to initialize: ");

                Throwable selected = e;
                do {
                    stringBuilder.append("\n").append("Caused by: ").append(selected instanceof java.net.UnknownHostException ? "UnknownHostException" : ExceptionUtils.getMessage(selected));
                    selected = selected.getCause();
                } while (selected != null);

                String message = stringBuilder.toString()
                        .replace(config.getString("Experiment_JdbcAccountLinkBackend"), "<jdbc url>")
                        .replace(config.getString("Experiment_JdbcUsername"), "<jdbc username>");
                if (!StringUtils.isEmpty(config.getString("Experiment_JdbcPassword"))) {
                    message = message.replace(config.getString("Experiment_JdbcPassword"), "");
                }

                for (String line : message.split("\n")) {
                    DiscordSRV.warning(line);
                }
                DiscordSRV.warning("Account link manager falling back to file backend");
                accountLinkManager = new AppendOnlyFileAccountLinkManager();
            }
        } else {
            accountLinkManager = new AppendOnlyFileAccountLinkManager();
        }
        registerGameListener(accountLinkManager);

        // register game listeners
        registerGameListener(new PlayerDeathListener());
        registerGameListener(new PlayerJoinLeaveListener());
        registerGameListener(new PlayerAdvancementDoneListener());
        registerGameListener(new PlayerChatListener());
        registerGameListener(requireLinkModule);

        // ban synchronization (Minecraft -> Discord)
        banSynchronizer = new BanSynchronizer();
        banSynchronizer.start();

        // LuckPerms (permissions, groups & contexts)
        if (platformInstance.isModLoaded("luckperms")) {
            try {
                LuckPermsHook.enable();
                DiscordSRV.info(LangUtil.InternalMessage.PLUGIN_HOOK_ENABLING.toString().replace("{plugin}", "LuckPerms"));
            } catch (Throwable t) {
                error("Failed to hook LuckPerms", t);
            }
        }

        // start channel topic updater
        if (channelTopicUpdater != null && channelTopicUpdater.getState() != Thread.State.NEW) channelTopicUpdater.interrupt();
        channelTopicUpdater = new ChannelTopicUpdater();
        channelTopicUpdater.start();

        // start channel updater
        if (channelUpdater != null && channelUpdater.getState() != Thread.State.NEW) channelUpdater.interrupt();
        channelUpdater = new ChannelUpdater();
        channelUpdater.start();

        // start the group synchronization task
        if (isGroupRoleSynchronizationEnabled()) {
            int cycleTime = DiscordSRV.config().getInt("GroupRoleSynchronizationCycleTime") * 20 * 60;
            if (cycleTime < 20 * 60) cycleTime = 20 * 60;
            try {
                groupSynchronizationManager.resync(GroupSynchronizationManager.SyncDirection.AUTHORITATIVE, GroupSynchronizationManager.SyncCause.TIMER);
            } catch (Exception e) {
                error("Failed to resync\n" + ExceptionUtils.getMessage(e));
            }
            registerGameListener(groupSynchronizationManager);
            SchedulerUtil.runTaskTimerAsynchronously(
                    () -> groupSynchronizationManager.resync(
                            GroupSynchronizationManager.SyncDirection.AUTHORITATIVE,
                            GroupSynchronizationManager.SyncCause.TIMER
                    ),
                    cycleTime,
                    cycleTime
            );
        }

        voiceModule = new VoiceModule();

        alertListener = new AlertListener();
        jda.addEventListener(alertListener);
        api.subscribe(alertListener);
        registerGameListener(alertListener);

        // set ready status
        if (jda.getStatus() == JDA.Status.CONNECTED) {
            isReady = true;
            api.callEvent(new DiscordReadyEvent());
        }
    }

    /**
     * Called by the platform when the server is stopping
     */
    public void onDisable() {
        shuttingDown = true;

        final long shutdownStartTime = System.currentTimeMillis();

        // prepare the shutdown message
        String shutdownFormat = LangUtil.Message.SERVER_SHUTDOWN_MESSAGE.toString();
        if (Pattern.compile("%[^%]+%").matcher(shutdownFormat).find()) {
            shutdownFormat = PlaceholderUtil.replacePlaceholdersToDiscord(shutdownFormat);
        }

        final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "DiscordSRV - Shutdown"));
        try {
            String finalShutdownFormat = shutdownFormat;
            executor.invokeAll(Collections.singletonList(() -> {
                // set server shutdown topics if enabled
                if (config().getBoolean("ChannelTopicUpdaterChannelTopicsAtShutdownEnabled")) {
                    String time = TimeUtil.timeStamp();
                    String serverVersion = platformInstance.getServerVersion();
                    String totalPlayers = Integer.toString(getTotalPlayerCount());
                    String shutdownTimestamp = Long.toString(System.currentTimeMillis() / 1000);
                    DiscordUtil.setTextChannelTopic(
                            getMainTextChannel(),
                            LangUtil.Message.CHAT_CHANNEL_TOPIC_AT_SERVER_SHUTDOWN.toString()
                                    .replaceAll("%time%|%date%", time)
                                    .replace("%serverversion%", serverVersion)
                                    .replace("%totalplayers%", totalPlayers)
                                    .replace("%timestamp%", shutdownTimestamp)
                    );
                    DiscordUtil.setTextChannelTopic(
                            getConsoleChannel(),
                            LangUtil.Message.CONSOLE_CHANNEL_TOPIC_AT_SERVER_SHUTDOWN.toString()
                                    .replaceAll("%time%|%date%", time)
                                    .replace("%serverversion%", serverVersion)
                                    .replace("%totalplayers%", totalPlayers)
                                    .replace("%timestamp%", shutdownTimestamp)
                    );
                }

                if (channelUpdater != null) {
                    for (ChannelUpdater.UpdaterChannel updaterChannel : channelUpdater.getUpdaterChannels()) {
                        updaterChannel.updateToShutdownFormat();
                    }
                }

                // we're no longer ready
                isReady = false;

                // unregister game listeners
                gameListeners.clear();

                // shutdown scheduler tasks
                SchedulerUtil.cancelTasks();

                // stop alerts
                if (alertListener != null) alertListener.unregister();

                // shut down voice module
                if (voiceModule != null) voiceModule.shutdown();

                // unhook LuckPerms
                LuckPermsHook.disable();

                // stop ban synchronization
                if (banSynchronizer != null) banSynchronizer.shutdown();

                // kill channel topic updater
                if (channelTopicUpdater != null) channelTopicUpdater.interrupt();

                // kill channel updater
                if (channelUpdater != null) channelUpdater.interrupt();

                // kill presence updater
                if (presenceUpdater != null) presenceUpdater.interrupt();

                // kill nickname updater
                if (nicknameUpdater != null) nicknameUpdater.interrupt();

                // kill server watchdog
                if (serverWatchdog != null) serverWatchdog.interrupt();

                // shutdown the console appender
                if (consoleAppender != null) consoleAppender.shutdown();

                // remove the jda filter
                if (jdaFilter != null) {
                    try {
                        LoggerContext context = (LoggerContext) LogManager.getContext(false);
                        context.getConfiguration().getRootLogger().removeFilter(jdaFilter);
                        ((org.apache.logging.log4j.core.Logger) LogManager.getRootLogger()).get().removeFilter(jdaFilter);
                        jdaFilter = null;
                        debug("JdaFilter removed");
                    } catch (Throwable t) {
                        logger.warning("Could not remove JDA Filter: " + t);
                    }
                }

                // Clear JDA listeners
                if (jda != null) jda.getEventManager().getRegisteredListeners().forEach(listener -> jda.getEventManager().unregister(listener));

                // send server shutdown message
                DiscordUtil.sendMessageBlocking(getOptionalTextChannel("status"), finalShutdownFormat, true);

                // try to shut down jda gracefully
                if (jda != null) {
                    CompletableFuture<Void> shutdownTask = new CompletableFuture<>();
                    jda.addEventListener(new ListenerAdapter() {
                        @Override
                        public void onShutdown(@NotNull ShutdownEvent event) {
                            shutdownTask.complete(null);
                        }
                    });
                    jda.shutdownNow();
                    jda = null;
                    try {
                        shutdownTask.get(5, TimeUnit.SECONDS);
                    } catch (TimeoutException e) {
                        logger.warning("JDA took too long to shut down, skipping");
                    }
                }

                if (callbackThreadPool != null) callbackThreadPool.shutdownNow();

                DiscordSRV.info(LangUtil.InternalMessage.SHUTDOWN_COMPLETED.toString()
                        .replace("{ms}", String.valueOf(System.currentTimeMillis() - shutdownStartTime))
                );

                return null;
            }), 15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            error(e);
        }
        executor.shutdownNow();
        enabled = false;
    }

    /**
     * Handles the /discord command (aliased /discordsrv)
     *
     * @param sender the sender of the command
     * @param args the arguments, not including the command name itself
     */
    public void onCommand(CommandSender sender, String[] args) {
        if (!isEnabled() || jda == null) {
            if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
                commandManager.handle(sender, args[0], Arrays.stream(args).skip(1).toArray(String[]::new));
                return;
            }

            if (invalidBotToken) {
                sender.sendMessage(Component.text("DiscordSRV is disabled: your bot token is invalid.", NamedTextColor.RED));
                sender.sendMessage(Component.text("Please enter a valid token into DiscordSRV's config.yml (config/discordsrv/config.yml) and restart your server to get DiscordSRV to work.", NamedTextColor.RED));
            } else if (DiscordDisconnectListener.mostRecentCloseCode == CloseCode.DISALLOWED_INTENTS) {
                sender.sendMessage(Component.text("DiscordSRV is disabled: your DiscordSRV bot is lacking required intents.", NamedTextColor.RED));
                sender.sendMessage(Component.text("Please check your server log (logs/latest.log) for a extended error message during DiscordSRV's startup to get DiscordSRV to work.", NamedTextColor.RED));
            } else if (isEnabled()) {
                sender.sendMessage(Component.text("DiscordSRV is still connecting to Discord, try again in a moment.", NamedTextColor.RED));
            } else {
                sender.sendMessage(Component.text("DiscordSRV is disabled, check your server log (logs/latest.log) for errors during DiscordSRV's startup to find out why", NamedTextColor.RED));
            }
            return;
        }

        if (args.length == 0) {
            commandManager.handle(sender, null, new String[] {});
        } else {
            commandManager.handle(sender, args[0], Arrays.stream(args).skip(1).toArray(String[]::new));
        }
    }

    /**
     * Tab completion for the /discord command
     *
     * @param sender the sender
     * @param args the arguments typed so far, not including the command name (the last element may be empty)
     * @return suggestions for the last argument
     */
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (!isEnabled() || args.length != 1) return Collections.emptyList();

        String command = args[0].toLowerCase(Locale.ROOT);
        List<String> suggestions = new ArrayList<>();
        for (Map.Entry<String, Method> commandPair : getCommandManager().getCommands().entrySet()) {
            if (!commandPair.getKey().toLowerCase(Locale.ROOT).startsWith(command)) continue;
            if (GamePermissionUtil.hasPermission(sender, commandPair.getValue().getAnnotation(github.scarsz.discordsrv.commands.Command.class).permission())) {
                suggestions.add(commandPair.getKey());
            }
        }
        return suggestions;
    }

    // --- game events (forwarded by the platform) ---

    /**
     * Registers a listener for game events. Replaces Bukkit's PluginManager#registerEvents.
     */
    public void registerGameListener(GameListener listener) {
        if (listener != null && !gameListeners.contains(listener)) gameListeners.add(listener);
    }

    public void unregisterGameListener(GameListener listener) {
        gameListeners.remove(listener);
    }

    private <E extends GameEvent> void dispatch(E event, java.util.function.BiConsumer<GameListener, E> handler) {
        for (GameListener listener : gameListeners) {
            try {
                handler.accept(listener, event);
            } catch (Throwable t) {
                error("Error passing " + event.getEventName() + " to " + listener.getClass().getName(), t);
            }
        }
    }

    public void callPlayerJoin(PlayerJoinEvent event) { dispatch(event, GameListener::onPlayerJoin); }
    public void callPlayerQuit(PlayerQuitEvent event) { dispatch(event, GameListener::onPlayerQuit); }
    public void callPlayerChat(PlayerChatEvent event) { dispatch(event, GameListener::onPlayerChat); }
    public void callPlayerDeath(PlayerDeathEvent event) { dispatch(event, GameListener::onPlayerDeath); }
    public void callPlayerAdvancementDone(PlayerAdvancementDoneEvent event) { dispatch(event, GameListener::onPlayerAdvancementDone); }
    public void callPlayerCommand(PlayerCommandEvent event) { dispatch(event, GameListener::onPlayerCommand); }
    public void callServerCommand(ServerCommandEvent event) { dispatch(event, GameListener::onServerCommand); }

    /**
     * Called by the platform at the end of every server tick
     */
    public void onServerTick() {
        Lag.tick();
    }

    /**
     * Called by the platform when a player attempts to log in, after the vanilla ban/whitelist checks passed.
     *
     * @param ip the address the player is connecting from (may be null)
     * @return a kick message (legacy formatted) if the player should be disallowed, otherwise null
     */
    public String checkLogin(UUID playerUuid, String playerName, String ip) {
        RequireLinkModule module = requireLinkModule;
        return module != null ? module.check(playerName, playerUuid, ip) : null;
    }

    // --- message processing ---

    /**
     * Gets the alias for the given world. World aliases were provided by Multiverse-Core on Bukkit,
     * on NeoForge this returns the world (dimension) name as-is.
     */
    public String getWorldAlias(String world) {
        return world;
    }

    public void processChatMessage(GamePlayer player, String message, String channel, boolean cancelled) {
        this.processChatMessage(player, MessageUtil.toComponent(message, true), channel, cancelled, null);
    }

    public void processChatMessage(GamePlayer player, Component message, String channel, boolean cancelled) {
        this.processChatMessage(player, message, channel, cancelled, null);
    }

    public void processChatMessage(GamePlayer player, Component message, String channel, boolean cancelled, GameEvent event) {
        // log debug message to notify that a chat message was being processed
        debug(Debug.MINECRAFT_TO_DISCORD, "Chat message received, canceled: " + cancelled + ", channel: " + channel);

        if (player == null) {
            debug(Debug.MINECRAFT_TO_DISCORD, "Received chat message was from a null sender, not processing message");
            return;
        }

        // return if player doesn't have permission
        if (!GamePermissionUtil.hasPermission(player, "discordsrv.chat")) {
            debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord due to lack of in-game permission (discordsrv.chat)");
            return;
        }

        // return if event canceled
        if (config().getBooleanElse("RespectChatPlugins", true) && cancelled) {
            debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord because the chat event was canceled");
            return;
        }

        // return if should not send in-game chat
        if (!config().getBoolean("DiscordChatChannelMinecraftToDiscord")) {
            debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord because DiscordChatChannelMinecraftToDiscord is false");
            return;
        }

        // return if doesn't match prefix filter
        String prefix = config().getString("DiscordChatChannelPrefixRequiredToProcessMessage");
        boolean blacklist = config.getBoolean("DiscordChatChannelPrefixActsAsBlacklist");

        String legacy = MessageUtil.toLegacy(message);
        if (MessageUtil.strip(legacy).startsWith(prefix) == blacklist) {
            debug(Debug.MINECRAFT_TO_DISCORD, "User " + player.getName() + " sent a message but it was not delivered to Discord because " + (blacklist ? "the message started with \"" + prefix : "the message didn't start with \"" + prefix) + "\" (DiscordChatChannelPrefixRequiredToProcessMessage): \"" + legacy + "\"");
            return;
        }

        GameChatMessagePreProcessEvent preEvent = api.callEvent(new GameChatMessagePreProcessEvent(channel, message, player, event));
        if (preEvent.isCancelled()) {
            debug(Debug.MINECRAFT_TO_DISCORD, "GameChatMessagePreProcessEvent was cancelled, message send aborted");
            return;
        }
        channel = preEvent.getChannel(); // update channel from event in case any listeners modified it
        message = preEvent.getMessageComponent(); // update message from event in case any listeners modified it

        String userPrimaryGroup = LuckPermsHook.getPrimaryGroup(player.getUniqueId());
        boolean hasGoodGroup = StringUtils.isNotBlank(userPrimaryGroup);

        // capitalize the first letter of the user's primary group to look neater
        if (hasGoodGroup) userPrimaryGroup = userPrimaryGroup.substring(0, 1).toUpperCase() + userPrimaryGroup.substring(1);
        else userPrimaryGroup = "";

        boolean reserializer = DiscordSRV.config().getBoolean("Experiment_MCDiscordReserializer_ToDiscord");
        boolean webhookMessageDelivery = config().getBoolean("Experiment_WebhookChatMessageDelivery");

        String discordMessageContent;
        if (reserializer) {
            discordMessageContent = MessageUtil.reserializeToDiscord(message);
        } else {
            discordMessageContent = MessageUtil.strip(MessageUtil.toLegacy(message));
        }

        // Modify the message's content with the declared Regexes
        if (webhookMessageDelivery) {
            discordMessageContent = processRegex(discordMessageContent);
            if (discordMessageContent == null) return;
        }

        if (config().getBoolean("DiscordChatChannelTranslateMentions")) {
            discordMessageContent = DiscordUtil.convertMentionsFromNames(discordMessageContent, getMainGuild());
        } else {
            discordMessageContent = discordMessageContent.replace("@", "@​"); // zero-width space
        }

        String processedMessage = discordMessageContent;

        if (!webhookMessageDelivery) {
            // If webhook delivery is not enable, we add all the placeholders
            String username = player.getName();
            if (!reserializer) username = DiscordUtil.escapeMarkdown(username);

            String displayName = MessageUtil.strip(player.getDisplayName());

            // Replace the internal placeholders in the message pattern
            String discordMessagePattern = (hasGoodGroup
                    ? LangUtil.Message.CHAT_TO_DISCORD.toString()
                    : LangUtil.Message.CHAT_TO_DISCORD_NO_PRIMARY_GROUP.toString())
                    .replace("%displayname%", DiscordUtil.escapeMarkdown(displayName))
                    .replace("%displaynamenoescapes%", displayName)
                    .replace("%username%", username)
                    .replaceAll("%time%|%date%", TimeUtil.timeStamp())
                    .replace("%channelname%", channel != null ? channel.substring(0, 1).toUpperCase() + channel.substring(1) : "")
                    .replace("%primarygroup%", userPrimaryGroup)
                    .replace("%usernamenoescapes%", MessageUtil.strip(player.getName()))
                    .replace("%world%", player.getWorldName())
                    .replace("%worldalias%", MessageUtil.strip(getWorldAlias(player.getWorldName())));
            // Replace the placeholders in the message pattern
            discordMessagePattern = PlaceholderUtil.replacePlaceholdersToDiscord(discordMessagePattern, player);

            // Strip the final message from any rouge color/style codes.
            if (!reserializer) {
                discordMessagePattern = MessageUtil.strip(discordMessagePattern);
            }

            // Replace the message after to avoid replacing rouge placeholders inside of the message's content
            discordMessagePattern = discordMessagePattern
                    .replace("%message%", discordMessageContent);

            discordMessagePattern = processRegex(discordMessagePattern);
            if (discordMessagePattern == null) return;

            processedMessage = discordMessagePattern;
        }

        // Send the post process message event
        GameChatMessagePostProcessEvent postEvent = api.callEvent(new GameChatMessagePostProcessEvent(channel, processedMessage, player, preEvent.isCancelled(), event));
        if (postEvent.isCancelled()) {
            debug(Debug.MINECRAFT_TO_DISCORD, "GameChatMessagePostProcessEvent was cancelled, message send aborted");
            return;
        }
        channel = postEvent.getChannel(); // update channel from event in case any listeners modified it
        processedMessage = postEvent.getProcessedMessage(); // update message from event in case any listeners modified it

        // If the channel is null, replace it with the global channel
        if (channel == null) channel = getOptionalChannel("global");
        TextChannel destinationChannel = getDestinationTextChannelForGameChannelName(channel);

        if (!webhookMessageDelivery) {
            DiscordUtil.sendMessage(destinationChannel, processedMessage);
        } else {
            if (destinationChannel == null) {
                debug(Debug.MINECRAFT_TO_DISCORD, "Failed to find Discord channel to forward message from game channel " + channel);
                return;
            }

            if (!DiscordUtil.checkPermission(destinationChannel, Permission.MANAGE_WEBHOOKS)) {
                DiscordSRV.error("Couldn't deliver chat message as webhook because the bot lacks the \"Manage Webhooks\" permission.");
                return;
            }

            WebhookUtil.deliverMessage(destinationChannel, player, processedMessage);
        }
    }

    private String processRegex(String discordMessage) {
        for (Map.Entry<Pattern, String> entry : getGameRegexes().entrySet()) {
            discordMessage = entry.getKey().matcher(discordMessage).replaceAll(entry.getValue());
            if (StringUtils.isBlank(discordMessage)) {
                DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Not processing Minecraft message because it was cleared by a filter: " + entry.getKey().pattern());
                return null;
            }
        }
        return discordMessage;
    }

    @Deprecated
    public void broadcastMessageToMinecraftServer(String channel, String message, User author) {
        // apply placeholders
        GamePlayer authorPlayer = null;
        UUID authorLinkedUuid = getAccountLinkManager().getUuid(author.getId());
        if (authorLinkedUuid != null) authorPlayer = platformInstance.getPlayer(authorLinkedUuid);

        message = PlaceholderUtil.replacePlaceholders(message, authorPlayer);

        broadcastMessageToMinecraftServer(channel, MessageUtil.toComponent(message), author);
    }

    public void broadcastMessageToMinecraftServer(String channel, Component message, User author) {
        // there are no chat channel plugins on NeoForge: only the global channel is broadcast in game
        if (channel != null && !channel.equalsIgnoreCase("global") && !channel.equals(getMainChatChannel())) return;

        DiscordGuildMessagePreBroadcastEvent preBroadcastEvent = api.callEvent(new DiscordGuildMessagePreBroadcastEvent
                (author, channel, message, PlayerUtil.getOnlinePlayers()));
        message = preBroadcastEvent.getMessage();
        channel = preBroadcastEvent.getChannel();
        MessageUtil.sendMessage(preBroadcastEvent.getRecipients(), message);
        PlayerUtil.notifyPlayersOfMentions(null, MessageUtil.toLegacy(message));

        // hacky fix to avoid api breakage :/
        message = message.replaceText(TextReplacementConfig.builder()
                .matchLiteral("%channelcolor%")
                .replacement("")
                .build());

        api.callEvent(new DiscordGuildMessagePostBroadcastEvent(channel, message));

        if (DiscordSRV.config().getBoolean("DiscordChatChannelBroadcastDiscordMessagesToConsole")) {
            DiscordSRV.info(LangUtil.InternalMessage.CHAT + ": " + MessageUtil.strip(MessageUtil.toLegacy(message).replace("»", ">")));
        }
    }

    /**
     * Triggers a join message for the given player to be sent to Discord. Useful for fake join messages.
     *
     * @param player the player
     * @param joinMessage the join message (as seen in game)
     * @see #sendLeaveMessage(GamePlayer, String)
     */
    public void sendJoinMessage(GamePlayer player, String joinMessage) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");

        MessageFormat messageFormat = player.hasPlayedBefore()
                ? getMessageFromConfiguration("MinecraftPlayerJoinMessage")
                : getMessageFromConfiguration("MinecraftPlayerFirstJoinMessage");
        sendPlayerMessage(player, joinMessage, messageFormat, "join");
    }

    /**
     * Triggers a leave message for the given player to be sent to Discord. Useful for fake leave messages.
     *
     * @param player the player
     * @param quitMessage the leave/quit message (as seen in game)
     * @see #sendJoinMessage(GamePlayer, String)
     */
    public void sendLeaveMessage(GamePlayer player, String quitMessage) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");

        MessageFormat messageFormat = getMessageFromConfiguration("MinecraftPlayerLeaveMessage");
        sendPlayerMessage(player, quitMessage, messageFormat, "leave");
    }

    private void sendPlayerMessage(GamePlayer player, String gameMessage, MessageFormat messageFormat, String channelType) {
        if (messageFormat == null || !messageFormat.isAnyContent()) {
            debug("Not sending " + channelType + " message due to it being disabled");
            return;
        }

        TextChannel textChannel = getOptionalTextChannel(channelType);
        if (textChannel == null) {
            DiscordSRV.debug("Not sending " + channelType + " message, text channel is null");
            return;
        }

        final String displayName = StringUtils.isNotBlank(player.getDisplayName()) ? MessageUtil.strip(player.getDisplayName()) : "";
        final String message = StringUtils.isNotBlank(gameMessage) ? gameMessage : "";
        final String name = player.getName();
        final String avatarUrl = getAvatarUrl(player);
        final String botAvatarUrl = jda.getSelfUser().getEffectiveAvatarUrl();
        String botName = getMainGuild() != null ? getMainGuild().getSelfMember().getEffectiveName() : jda.getSelfUser().getName();

        BiFunction<String, Boolean, String> translator = (content, needsEscape) -> {
            if (content == null) return null;
            content = content
                    .replaceAll("%time%|%date%", TimeUtil.timeStamp())
                    .replace("%message%", MessageUtil.strip(needsEscape ? DiscordUtil.escapeMarkdown(message) : message))
                    .replace("%username%", needsEscape ? DiscordUtil.escapeMarkdown(name) : name)
                    .replace("%displayname%", needsEscape ? DiscordUtil.escapeMarkdown(displayName) : displayName)
                    .replace("%usernamenoescapes%", name)
                    .replace("%displaynamenoescapes%", displayName)
                    .replace("%embedavatarurl%", avatarUrl)
                    .replace("%botavatarurl%", botAvatarUrl)
                    .replace("%botname%", botName);
            content = DiscordUtil.translateEmotes(content, textChannel.getGuild());
            content = PlaceholderUtil.replacePlaceholdersToDiscord(content, player);
            return content;
        };

        MessageCreateData discordMessage = translateMessage(messageFormat, translator);
        if (discordMessage == null) return;

        String webhookName = translator.apply(messageFormat.getWebhookName(), false);
        String webhookAvatarUrl = translator.apply(messageFormat.getWebhookAvatarUrl(), false);

        if (messageFormat.isUseWebhooks()) {
            WebhookUtil.deliverMessage(textChannel, webhookName, webhookAvatarUrl,
                    discordMessage.getContent(), discordMessage.getEmbeds().stream().findFirst().orElse(null));
        } else {
            DiscordUtil.queueMessage(textChannel, discordMessage, true);
        }
    }

    public MessageFormat getMessageFromConfiguration(String key) {
        return MessageFormatResolver.getMessageFromConfiguration(config(), key);
    }

    @CheckReturnValue
    public static MessageCreateData translateMessage(MessageFormat messageFormat, BiFunction<String, Boolean, String> translator) {
        MessageCreateBuilder messageBuilder = new MessageCreateBuilder();
        Optional.ofNullable(messageFormat.getContent()).map(content -> translator.apply(content, true))
                .filter(StringUtils::isNotBlank).ifPresent(messageBuilder::setContent);

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setAuthor(
                Optional.ofNullable(messageFormat.getAuthorName())
                        .map(content -> translator.apply(content, false)).filter(StringUtils::isNotBlank).orElse(null),
                Optional.ofNullable(messageFormat.getAuthorUrl())
                        .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null),
                Optional.ofNullable(messageFormat.getAuthorImageUrl())
                        .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null)
        );
        embedBuilder.setThumbnail(Optional.ofNullable(messageFormat.getThumbnailUrl())
                .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null));
        embedBuilder.setImage(Optional.ofNullable(messageFormat.getImageUrl())
                .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null));
        embedBuilder.setDescription(Optional.ofNullable(messageFormat.getDescription())
                .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null));
        embedBuilder.setTitle(
                Optional.ofNullable(messageFormat.getTitle()).map(content -> translator.apply(content, false)).filter(StringUtils::isNotBlank).orElse(null),
                Optional.ofNullable(messageFormat.getTitleUrl()).map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null)
        );
        embedBuilder.setFooter(
                Optional.ofNullable(messageFormat.getFooterText())
                        .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null),
                Optional.ofNullable(messageFormat.getFooterIconUrl())
                        .map(content -> translator.apply(content, true)).filter(StringUtils::isNotBlank).orElse(null)
        );
        if (messageFormat.getFields() != null) messageFormat.getFields().forEach(field ->
                embedBuilder.addField(translator.apply(field.getName(), true), translator.apply(field.getValue(), true), field.isInline()));
        embedBuilder.setColor(messageFormat.getColorRaw());
        embedBuilder.setTimestamp(messageFormat.getTimestamp());
        if (!embedBuilder.isEmpty()) messageBuilder.setEmbeds(embedBuilder.build());

        return messageBuilder.isEmpty() ? null : messageBuilder.build();
    }

    public static String getAvatarUrl(String username, UUID uuid) {
        String avatarUrl = constructAvatarUrl(username, uuid, "");
        avatarUrl = PlaceholderUtil.replacePlaceholdersToDiscord(avatarUrl);
        return avatarUrl;
    }
    public static String getAvatarUrl(GamePlayer player) {
        String avatarUrl = constructAvatarUrl(player.getName(), player.getUniqueId(), player.getSkinTexture());
        avatarUrl = PlaceholderUtil.replacePlaceholdersToDiscord(avatarUrl, player);
        return avatarUrl;
    }
    private static String constructAvatarUrl(String username, UUID uuid, String texture) {
        Platform platform = getPlatform();
        boolean offline = uuid == null || PlayerUtil.uuidIsOffline(uuid);
        if (StringUtils.isNotBlank(username) && offline) {
            // resolve username to uuid
            UUID resolved = platform.getPlayerUuid(username);
            if (resolved != null) {
                uuid = resolved;
                offline = PlayerUtil.uuidIsOffline(uuid);
            }
        }
        if (StringUtils.isBlank(username) && uuid != null) {
            // resolve uuid to username
            username = platform.getPlayerName(uuid);
        }
        if (StringUtils.isBlank(texture) && uuid != null) {
            // grab texture from player if online
            GamePlayer player = platform.getPlayer(uuid);
            if (player != null) texture = player.getSkinTexture();
        }
        if (username == null) username = "";

        String avatarUrl = DiscordSRV.config().getString("AvatarUrl");
        String defaultUrl = "https://crafthead.net/helm/{uuid-nodashes}/{size}#{texture}";
        String offlineUrl = "https://crafthead.net/helm/{username}/{size}#{texture}";

        if (StringUtils.isBlank(avatarUrl)) {
            avatarUrl = !offline ? defaultUrl : offlineUrl;
        }

        if (avatarUrl.contains("://crafatar.com/")) {
            avatarUrl = !offline ? defaultUrl : offlineUrl;
            DiscordSRV.config().setRuntimeValue("AvatarUrl", avatarUrl);

            DiscordSRV.warning("Your AvatarUrl config option uses crafatar.com, which no longer allows usage with Discord. An alternative provider will be used.");
            DiscordSRV.warning("You should set your AvatarUrl (in config.yml) to an empty string (\"\") to get rid of this warning.");
        }

        if (offline && (avatarUrl.contains("{uuid}") || avatarUrl.contains("{uuid-nodashes}")) && !offlineUuidAvatarUrlNagged) {
            DiscordSRV.error("Your AvatarUrl config option contains {uuid} or {uuid-nodashes} but this server is using offline UUIDs.");
            offlineUuidAvatarUrlNagged = true;
        }

        if (username.startsWith("*") || username.startsWith(".")) {
            // geyser/floodgate add a prefix to the beginning of its usernames
            username = username.substring(1);
        }
        username = URLEncoder.encode(username, StandardCharsets.UTF_8);

        String usedBaseUrl = avatarUrl;
        avatarUrl = avatarUrl
                .replace("{texture}", texture != null ? texture : "")
                .replace("{username}", username)
                .replace("{uuid}", uuid != null ? uuid.toString() : "")
                .replace("{uuid-nodashes}", uuid != null ? uuid.toString().replace("-", "") : "")
                .replace("{size}", "128");

        DiscordSRV.debug("Constructed avatar url: " + avatarUrl + " from " + usedBaseUrl);
        DiscordSRV.debug("Avatar url is for " + (offline ? "**offline** " : "") + "uuid: " + uuid + ". The texture is: " + texture);

        return avatarUrl;
    }

    public static int getLength(MessageCreateData message) {
        StringBuilder content = new StringBuilder();
        content.append(message.getContent());

        message.getEmbeds().stream().findFirst().ifPresent(embed -> {
            if (embed.getTitle() != null) {
                content.append(embed.getTitle());
            }
            if (embed.getDescription() != null) {
                content.append(embed.getDescription());
            }
            if (embed.getAuthor() != null) {
                content.append(embed.getAuthor().getName());
            }
            for (MessageEmbed.Field field : embed.getFields()) {
                content.append(field.getName()).append(field.getValue());
            }
        });

        return content.toString().replaceAll("[^A-z]", "").length();
    }

    public List<Role> getSelectedRoles(Member member) {
        List<Role> selectedRoles;
        List<String> discordRolesSelection = DiscordSRV.config().getStringList("DiscordChatChannelRolesSelection");
        // if we have a whitelist in the config
        if (DiscordSRV.config().getBoolean("DiscordChatChannelRolesSelectionAsWhitelist")) {
            selectedRoles = member.getRoles().stream()
                    .filter(role -> discordRolesSelection.contains(DiscordUtil.getRoleName(role)) || discordRolesSelection.contains(role.getId()))
                    .collect(Collectors.toList());
        } else { // if we have a blacklist in the settings
            selectedRoles = member.getRoles().stream()
                    .filter(role -> !(discordRolesSelection.contains(DiscordUtil.getRoleName(role)) || discordRolesSelection.contains(role.getId())))
                    .collect(Collectors.toList());
        }
        selectedRoles.removeIf(role -> StringUtils.isBlank(role.getName()));
        return selectedRoles;
    }

    public Role getTopSelectedRole(Member member) {
        List<Role> selectedRoles = getSelectedRoles(member);
        if (selectedRoles.isEmpty()) return null;
        return member.getRoles().stream()
                .filter(selectedRoles::contains)
                .findFirst().orElse(null);
    }

    public Map<String, String> getGroupSynchronizables() {
        return new HashMap<>(config.getMap("GroupRoleSynchronizationGroupsAndRolesToSync"));
    }

    public Map<String, String> getCannedResponses() {
        Map<String, String> responses = new HashMap<>();
        config.getMap("DiscordCannedResponses").forEach((trigger, response) -> {
            if (StringUtils.isEmpty(trigger)) {
                DiscordSRV.debug("Skipping canned response with empty trigger");
                return;
            }
            responses.put(trigger, response);
        });
        return responses;
    }

    public static int getTotalPlayerCount() {
        return getPlatform().getTotalPlayerCount();
    }

    /**
     * @return Whether DiscordSRV group role synchronization has been enabled in the configuration.
     */
    public boolean isGroupRoleSynchronizationEnabled() {
        return isGroupRoleSynchronizationEnabled(true);
    }

    /**
     * @return Whether DiscordSRV group role synchronization has been enabled in the configuration.
     * @param checkPermissions whether to check if a permissions mod (LuckPerms) is available
     */
    public boolean isGroupRoleSynchronizationEnabled(boolean checkPermissions) {
        if (checkPermissions && !LuckPermsHook.isEnabled()) return false;
        final Map<String, String> groupsAndRolesToSync = config.getMap("GroupRoleSynchronizationGroupsAndRolesToSync");
        if (groupsAndRolesToSync.isEmpty()) return false;
        for (Map.Entry<String, String> entry : groupsAndRolesToSync.entrySet()) {
            final String group = entry.getKey();
            if (!group.isEmpty()) {
                final String roleId = entry.getValue();
                if (!(roleId.isEmpty() || roleId.replace("0", "").trim().isEmpty())) return true;
            }
        }
        return false;
    }

    public String getOptionalChannel(String name) {
        return getChannels().containsKey(name)
                ? name
                : getMainChatChannel();
    }
    public TextChannel getOptionalTextChannel(String gameChannel) {
        return getDestinationTextChannelForGameChannelName(getOptionalChannel(gameChannel));
    }

    public AccountLinkManager getAccountLinkManager() {
        return this.accountLinkManager;
    }
}
