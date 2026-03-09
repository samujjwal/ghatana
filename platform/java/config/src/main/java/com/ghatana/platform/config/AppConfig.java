package com.ghatana.platform.config;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

/**
 * Standardized application configuration accessor that eliminates direct
 * {@code System.getenv()} / {@code System.getProperty()} calls scattered
 * across product code.
 *
 * <p>Resolution order (highest priority first):
 * <ol>
 *   <li>Environment variables (e.g., {@code GHATANA_DB_HOST})</li>
 *   <li>System properties (e.g., {@code -Dghatana.db.host=...})</li>
 *   <li>Config file (application.yml via {@link YamlConfigSource})</li>
 *   <li>Defaults registered at startup</li>
 * </ol>
 *
 * <p><b>Environment variable mapping:</b>
 * Config key {@code ghatana.db.host} maps to env var {@code GHATANA_DB_HOST}
 * (dots → underscores, uppercase).
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AppConfig config = AppConfig.load();
 * String dbHost = config.require("ghatana.db.host");
 * int port = config.getInt("ghatana.http.port", 8080);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralized application configuration accessor
 * @doc.layer core
 * @doc.pattern Configuration
 */
public final class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    private final Map<String, String> defaults = new LinkedHashMap<>();
    private final List<ConfigSource> sources;

    private AppConfig(@NotNull List<ConfigSource> sources) {
        this.sources = List.copyOf(sources);
    }

    /**
     * Creates a standard AppConfig with env → system props → YAML file → defaults.
     *
     * @return configured AppConfig instance
     */
    @NotNull
    public static AppConfig load() {
        List<ConfigSource> sources = new ArrayList<>();
        sources.add(new EnvironmentConfigSource());
        sources.add(new SystemPropertiesConfigSource());
        // YAML file if present
        try {
            sources.add(new YamlConfigSource(Path.of("application.yml")));
        } catch (Exception ignored) {
            log.debug("No application.yml found on classpath — using env/sysprops only");
        }
        return new AppConfig(sources);
    }

    /**
     * Creates an AppConfig for testing with explicit overrides.
     *
     * @param overrides key-value pairs to use as config
     * @return configured AppConfig instance
     */
    @NotNull
    public static AppConfig forTesting(@NotNull Map<String, String> overrides) {
        Map<String, Object> props = new HashMap<>(overrides);
        MemoryConfigSource memory = new MemoryConfigSource("testing", props);
        return new AppConfig(List.of(memory));
    }

    /**
     * Registers a default value (lowest priority).
     *
     * @param key   config key
     * @param value default value
     * @return this (for chaining)
     */
    @NotNull
    public AppConfig withDefault(@NotNull String key, @NotNull String value) {
        defaults.put(key, value);
        return this;
    }

    /**
     * Gets a config value, or the default if not found.
     *
     * @param key          config key (e.g., "ghatana.db.host")
     * @param defaultValue fallback value
     * @return resolved value
     */
    @NotNull
    public String get(@NotNull String key, @NotNull String defaultValue) {
        String value = resolve(key);
        return value != null ? value : defaults.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a required config value. Throws if missing.
     *
     * @param key config key
     * @return resolved value
     * @throws IllegalStateException if the key is not configured
     */
    @NotNull
    public String require(@NotNull String key) {
        String value = resolve(key);
        if (value == null) {
            value = defaults.get(key);
        }
        if (value == null) {
            throw new IllegalStateException(
                    "Required configuration key '" + key + "' is not set. " +
                    "Set environment variable '" + toEnvVar(key) + "' or " +
                    "system property '" + key + "'.");
        }
        return value;
    }

    /**
     * Gets an integer config value.
     *
     * @param key          config key
     * @param defaultValue fallback
     * @return resolved integer
     */
    public int getInt(@NotNull String key, int defaultValue) {
        String value = resolve(key);
        if (value == null) value = defaults.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer for config key '{}': '{}' — using default {}",
                    key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Gets a long config value.
     *
     * @param key          config key
     * @param defaultValue fallback
     * @return resolved long
     */
    public long getLong(@NotNull String key, long defaultValue) {
        String value = resolve(key);
        if (value == null) value = defaults.get(key);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid long for config key '{}': '{}' — using default {}",
                    key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Gets a boolean config value.
     *
     * @param key          config key
     * @param defaultValue fallback
     * @return resolved boolean
     */
    public boolean getBoolean(@NotNull String key, boolean defaultValue) {
        String value = resolve(key);
        if (value == null) value = defaults.get(key);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    /**
     * Gets an optional config value.
     *
     * @param key config key
     * @return optional containing the value if present
     */
    @NotNull
    public Optional<String> getOptional(@NotNull String key) {
        String value = resolve(key);
        if (value == null) value = defaults.get(key);
        return Optional.ofNullable(value);
    }

    private String resolve(@NotNull String key) {
        for (ConfigSource source : sources) {
            Optional<String> value = source.getString(key);
            if (value.isPresent()) return value.get();
        }
        return null;
    }

    /**
     * Converts a config key to its environment variable form.
     * {@code ghatana.db.host} → {@code GHATANA_DB_HOST}
     */
    @NotNull
    static String toEnvVar(@NotNull String key) {
        return key.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
    }
}
