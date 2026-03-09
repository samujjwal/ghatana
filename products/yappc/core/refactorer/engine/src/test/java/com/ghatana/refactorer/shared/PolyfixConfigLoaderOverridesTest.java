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

    @Test
    void applyOverrides_shouldApplyNestedProperties() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides =
                Map.of(
                        "budgets.maxPasses", "10",
                        "policies.tsAllowTemporaryAny", "false",
                        "tools.node", "/custom/path/to/node");

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides);

        // Then
        assertEquals(10, result.budgets().maxPasses());
        assertFalse(result.policies().tsAllowTemporaryAny());
        assertEquals("/custom/path/to/node", result.tools().node());
    }

    @Test
    void applyOverrides_shouldHandleNestedProperties() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides =
                Map.of(
                        "budgets.maxPasses", "5",
                        "policies.tsAllowTemporaryAny", "true",
                        "policies.pythonAddMissingImports", "false",
                        "tools.node", "/usr/local/bin/node");

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides);

        // Then
        assertEquals(5, result.budgets().maxPasses());
        assertTrue(result.policies().tsAllowTemporaryAny());
        assertFalse(result.policies().pythonAddMissingImports());
        assertEquals("/usr/local/bin/node", result.tools().node());
    }

    @Test
    void applyOverrides_shouldHandleStringValues() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides =
                Map.of(
                        "tools.node", "/usr/local/bin/node",
                        "tools.eslint", "/usr/local/bin/eslint",
                        "tools.tsc", "/usr/local/bin/tsc");

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides);

        // Then
        assertEquals("/usr/local/bin/node", result.tools().node());
        assertEquals("/usr/local/bin/eslint", result.tools().eslint());
        assertEquals("/usr/local/bin/tsc", result.tools().tsc());
    }

    @Test
    void applyOverrides_shouldHandleInvalidPaths() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides = Map.of("nonexistent.path", "value");

        // When / Then
        assertThrows(
                IllegalArgumentException.class,
                () -> PolyfixConfigLoader.applyOverrides(config, overrides));
    }

    @Test
    void applyOverrides_shouldHandleNullValuesInMap() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides = new java.util.HashMap<>();
        overrides.put("budgets.maxPasses", null);
        overrides.put(null, "value");

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides);

        // Then - should not throw and should return the original config
        assertEquals(config, result);
    }

    @Test
    void applyOverrides_shouldHandleEmptyMap() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides = Collections.emptyMap();

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides);

        // Then - should return the original config
        assertEquals(config, result);
    }

    @Test
    void applyOverrides_shouldHandleNullMap() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, null);

        // Then - should return the original config
        assertEquals(config, result);
    }

    @Test
    void applyOverrides_shouldHandleInvalidBooleanValue() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides = Map.of("policies.tsAllowTemporaryAny", "not-a-boolean");

        // When / Then
        Exception exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> PolyfixConfigLoader.applyOverrides(config, overrides));

        assertTrue(
                exception.getCause()
                        instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException);
    }

    @Test
    void applyOverrides_shouldHandleInvalidIntegerValue() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides = Map.of("budgets.maxPasses", "not-an-integer");

        // When / Then
        Exception exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> PolyfixConfigLoader.applyOverrides(config, overrides));

        assertTrue(
                exception.getCause()
                        instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException);
    }

    @Test
    void applyOverrides_shouldHandleUnsupportedType() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides = Map.of("languages", "[java,typescript]");

        // When / Then - Should throw IllegalStateException with MismatchedInputException as cause
        Exception exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> PolyfixConfigLoader.applyOverrides(config, overrides));

        assertTrue(
                exception.getCause()
                        instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException);
    }

    @Test
    void applyOverrides_shouldHandleBooleanValues() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides =
                Map.of(
                        "policies.tsAllowTemporaryAny", "true",
                        "policies.pythonAddMissingImports", "false");

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides);

        // Then
        assertTrue(result.policies().tsAllowTemporaryAny());
        assertFalse(result.policies().pythonAddMissingImports());
    }

    @Test
    void applyOverrides_shouldHandleNumericValues() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides =
                Map.of(
                        "budgets.maxPasses", "5",
                        "budgets.maxEditsPerFile", "15");

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides);

        // Then
        assertEquals(5, result.budgets().maxPasses());
        assertEquals(15, result.budgets().maxEditsPerFile());
    }

    @Test
    void applyOverrides_shouldThrowOnInvalidPath() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides = Map.of("nonexistent.path", "value");

        // When / Then
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> PolyfixConfigLoader.applyOverrides(config, overrides));
        assertTrue(exception.getMessage().contains("Invalid configuration path"));
    }

    @Test
    void applyOverrides_shouldHandleEmptyOverrides() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();
        Map<String, String> overrides = Map.of();

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, overrides);

        // Then - should return the same config
        assertEquals(config, result);
    }

    @Test
    void applyOverrides_shouldHandleNullOverrides() {
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig();

        // When
        PolyfixConfig result = PolyfixConfigLoader.applyOverrides(config, null);

        // Then - should return the same config
        assertEquals(config, result);
    }
}
