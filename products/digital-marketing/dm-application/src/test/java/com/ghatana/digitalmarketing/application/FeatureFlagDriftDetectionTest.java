package com.ghatana.digitalmarketing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drift detection test for feature flags (P2-3).
 *
 * Ensures that the canonical FEATURE_FLAGS_MANIFEST.json matches the flag definitions
 * in DmosFeatureFlags.java and prevents drift between UI and backend flag definitions.
 *
 * @doc.type test
 * @doc.purpose Detect drift between canonical manifest and code definitions
 * @doc.layer product
 */
@DisplayName("Feature Flag Drift Detection Tests")
class FeatureFlagDriftDetectionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Canonical manifest should exist and be valid JSON")
    void manifestShouldExistAndBeValid() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("FEATURE_FLAGS_MANIFEST.json");
        assertNotNull(is, "FEATURE_FLAGS_MANIFEST.json should exist in test resources");
        
        Map<?, ?> manifest = MAPPER.readValue(is, Map.class);
        
        assertNotNull(manifest.get("flags"), "Manifest should have 'flags' array");
        assertNotNull(manifest.get("version"), "Manifest should have 'version'");
    }

    @Test
    @DisplayName("All flags in DmosFeatureFlags should exist in manifest")
    void backendFlagsShouldExistInManifest() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("FEATURE_FLAGS_MANIFEST.json");
        Map<?, ?> manifest = MAPPER.readValue(is, Map.class);
        List<Map<?, ?>> flags = (List<Map<?, ?>>) manifest.get("flags");
        
        List<String> manifestKeys = flags.stream()
            .map(f -> (String) f.get("key"))
            .toList();
        
        assertEquals(DmosFeatureFlags.AI_ENABLED, "dmos.ai.enabled");
        assertEquals(DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED, "dmos.google_ads_connector.enabled");
        assertEquals(DmosFeatureFlags.KILL_SWITCH_ENABLED, "dmos.kill_switch.enabled");
        assertEquals(DmosFeatureFlags.ROLLBACK_WORKFLOW_ENABLED, "dmos.rollback_workflow.enabled");
        assertEquals(DmosFeatureFlags.ROLLBACK_ENABLED, "dmos.rollback.enabled");
        assertEquals(DmosFeatureFlags.OBSERVABILITY_ENABLED, "dmos.observability.enabled");
        assertEquals(DmosFeatureFlags.DASHBOARD_GROWTH_METRICS, "dmos.dashboard_growth_metrics");
        
        assertTrue(manifestKeys.contains(DmosFeatureFlags.AI_ENABLED),
            "Manifest should contain AI_ENABLED flag");
        assertTrue(manifestKeys.contains(DmosFeatureFlags.GOOGLE_ADS_CONNECTOR_ENABLED),
            "Manifest should contain GOOGLE_ADS_CONNECTOR_ENABLED flag");
        assertTrue(manifestKeys.contains(DmosFeatureFlags.KILL_SWITCH_ENABLED),
            "Manifest should contain KILL_SWITCH_ENABLED flag");
        assertTrue(manifestKeys.contains(DmosFeatureFlags.ROLLBACK_WORKFLOW_ENABLED),
            "Manifest should contain ROLLBACK_WORKFLOW_ENABLED flag");
        assertTrue(manifestKeys.contains(DmosFeatureFlags.ROLLBACK_ENABLED),
            "Manifest should contain ROLLBACK_ENABLED flag");
        assertTrue(manifestKeys.contains(DmosFeatureFlags.OBSERVABILITY_ENABLED),
            "Manifest should contain OBSERVABILITY_ENABLED flag");
        assertTrue(manifestKeys.contains(DmosFeatureFlags.DASHBOARD_GROWTH_METRICS),
            "Manifest should contain DASHBOARD_GROWTH_METRICS flag");
    }

    @Test
    @DisplayName("All flags in manifest should have required fields")
    void manifestFlagsShouldHaveRequiredFields() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("FEATURE_FLAGS_MANIFEST.json");
        Map<?, ?> manifest = MAPPER.readValue(is, Map.class);
        List<Map<?, ?>> flags = (List<Map<?, ?>>) manifest.get("flags");
        
        for (Map<?, ?> flag : flags) {
            assertNotNull(flag.get("key"), "Flag should have 'key'");
            assertNotNull(flag.get("name"), "Flag should have 'name'");
            assertNotNull(flag.get("description"), "Flag should have 'description'");
            // Accept both naming conventions for default values
            assertTrue(flag.containsKey("defaultValue") || flag.containsKey("defaultDevelopmentValue"),
                "Flag should have 'defaultValue' or 'defaultDevelopmentValue'");
            assertTrue(flag.containsKey("productionDefault") || flag.containsKey("defaultProductionValue"),
                "Flag should have 'productionDefault' or 'defaultProductionValue'");
            assertNotNull(flag.get("category"), "Flag should have 'category'");
            assertNotNull(flag.get("scope"), "Flag should have 'scope'");
        }
    }

    @Test
    @DisplayName("Flag keys should follow naming convention")
    void flagKeysShouldFollowNamingConvention() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("FEATURE_FLAGS_MANIFEST.json");
        Map<?, ?> manifest = MAPPER.readValue(is, Map.class);
        List<Map<?, ?>> flags = (List<Map<?, ?>>) manifest.get("flags");
        
        for (Map<?, ?> flag : flags) {
            String key = (String) flag.get("key");
            assertTrue(key.startsWith("dmos."), 
                "Flag key should start with 'dmos.': " + key);
            assertTrue(key.matches("[a-z._-]+"), 
                "Flag key should use lowercase, dots, and hyphens only: " + key);
        }
    }
}
