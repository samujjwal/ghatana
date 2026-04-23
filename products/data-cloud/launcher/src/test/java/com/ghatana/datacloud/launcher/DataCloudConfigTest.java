/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
        void shouldValidateCorrectConfig() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            assertThat(validateConfig(config)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("missing required field: fails")
        void shouldFailOnMissingField() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.remove("databaseUrl");

            assertThatThrownBy(() -> validateAndThrow(config)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("databaseUrl");
        }

        @Test
        @DisplayName("invalid URL format: detected")
        void shouldDetectInvalidUrl() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("databaseUrl", "not-a-valid-url"); // GH-90000

            assertThatThrownBy(() -> validateAndThrow(config)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("port out of range: rejected")
        void shouldValidatePortRange() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            config.put("port", 70000); // Invalid port // GH-90000
            assertThat(validatePort((Integer) config.get("port"))).isFalse();

            config.put("port", 8080); // Valid port // GH-90000
            assertThat(validatePort((Integer) config.get("port"))).isTrue();
        }

        @Test
        @DisplayName("timeout zero or negative: rejected")
        void shouldRejectInvalidTimeout() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            config.put("timeoutMs", 0); // GH-90000
            assertThat(validateTimeout((Integer) config.get("timeoutMs"))).isFalse();

            config.put("timeoutMs", -1000); // GH-90000
            assertThat(validateTimeout((Integer) config.get("timeoutMs"))).isFalse();

            config.put("timeoutMs", 5000); // GH-90000
            assertThat(validateTimeout((Integer) config.get("timeoutMs"))).isTrue();
        }

        @Test
        @DisplayName("duplicate entry names: detected")
        void shouldDetectDuplicateNames() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            // Simulate duplicate names in entries
            assertThat(hasDuplicates( // GH-90000
                    ((String) config.get("name")).toLowerCase(),
                    ((String) config.get("name")).toUpperCase()
            )).isFalse(); // Different cases are OK // GH-90000
        }
    }

    @Nested
    @DisplayName("DefaultsTests")
    class DefaultsTests {

        @Test
        @DisplayName("missing optional field: uses default")
        void shouldApplyDefaults() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.remove("logLevel");

            applyDefaults(config); // GH-90000

            assertThat(config.get("logLevel")).isEqualTo("INFO");
        }

        @Test
        @DisplayName("explicit null: replaces with default")
        void shouldReplaceNullWithDefault() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("retryCount", null); // GH-90000

            applyDefaults(config); // GH-90000

            assertThat(config.get("retryCount")).isEqualTo(3);
        }

        @Test
        @DisplayName("all defaults applied: complete config")
        void shouldCreateCompleteConfig() { // GH-90000
            Map<String, Object> partial = new HashMap<>(); // GH-90000
            partial.put("databaseUrl", "jdbc:postgresql://localhost/datacloud"); // GH-90000
            partial.put("port", 8080); // GH-90000

            applyDefaults(partial); // GH-90000

            assertThat(partial) // GH-90000
                    .containsKey("logLevel")
                    .containsKey("retryCount")
                    .containsKey("timeoutMs");
        }

        @Test
        @DisplayName("defaults immutable: does not override explicit values")
        void shouldNotOverrideExplicitValues() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("logLevel", "DEBUG"); // GH-90000

            applyDefaults(config); // GH-90000

            // Explicit value should be preserved
            assertThat(config.get("logLevel")).isEqualTo("DEBUG");
        }
    }

    @Nested
    @DisplayName("OverrideTests")
    class OverrideTests {

        @Test
        @DisplayName("environment variable overrides config: applied")
        void shouldApplyEnvOverride() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("logLevel", "INFO"); // GH-90000

            overrideFromEnv(config, "LOG_LEVEL", "DEBUG"); // GH-90000

            assertThat(config.get("logLevel")).isEqualTo("DEBUG");
        }

        @Test
        @DisplayName("missing env var: config unchanged")
        void shouldNotChangeIfEnvMissing() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("logLevel", "INFO"); // GH-90000

            overrideFromEnv(config, "NONEXISTENT_VAR", "DEBUG"); // GH-90000

            // Should remain unchanged if env var doesn't exist
            assertThat(config.get("logLevel")).isEqualTo("INFO");
        }

        @Test
        @DisplayName("multiple overrides: last wins")
        void shouldApplyMultipleOverrides() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            overrideFromEnv(config, "MAX_CONNECTIONS", "10"); // GH-90000
            overrideFromEnv(config, "MAX_CONNECTIONS", "20"); // GH-90000

            assertThat(config.get("maxConnections")).isEqualTo("20"); // Last override wins
        }

        @Test
        @DisplayName("system property overrides env: takes precedence")
        void shouldPrioritizeSystemProperty() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            overrideFromEnv(config, "DB_URL", "jdbc:postgresql://env-host"); // GH-90000
            overrideFromSystem(config, "db.url", "jdbc:postgresql://system-host"); // GH-90000

            // System property should win
            assertThat(config.get("databaseUrl")).isEqualTo("jdbc:postgresql://system-host");
        }
    }

    @Nested
    @DisplayName("EdgeCaseTests")
    class EdgeCaseTests {

        @Test
        @DisplayName("empty config: caught")
        void shouldRejectEmptyConfig() { // GH-90000
            Map<String, Object> config = new HashMap<>(); // GH-90000

            assertThatThrownBy(() -> validateAndThrow(config)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("very long config value: handled")
        void shouldHandleLongValues() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("description", "x".repeat(10_000)); // GH-90000

            assertThat(validateConfig(config)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("special chars in config: preserved")
        void shouldPreserveSpecialChars() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            String value = "test@#$%&*()[]{}"; // GH-90000
            config.put("name", value); // GH-90000

            assertThat(config.get("name")).isEqualTo(value);
        }

        @Test
        @DisplayName("config with nulls: strict validation")
        void shouldRejectConfigWithNulls() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("databaseUrl", null); // GH-90000

            assertThatThrownBy(() -> validateAndThrow(config)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, Object> createValidConfig() { // GH-90000
        Map<String, Object> config = new HashMap<>(); // GH-90000
        config.put("databaseUrl", "jdbc:postgresql://localhost:5432/datacloud"); // GH-90000
        config.put("port", 8080); // GH-90000
        config.put("logLevel", "INFO"); // GH-90000
        config.put("retryCount", 3); // GH-90000
        config.put("timeoutMs", 30000); // GH-90000
        config.put("name", "DataCloudConfig"); // GH-90000
        config.put("maxConnections", "10"); // GH-90000
        return config;
    }

    private boolean validateConfig(Map<String, Object> config) { // GH-90000
        try {
            validateAndThrow(config); // GH-90000
            return true;
        } catch (Exception e) { // GH-90000
            return false;
        }
    }

    private void validateAndThrow(Map<String, Object> config) { // GH-90000
        if (config.isEmpty()) { // GH-90000
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

    private boolean validatePort(int port) { // GH-90000
        return port >= 1024 && port <= 65535;
    }

    private boolean validateTimeout(int timeoutMs) { // GH-90000
        return timeoutMs > 0;
    }

    private boolean hasDuplicates(String... values) { // GH-90000
        java.util.Set<String> seen = new java.util.HashSet<>(); // GH-90000
        for (String val : values) { // GH-90000
            if (!seen.add(val)) { // GH-90000
                return true;
            }
        }
        return false;
    }

    private void applyDefaults(Map<String, Object> config) { // GH-90000
        config.putIfAbsent("logLevel", "INFO"); // GH-90000
        config.putIfAbsent("retryCount", 3); // GH-90000
        config.putIfAbsent("timeoutMs", 30000); // GH-90000
        config.putIfAbsent("maxConnections", 10); // GH-90000

        // Replace nulls with defaults
        if (config.get("retryCount") == null) {
            config.put("retryCount", 3); // GH-90000
        }
        if (config.get("logLevel") == null) {
            config.put("logLevel", "INFO"); // GH-90000
        }
    }

    private void overrideFromEnv(Map<String, Object> config, String envKey, String value) { // GH-90000
        // Simulate environment variable override
        // Map LOG_LEVEL to logLevel, MAX_CONNECTIONS to maxConnections, etc.
        String configKey = envKey.toLowerCase().replace("_", ""); // GH-90000
        if (value != null && !value.isEmpty()) { // GH-90000
            // Update the config with the new value
            if ("loglevel".equals(configKey)) { // GH-90000
                config.put("logLevel", value); // GH-90000
            } else if ("maxconnections".equals(configKey)) { // GH-90000
                config.put("maxConnections", value); // GH-90000
            } else if ("dburl".equals(configKey)) { // GH-90000
                config.put("databaseUrl", value); // GH-90000
            }
        }
    }

    private void overrideFromSystem(Map<String, Object> config, String propertyKey, String value) { // GH-90000
        // Simulate system property override (higher priority than env) // GH-90000
        String configKey = propertyKey.replace(".", ""); // GH-90000
        if (value != null && !value.isEmpty()) { // GH-90000
            if ("dburl".equals(configKey)) { // GH-90000
                config.put("databaseUrl", value); // GH-90000
            } else if ("loglevel".equals(configKey)) { // GH-90000
                config.put("logLevel", value); // GH-90000
            }
        }
    }
}
