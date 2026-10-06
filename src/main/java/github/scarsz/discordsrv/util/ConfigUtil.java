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

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.config.DynamicConfig;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class ConfigUtil {

    public static void migrate() {
        String configVersionRaw = DiscordSRV.config().getString("ConfigVersion");
        if (configVersionRaw.contains("/")) configVersionRaw = configVersionRaw.substring(0, configVersionRaw.indexOf("/"));
        if (configVersionRaw.contains("${version}") || configVersionRaw.contains("${project.version}")) configVersionRaw = "0.0.0";

        String pluginVersionRaw = DiscordSRV.getPlugin().getVersion();
        if (configVersionRaw.equals(pluginVersionRaw)) return;

        int comparison = compareVersions(configVersionRaw, pluginVersionRaw);
        if (comparison == 0) return; // no migration necessary
        if (comparison > 0) {
            DiscordSRV.warning("You're attempting to use a higher config version than the mod. Things probably won't work correctly.");
            return;
        }

        String oldVersionName = configVersionRaw.equals("0.0.0") ? "invalidversion" : configVersionRaw;
        DiscordSRV.info("Your DiscordSRV config file was outdated; attempting migration...");

        try {
            DynamicConfig.Source configProvider = DiscordSRV.config().getProvider("config");
            DynamicConfig.Source messageProvider = DiscordSRV.config().getProvider("messages");
            DynamicConfig.Source voiceProvider = DiscordSRV.config().getProvider("voice");
            DynamicConfig.Source linkingProvider = DiscordSRV.config().getProvider("linking");
            DynamicConfig.Source synchronizationProvider = DiscordSRV.config().getProvider("synchronization");
            DynamicConfig.Source alertsProvider = DiscordSRV.config().getProvider("alerts");

            migrate("config.yml-build." + oldVersionName + ".old", DiscordSRV.getPlugin().getConfigFile(), configProvider);
            migrate("messages.yml-build." + oldVersionName + ".old", DiscordSRV.getPlugin().getMessagesFile(), messageProvider);
            migrate("voice.yml-build." + oldVersionName + ".old", DiscordSRV.getPlugin().getVoiceFile(), voiceProvider);
            migrate("linking.yml-build." + oldVersionName + ".old", DiscordSRV.getPlugin().getLinkingFile(), linkingProvider);
            migrate("synchronization.yml-build." + oldVersionName + ".old", DiscordSRV.getPlugin().getSynchronizationFile(), synchronizationProvider);
            migrate("alerts.yml-build." + oldVersionName + ".old", DiscordSRV.getPlugin().getAlertsFile(), alertsProvider);
            DiscordSRV.info("Successfully migrated configuration files to version " + pluginVersionRaw);
        } catch (Exception e) {
            DiscordSRV.error("Failed migrating configs: " + e.getMessage());
            DiscordSRV.debug(ExceptionUtils.getStackTrace(e));
        }
    }

    private static void migrate(String fromFileName, File to, DynamicConfig.Source provider) throws IOException {
        File from = new File(DiscordSRV.getPlugin().getDataFolder(), fromFileName);
        if (from.exists()) from = new File(DiscordSRV.getPlugin().getDataFolder(), fromFileName + "-" + System.currentTimeMillis());
        if (!to.exists()) {
            provider.saveDefaults();
            provider.load();
            return;
        }
        Files.move(to.toPath(), from.toPath(), StandardCopyOption.REPLACE_EXISTING);
        provider.saveDefaults();

        List<String> oldConfigLines = Arrays.stream(new String(Files.readAllBytes(from.toPath()), StandardCharsets.UTF_8).split(System.lineSeparator() + "|\n")).collect(Collectors.toList());
        List<String> newConfigLines = Arrays.stream(new String(Files.readAllBytes(to.toPath()), StandardCharsets.UTF_8).split(System.lineSeparator() + "|\n")).collect(Collectors.toList());

        Map<String, String> options = new HashMap<>();

        String option = null;
        StringBuilder optionValue = null;
        StringBuilder buffer = new StringBuilder();
        for (String line : oldConfigLines) {
            boolean blank = StringUtils.isBlank(line);
            if (line.startsWith("#") || (blank && option == null)) continue;
            if (blank || line.startsWith("}")) {
                if (optionValue != null) {
                    optionValue.append(line);
                    buffer.append('\n');
                }
                continue;
            } else if (StringUtils.isBlank(line.substring(0, 1))) {
                if (optionValue != null) {
                    optionValue.append(buffer).append('\n').append(line);
                    buffer.setLength(0);
                }
                continue;
            }

            if (option != null) {
                options.put(option, optionValue.toString());
                option = null;
                buffer.setLength(0);
            }
            String[] lineSplit = line.split(":", 2);
            if (lineSplit.length != 2) {
                if (optionValue != null) optionValue.append('\n').append(line);
                continue;
            }
            String key = lineSplit[0].trim();
            if (key.equals("ConfigVersion")) continue;
            option = key;
            String value = lineSplit[1].trim();
            if (option.equals("AvatarUrl") && value.contains("https://crafatar.com")) {
                DiscordSRV.warning("AvatarUrl config option contained \"https://crafatar.com\"; Crafatar no longer allows queries from Discord so the new default provider will be used instead.");
                value = "\"\"";
            } else if (option.equals("ProxyHost") && value.equals("\"https://example.com\"")) {
                value = "\"example.com\"";
            }
            optionValue = new StringBuilder(value);
        }
        if (optionValue != null) options.put(option, optionValue.toString());

        StringBuilder newConfig = new StringBuilder();

        boolean sameOption = false;
        StringBuilder comments = new StringBuilder();
        for (String line : newConfigLines) {
            if (StringUtils.isBlank(line) || line.startsWith("#")) {
                comments.append(line).append('\n');
                continue;
            }

            if (sameOption) {
                if (StringUtils.isBlank(line.substring(0, 1))) {
                    continue;
                } else {
                    newConfig.append(option).append(": ").append(options.get(option)).append('\n').append(comments);
                    comments.setLength(0);
                    sameOption = false;
                }
            }
            String[] lineSplit = line.split(":", 2);
            if (lineSplit.length != 2) continue;
            newConfig.append(comments);
            comments.setLength(0);
            String key = lineSplit[0];
            if (!options.containsKey(key)) {
                newConfig.append(line).append('\n');
                continue;
            }
            option = key;
            DiscordSRV.debug("Migrating config option " + option + " with value " + (DebugUtil.SENSITIVE_OPTIONS.stream().anyMatch(option::equalsIgnoreCase) ? "OMITTED" : options.get(option)) + " to new config");
            sameOption = true;
        }
        if (option != null) newConfig.append(option).append(": ").append(options.get(option));
        newConfig.append(comments);

        Files.write(to.toPath(), newConfig.toString().getBytes(StandardCharsets.UTF_8));

        provider.load();
    }

    public static void logMissingOptions() {
        for (DynamicConfig.Source source : DiscordSRV.config().getSources().values()) {
            Set<String> keys;
            try {
                keys = getAllKeys(source.getDefaults());
                keys.removeAll(getAllKeys(source.getValues()));
            } catch (Throwable t) {
                DiscordSRV.error("Failed to check " + source.getResourceName() + " for missing options, is it broken?", t);
                continue;
            }

            for (String missing : keys) {
                // ignore map entries
                if (missing.contains(".")) continue;

                DiscordSRV.warning("Config key " + missing + " is missing from the " + source.getResourceName() + ".yml. Using the default value of " + source.getDefaults().get(missing));
            }
        }
    }

    /**
     * Compares two dotted version strings (eg. 1.30.5 and 1.30.5-neoforge.2), ignoring -SNAPSHOT suffixes
     * @return a negative number, zero, or a positive number as a is less than, equal to, or greater than b
     */
    public static int compareVersions(String a, String b) {
        String[] partsA = a.replace("-SNAPSHOT", "").split("[.+-]");
        String[] partsB = b.replace("-SNAPSHOT", "").split("[.+-]");
        for (int i = 0; i < Math.max(partsA.length, partsB.length); i++) {
            String partA = i < partsA.length ? partsA[i] : "0";
            String partB = i < partsB.length ? partsB[i] : "0";
            int result;
            if (partA.matches("\\d+") && partB.matches("\\d+")) {
                result = Long.compare(Long.parseLong(partA), Long.parseLong(partB));
            } else {
                result = partA.compareTo(partB);
            }
            if (result != 0) return result;
        }
        return 0;
    }

    public static Set<String> getAllKeys(Map<String, Object> map) {
        return getAllKeys(map, null);
    }
    public static Set<String> getAllKeys(Map<String, Object> map, String prefix) {
        Set<String> keys = new HashSet<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = (prefix != null ? prefix + "." : "") + entry.getKey();
            keys.add(key);

            if (entry.getValue() instanceof Map) keys.addAll(getAllKeys((Map) entry.getValue(), key));
        }
        return keys;
    }

}
