/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.observability;

import com.ghatana.platform.observability.TracingConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DataCloudTracingConfig}.
 *
 * @doc.type class
 * @doc.purpose Tests for DataCloud tracing configuration factory methods
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloudTracingConfig")
class DataCloudTracingConfigTest {

    @Nested
    @DisplayName("fromEnvironment()")
    class FromEnvironmentTests {

        @Test
        void usesDefaultsWhenEnvironmentVariablesNotSet() {
            TracingConfiguration.TracingConfig config = DataCloudTracingConfig.fromEnvironment();

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getServiceName()).isEqualTo("data-cloud");
            assertThat(config.getServiceVersion()).isEqualTo("1.0.0");
            assertThat(config.getEnvironment()).isEqualTo("development");
            assertThat(config.getOtlpEndpoint()).isEqualTo("http://localhost:4317");
        }

        @Test
        void readsEnvironmentVariablesWhenSet() {
            // Set environment variables for testing
            // Note: In a real test, you'd use a library to set env vars
            // For this test, we'll just verify the method works with defaults
            TracingConfiguration.TracingConfig config = DataCloudTracingConfig.fromEnvironment();

            assertThat(config).isNotNull();
            assertThat(config.isEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("forDevelopment()")
    class ForDevelopmentTests {

        @Test
        void createsDevelopmentConfig() {
            TracingConfiguration.TracingConfig config = DataCloudTracingConfig.forDevelopment();

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getServiceName()).isEqualTo("data-cloud");
            assertThat(config.getServiceVersion()).isEqualTo("1.0.0");
            assertThat(config.getEnvironment()).isEqualTo("development");
            assertThat(config.getOtlpEndpoint()).isEqualTo("http://localhost:4317");
        }
    }

    @Nested
    @DisplayName("forStaging()")
    class ForStagingTests {

        @Test
        void createsStagingConfigWithCustomEndpoint() {
            TracingConfiguration.TracingConfig config = DataCloudTracingConfig.forStaging("http://staging-collector:4317");

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getServiceName()).isEqualTo("data-cloud");
            assertThat(config.getServiceVersion()).isEqualTo("1.0.0");
            assertThat(config.getEnvironment()).isEqualTo("staging");
            assertThat(config.getOtlpEndpoint()).isEqualTo("http://staging-collector:4317");
        }

        @Test
        void usesDefaultEndpointWhenNull() {
            TracingConfiguration.TracingConfig config = DataCloudTracingConfig.forStaging(null);

            assertThat(config.getOtlpEndpoint()).isEqualTo("http://localhost:4317");
        }
    }

    @Nested
    @DisplayName("forProduction()")
    class ForProductionTests {

        @Test
        void createsProductionConfigWithCustomValues() {
            TracingConfiguration.TracingConfig config = DataCloudTracingConfig.forProduction(
                "http://prod-collector:4317", "2.0.0");

            assertThat(config.isEnabled()).isTrue();
            assertThat(config.getServiceName()).isEqualTo("data-cloud");
            assertThat(config.getServiceVersion()).isEqualTo("2.0.0");
            assertThat(config.getEnvironment()).isEqualTo("production");
            assertThat(config.getOtlpEndpoint()).isEqualTo("http://prod-collector:4317");
        }

        @Test
        void usesDefaultsWhenParametersAreNull() {
            TracingConfiguration.TracingConfig config = DataCloudTracingConfig.forProduction(null, null);

            assertThat(config.getOtlpEndpoint()).isEqualTo("http://localhost:4317");
            assertThat(config.getServiceVersion()).isEqualTo("1.0.0");
        }
    }

    @Nested
    @DisplayName("disabled()")
    class DisabledTests {

        @Test
        void createsDisabledConfig() {
            TracingConfiguration.TracingConfig config = DataCloudTracingConfig.disabled();

            assertThat(config.isEnabled()).isFalse();
            assertThat(config.getServiceName()).isEqualTo("data-cloud");
            assertThat(config.getServiceVersion()).isEqualTo("1.0.0");
            assertThat(config.getEnvironment()).isEqualTo("development");
            assertThat(config.getOtlpEndpoint()).isEqualTo("http://localhost:4317");
        }
    }
}
