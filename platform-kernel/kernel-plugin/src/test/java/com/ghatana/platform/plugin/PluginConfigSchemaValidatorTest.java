/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.plugin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Tests for PluginConfigSchemaValidator — config schema validation at install time
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("PluginConfigSchemaValidator tests")
class PluginConfigSchemaValidatorTest {

    private final PluginConfigSchemaValidator validator = new PluginConfigSchemaValidator();
    private static final String PLUGIN_ID = "com.ghatana.plugin.test";

    @Test
    @DisplayName("empty schema accepts any config without error")
    void emptySchemaPassesThrough() {
        Map<String, Object> schema = Map.of();
        Map<String, Object> config = Map.of("someKey", "someValue");

        assertThatNoException()
                .as("empty schema must not reject any config")
                .isThrownBy(() -> validator.validate(PLUGIN_ID, schema, config));
    }

    @Test
    @DisplayName("required property present must pass")
    void requiredPropertyPresent() {
        Map<String, Object> schema = Map.of(
                "apiEndpoint", Map.of("type", "string", "required", true));
        Map<String, Object> config = Map.of("apiEndpoint", "https://example.com");

        assertThatNoException().isThrownBy(() -> validator.validate(PLUGIN_ID, schema, config));
    }

    @Test
    @DisplayName("required property absent must fail with actionable message")
    void requiredPropertyAbsentFails() {
        Map<String, Object> schema = Map.of(
                "apiEndpoint", Map.of("type", "string", "required", true));
        Map<String, Object> config = Map.of();

        assertThatThrownBy(() -> validator.validate(PLUGIN_ID, schema, config))
                .isInstanceOf(PluginConfigSchemaValidator.PluginConfigSchemaViolationException.class)
                .satisfies(ex -> {
                    PluginConfigSchemaValidator.PluginConfigSchemaViolationException violation =
                            (PluginConfigSchemaValidator.PluginConfigSchemaViolationException) ex;
                    assertThat(violation.getPluginId()).isEqualTo(PLUGIN_ID);
                    assertThat(violation.getViolations())
                            .anyMatch(v -> v.contains("apiEndpoint") && v.contains("required"));
                });
    }

    @Test
    @DisplayName("secret property with env-reference prefix must pass")
    void secretPropertyWithEnvReferencePasses() {
        Map<String, Object> schema = Map.of(
                "apiKey", Map.of("type", "string", "required", true, "secret", true));
        Map<String, Object> config = Map.of("apiKey", "env:MY_API_KEY");

        assertThatNoException()
                .as("env: reference must be accepted for secret property")
                .isThrownBy(() -> validator.validate(PLUGIN_ID, schema, config));
    }

    @Test
    @DisplayName("secret property with vault-reference prefix must pass")
    void secretPropertyWithVaultReferencePasses() {
        Map<String, Object> schema = Map.of(
                "dbPassword", Map.of("type", "string", "required", true, "secret", true));
        Map<String, Object> config = Map.of("dbPassword", "vault:secret/my-plugin/db-pass");

        assertThatNoException().isThrownBy(() -> validator.validate(PLUGIN_ID, schema, config));
    }

    @Test
    @DisplayName("secret property with literal value must fail with actionable message")
    void secretPropertyWithLiteralValueFails() {
        Map<String, Object> schema = Map.of(
                "apiKey", Map.of("type", "string", "required", true, "secret", true));
        Map<String, Object> config = Map.of("apiKey", "supersecret123");

        assertThatThrownBy(() -> validator.validate(PLUGIN_ID, schema, config))
                .isInstanceOf(PluginConfigSchemaValidator.PluginConfigSchemaViolationException.class)
                .satisfies(ex -> {
                    List<String> violations =
                            ((PluginConfigSchemaValidator.PluginConfigSchemaViolationException) ex).getViolations();
                    assertThat(violations)
                            .as("violation message must mention the key and reference format")
                            .anyMatch(v -> v.contains("apiKey") && v.contains("env:"));
                });
    }

    @Test
    @DisplayName("wrong type must fail with actionable message")
    void wrongTypeFails() {
        Map<String, Object> schema = Map.of(
                "maxRetries", Map.of("type", "number", "required", true));
        Map<String, Object> config = Map.of("maxRetries", "three");

        assertThatThrownBy(() -> validator.validate(PLUGIN_ID, schema, config))
                .isInstanceOf(PluginConfigSchemaValidator.PluginConfigSchemaViolationException.class)
                .satisfies(ex -> {
                    List<String> violations =
                            ((PluginConfigSchemaValidator.PluginConfigSchemaViolationException) ex).getViolations();
                    assertThat(violations).anyMatch(v -> v.contains("maxRetries") && v.contains("number"));
                });
    }

    @Test
    @DisplayName("multiple violations are all reported in a single exception")
    void multipleViolationsReported() {
        Map<String, Object> schema = Map.of(
                "apiKey", Map.of("type", "string", "required", true, "secret", true),
                "timeout", Map.of("type", "number", "required", true));
        Map<String, Object> config = Map.of(
                "apiKey", "plaintext-secret"
                // timeout is absent
        );

        assertThatThrownBy(() -> validator.validate(PLUGIN_ID, schema, config))
                .isInstanceOf(PluginConfigSchemaValidator.PluginConfigSchemaViolationException.class)
                .satisfies(ex -> {
                    List<String> violations =
                            ((PluginConfigSchemaValidator.PluginConfigSchemaViolationException) ex).getViolations();
                    assertThat(violations).hasSizeGreaterThanOrEqualTo(2);
                });
    }
}
