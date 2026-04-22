package com.ghatana.datacloud.feature;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DataCloudFeatureFlags} — evaluation, override, and fallback behaviour.
 */
@DisplayName("DataCloudFeatureFlags [GH-90000]")
class DataCloudFeatureFlagsTest {

    @BeforeEach
    void setUp() { // GH-90000
        DataCloudFeatureFlags.clearOverrides(); // GH-90000
        DataCloudFeatureFlags.resetGlobalForTesting(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        DataCloudFeatureFlags.clearOverrides(); // GH-90000
        DataCloudFeatureFlags.resetGlobalForTesting(); // GH-90000
    }

    // ────────────────────────────────────────────────────────────────────────
    // Default values (no env, no override, no global) // GH-90000
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Safe defaults before initialisation [GH-90000]")
    class SafeDefaults {

        @Test
        void onByDefaultFeaturesReturnTrue() { // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_ADVANCED_ANALYTICS)).isTrue(); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_REAL_TIME_STREAMING)).isTrue(); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_GOVERNANCE)).isTrue(); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_EXPORT)).isTrue(); // GH-90000
        }

        @Test
        void optInFeaturesReturnFalseByDefault() { // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_ML_INTELLIGENCE)).isFalse(); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_KNOWLEDGE_GRAPH)).isFalse(); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isFalse(); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_VOICE_GATEWAY)).isFalse(); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_ANOMALY_DETECTION)).isFalse(); // GH-90000
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test overrides
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Test overrides take highest precedence [GH-90000]")
    class Overrides {

        @Test
        void overrideEnablesOptInFeature() { // GH-90000
            DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_AI_ASSIST, true); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isTrue(); // GH-90000
        }

        @Test
        void overrideDisablesDefaultOnFeature() { // GH-90000
            DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_GOVERNANCE, false); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_GOVERNANCE)).isFalse(); // GH-90000
        }

        @Test
        void clearOverridesRestoresDefaults() { // GH-90000
            DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_AI_ASSIST, true); // GH-90000
            DataCloudFeatureFlags.clearOverrides(); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isFalse(); // GH-90000
        }

        @Test
        void overrideTakesPrecedenceOverGlobalInstance() { // GH-90000
            // Global loaded from environment — AI_ASSIST default is false
            DataCloudFeatureFlags global = DataCloudFeatureFlags.fromEnvironment(); // GH-90000
            DataCloudFeatureFlags.setGlobal(global); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isFalse(); // GH-90000

            // Override with true wins over the global instance
            DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_AI_ASSIST, true); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isTrue(); // GH-90000
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Global instance
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Global instance is consulted after overrides [GH-90000]")
    class GlobalInstance {

        @Test
        void fromEnvironmentReturnsExpectedDefaults() { // GH-90000
            DataCloudFeatureFlags flags = DataCloudFeatureFlags.fromEnvironment(); // GH-90000
            // Opt-in flags
            assertThat(flags.enabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isFalse(); // GH-90000
            assertThat(flags.enabled(DataCloudFeature.DATA_CLOUD_VOICE_GATEWAY)).isFalse(); // GH-90000
            // On-by-default flags
            assertThat(flags.enabled(DataCloudFeature.DATA_CLOUD_EXPORT)).isTrue(); // GH-90000
            assertThat(flags.enabled(DataCloudFeature.DATA_CLOUD_GOVERNANCE)).isTrue(); // GH-90000
        }

        @Test
        void globalSingletonIsUsedByStaticCheck() { // GH-90000
            DataCloudFeatureFlags flags = DataCloudFeatureFlags.fromEnvironment(); // GH-90000
            DataCloudFeatureFlags.setGlobal(flags); // GH-90000

            assertThat(DataCloudFeatureFlags.global()).isSameAs(flags); // GH-90000
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_EXPORT)).isTrue(); // GH-90000
        }

        @Test
        void resetGlobalForTestingClearsInstance() { // GH-90000
            DataCloudFeatureFlags.setGlobal(DataCloudFeatureFlags.fromEnvironment()); // GH-90000
            DataCloudFeatureFlags.resetGlobalForTesting(); // GH-90000
            assertThat(DataCloudFeatureFlags.global()).isNull(); // GH-90000
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // All features have a defined default
    // ────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("All DataCloudFeature values have a consistent default [GH-90000]")
    void allFeaturesHaveConsistentDefault() { // GH-90000
        for (DataCloudFeature feature : DataCloudFeature.values()) { // GH-90000
            boolean enumDefault = feature.defaultEnabled(); // GH-90000
            // With no global and no override, isEnabled must equal the enum default
            boolean staticVal = DataCloudFeatureFlags.isEnabled(feature); // GH-90000
            assertThat(staticVal) // GH-90000
                    .as("Feature %s should return its enum default %s when uninitialised", feature, enumDefault) // GH-90000
                    .isEqualTo(enumDefault); // GH-90000
        }
    }
}
