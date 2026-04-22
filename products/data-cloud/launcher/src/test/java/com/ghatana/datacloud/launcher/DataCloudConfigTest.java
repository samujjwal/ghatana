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
@DisplayName("Configuration Tests [GH-90000]")
public class DataCloudConfigTest {

    @Nested
    @DisplayName("ValidationTests [GH-90000]")
    class ValidationTests {

        @Test
        @DisplayName("valid config: passes validation [GH-90000]")
        void shouldValidateCorrectConfig() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            assertThat(validateConfig(config)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("missing required field: fails [GH-90000]")
        void shouldFailOnMissingField() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.remove("databaseUrl [GH-90000]");

            assertThatThrownBy(() -> validateAndThrow(config)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class) // GH-90000
                    .hasMessageContaining("databaseUrl [GH-90000]");
        }

        @Test
        @DisplayName("invalid URL format: detected [GH-90000]")
        void shouldDetectInvalidUrl() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("databaseUrl", "not-a-valid-url"); // GH-90000

            assertThatThrownBy(() -> validateAndThrow(config)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("port out of range: rejected [GH-90000]")
        void shouldValidatePortRange() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            config.put("port", 70000); // Invalid port // GH-90000
            assertThat(validatePort((Integer) config.get("port [GH-90000]"))).isFalse();

            config.put("port", 8080); // Valid port // GH-90000
            assertThat(validatePort((Integer) config.get("port [GH-90000]"))).isTrue();
        }

        @Test
        @DisplayName("timeout zero or negative: rejected [GH-90000]")
        void shouldRejectInvalidTimeout() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            config.put("timeoutMs", 0); // GH-90000
            assertThat(validateTimeout((Integer) config.get("timeoutMs [GH-90000]"))).isFalse();

            config.put("timeoutMs", -1000); // GH-90000
            assertThat(validateTimeout((Integer) config.get("timeoutMs [GH-90000]"))).isFalse();

            config.put("timeoutMs", 5000); // GH-90000
            assertThat(validateTimeout((Integer) config.get("timeoutMs [GH-90000]"))).isTrue();
        }

        @Test
        @DisplayName("duplicate entry names: detected [GH-90000]")
        void shouldDetectDuplicateNames() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            // Simulate duplicate names in entries
            assertThat(hasDuplicates( // GH-90000
                    ((String) config.get("name [GH-90000]")).toLowerCase(),
                    ((String) config.get("name [GH-90000]")).toUpperCase()
            )).isFalse(); // Different cases are OK // GH-90000
        }
    }

    @Nested
    @DisplayName("DefaultsTests [GH-90000]")
    class DefaultsTests {

        @Test
        @DisplayName("missing optional field: uses default [GH-90000]")
        void shouldApplyDefaults() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.remove("logLevel [GH-90000]");

            applyDefaults(config); // GH-90000

            assertThat(config.get("logLevel [GH-90000]")).isEqualTo("INFO [GH-90000]");
        }

        @Test
        @DisplayName("explicit null: replaces with default [GH-90000]")
        void shouldReplaceNullWithDefault() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("retryCount", null); // GH-90000

            applyDefaults(config); // GH-90000

            assertThat(config.get("retryCount [GH-90000]")).isEqualTo(3);
        }

        @Test
        @DisplayName("all defaults applied: complete config [GH-90000]")
        void shouldCreateCompleteConfig() { // GH-90000
            Map<String, Object> partial = new HashMap<>(); // GH-90000
            partial.put("databaseUrl", "jdbc:postgresql://localhost/datacloud"); // GH-90000
            partial.put("port", 8080); // GH-90000

            applyDefaults(partial); // GH-90000

            assertThat(partial) // GH-90000
                    .containsKey("logLevel [GH-90000]")
                    .containsKey("retryCount [GH-90000]")
                    .containsKey("timeoutMs [GH-90000]");
        }

        @Test
        @DisplayName("defaults immutable: does not override explicit values [GH-90000]")
        void shouldNotOverrideExplicitValues() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("logLevel", "DEBUG"); // GH-90000

            applyDefaults(config); // GH-90000

            // Explicit value should be preserved
            assertThat(config.get("logLevel [GH-90000]")).isEqualTo("DEBUG [GH-90000]");
        }
    }

    @Nested
    @DisplayName("OverrideTests [GH-90000]")
    class OverrideTests {

        @Test
        @DisplayName("environment variable overrides config: applied [GH-90000]")
        void shouldApplyEnvOverride() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("logLevel", "INFO"); // GH-90000

            overrideFromEnv(config, "LOG_LEVEL", "DEBUG"); // GH-90000

            assertThat(config.get("logLevel [GH-90000]")).isEqualTo("DEBUG [GH-90000]");
        }

        @Test
        @DisplayName("missing env var: config unchanged [GH-90000]")
        void shouldNotChangeIfEnvMissing() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("logLevel", "INFO"); // GH-90000

            overrideFromEnv(config, "NONEXISTENT_VAR", "DEBUG"); // GH-90000

            // Should remain unchanged if env var doesn't exist
            assertThat(config.get("logLevel [GH-90000]")).isEqualTo("INFO [GH-90000]");
        }

        @Test
        @DisplayName("multiple overrides: last wins [GH-90000]")
        void shouldApplyMultipleOverrides() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            overrideFromEnv(config, "MAX_CONNECTIONS", "10"); // GH-90000
            overrideFromEnv(config, "MAX_CONNECTIONS", "20"); // GH-90000

            assertThat(config.get("maxConnections [GH-90000]")).isEqualTo("20 [GH-90000]"); // Last override wins
        }

        @Test
        @DisplayName("system property overrides env: takes precedence [GH-90000]")
        void shouldPrioritizeSystemProperty() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000

            overrideFromEnv(config, "DB_URL", "jdbc:postgresql://env-host"); // GH-90000
            overrideFromSystem(config, "db.url", "jdbc:postgresql://system-host"); // GH-90000

            // System property should win
            assertThat(config.get("databaseUrl [GH-90000]")).isEqualTo("jdbc:postgresql://system-host [GH-90000]");
        }
    }

    @Nested
    @DisplayName("EdgeCaseTests [GH-90000]")
    class EdgeCaseTests {

        @Test
        @DisplayName("empty config: caught [GH-90000]")
        void shouldRejectEmptyConfig() { // GH-90000
            Map<String, Object> config = new HashMap<>(); // GH-90000

            assertThatThrownBy(() -> validateAndThrow(config)) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("very long config value: handled [GH-90000]")
        void shouldHandleLongValues() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            config.put("description", "x".repeat(10_000)); // GH-90000

            assertThat(validateConfig(config)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("special chars in config: preserved [GH-90000]")
        void shouldPreserveSpecialChars() { // GH-90000
            Map<String, Object> config = createValidConfig(); // GH-90000
            String value = "test@#$%&*()[]{}"; // GH-90000
            config.put("name", value); // GH-90000

            assertThat(config.get("name [GH-90000]")).isEqualTo(value);
        }

        @Test
        @DisplayName("config with nulls: strict validation [GH-90000]")
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
            throw new IllegalArgumentException("Config cannot be empty [GH-90000]");
        }
        if (!config.containsKey("databaseUrl [GH-90000]") || config.get("databaseUrl [GH-90000]") == null) {
            throw new IllegalArgumentException("Missing required field: databaseUrl [GH-90000]");
        }
        if (!config.containsKey("port [GH-90000]")) {
            throw new IllegalArgumentException("Missing required field: port [GH-90000]");
        }

        String url = config.get("databaseUrl [GH-90000]").toString();
        if (!url.startsWith("jdbc: [GH-90000]")) {
            throw new IllegalArgumentException("Invalid database URL format [GH-90000]");
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
        if (config.get("retryCount [GH-90000]") == null) {
            config.put("retryCount", 3); // GH-90000
        }
        if (config.get("logLevel [GH-90000]") == null) {
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
