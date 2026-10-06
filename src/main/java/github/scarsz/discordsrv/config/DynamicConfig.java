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

package github.scarsz.discordsrv.config;

import alexh.weak.Dynamic;
import lombok.Getter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;

/**
 * A layered YAML configuration, modelled after the configuralize library used by the Spigot version of DiscordSRV.
 * <p>
 * Each {@link Source} is a pair of a bundled default resource ({@code /<name>/<language>.yml}) and a user editable
 * file. When looking up a key, the user files of every source are consulted first (in the order the sources were
 * added), falling back to the bundled defaults. Runtime values (set with {@link #setRuntimeValue(String, Object)})
 * take precedence over everything.
 * <p>
 * Keys may be top level keys (which can contain dots themselves) or dot separated paths into nested maps.
 */
public class DynamicConfig {

    @Getter private final Map<String, Source> sources = new LinkedHashMap<>();
    private final Map<String, Object> runtimeValues = new HashMap<>();
    @Getter private Language language = Language.EN;

    public void addSource(Class<?> resourceClass, String resourceName, File file) {
        sources.put(resourceName, new Source(resourceClass, resourceName, file));
    }

    public Source getProvider(String resourceName) {
        return sources.get(resourceName);
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public boolean isLanguageAvailable(Language language) {
        for (Source source : sources.values()) {
            if (source.resourceClass.getResource(source.getResourcePath(language)) == null) return false;
        }
        return true;
    }

    /**
     * Writes the default files for all sources that don't have a user file yet
     */
    public void saveAllDefaults() throws IOException {
        for (Source source : sources.values()) source.saveDefaults();
    }

    public void loadAll() throws IOException {
        for (Source source : sources.values()) source.load();
    }

    public void setRuntimeValue(String key, Object value) {
        synchronized (runtimeValues) {
            if (value == null) runtimeValues.remove(key);
            else runtimeValues.put(key, value);
        }
    }

    // --- lookup ---

    /**
     * Gets the raw value for the given key, or null if the key isn't present anywhere
     */
    public Object get(String key) {
        synchronized (runtimeValues) {
            if (runtimeValues.containsKey(key)) return runtimeValues.get(key);
        }
        for (Source source : sources.values()) {
            Object value = lookup(source.values, key);
            if (value != null) return value;
        }
        for (Source source : sources.values()) {
            Object value = lookup(source.defaults, key);
            if (value != null) return value;
        }
        return null;
    }

    /**
     * Gets the value for the given key as a {@link Dynamic}. The dynamic will not be present if the key is missing.
     */
    public Dynamic dget(String key) {
        return Dynamic.from(get(key));
    }

    @SuppressWarnings("unchecked")
    static Object lookup(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        if (map.containsKey(key)) return map.get(key);

        // walk the path, allowing keys that contain dots themselves (eg. "Require linked account to play.Enabled")
        int index = key.indexOf('.');
        while (index != -1) {
            String head = key.substring(0, index);
            Object child = map.get(head);
            if (child instanceof Map) {
                Object value = lookup((Map<String, Object>) child, key.substring(index + 1));
                if (value != null) return value;
            }
            index = key.indexOf('.', index + 1);
        }
        return null;
    }

    public Optional<Object> getOptional(String key) {
        return Optional.ofNullable(get(key));
    }

    private <T> Optional<T> getOptional(String key, Function<Object, T> converter) {
        Object value = get(key);
        if (value == null) return Optional.empty();
        try {
            return Optional.ofNullable(converter.apply(value));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public Optional<String> getOptionalString(String key) {
        return getOptional(key, DynamicConfig::toStringValue);
    }

    public Optional<Boolean> getOptionalBoolean(String key) {
        return getOptional(key, DynamicConfig::toBoolean);
    }

    public Optional<Integer> getOptionalInt(String key) {
        return getOptional(key, value -> toNumber(value).intValue());
    }

    public Optional<Long> getOptionalLong(String key) {
        return getOptional(key, value -> toNumber(value).longValue());
    }

    public Optional<Double> getOptionalDouble(String key) {
        return getOptional(key, value -> toNumber(value).doubleValue());
    }

    public Optional<List<Object>> getOptionalList(String key) {
        return getOptional(key, value -> value instanceof Collection ? new ArrayList<>((Collection<?>) value) : null);
    }

    public Optional<List<String>> getOptionalStringList(String key) {
        return getOptional(key, value -> {
            if (value instanceof Collection) {
                List<String> list = new ArrayList<>();
                for (Object o : (Collection<?>) value) if (o != null) list.add(toStringValue(o));
                return list;
            }
            return null;
        });
    }

    public String getString(String key) {
        return getOptionalString(key).orElse("");
    }

    public String getStringElse(String key, String defaultValue) {
        return getOptionalString(key).orElse(defaultValue);
    }

    public boolean getBoolean(String key) {
        return getOptionalBoolean(key).orElse(false);
    }

    public boolean getBooleanElse(String key, boolean defaultValue) {
        return getOptionalBoolean(key).orElse(defaultValue);
    }

    public int getInt(String key) {
        return getOptionalInt(key).orElse(0);
    }

    public int getIntElse(String key, int defaultValue) {
        return getOptionalInt(key).orElse(defaultValue);
    }

    public long getLong(String key) {
        return getOptionalLong(key).orElse(0L);
    }

    public double getDouble(String key) {
        return getOptionalDouble(key).orElse(0D);
    }

    public List<String> getStringList(String key) {
        return getOptionalStringList(key).orElse(new ArrayList<>());
    }

    /**
     * Gets a map (string keys, string values) for the given key; insertion order is preserved.
     */
    public Map<String, String> getMap(String key) {
        Object value = get(key);
        Map<String, String> map = new LinkedHashMap<>();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (entry.getKey() == null) continue;
                map.put(toStringValue(entry.getKey()), entry.getValue() != null ? toStringValue(entry.getValue()) : "");
            }
        }
        return map;
    }

    // --- conversion ---

    static String toStringValue(Object value) {
        if (value == null) return null;
        if (value instanceof Double || value instanceof Float) {
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) return String.valueOf((long) d);
        }
        return value.toString();
    }

    static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        String s = value.toString().trim();
        if (s.equalsIgnoreCase("true") || s.equalsIgnoreCase("yes") || s.equalsIgnoreCase("on")) return true;
        if (s.equalsIgnoreCase("false") || s.equalsIgnoreCase("no") || s.equalsIgnoreCase("off")) return false;
        throw new IllegalArgumentException("Not a boolean: " + s);
    }

    static Number toNumber(Object value) {
        if (value instanceof Number) return (Number) value;
        String s = value.toString().trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return Double.parseDouble(s);
        }
    }

    static Yaml createYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(64 * 1024 * 1024);
        return new Yaml(new SafeConstructor(options));
    }

    /**
     * Parses the given YAML text into a map; any non-string keys are converted into strings.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(Reader reader) {
        Object loaded = createYaml().load(reader);
        if (loaded == null) return new LinkedHashMap<>();
        if (!(loaded instanceof Map)) throw new IllegalArgumentException("Config root is not a map");
        return (Map<String, Object>) normalize(loaded);
    }

    private static Object normalize(Object object) {
        if (object instanceof Map) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) object).entrySet()) {
                map.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
            }
            return map;
        } else if (object instanceof List) {
            List<Object> list = new ArrayList<>();
            for (Object o : (List<?>) object) list.add(normalize(o));
            return list;
        }
        return object;
    }

    /**
     * A config file backed by a bundled default resource
     */
    public class Source {

        @Getter private final Class<?> resourceClass;
        @Getter private final String resourceName;
        @Getter private final File file;
        private volatile Map<String, Object> defaults = Collections.emptyMap();
        private volatile Map<String, Object> values = Collections.emptyMap();

        Source(Class<?> resourceClass, String resourceName, File file) {
            this.resourceClass = resourceClass;
            this.resourceName = resourceName;
            this.file = file;
        }

        String getResourcePath(Language language) {
            return "/" + resourceName + "/" + language.getCode().toLowerCase(Locale.ROOT) + ".yml";
        }

        private InputStream openDefaultResource() throws IOException {
            InputStream stream = resourceClass.getResourceAsStream(getResourcePath(language));
            if (stream == null) stream = resourceClass.getResourceAsStream(getResourcePath(Language.EN));
            if (stream == null) throw new IOException("Missing bundled resource " + getResourcePath(Language.EN));
            return stream;
        }

        /**
         * Copies the bundled default resource to the user file if it doesn't exist yet
         */
        public void saveDefaults() throws IOException {
            if (file.exists()) return;
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("Failed to create " + parent);
            try (InputStream stream = openDefaultResource()) {
                Files.copy(stream, file.toPath());
            }
        }

        public void load() throws IOException {
            try (Reader reader = new InputStreamReader(openDefaultResource(), StandardCharsets.UTF_8)) {
                defaults = parse(reader);
            }
            if (file.exists()) {
                try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
                    values = parse(reader);
                } catch (RuntimeException e) {
                    throw new IOException("Failed to parse " + file.getName() + ": " + e.getMessage(), e);
                }
            } else {
                values = Collections.emptyMap();
            }
        }

        public Map<String, Object> getDefaults() {
            return defaults;
        }

        public Map<String, Object> getValues() {
            return values;
        }

    }

}
