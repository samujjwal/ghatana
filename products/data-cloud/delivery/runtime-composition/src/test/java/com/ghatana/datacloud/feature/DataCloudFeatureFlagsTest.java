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
@DisplayName("DataCloudFeatureFlags")
class DataCloudFeatureFlagsTest {

    @BeforeEach
    void setUp() { 
        DataCloudFeatureFlags.clearOverrides(); 
        DataCloudFeatureFlags.resetGlobalForTesting(); 
    }

    @AfterEach
    void tearDown() { 
        DataCloudFeatureFlags.clearOverrides(); 
        DataCloudFeatureFlags.resetGlobalForTesting(); 
    }

    // ────────────────────────────────────────────────────────────────────────
    // Default values (no env, no override, no global) 
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Safe defaults before initialisation")
    class SafeDefaults {

        @Test
        void onByDefaultFeaturesReturnTrue() { 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_ADVANCED_ANALYTICS)).isTrue(); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_REAL_TIME_STREAMING)).isTrue(); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_GOVERNANCE)).isTrue(); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_EXPORT)).isTrue(); 
        }

        @Test
        void optInFeaturesReturnFalseByDefault() { 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_ML_INTELLIGENCE)).isFalse(); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_KNOWLEDGE_GRAPH)).isFalse(); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isFalse(); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_VOICE_GATEWAY)).isFalse(); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_ANOMALY_DETECTION)).isFalse(); 
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test overrides
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Test overrides take highest precedence")
    class Overrides {

        @Test
        void overrideEnablesOptInFeature() { 
            DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_AI_ASSIST, true); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isTrue(); 
        }

        @Test
        void overrideDisablesDefaultOnFeature() { 
            DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_GOVERNANCE, false); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_GOVERNANCE)).isFalse(); 
        }

        @Test
        void clearOverridesRestoresDefaults() { 
            DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_AI_ASSIST, true); 
            DataCloudFeatureFlags.clearOverrides(); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isFalse(); 
        }

        @Test
        void overrideTakesPrecedenceOverGlobalInstance() { 
            // Global loaded from environment — AI_ASSIST default is false
            DataCloudFeatureFlags global = DataCloudFeatureFlags.fromEnvironment(); 
            DataCloudFeatureFlags.setGlobal(global); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isFalse(); 

            // Override with true wins over the global instance
            DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_AI_ASSIST, true); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isTrue(); 
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Global instance
    // ────────────────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Global instance is consulted after overrides")
    class GlobalInstance {

        @Test
        void fromEnvironmentReturnsExpectedDefaults() { 
            DataCloudFeatureFlags flags = DataCloudFeatureFlags.fromEnvironment(); 
            // Opt-in flags
            assertThat(flags.enabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)).isFalse(); 
            assertThat(flags.enabled(DataCloudFeature.DATA_CLOUD_VOICE_GATEWAY)).isFalse(); 
            // On-by-default flags
            assertThat(flags.enabled(DataCloudFeature.DATA_CLOUD_EXPORT)).isTrue(); 
            assertThat(flags.enabled(DataCloudFeature.DATA_CLOUD_GOVERNANCE)).isTrue(); 
        }

        @Test
        void globalSingletonIsUsedByStaticCheck() { 
            DataCloudFeatureFlags flags = DataCloudFeatureFlags.fromEnvironment(); 
            DataCloudFeatureFlags.setGlobal(flags); 

            assertThat(DataCloudFeatureFlags.global()).isSameAs(flags); 
            assertThat(DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_EXPORT)).isTrue(); 
        }

        @Test
        void resetGlobalForTestingClearsInstance() { 
            DataCloudFeatureFlags.setGlobal(DataCloudFeatureFlags.fromEnvironment()); 
            DataCloudFeatureFlags.resetGlobalForTesting(); 
            assertThat(DataCloudFeatureFlags.global()).isNull(); 
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // All features have a defined default
    // ────────────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("All DataCloudFeature values have a consistent default")
    void allFeaturesHaveConsistentDefault() { 
        for (DataCloudFeature feature : DataCloudFeature.values()) { 
            boolean enumDefault = feature.defaultEnabled(); 
            // With no global and no override, isEnabled must equal the enum default
            boolean staticVal = DataCloudFeatureFlags.isEnabled(feature); 
            assertThat(staticVal) 
                    .as("Feature %s should return its enum default %s when uninitialised", feature, enumDefault) 
                    .isEqualTo(enumDefault); 
        }
    }
}
