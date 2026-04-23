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
    void setUp() throws IOException { // GH-90000
        // Copy test config to temp directory
        configFile = tempDir.resolve("polyfix.json");
        Files.copy(getClass().getResourceAsStream("/test-configs/valid-config.json"), configFile);
    }

    @Test
    void load_shouldLoadValidConfig() throws Exception { // GH-90000
        // When
        PolyfixConfig config = PolyfixConfigLoader.load(tempDir, null); // GH-90000

        // Then
        assertNotNull(config); // GH-90000
        assertIterableEquals(List.of(JAVA, "typescript", "python"), config.languages()); // GH-90000
        assertIterableEquals(List.of(SCHEMAS, "custom-schemas"), config.schemaPaths()); // GH-90000
        assertEquals(5, config.budgets().maxPasses()); // GH-90000
        assertEquals(10, config.budgets().maxEditsPerFile()); // GH-90000
        assertFalse(config.policies().tsAllowTemporaryAny()); // GH-90000
        assertTrue(config.policies().pythonAddMissingImports()); // GH-90000
        assertTrue(config.policies().bashEnforceStrictMode()); // GH-90000
        assertFalse(config.policies().jsonAutofillRequiredDefaults()); // GH-90000
        assertEquals("/usr/local/bin/node", config.tools().node()); // GH-90000
        assertEquals(ESLINT, config.tools().eslint()); // GH-90000
        assertEquals(TSC, config.tools().tsc()); // GH-90000
        assertEquals(PRETTIER, config.tools().prettier()); // GH-90000
        assertEquals(RUFF, config.tools().ruff()); // GH-90000
        assertEquals(BLACK, config.tools().black()); // GH-90000
        assertEquals(MYPY, config.tools().mypy()); // GH-90000
        assertEquals(SHELLCHECK, config.tools().shellcheck()); // GH-90000
        assertEquals(SHFMT, config.tools().shfmt()); // GH-90000
        assertEquals(CARGO, config.tools().cargo()); // GH-90000
        assertEquals(RUSTFMT, config.tools().rustfmt()); // GH-90000
        assertEquals(SEMGREP, config.tools().semgrep()); // GH-90000
    }

    @Test
    void load_shouldUseDefaultsWhenNoConfigFile() throws Exception { // GH-90000
        // Given
        Files.deleteIfExists(configFile); // GH-90000

        // When
        PolyfixConfig config = PolyfixConfigLoader.load(tempDir, null); // GH-90000

        // Then
        assertNotNull(config); // GH-90000
        assertFalse(config.languages().isEmpty()); // GH-90000
        assertTrue(config.budgets().maxPasses() > 0); // GH-90000
    }

    @Test
    void load_shouldApplyOverrides() throws Exception { // GH-90000
        // Given
        Map<String, String> overrides =
                Map.of( // GH-90000
                        "budgets.maxPasses", "10",
                        "policies.tsAllowTemporaryAny", "false",
                        "tools.node", "/custom/path/to/node");

        // When
        PolyfixConfig config = PolyfixConfigLoader.load(tempDir, overrides); // GH-90000

        // Then
        assertEquals(10, config.budgets().maxPasses()); // GH-90000
        assertFalse(config.policies().tsAllowTemporaryAny()); // GH-90000
        assertEquals("/custom/path/to/node", config.tools().node()); // GH-90000
    }

    @Test
    void load_shouldThrowOnInvalidConfig() throws Exception { // GH-90000
        // Given
        Files.writeString(configFile, "{ \"invalid\": \"config\" }"); // GH-90000

        // When / Then
        Exception exception =
                assertThrows(IOException.class, () -> PolyfixConfigLoader.load(tempDir, null)); // GH-90000
        assertNotNull(exception); // GH-90000
        assertTrue( // GH-90000
                exception.getMessage().contains("Invalid configuration format")
                        || exception.getCause() // GH-90000
                                instanceof
                                com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException);
    }

    @Test
    void load_shouldValidateLanguageSupport() throws Exception { // GH-90000
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

        Files.writeString(configFile, invalidConfig); // GH-90000

        // When / Then
        Exception exception =
                assertThrows( // GH-90000
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.load(tempDir, null)); // GH-90000
        assertNotNull(exception); // GH-90000
        assertTrue(exception.getMessage().contains("Unsupported language"));
    }

    @Test
    void load_shouldHandleInvalidJson() throws Exception { // GH-90000
        // Given
        Files.writeString(configFile, "{ invalid json }"); // GH-90000

        // When / Then
        assertThrows(IOException.class, () -> PolyfixConfigLoader.load(tempDir, null)); // GH-90000
    }

    @Test
    void load_shouldHandleMissingConfigFile() throws Exception { // GH-90000
        // Given
        Files.deleteIfExists(configFile); // GH-90000

        // When
        PolyfixConfig config = PolyfixConfigLoader.load(tempDir, null); // GH-90000

        // Then - should use defaults
        assertNotNull(config); // GH-90000
        assertFalse(config.languages().isEmpty()); // GH-90000
        assertTrue(config.budgets().maxPasses() > 0); // GH-90000
    }

    @Test
    void validateConfig_shouldThrowOnNullLanguages() { // GH-90000
        // Given
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        null, // languages is null
                        List.of(SCHEMAS), // GH-90000
                        new PolyfixConfig.Budgets(1, 1), // GH-90000
                        new PolyfixConfig.Policies(true, true, true, false), // GH-90000
                        new PolyfixConfig.Tools( // GH-90000
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
                assertThrows( // GH-90000
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config)); // GH-90000
        assertTrue(exception.getMessage().contains("languages cannot be null"));
    }

    @Test
    void validateConfig_shouldThrowOnNullBudgets() { // GH-90000
        // Given
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of(JAVA), // GH-90000
                        List.of(SCHEMAS), // GH-90000
                        null, // budgets is null
                        new PolyfixConfig.Policies(true, true, true, false), // GH-90000
                        new PolyfixConfig.Tools( // GH-90000
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
                assertThrows( // GH-90000
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config)); // GH-90000
        assertTrue(exception.getMessage().contains("budgets cannot be null"));
    }

    @Test
    void validateConfig_shouldThrowOnNullPolicies() { // GH-90000
        // Given
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of(JAVA), // GH-90000
                        List.of(SCHEMAS), // GH-90000
                        new PolyfixConfig.Budgets(1, 1), // GH-90000
                        null, // policies is null
                        new PolyfixConfig.Tools( // GH-90000
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
                assertThrows( // GH-90000
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config)); // GH-90000
        assertTrue(exception.getMessage().contains("policies cannot be null"));
    }

    @Test
    void validateConfig_shouldThrowOnNullTools() { // GH-90000
        // Given
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of(JAVA), // GH-90000
                        List.of(SCHEMAS), // GH-90000
                        new PolyfixConfig.Budgets(1, 1), // GH-90000
                        new PolyfixConfig.Policies(true, true, true, false), // GH-90000
                        null // tools is null
                        );

        // When / Then
        IllegalArgumentException exception =
                assertThrows( // GH-90000
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config)); // GH-90000
        assertTrue(exception.getMessage().contains("tools cannot be null"));
    }

    @Test
    void validateConfig_shouldThrowOnInvalidMaxPasses() { // GH-90000
        // Given
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of(JAVA), // GH-90000
                        List.of(SCHEMAS), // GH-90000
                        new PolyfixConfig.Budgets(0, 1), // maxPasses < 1 // GH-90000
                        new PolyfixConfig.Policies(true, true, true, false), // GH-90000
                        new PolyfixConfig.Tools( // GH-90000
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
                assertThrows( // GH-90000
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config)); // GH-90000
        assertTrue(exception.getMessage().contains("maxPasses must be at least 1"));
    }

    @Test
    void validateConfig_shouldThrowOnInvalidMaxEditsPerFile() { // GH-90000
        // Given
        PolyfixConfig config =
                new PolyfixConfig( // GH-90000
                        List.of(JAVA), // GH-90000
                        List.of(SCHEMAS), // GH-90000
                        new PolyfixConfig.Budgets(1, -1), // maxEditsPerFile < 0 // GH-90000
                        new PolyfixConfig.Policies(true, true, true, false), // GH-90000
                        new PolyfixConfig.Tools( // GH-90000
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
                assertThrows( // GH-90000
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.validateConfig(config)); // GH-90000
        assertTrue(exception.getMessage().contains("maxEditsPerFile cannot be negative"));
    }
}
