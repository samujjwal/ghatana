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

    // Constants for duplicate literals
    private static final String ESLINT = "eslint";
    private static final String RUFF = "ruff";
    private static final String BLACK = "black";
    private static final String MYPY = "mypy";
    private static final String SHELLCHECK = "shellcheck";
    private static final String SHFMT = "shfmt";
    private static final String CARGO = "cargo";
    private static final String RUSTFMT = "rustfmt";
    private static final String SEMGREP = "semgrep";
    private static final String NODE = "node";
    private static final String TSC = "./node_modules/.bin/tsc";
    private static final String JAVA = "java";
    private static final String SCHEMAS = "schemas";
    private static final String PRETTIER = "prettier";

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
        assertIterableEquals(List.of(JAVA, "typescript", "python"), config.languages());
        assertIterableEquals(List.of(SCHEMAS, "custom-schemas"), config.schemaPaths());
        assertEquals(5, config.budgets().maxPasses());
        assertEquals(10, config.budgets().maxEditsPerFile());
        assertFalse(config.policies().tsAllowTemporaryAny());
        assertTrue(config.policies().pythonAddMissingImports());
        assertTrue(config.policies().bashEnforceStrictMode());
        assertFalse(config.policies().jsonAutofillRequiredDefaults());
        assertEquals("/usr/local/bin/node", config.tools().node());
        assertEquals(ESLINT, config.tools().eslint());
        assertEquals(TSC, config.tools().tsc());
        assertEquals(PRETTIER, config.tools().prettier());
        assertEquals(RUFF, config.tools().ruff());
        assertEquals(BLACK, config.tools().black());
        assertEquals(MYPY, config.tools().mypy());
        assertEquals(SHELLCHECK, config.tools().shellcheck());
        assertEquals(SHFMT, config.tools().shfmt());
        assertEquals(CARGO, config.tools().cargo());
        assertEquals(RUSTFMT, config.tools().rustfmt());
        assertEquals(SEMGREP, config.tools().semgrep());
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
                        List.of(SCHEMAS),
                        new PolyfixConfig.Budgets(1, 1),
                        new PolyfixConfig.Policies(true, true, true, false),
                        new PolyfixConfig.Tools(
                                NODE,
                                ESLINT,
                                TSC,
                                PRETTIER,
                                RUFF,
                                BLACK,
                                MYPY,
                                SHELLCHECK,
                                SHFMT,
                                CARGO,
                                RUSTFMT,
                                SEMGREP));

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
                        List.of(JAVA),
                        List.of(SCHEMAS),
                        null, // budgets is null
                        new PolyfixConfig.Policies(true, true, true, false),
                        new PolyfixConfig.Tools(
                                NODE,
                                ESLINT,
                                TSC,
                                PRETTIER,
                                RUFF,
                                BLACK,
                                MYPY,
                                SHELLCHECK,
                                SHFMT,
                                CARGO,
                                RUSTFMT,
                                SEMGREP));

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
                        List.of(JAVA),
                        List.of(SCHEMAS),
                        new PolyfixConfig.Budgets(1, 1),
                        null, // policies is null
                        new PolyfixConfig.Tools(
                                NODE,
                                ESLINT,
                                TSC,
                                PRETTIER,
                                RUFF,
                                BLACK,
                                MYPY,
                                SHELLCHECK,
                                SHFMT,
                                CARGO,
                                RUSTFMT,
                                SEMGREP));

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
                        List.of(JAVA),
                        List.of(SCHEMAS),
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
                        List.of(JAVA),
                        List.of(SCHEMAS),
                        new PolyfixConfig.Budgets(0, 1), // maxPasses < 1
                        new PolyfixConfig.Policies(true, true, true, false),
                        new PolyfixConfig.Tools(
                                NODE,
                                ESLINT,
                                TSC,
                                PRETTIER,
                                RUFF,
                                BLACK,
                                MYPY,
                                SHELLCHECK,
                                SHFMT,
                                CARGO,
                                RUSTFMT,
                                SEMGREP));

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
                        List.of(JAVA),
                        List.of(SCHEMAS),
                        new PolyfixConfig.Budgets(1, -1), // maxEditsPerFile < 0
                        new PolyfixConfig.Policies(true, true, true, false),
                        new PolyfixConfig.Tools(
                                NODE,
                                ESLINT,
                                TSC,
                                PRETTIER,
                                RUFF,
                                BLACK,
                                MYPY,
                                SHELLCHECK,
                                SHFMT,
                                CARGO,
                                RUSTFMT,
                                SEMGREP));

        // When / Then
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config));
        assertTrue(exception.getMessage().contains("maxEditsPerFile cannot be negative"));
    }
}
