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
                .enableByDefault(Feature.PLATFORM_ADVANCED_OBSERVABILITY)
                .enableByDefault(Feature.SECURITY_GATEWAY_RBAC)
                .disableByDefault(Feature.SECURITY_GATEWAY_ABAC)
                .build();
    }

    @Test
    @DisplayName("should return true for enabled features")
    void shouldReturnTrueForEnabledFeatures() {
        assertThat(featureService.isEnabled(Feature.PLATFORM_ADVANCED_OBSERVABILITY)).isTrue();
        assertThat(featureService.isEnabled(Feature.SECURITY_GATEWAY_RBAC)).isTrue();
    }

    @Test
    @DisplayName("should return false for disabled features")
    void shouldReturnFalseForDisabledFeatures() {
        assertThat(featureService.isEnabled(Feature.SECURITY_GATEWAY_ABAC)).isFalse();
        assertThat(featureService.isDisabled(Feature.SECURITY_GATEWAY_ABAC)).isTrue();
    }

    @Test
    @DisplayName("should return false for features not configured")
    void shouldReturnFalseForUnconfiguredFeatures() {
        assertThat(featureService.isEnabled(Feature.SECURITY_GATEWAY_ZERO_TRUST)).isFalse();
    }

    @Test
    @DisplayName("should execute action when feature is enabled")
    void shouldExecuteActionWhenFeatureEnabled() {
        AtomicBoolean executed = new AtomicBoolean(false);

        featureService.ifEnabled(Feature.PLATFORM_ADVANCED_OBSERVABILITY, () -> executed.set(true));

        assertThat(executed.get()).isTrue();
    }

    @Test
    @DisplayName("should not execute action when feature is disabled")
    void shouldNotExecuteActionWhenFeatureDisabled() {
        AtomicBoolean executed = new AtomicBoolean(false);

        featureService.ifEnabled(Feature.SECURITY_GATEWAY_ABAC, () -> executed.set(true));

        assertThat(executed.get()).isFalse();
    }

    @Test
    @DisplayName("should get value when feature is enabled")
    void shouldGetValueWhenFeatureEnabled() {
        String result = featureService.getIfEnabled(
                Feature.PLATFORM_ADVANCED_OBSERVABILITY,
                () -> "enabled",
                "disabled"
        );

        assertThat(result).isEqualTo("enabled");
    }

    @Test
    @DisplayName("should get default value when feature is disabled")
    void shouldGetDefaultValueWhenFeatureDisabled() {
        String result = featureService.getIfEnabled(
                Feature.SECURITY_GATEWAY_ABAC,
                () -> "enabled",
                "disabled"
        );

        assertThat(result).isEqualTo("disabled");
    }

    @Test
    @DisplayName("should return all enabled features")
    void shouldReturnAllEnabledFeatures() {
        Set<Feature> enabled = featureService.getEnabledFeatures();

        assertThat(enabled).contains(Feature.PLATFORM_ADVANCED_OBSERVABILITY, Feature.SECURITY_GATEWAY_RBAC);
        assertThat(enabled).doesNotContain(Feature.SECURITY_GATEWAY_ABAC);
    }

    @Test
    @DisplayName("should allow programmatic enable/disable")
    void shouldAllowProgrammaticEnableDisable() {
        assertThat(featureService.isEnabled(Feature.SECURITY_GATEWAY_ABAC)).isFalse();

        featureService.enable(Feature.SECURITY_GATEWAY_ABAC);
        assertThat(featureService.isEnabled(Feature.SECURITY_GATEWAY_ABAC)).isTrue();

        featureService.disable(Feature.SECURITY_GATEWAY_ABAC);
        assertThat(featureService.isEnabled(Feature.SECURITY_GATEWAY_ABAC)).isFalse();
    }

    @Test
    @DisplayName("should reset feature to default state")
    void shouldResetFeatureToDefaultState() {
        featureService.enable(Feature.SECURITY_GATEWAY_ABAC);
        assertThat(featureService.isEnabled(Feature.SECURITY_GATEWAY_ABAC)).isTrue();

        featureService.reset(Feature.SECURITY_GATEWAY_ABAC);
        assertThat(featureService.isEnabled(Feature.SECURITY_GATEWAY_ABAC)).isFalse();
    }

    @Test
    @DisplayName("withDefaults should create service with sensible defaults")
    void withDefaultsShouldCreateServiceWithSensibleDefaults() { 
        FeatureService defaultService = FeatureService.withDefaults(); 

        assertThat(defaultService.isEnabled(Feature.PLATFORM_ADVANCED_OBSERVABILITY)).isTrue(); 
        assertThat(defaultService.isEnabled(Feature.SECURITY_GATEWAY_OAUTH)).isTrue(); 
    }
}
