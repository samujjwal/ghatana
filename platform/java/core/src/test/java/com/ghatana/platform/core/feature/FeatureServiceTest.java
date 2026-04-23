package com.ghatana.platform.core.feature;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FeatureService")
class FeatureServiceTest {

    private FeatureService featureService;

    @BeforeEach
    void setUp() { // GH-90000
        featureService = FeatureService.builder() // GH-90000
                .enableByDefault(Feature.AEP_ADVANCED_PATTERNS) // GH-90000
                .enableByDefault(Feature.YAPPC_SCAFFOLDING) // GH-90000
                .disableByDefault(Feature.AEP_MACHINE_LEARNING) // GH-90000
                .build(); // GH-90000
    }

    @Test
    @DisplayName("should return true for enabled features")
    void shouldReturnTrueForEnabledFeatures() { // GH-90000
        assertThat(featureService.isEnabled(Feature.AEP_ADVANCED_PATTERNS)).isTrue(); // GH-90000
        assertThat(featureService.isEnabled(Feature.YAPPC_SCAFFOLDING)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should return false for disabled features")
    void shouldReturnFalseForDisabledFeatures() { // GH-90000
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isFalse(); // GH-90000
        assertThat(featureService.isDisabled(Feature.AEP_MACHINE_LEARNING)).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should return false for features not configured")
    void shouldReturnFalseForUnconfiguredFeatures() { // GH-90000
        assertThat(featureService.isEnabled(Feature.DATA_CLOUD_KNOWLEDGE_GRAPH)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("should execute action when feature is enabled")
    void shouldExecuteActionWhenFeatureEnabled() { // GH-90000
        AtomicBoolean executed = new AtomicBoolean(false); // GH-90000

        featureService.ifEnabled(Feature.AEP_ADVANCED_PATTERNS, () -> executed.set(true)); // GH-90000

        assertThat(executed.get()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("should not execute action when feature is disabled")
    void shouldNotExecuteActionWhenFeatureDisabled() { // GH-90000
        AtomicBoolean executed = new AtomicBoolean(false); // GH-90000

        featureService.ifEnabled(Feature.AEP_MACHINE_LEARNING, () -> executed.set(true)); // GH-90000

        assertThat(executed.get()).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("should get value when feature is enabled")
    void shouldGetValueWhenFeatureEnabled() { // GH-90000
        String result = featureService.getIfEnabled( // GH-90000
                Feature.AEP_ADVANCED_PATTERNS,
                () -> "enabled", // GH-90000
                "disabled"
        );

        assertThat(result).isEqualTo("enabled");
    }

    @Test
    @DisplayName("should get default value when feature is disabled")
    void shouldGetDefaultValueWhenFeatureDisabled() { // GH-90000
        String result = featureService.getIfEnabled( // GH-90000
                Feature.AEP_MACHINE_LEARNING,
                () -> "enabled", // GH-90000
                "disabled"
        );

        assertThat(result).isEqualTo("disabled");
    }

    @Test
    @DisplayName("should return all enabled features")
    void shouldReturnAllEnabledFeatures() { // GH-90000
        Set<Feature> enabled = featureService.getEnabledFeatures(); // GH-90000

        assertThat(enabled).contains(Feature.AEP_ADVANCED_PATTERNS, Feature.YAPPC_SCAFFOLDING); // GH-90000
        assertThat(enabled).doesNotContain(Feature.AEP_MACHINE_LEARNING); // GH-90000
    }

    @Test
    @DisplayName("should allow programmatic enable/disable")
    void shouldAllowProgrammaticEnableDisable() { // GH-90000
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isFalse(); // GH-90000

        featureService.enable(Feature.AEP_MACHINE_LEARNING); // GH-90000
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isTrue(); // GH-90000

        featureService.disable(Feature.AEP_MACHINE_LEARNING); // GH-90000
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("should reset feature to default state")
    void shouldResetFeatureToDefaultState() { // GH-90000
        featureService.enable(Feature.AEP_MACHINE_LEARNING); // GH-90000
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isTrue(); // GH-90000

        featureService.reset(Feature.AEP_MACHINE_LEARNING); // GH-90000
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isFalse(); // GH-90000
    }

    @Test
    @DisplayName("withDefaults should create service with sensible defaults")
    void withDefaultsShouldCreateServiceWithSensibleDefaults() { // GH-90000
        FeatureService defaultService = FeatureService.withDefaults(); // GH-90000

        assertThat(defaultService.isEnabled(Feature.PLATFORM_ADVANCED_OBSERVABILITY)).isTrue(); // GH-90000
        assertThat(defaultService.isEnabled(Feature.SECURITY_GATEWAY_OAUTH)).isTrue(); // GH-90000
    }
}
