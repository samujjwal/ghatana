package com.ghatana.platform.testing.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Testing utilities for configuration-related test setup.
 *
 * <p>Provides helpers for creating test-friendly configuration maps, setting
 * system properties for the duration of a test, and restoring them afterwards.
 *
 * <p>Usage:
 * <pre>{@code
 * Map<String, String> config = ConfigTestUtils.config()
 *     .with("db.url", "jdbc:h2:mem:test")
 *     .with("db.user", "sa")
 *     .build();
 *
 * // System property scope
 * ConfigTestUtils.withSystemProperty("feature.xyz", "true", () -> {
 *     // test runs with property set
 * });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Test utilities for configuration construction and system property management
 * @doc.layer platform
 * @doc.pattern Utility, TestHelper
 *
 * @since 2026-03-27
 */
public final class ConfigTestUtils {

    private ConfigTestUtils() {} // Utility class

    /**
     * Creates a new config builder with an empty backing map.
     *
     * @return a new {@link ConfigBuilder}
     */
    public static ConfigBuilder config() {
        return new ConfigBuilder();
    }

    /**
     * Sets a system property for the duration of the action, then restores the
     * original value (or removes the property if it was not previously set).
     *
     * @param key    the system property key
     * @param value  the temporary value
     * @param action the action to execute with the property set
     */
    public static void withSystemProperty(String key, String value, Runnable action) {
        String original = System.getProperty(key);
        try {
            System.setProperty(key, value);
            action.run();
        } finally {
            if (original == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, original);
            }
        }
    }

    /**
     * Sets multiple system properties for the duration of the action, restoring
     * all original values afterward.
     *
     * @param properties key-value pairs to set temporarily
     * @param action     the action to run with the properties set
     */
    public static void withSystemProperties(Map<String, String> properties, Runnable action) {
        Map<String, String> originals = new HashMap<>();
        try {
            properties.forEach((k, v) -> {
                originals.put(k, System.getProperty(k));
                System.setProperty(k, v);
            });
            action.run();
        } finally {
            properties.keySet().forEach(k -> {
                String orig = originals.get(k);
                if (orig == null) {
                    System.clearProperty(k);
                } else {
                    System.setProperty(k, orig);
                }
            });
        }
    }

    /**
     * Converts a {@code Map<String, String>} to a {@link Properties} object.
     * Useful when APIs require Properties instead of Map.
     *
     * @param map the source map
     * @return a new Properties containing all map entries
     */
    public static Properties toProperties(Map<String, String> map) {
        Properties props = new Properties();
        map.forEach(props::setProperty);
        return props;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /**
     * Fluent builder for test configuration maps.
     */
    public static final class ConfigBuilder {

        private final Map<String, String> entries = new HashMap<>();

        /**
         * Adds a key-value entry.
         *
         * @param key   the config key
         * @param value the config value
         * @return this builder
         */
        public ConfigBuilder with(String key, String value) {
            entries.put(key, value);
            return this;
        }

        /**
         * Adds multiple entries from another map.
         *
         * @param overrides the entries to add
         * @return this builder
         */
        public ConfigBuilder withAll(Map<String, String> overrides) {
            entries.putAll(overrides);
            return this;
        }

        /**
         * Builds and returns an immutable config map.
         *
         * @return an unmodifiable copy of the accumulated entries
         */
        public Map<String, String> build() {
            return Map.copyOf(entries);
        }

        /**
         * Builds a mutable map (useful when the SUT mutates config).
         *
         * @return a mutable HashMap copy
         */
        public Map<String, String> buildMutable() {
            return new HashMap<>(entries);
        }
    }
}
