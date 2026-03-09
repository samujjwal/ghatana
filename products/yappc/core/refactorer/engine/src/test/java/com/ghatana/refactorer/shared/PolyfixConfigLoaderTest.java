/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**

 * @doc.type class

 * @doc.purpose Handles polyfix config loader test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PolyfixConfigLoaderTest {

    @TempDir Path tempDir;
    private Path configFile;

    @BeforeEach
    void setUp() throws IOException {
        // Copy test config to temp directory
        configFile = tempDir.resolve("polyfix.json");
        Files.copy(getClass().getResourceAsStream("/test-configs/valid-config.json"), configFile);
    }

    @Test
    void load_shouldLoadValidConfig() throws Exception {
        // When
        PolyfixConfig config = PolyfixConfigLoader.load(tempDir, null);

        // Then
        assertNotNull(config);
        assertIterableEquals(List.of("java", "typescript", "python"), config.languages());
        assertIterableEquals(List.of("schemas", "custom-schemas"), config.schemaPaths());
        assertEquals(5, config.budgets().maxPasses());
        assertEquals(10, config.budgets().maxEditsPerFile());
        assertFalse(config.policies().tsAllowTemporaryAny());
        assertTrue(config.policies().pythonAddMissingImports());
        assertTrue(config.policies().bashEnforceStrictMode());
        assertFalse(config.policies().jsonAutofillRequiredDefaults());
        assertEquals("/usr/local/bin/node", config.tools().node());
        assertEquals("eslint", config.tools().eslint());
        assertEquals("./node_modules/.bin/tsc", config.tools().tsc());
        assertEquals("prettier", config.tools().prettier());
        assertEquals("ruff", config.tools().ruff());
        assertEquals("black", config.tools().black());
        assertEquals("mypy", config.tools().mypy());
        assertEquals("shellcheck", config.tools().shellcheck());
        assertEquals("shfmt", config.tools().shfmt());
        assertEquals("cargo", config.tools().cargo());
        assertEquals("rustfmt", config.tools().rustfmt());
        assertEquals("semgrep", config.tools().semgrep());
    }

    @Test
    void load_shouldUseDefaultsWhenNoConfigFile() throws Exception {
        // Given
        Files.deleteIfExists(configFile);

        // When
        PolyfixConfig config = PolyfixConfigLoader.load(tempDir, null);

        // Then
        assertNotNull(config);
        assertFalse(config.languages().isEmpty());
        assertTrue(config.budgets().maxPasses() > 0);
    }

    @Test
    void load_shouldApplyOverrides() throws Exception {
        // Given
        Map<String, String> overrides =
                Map.of(
                        "budgets.maxPasses", "10",
                        "policies.tsAllowTemporaryAny", "false",
                        "tools.node", "/custom/path/to/node");

        // When
        PolyfixConfig config = PolyfixConfigLoader.load(tempDir, overrides);

        // Then
        assertEquals(10, config.budgets().maxPasses());
        assertFalse(config.policies().tsAllowTemporaryAny());
        assertEquals("/custom/path/to/node", config.tools().node());
    }

    @Test
    void load_shouldThrowOnInvalidConfig() throws Exception {
        // Given
        Files.writeString(configFile, "{ \"invalid\": \"config\" }");

        // When / Then
        Exception exception =
                assertThrows(IOException.class, () -> PolyfixConfigLoader.load(tempDir, null));
        assertNotNull(exception);
        assertTrue(
                exception.getMessage().contains("Invalid configuration format")
                        || exception.getCause()
                                instanceof
                                com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException);
    }

    @Test
    void load_shouldValidateLanguageSupport() throws Exception {
        // Given
        String invalidConfig =
                "{ \"languages\": [\"unsupported-lang\"], \"budgets\": {\"maxPasses\": 1,"
                        + " \"maxEditsPerFile\": 1}, \"policies\": {\"tsAllowTemporaryAny\": true,"
                        + " \"pythonAddMissingImports\": true, \"bashEnforceStrictMode\": true,"
                        + " \"jsonAutofillRequiredDefaults\": false}, \"tools\":"
                        + " {\"node\":\"node\",\"eslint\":\"eslint\",\"tsc\":\"tsc\","
                        + "\"prettier\":\"prettier\",\"ruff\":\"ruff\",\"black\":\"black\","
                        + "\"mypy\":\"mypy\",\"shellcheck\":\"shellcheck\",\"shfmt\":\"shfmt\","
                        + "\"cargo\":\"cargo\",\"rustfmt\":\"rustfmt\",\"semgrep\":\"semgrep\"}}";

        Files.writeString(configFile, invalidConfig);

        // When / Then
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.load(tempDir, null));
        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("Unsupported language"));
    }

    @Test
    void load_shouldHandleInvalidJson() throws Exception {
        // Given
        Files.writeString(configFile, "{ invalid json }");

        // When / Then
        assertThrows(IOException.class, () -> PolyfixConfigLoader.load(tempDir, null));
    }

    @Test
    void load_shouldHandleMissingConfigFile() throws Exception {
        // Given
        Files.deleteIfExists(configFile);

        // When
        PolyfixConfig config = PolyfixConfigLoader.load(tempDir, null);

        // Then - should use defaults
        assertNotNull(config);
        assertFalse(config.languages().isEmpty());
        assertTrue(config.budgets().maxPasses() > 0);
    }

    @Test
    void validateConfig_shouldThrowOnNullLanguages() {
        // Given
        PolyfixConfig config =
                new PolyfixConfig(
                        null, // languages is null
                        List.of("schemas"),
                        new PolyfixConfig.Budgets(1, 1),
                        new PolyfixConfig.Policies(true, true, true, false),
                        new PolyfixConfig.Tools(
                                "node",
                                "eslint",
                                "tsc",
                                "prettier",
                                "ruff",
                                "black",
                                "mypy",
                                "shellcheck",
                                "shfmt",
                                "cargo",
                                "rustfmt",
                                "semgrep"));

        // When / Then
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config));
        assertTrue(exception.getMessage().contains("languages cannot be null"));
    }

    @Test
    void validateConfig_shouldThrowOnNullBudgets() {
        // Given
        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("java"),
                        List.of("schemas"),
                        null, // budgets is null
                        new PolyfixConfig.Policies(true, true, true, false),
                        new PolyfixConfig.Tools(
                                "node",
                                "eslint",
                                "tsc",
                                "prettier",
                                "ruff",
                                "black",
                                "mypy",
                                "shellcheck",
                                "shfmt",
                                "cargo",
                                "rustfmt",
                                "semgrep"));

        // When / Then
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config));
        assertTrue(exception.getMessage().contains("budgets cannot be null"));
    }

    @Test
    void validateConfig_shouldThrowOnNullPolicies() {
        // Given
        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("java"),
                        List.of("schemas"),
                        new PolyfixConfig.Budgets(1, 1),
                        null, // policies is null
                        new PolyfixConfig.Tools(
                                "node",
                                "eslint",
                                "tsc",
                                "prettier",
                                "ruff",
                                "black",
                                "mypy",
                                "shellcheck",
                                "shfmt",
                                "cargo",
                                "rustfmt",
                                "semgrep"));

        // When / Then
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config));
        assertTrue(exception.getMessage().contains("policies cannot be null"));
    }

    @Test
    void validateConfig_shouldThrowOnNullTools() {
        // Given
        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("java"),
                        List.of("schemas"),
                        new PolyfixConfig.Budgets(1, 1),
                        new PolyfixConfig.Policies(true, true, true, false),
                        null // tools is null
                        );

        // When / Then
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config));
        assertTrue(exception.getMessage().contains("tools cannot be null"));
    }

    @Test
    void validateConfig_shouldThrowOnInvalidMaxPasses() {
        // Given
        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("java"),
                        List.of("schemas"),
                        new PolyfixConfig.Budgets(0, 1), // maxPasses < 1
                        new PolyfixConfig.Policies(true, true, true, false),
                        new PolyfixConfig.Tools(
                                "node",
                                "eslint",
                                "tsc",
                                "prettier",
                                "ruff",
                                "black",
                                "mypy",
                                "shellcheck",
                                "shfmt",
                                "cargo",
                                "rustfmt",
                                "semgrep"));

        // When / Then
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config));
        assertTrue(exception.getMessage().contains("maxPasses must be at least 1"));
    }

    @Test
    void validateConfig_shouldThrowOnInvalidMaxEditsPerFile() {
        // Given
        PolyfixConfig config =
                new PolyfixConfig(
                        List.of("java"),
                        List.of("schemas"),
                        new PolyfixConfig.Budgets(1, -1), // maxEditsPerFile < 0
                        new PolyfixConfig.Policies(true, true, true, false),
                        new PolyfixConfig.Tools(
                                "node",
                                "eslint",
                                "tsc",
                                "prettier",
                                "ruff",
                                "black",
                                "mypy",
                                "shellcheck",
                                "shfmt",
                                "cargo",
                                "rustfmt",
                                "semgrep"));

        // When / Then
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config));
        assertTrue(exception.getMessage().contains("maxEditsPerFile cannot be negative"));
    }
}
