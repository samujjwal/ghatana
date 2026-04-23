/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataCloudStartupValidator}.
 *
 * <p>Validates startup configuration logic including:
 * <ul>
 *   <li>STANDALONE mode validation (DC_SERVER_URL)</li> // GH-90000
 *   <li>DISTRIBUTED mode validation (DC_CLUSTER_URLS)</li> // GH-90000
 *   <li>EMBEDDED mode validation (no URL required)</li> // GH-90000
 *   <li>HTTPS requirements in production</li>
 *   <li>Auth token requirements in production</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose Unit tests for DataCloudStartupValidator
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DataCloud Startup Validator Tests")
class DataCloudStartupValidatorTest {

    // =========================================================================
    // STANDALONE MODE VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("STANDALONE mode validation")
    class StandaloneModeValidation {

        @Test
        @DisplayName("should pass with valid HTTPS URL in production")
        void shouldPassWithValidHttpsUrlInProduction() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "STANDALONE",
                "DC_SERVER_URL", "https://datacloud.example.com",
                "DATACLOUD_HTTP_AUTH_TOKEN", "valid-token",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000
            DataCloudStartupValidator.validate(config); // GH-90000
            // Should not throw
        }

        @Test
        @DisplayName("should pass with HTTP URL in development")
        void shouldPassWithHttpUrlInDevelopment() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "STANDALONE",
                "DC_SERVER_URL", "http://localhost:8080",
                "APP_ENV", "development"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000
            DataCloudStartupValidator.validate(config); // GH-90000
            // Should not throw
        }

        @Test
        @DisplayName("should fail with blank server URL in STANDALONE mode")
        void shouldFailWithBlankServerUrlInStandaloneMode() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "STANDALONE",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000

            assertThatThrownBy(() -> DataCloudStartupValidator.validate(config)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DC_SERVER_URL to be set");
        }

        @Test
        @DisplayName("should fail with HTTP URL in production")
        void shouldFailWithHttpUrlInProduction() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "STANDALONE",
                "DC_SERVER_URL", "http://datacloud.example.com",
                "DATACLOUD_HTTP_AUTH_TOKEN", "valid-token",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000

            assertThatThrownBy(() -> DataCloudStartupValidator.validate(config)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("must start with 'https://'");
        }

        @Test
        @DisplayName("should fail with blank auth token in production")
        void shouldFailWithBlankAuthTokenInProduction() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "STANDALONE",
                "DC_SERVER_URL", "https://datacloud.example.com",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000

            assertThatThrownBy(() -> DataCloudStartupValidator.validate(config)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_HTTP_AUTH_TOKEN to be set");
        }
    }

    // =========================================================================
    // DISTRIBUTED MODE VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("DISTRIBUTED mode validation")
    class DistributedModeValidation {

        @Test
        @DisplayName("should pass with valid HTTPS cluster URLs in production")
        void shouldPassWithValidHttpsClusterUrlsInProduction() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "DISTRIBUTED",
                "DC_CLUSTER_URLS", "https://node1.example.com,https://node2.example.com",
                "DATACLOUD_HTTP_AUTH_TOKEN", "valid-token",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000
            DataCloudStartupValidator.validate(config); // GH-90000
            // Should not throw
        }

        @Test
        @DisplayName("should pass with HTTP cluster URLs in development")
        void shouldPassWithHttpClusterUrlsInDevelopment() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "DISTRIBUTED",
                "DC_CLUSTER_URLS", "http://localhost:8080,http://localhost:8081",
                "APP_ENV", "development"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000
            DataCloudStartupValidator.validate(config); // GH-90000
            // Should not throw
        }

        @Test
        @DisplayName("should fail with blank cluster URLs")
        void shouldFailWithBlankClusterUrls() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "DISTRIBUTED",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000

            assertThatThrownBy(() -> DataCloudStartupValidator.validate(config)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DC_CLUSTER_URLS to be set");
        }

        @Test
        @DisplayName("should fail with single cluster URL")
        void shouldFailWithSingleClusterUrl() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "DISTRIBUTED",
                "DC_CLUSTER_URLS", "https://node1.example.com",
                "DATACLOUD_HTTP_AUTH_TOKEN", "valid-token",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000

            assertThatThrownBy(() -> DataCloudStartupValidator.validate(config)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("at least 2 nodes");
        }

        @Test
        @DisplayName("should fail with HTTP cluster URL in production")
        void shouldFailWithHttpClusterUrlInProduction() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "DISTRIBUTED",
                "DC_CLUSTER_URLS", "http://node1.example.com,http://node2.example.com",
                "DATACLOUD_HTTP_AUTH_TOKEN", "valid-token",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000

            assertThatThrownBy(() -> DataCloudStartupValidator.validate(config)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("must start with 'https://'");
        }

        @Test
        @DisplayName("should fail with blank auth token in production")
        void shouldFailWithBlankAuthTokenInProduction() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "DISTRIBUTED",
                "DC_CLUSTER_URLS", "https://node1.example.com,https://node2.example.com",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000

            assertThatThrownBy(() -> DataCloudStartupValidator.validate(config)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_HTTP_AUTH_TOKEN to be set");
        }
    }

    // =========================================================================
    // EMBEDDED MODE VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("EMBEDDED mode validation")
    class EmbeddedModeValidation {

        @Test
        @DisplayName("should pass without any URL in EMBEDDED mode")
        void shouldPassWithoutAnyUrlInEmbeddedMode() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "EMBEDDED",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000
            DataCloudStartupValidator.validate(config); // GH-90000
            // Should not throw
        }

        @Test
        @DisplayName("should pass with any URL in EMBEDDED mode")
        void shouldPassWithAnyUrlInEmbeddedMode() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "EMBEDDED",
                "DC_SERVER_URL", "https://example.com",
                "DC_CLUSTER_URLS", "https://node1.example.com",
                "DATACLOUD_HTTP_AUTH_TOKEN", "any-token",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000
            DataCloudStartupValidator.validate(config); // GH-90000
            // Should not throw
        }
    }

    // =========================================================================
    // INVALID DEPLOYMENT MODE
    // =========================================================================

    @Nested
    @DisplayName("Invalid deployment mode")
    class InvalidDeploymentMode {

        @Test
        @DisplayName("should fail with invalid deployment mode")
        void shouldFailWithInvalidDeploymentMode() { // GH-90000
            Map<String, String> env = Map.of( // GH-90000
                "DC_DEPLOYMENT_MODE", "INVALID_MODE",
                "APP_ENV", "production"
            );

            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000

            assertThatThrownBy(() -> DataCloudStartupValidator.validate(config)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("EMBEDDED, STANDALONE, or DISTRIBUTED");
        }
    }
}
