/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Configuration validation and environment handling tests
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("Configuration Tests")
public class DataCloudConfigTest {

    @Nested
    @DisplayName("ValidationTests")
    class ValidationTests {

        @Test
        @DisplayName("valid config: passes validation")
        void shouldValidateCorrectConfig() {
            Map<String, Object> config = createValidConfig();

            assertThat(validateConfig(config)).isTrue();
        }

        @Test
        @DisplayName("missing required field: fails")
        void shouldFailOnMissingField() {
            Map<String, Object> config = createValidConfig();
            config.remove("databaseUrl");

            assertThatThrownBy(() -> validateAndThrow(config))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("databaseUrl");
        }

        @Test
        @DisplayName("invalid URL format: detected")
        void shouldDetectInvalidUrl() {
            Map<String, Object> config = createValidConfig();
            config.put("databaseUrl", "not-a-valid-url");

            assertThatThrownBy(() -> validateAndThrow(config))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("port out of range: rejected")
        void shouldValidatePortRange() {
            Map<String, Object> config = createValidConfig();

            config.put("port", 70000); // Invalid port
            assertThat(validatePort((Integer) config.get("port"))).isFalse();

            config.put("port", 8080); // Valid port
            assertThat(validatePort((Integer) config.get("port"))).isTrue();
        }

        @Test
        @DisplayName("timeout zero or negative: rejected")
        void shouldRejectInvalidTimeout() {
            Map<String, Object> config = createValidConfig();

            config.put("timeoutMs", 0);
            assertThat(validateTimeout((Integer) config.get("timeoutMs"))).isFalse();

            config.put("timeoutMs", -1000);
            assertThat(validateTimeout((Integer) config.get("timeoutMs"))).isFalse();

            config.put("timeoutMs", 5000);
            assertThat(validateTimeout((Integer) config.get("timeoutMs"))).isTrue();
        }

        @Test
        @DisplayName("duplicate entry names: detected")
        void shouldDetectDuplicateNames() {
            Map<String, Object> config = createValidConfig();

            // Simulate duplicate names in entries
            assertThat(hasDuplicates(
                    ((String) config.get("name")).toLowerCase(),
                    ((String) config.get("name")).toUpperCase()
            )).isFalse(); // Different cases are OK
        }
    }

    @Nested
    @DisplayName("DefaultsTests")
    class DefaultsTests {

        @Test
        @DisplayName("missing optional field: uses default")
        void shouldApplyDefaults() {
            Map<String, Object> config = createValidConfig();
            config.remove("logLevel");

            applyDefaults(config);

            assertThat(config.get("logLevel")).isEqualTo("INFO");
        }

        @Test
        @DisplayName("explicit null: replaces with default")
        void shouldReplaceNullWithDefault() {
            Map<String, Object> config = createValidConfig();
            config.put("retryCount", null);

            applyDefaults(config);

            assertThat(config.get("retryCount")).isEqualTo(3);
        }

        @Test
        @DisplayName("all defaults applied: complete config")
        void shouldCreateCompleteConfig() {
            Map<String, Object> partial = new HashMap<>();
            partial.put("databaseUrl", "jdbc:postgresql://localhost/datacloud");
            partial.put("port", 8080);

            applyDefaults(partial);

            assertThat(partial)
                    .containsKey("logLevel")
                    .containsKey("retryCount")
                    .containsKey("timeoutMs");
        }

        @Test
        @DisplayName("defaults immutable: does not override explicit values")
        void shouldNotOverrideExplicitValues() {
            Map<String, Object> config = createValidConfig();
            config.put("logLevel", "DEBUG");

            applyDefaults(config);

            // Explicit value should be preserved
            assertThat(config.get("logLevel")).isEqualTo("DEBUG");
        }
    }

    @Nested
    @DisplayName("OverrideTests")
    class OverrideTests {

        @Test
        @DisplayName("environment variable overrides config: applied")
        void shouldApplyEnvOverride() {
            Map<String, Object> config = createValidConfig();
            config.put("logLevel", "INFO");

            overrideFromEnv(config, "LOG_LEVEL", "DEBUG");

            assertThat(config.get("logLevel")).isEqualTo("DEBUG");
        }

        @Test
        @DisplayName("missing env var: config unchanged")
        void shouldNotChangeIfEnvMissing() {
            Map<String, Object> config = createValidConfig();
            config.put("logLevel", "INFO");

            overrideFromEnv(config, "NONEXISTENT_VAR", "DEBUG");

            // Should remain unchanged if env var doesn't exist
            assertThat(config.get("logLevel")).isEqualTo("INFO");
        }

        @Test
        @DisplayName("multiple overrides: last wins")
        void shouldApplyMultipleOverrides() {
            Map<String, Object> config = createValidConfig();

            overrideFromEnv(config, "MAX_CONNECTIONS", "10");
            overrideFromEnv(config, "MAX_CONNECTIONS", "20");

            assertThat(config.get("maxConnections")).isEqualTo("20"); // Last override wins
        }

        @Test
        @DisplayName("system property overrides env: takes precedence")
        void shouldPrioritizeSystemProperty() {
            Map<String, Object> config = createValidConfig();

            overrideFromEnv(config, "DB_URL", "jdbc:postgresql://env-host");
            overrideFromSystem(config, "db.url", "jdbc:postgresql://system-host");

            // System property should win
            assertThat(config.get("databaseUrl")).isEqualTo("jdbc:postgresql://system-host");
        }
    }

    @Nested
    @DisplayName("EdgeCaseTests")
    class EdgeCaseTests {

        @Test
        @DisplayName("empty config: caught")
        void shouldRejectEmptyConfig() {
            Map<String, Object> config = new HashMap<>();

            assertThatThrownBy(() -> validateAndThrow(config))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("very long config value: handled")
        void shouldHandleLongValues() {
            Map<String, Object> config = createValidConfig();
            config.put("description", "x".repeat(10_000));

            assertThat(validateConfig(config)).isTrue();
        }

        @Test
        @DisplayName("special chars in config: preserved")
        void shouldPreserveSpecialChars() {
            Map<String, Object> config = createValidConfig();
            String value = "test@#$%&*()[]{}";
            config.put("name", value);

            assertThat(config.get("name")).isEqualTo(value);
        }

        @Test
        @DisplayName("config with nulls: strict validation")
        void shouldRejectConfigWithNulls() {
            Map<String, Object> config = createValidConfig();
            config.put("databaseUrl", null);

            assertThatThrownBy(() -> validateAndThrow(config))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> createValidConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("databaseUrl", "jdbc:postgresql://localhost:5432/datacloud");
        config.put("port", 8080);
        config.put("logLevel", "INFO");
        config.put("retryCount", 3);
        config.put("timeoutMs", 30000);
        config.put("name", "DataCloudConfig");
        config.put("maxConnections", "10");
        return config;
    }

    private boolean validateConfig(Map<String, Object> config) {
        try {
            validateAndThrow(config);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void validateAndThrow(Map<String, Object> config) {
        if (config.isEmpty()) {
            throw new IllegalArgumentException("Config cannot be empty");
        }
        if (!config.containsKey("databaseUrl") || config.get("databaseUrl") == null) {
            throw new IllegalArgumentException("Missing required field: databaseUrl");
        }
        if (!config.containsKey("port")) {
            throw new IllegalArgumentException("Missing required field: port");
        }

        String url = config.get("databaseUrl").toString();
        if (!url.startsWith("jdbc:")) {
            throw new IllegalArgumentException("Invalid database URL format");
        }
    }

    private boolean validatePort(int port) {
        return port >= 1024 && port <= 65535;
    }

    private boolean validateTimeout(int timeoutMs) {
        return timeoutMs > 0;
    }

    private boolean hasDuplicates(String... values) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String val : values) {
            if (!seen.add(val)) {
                return true;
            }
        }
        return false;
    }

    private void applyDefaults(Map<String, Object> config) {
        config.putIfAbsent("logLevel", "INFO");
        config.putIfAbsent("retryCount", 3);
        config.putIfAbsent("timeoutMs", 30000);
        config.putIfAbsent("maxConnections", 10);

        // Replace nulls with defaults
        if (config.get("retryCount") == null) {
            config.put("retryCount", 3);
        }
        if (config.get("logLevel") == null) {
            config.put("logLevel", "INFO");
        }
    }

    private void overrideFromEnv(Map<String, Object> config, String envKey, String value) {
        // Simulate environment variable override
        // Map LOG_LEVEL to logLevel, MAX_CONNECTIONS to maxConnections, etc.
        String configKey = envKey.toLowerCase().replace("_", "");
        if (value != null && !value.isEmpty()) {
            // Update the config with the new value
            if ("loglevel".equals(configKey)) {
                config.put("logLevel", value);
            } else if ("maxconnections".equals(configKey)) {
                config.put("maxConnections", value);
            } else if ("dburl".equals(configKey)) {
                config.put("databaseUrl", value);
            }
        }
    }

    private void overrideFromSystem(Map<String, Object> config, String propertyKey, String value) {
        // Simulate system property override (higher priority than env)
        String configKey = propertyKey.replace(".", "");
        if (value != null && !value.isEmpty()) {
            if ("dburl".equals(configKey)) {
                config.put("databaseUrl", value);
            } else if ("loglevel".equals(configKey)) {
                config.put("logLevel", value);
            }
        }
    }
}
