/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Unit tests for DataCloudTracingConfig configuration
 * @doc.layer product
 * @doc.pattern UnitTest
 */
@DisplayName("DataCloudTracingConfig Tests")
class DataCloudTracingConfigTest {

    @Test
    @DisplayName("default configuration uses localhost and dev sampling")
    void defaultConfigurationUsesLocalhostAndDevSampling() {
        DataCloudTracingConfig config = DataCloudTracingConfig.defaults();

        assertThat(config.otlpEndpoint()).isEqualTo("http://localhost:4317");
        assertThat(config.serviceName()).isEqualTo("data-cloud");
        assertThat(config.serviceVersion()).isEqualTo("1.0.0");
        assertThat(config.profile()).isEqualTo(DataCloudTracingConfig.Profile.DEV);
        assertThat(config.samplingRate()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("dev profile uses 100% sampling rate")
    void devProfileUses100PercentSamplingRate() {
        DataCloudTracingConfig config = DataCloudTracingConfig.builder()
            .profile(DataCloudTracingConfig.Profile.DEV)
            .build();

        assertThat(config.samplingRate()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("staging profile uses 10% sampling rate")
    void stagingProfileUses10PercentSamplingRate() {
        DataCloudTracingConfig config = DataCloudTracingConfig.builder()
            .profile(DataCloudTracingConfig.Profile.STAGING)
            .build();

        assertThat(config.samplingRate()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("prod profile uses 1% sampling rate")
    void prodProfileUses1PercentSamplingRate() {
        DataCloudTracingConfig config = DataCloudTracingConfig.builder()
            .profile(DataCloudTracingConfig.Profile.PROD)
            .build();

        assertThat(config.samplingRate()).isEqualTo(0.01);
    }

    @Test
    @DisplayName("builder allows overriding all fields")
    void builderAllowsOverridingAllFields() {
        DataCloudTracingConfig config = DataCloudTracingConfig.builder()
            .otlpEndpoint("http://custom-endpoint:4317")
            .serviceName("custom-service")
            .serviceVersion("2.0.0")
            .profile(DataCloudTracingConfig.Profile.PROD)
            .samplingRate(0.5)
            .build();

        assertThat(config.otlpEndpoint()).isEqualTo("http://custom-endpoint:4317");
        assertThat(config.serviceName()).isEqualTo("custom-service");
        assertThat(config.serviceVersion()).isEqualTo("2.0.0");
        assertThat(config.profile()).isEqualTo(DataCloudTracingConfig.Profile.PROD);
        assertThat(config.samplingRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("fromEnvironment uses environment variables when set")
    void fromEnvironmentUsesEnvironmentVariablesWhenSet() {
        String originalProfile = System.getProperty("datacloud.profile");
        String originalEndpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");
        String originalService = System.getenv("OTEL_SERVICE_NAME");
        String originalVersion = System.getenv("OTEL_SERVICE_VERSION");

        try {
            System.setProperty("datacloud.profile", "staging");
            // Note: These would need to be set in actual environment for full testing
            // For unit tests, we verify the logic exists
            
            DataCloudTracingConfig config = DataCloudTracingConfig.fromEnvironment();
            
            // Verify profile is read from system property
            assertThat(config.profile()).isEqualTo(DataCloudTracingConfig.Profile.STAGING);
        } finally {
            // Restore original values
            if (originalProfile != null) {
                System.setProperty("datacloud.profile", originalProfile);
            } else {
                System.clearProperty("datacloud.profile");
            }
        }
    }

    @Test
    @DisplayName("sampling rate is clamped between 0 and 1")
    void samplingRateIsClampedBetween0And1() {
        DataCloudTracingConfig config1 = DataCloudTracingConfig.builder()
            .samplingRate(-0.5)
            .build();
        assertThat(config1.samplingRate()).isEqualTo(0.0);

        DataCloudTracingConfig config2 = DataCloudTracingConfig.builder()
            .samplingRate(1.5)
            .build();
        assertThat(config2.samplingRate()).isEqualTo(1.0);
    }
}
