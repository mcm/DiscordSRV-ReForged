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

package github.scarsz.discordsrv.test;

import alexh.weak.Dynamic;
import github.scarsz.discordsrv.config.DynamicConfig;
import github.scarsz.discordsrv.config.Language;
import github.scarsz.discordsrv.util.ConfigUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class DynamicConfigTest {

    private static final String[] SOURCES = {"config", "messages", "voice", "linking", "synchronization", "alerts"};

    private DynamicConfig create(File folder) throws Exception {
        DynamicConfig config = new DynamicConfig();
        for (String source : SOURCES) {
            config.addSource(DynamicConfig.class, source, new File(folder, source + ".yml"));
        }
        config.saveAllDefaults();
        config.loadAll();
        return config;
    }

    @Test
    public void allBundledConfigsParse() throws Exception {
        for (String source : SOURCES) {
            for (Language language : Language.values()) {
                String path = "/" + source + "/" + language.getCode().toLowerCase() + ".yml";
                try (InputStream stream = DynamicConfig.class.getResourceAsStream(path)) {
                    if (stream == null) continue;
                    Map<String, Object> map = DynamicConfig.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
                    assertFalse(map.isEmpty(), path + " is empty");
                }
            }
        }
    }

    @Test
    public void defaultsAreWrittenAndRead(@TempDir File folder) throws Exception {
        DynamicConfig config = create(folder);
        for (String source : SOURCES) assertTrue(new File(folder, source + ".yml").exists(), source + ".yml was not written");

        assertEquals("BOTTOKEN", config.getString("BotToken"));
        assertTrue(config.getBoolean("DiscordChatChannelDiscordToMinecraft"));
        assertEquals(256, config.getInt("DiscordChatChannelTruncateLength"));
        assertEquals("000000000000000000", config.getMap("Channels").get("global"));
        assertTrue(config.getStringList("DiscordConsoleChannelBlacklistedCommands").contains("op"));
        // nested keys
        assertFalse(config.getBoolean("Require linked account to play.Enabled"));
        assertTrue(config.getStringList("Require linked account to play.Bypass names").size() > 0);
        assertNotNull(config.getString("MinecraftPlayerJoinMessage.Embed.Color"));
        Dynamic mustBeInServer = config.dget("Require linked account to play.Must be in Discord server");
        assertTrue(mustBeInServer.isPresent());
        // missing keys
        assertEquals("", config.getString("ThisKeyDoesNotExist"));
        assertTrue(config.getBooleanElse("ThisKeyDoesNotExist", true));
        assertFalse(config.getOptionalString("ThisKeyDoesNotExist").isPresent());
    }

    @Test
    public void userValuesOverrideDefaults(@TempDir File folder) throws Exception {
        Files.write(new File(folder, "config.yml").toPath(),
                "BotToken: \"abc\"\nChannels: {\"global\": 123456789012345678}\nDiscordChatChannelTruncateLength: 10\n".getBytes(StandardCharsets.UTF_8));
        DynamicConfig config = create(folder);
        assertEquals("abc", config.getString("BotToken"));
        assertEquals("123456789012345678", config.getMap("Channels").get("global"));
        assertEquals(10, config.getInt("DiscordChatChannelTruncateLength"));
        // keys missing from the user's file fall back to the defaults
        assertEquals("!c", config.getString("DiscordChatChannelConsoleCommandPrefix"));

        config.setRuntimeValue("BotToken", "runtime");
        assertEquals("runtime", config.getString("BotToken"));
    }

    @Test
    public void versionComparison() {
        assertEquals(0, ConfigUtil.compareVersions("1.30.5", "1.30.5"));
        assertTrue(ConfigUtil.compareVersions("1.30.4", "1.30.5") < 0);
        assertTrue(ConfigUtil.compareVersions("1.30.5-neoforge.1", "1.30.5-neoforge.2") < 0);
        assertTrue(ConfigUtil.compareVersions("1.30.10", "1.30.9") > 0);
        assertTrue(ConfigUtil.compareVersions("1.30.5-SNAPSHOT", "1.30.5") == 0);
    }

}
