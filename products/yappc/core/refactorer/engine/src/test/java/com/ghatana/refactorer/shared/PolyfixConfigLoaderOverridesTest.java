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
    void applyOverrides_shouldApplyNestedProperties() { 
        // Given
        PolyfixConfig config = PolyfixConfigLoader.getDefaultConfig(); 
        Map<String, String> overrides =
                Map.of( 
                        BUDGETS_MAX_PASSES, "10",
                        POLICIES_TS_ALLOW_TEMPORARY_ANY, "false",
                        TOOLS_NODE, "/custom/path/to/node");

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
                        BUDGETS_MAX_PASSES, "5",
                        POLICIES_TS_ALLOW_TEMPORARY_ANY, "true",
                        "policies.pythonAddMissingImports", "false",
                        TOOLS_NODE, NODE_PATH);

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
                        TOOLS_NODE, NODE_PATH,
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
        overrides.put(BUDGETS_MAX_PASSES, null); 
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
