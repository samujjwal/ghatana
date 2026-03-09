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
    void setUp() {
        featureService = FeatureService.builder()
                .enableByDefault(Feature.AEP_ADVANCED_PATTERNS)
                .enableByDefault(Feature.YAPPC_SCAFFOLDING)
                .disableByDefault(Feature.AEP_MACHINE_LEARNING)
                .build();
    }
    
    @Test
    @DisplayName("should return true for enabled features")
    void shouldReturnTrueForEnabledFeatures() {
        assertThat(featureService.isEnabled(Feature.AEP_ADVANCED_PATTERNS)).isTrue();
        assertThat(featureService.isEnabled(Feature.YAPPC_SCAFFOLDING)).isTrue();
    }
    
    @Test
    @DisplayName("should return false for disabled features")
    void shouldReturnFalseForDisabledFeatures() {
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isFalse();
        assertThat(featureService.isDisabled(Feature.AEP_MACHINE_LEARNING)).isTrue();
    }
    
    @Test
    @DisplayName("should return false for features not configured")
    void shouldReturnFalseForUnconfiguredFeatures() {
        assertThat(featureService.isEnabled(Feature.DATA_CLOUD_KNOWLEDGE_GRAPH)).isFalse();
    }
    
    @Test
    @DisplayName("should execute action when feature is enabled")
    void shouldExecuteActionWhenFeatureEnabled() {
        AtomicBoolean executed = new AtomicBoolean(false);
        
        featureService.ifEnabled(Feature.AEP_ADVANCED_PATTERNS, () -> executed.set(true));
        
        assertThat(executed.get()).isTrue();
    }
    
    @Test
    @DisplayName("should not execute action when feature is disabled")
    void shouldNotExecuteActionWhenFeatureDisabled() {
        AtomicBoolean executed = new AtomicBoolean(false);
        
        featureService.ifEnabled(Feature.AEP_MACHINE_LEARNING, () -> executed.set(true));
        
        assertThat(executed.get()).isFalse();
    }
    
    @Test
    @DisplayName("should get value when feature is enabled")
    void shouldGetValueWhenFeatureEnabled() {
        String result = featureService.getIfEnabled(
                Feature.AEP_ADVANCED_PATTERNS,
                () -> "enabled",
                "disabled"
        );
        
        assertThat(result).isEqualTo("enabled");
    }
    
    @Test
    @DisplayName("should get default value when feature is disabled")
    void shouldGetDefaultValueWhenFeatureDisabled() {
        String result = featureService.getIfEnabled(
                Feature.AEP_MACHINE_LEARNING,
                () -> "enabled",
                "disabled"
        );
        
        assertThat(result).isEqualTo("disabled");
    }
    
    @Test
    @DisplayName("should return all enabled features")
    void shouldReturnAllEnabledFeatures() {
        Set<Feature> enabled = featureService.getEnabledFeatures();
        
        assertThat(enabled).contains(Feature.AEP_ADVANCED_PATTERNS, Feature.YAPPC_SCAFFOLDING);
        assertThat(enabled).doesNotContain(Feature.AEP_MACHINE_LEARNING);
    }
    
    @Test
    @DisplayName("should allow programmatic enable/disable")
    void shouldAllowProgrammaticEnableDisable() {
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isFalse();
        
        featureService.enable(Feature.AEP_MACHINE_LEARNING);
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isTrue();
        
        featureService.disable(Feature.AEP_MACHINE_LEARNING);
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isFalse();
    }
    
    @Test
    @DisplayName("should reset feature to default state")
    void shouldResetFeatureToDefaultState() {
        featureService.enable(Feature.AEP_MACHINE_LEARNING);
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isTrue();
        
        featureService.reset(Feature.AEP_MACHINE_LEARNING);
        assertThat(featureService.isEnabled(Feature.AEP_MACHINE_LEARNING)).isFalse();
    }
    
    @Test
    @DisplayName("withDefaults should create service with sensible defaults")
    void withDefaultsShouldCreateServiceWithSensibleDefaults() {
        FeatureService defaultService = FeatureService.withDefaults();
        
        assertThat(defaultService.isEnabled(Feature.PLATFORM_ADVANCED_OBSERVABILITY)).isTrue();
        assertThat(defaultService.isEnabled(Feature.AEP_ADVANCED_PATTERNS)).isTrue();
        assertThat(defaultService.isEnabled(Feature.SECURITY_GATEWAY_OAUTH)).isTrue();
    }
}
