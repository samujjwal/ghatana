/* Licensed under Apache-2.0 */
package com.ghatana.refactorer.shared;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**

 * @doc.type class

 * @doc.purpose Handles polyfix config loader overrides test operations

 * @doc.layer core

 * @doc.pattern Test

 */

class PolyfixConfigLoaderOverridesTest {

    // Constants for duplicate literals
    private static final String BUDGETS_MAX_PASSES = "budgets.maxPasses";
    private static final String POLICIES_TS_ALLOW_TEMPORARY_ANY = "policies.tsAllowTemporaryAny";
    private static final String TOOLS_NODE = "tools.node";
    private static final String NODE_PATH = "/usr/local/bin/node";

    @Test
    void applyOverrides_shouldApplyNestedProperties() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides =
                Map.of( // GH-90000
                        BUDGETS_MAX_PASSES, "10",
                        POLICIES_TS_ALLOW_TEMPORARY_ANY, "false",
                        TOOLS_NODE, "/custom/path/to/node");

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides); // GH-90000

        // Then
        assertEquals(10, result.budgets().maxPasses()); // GH-90000
        assertFalse(result.policies().tsAllowTemporaryAny()); // GH-90000
        assertEquals("/custom/path/to/node", result.tools().node()); // GH-90000
    }

    @Test
    void applyOverrides_shouldHandleNestedProperties() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides =
                Map.of( // GH-90000
                        BUDGETS_MAX_PASSES, "5",
                        POLICIES_TS_ALLOW_TEMPORARY_ANY, "true",
                        "policies.pythonAddMissingImports", "false",
                        TOOLS_NODE, NODE_PATH);

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides); // GH-90000

        // Then
        assertEquals(5, result.budgets().maxPasses()); // GH-90000
        assertTrue(result.policies().tsAllowTemporaryAny()); // GH-90000
        assertFalse(result.policies().pythonAddMissingImports()); // GH-90000
        assertEquals("/usr/local/bin/node", result.tools().node()); // GH-90000
    }

    @Test
    void applyOverrides_shouldHandleStringValues() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides =
                Map.of( // GH-90000
                        TOOLS_NODE, NODE_PATH,
                        "tools.eslint", "/usr/local/bin/eslint",
                        "tools.tsc", "/usr/local/bin/tsc");

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides); // GH-90000

        // Then
        assertEquals("/usr/local/bin/node", result.tools().node()); // GH-90000
        assertEquals("/usr/local/bin/eslint", result.tools().eslint()); // GH-90000
        assertEquals("/usr/local/bin/tsc", result.tools().tsc()); // GH-90000
    }

    @Test
    void applyOverrides_shouldHandleInvalidPaths() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides = Map.of("nonexistent.path", "value"); // GH-90000

        // When / Then
        assertThrows( // GH-90000
                IllegalArgumentException.class,
                () -> PolyfixConfigLoader.applyOverrides(config, overrides)); // GH-90000
    }

    @Test
    void applyOverrides_shouldHandleNullValuesInMap() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides = new java.util.HashMap<>(); // GH-90000
        overrides.put(BUDGETS_MAX_PASSES, null); // GH-90000
        overrides.put(null, "value"); // GH-90000

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides); // GH-90000

        // Then - should not throw and should return the original config
        assertEquals(config, result); // GH-90000
    }

    @Test
    void applyOverrides_shouldHandleEmptyMap() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides = Collections.emptyMap(); // GH-90000

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides); // GH-90000

        // Then - should return the original config
        assertEquals(config, result); // GH-90000
    }

    @Test
    void applyOverrides_shouldHandleNullMap() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, null); // GH-90000

        // Then - should return the original config
        assertEquals(config, result); // GH-90000
    }

    @Test
    void applyOverrides_shouldHandleInvalidBooleanValue() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides = Map.of("policies.tsAllowTemporaryAny", "not-a-boolean"); // GH-90000

        // When / Then
        Exception exception =
                assertThrows( // GH-90000
                        IllegalStateException.class,
                        () -> PolyfixConfigLoader.applyOverrides(config, overrides)); // GH-90000

        assertTrue( // GH-90000
                exception.getCause() // GH-90000
                        instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException);
    }

    @Test
    void applyOverrides_shouldHandleInvalidIntegerValue() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides = Map.of("budgets.maxPasses", "not-an-integer"); // GH-90000

        // When / Then
        Exception exception =
                assertThrows( // GH-90000
                        IllegalStateException.class,
                        () -> PolyfixConfigLoader.applyOverrides(config, overrides)); // GH-90000

        assertTrue( // GH-90000
                exception.getCause() // GH-90000
                        instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException);
    }

    @Test
    void applyOverrides_shouldHandleUnsupportedType() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides = Map.of("languages", "[java,typescript]"); // GH-90000

        // When / Then - Should throw IllegalStateException with MismatchedInputException as cause
        Exception exception =
                assertThrows( // GH-90000
                        IllegalStateException.class,
                        () -> PolyfixConfigLoader.applyOverrides(config, overrides)); // GH-90000

        assertTrue( // GH-90000
                exception.getCause() // GH-90000
                        instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException);
    }

    @Test
    void applyOverrides_shouldHandleBooleanValues() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides =
                Map.of( // GH-90000
                        "policies.tsAllowTemporaryAny", "true",
                        "policies.pythonAddMissingImports", "false");

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides); // GH-90000

        // Then
        assertTrue(result.policies().tsAllowTemporaryAny()); // GH-90000
        assertFalse(result.policies().pythonAddMissingImports()); // GH-90000
    }

    @Test
    void applyOverrides_shouldHandleNumericValues() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides =
                Map.of( // GH-90000
                        "budgets.maxPasses", "5",
                        "budgets.maxEditsPerFile", "15");

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides); // GH-90000

        // Then
        assertEquals(5, result.budgets().maxPasses()); // GH-90000
        assertEquals(15, result.budgets().maxEditsPerFile()); // GH-90000
    }

    @Test
    void applyOverrides_shouldThrowOnInvalidPath() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides = Map.of("nonexistent.path", "value"); // GH-90000

        // When / Then
        IllegalArgumentException exception =
                assertThrows( // GH-90000
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.applyOverrides(config, overrides)); // GH-90000
        assertTrue(exception.getMessage().contains("Invalid configuration path"));
    }

    @Test
    void applyOverrides_shouldHandleEmptyOverrides() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000
        Map<String, String> overrides = Map.of(); // GH-90000

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides); // GH-90000

        // Then - should return the same config
        assertEquals(config, result); // GH-90000
    }

    @Test
    void applyOverrides_shouldHandleNullOverrides() { // GH-90000
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); // GH-90000

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, null); // GH-90000

        // Then - should return the same config
        assertEquals(config, result); // GH-90000
    }
}
