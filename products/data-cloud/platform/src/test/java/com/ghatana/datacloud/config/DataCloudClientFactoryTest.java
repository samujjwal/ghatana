/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.client.DataCloudClientFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DataCloudClientFactory#fromEnvironment(Map)}.
 *
 * <p>Uses {@link DataCloudClientFactory#fromEnvironment(Map)} so no system
 * environment mutation is required. Every test controls env vars via a plain
 * {@code HashMap}.</p>
 *
 * @doc.type class
 * @doc.purpose Unit tests for DataCloudClientFactory.fromEnvironment()
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("DataCloudClientFactory.fromEnvironment()")
class DataCloudClientFactoryTest {

    private static final String AUTH_TOKEN = "test-bearer-token-abc";

    // =========================================================================
    // EMBEDDED mode (default)
    // =========================================================================

    @Nested
    @DisplayName("EMBEDDED mode — startup validation")
    class EmbeddedMode {

        @Test
        void validatorPassesForEmptyEnv() {
            // Validator should NOT throw for EMBEDDED mode even with no URLs
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of());
            assertThatCode(() -> DataCloudStartupValidator.validate(config))
                .doesNotThrowAnyException();
        }

        @Test
        void validatorPassesForExplicitEmbeddedMode() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(
                Map.of(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "EMBEDDED"));
            assertThatCode(() -> DataCloudStartupValidator.validate(config))
                .doesNotThrowAnyException();
        }

        @Test
        void validatorDoesNotRequireServerUrlForEmbeddedMode() {
            // EMBEDDED mode never needs DC_SERVER_URL
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(
                Map.of(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "EMBEDDED"));
            assertThatCode(() -> DataCloudStartupValidator.validate(config))
                .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // STANDALONE mode
    // =========================================================================

    @Nested
    @DisplayName("STANDALONE mode")
    class StandaloneMode {

        @Test
        void throwsWhenServerUrlIsMissing() {
            Map<String, String> env = new HashMap<>();
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "STANDALONE");
            env.put(DataCloudEnvConfig.APP_ENV, "development");
            // DC_SERVER_URL not set → validator should fail fast
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DC_SERVER_URL");
        }

        @Test
        void createsClientInDevelopmentModeWithHttpUrl() {
            Map<String, String> env = new HashMap<>();
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "STANDALONE");
            env.put(DataCloudEnvConfig.DC_SERVER_URL, "http://dc-dev:8080");
            env.put(DataCloudEnvConfig.APP_ENV, "development");
            DataCloudClient client = DataCloudClientFactory.fromEnvironment(env);
            assertThat(client).isNotNull();
        }

        @Test
        void throwsInProductionWithoutAuthToken() {
            Map<String, String> env = new HashMap<>();
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "STANDALONE");
            env.put(DataCloudEnvConfig.DC_SERVER_URL, "https://dc.prod.example.com");
            // APP_ENV defaults to production; auth token missing
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATACLOUD_HTTP_AUTH_TOKEN");
        }

        @Test
        void createsClientInProductionWithHttpsAndAuthToken() {
            Map<String, String> env = new HashMap<>();
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "STANDALONE");
            env.put(DataCloudEnvConfig.DC_SERVER_URL, "https://dc.prod.example.com");
            env.put(DataCloudEnvConfig.DATACLOUD_HTTP_AUTH_TOKEN, AUTH_TOKEN);
            DataCloudClient client = DataCloudClientFactory.fromEnvironment(env);
            assertThat(client).isNotNull();
        }
    }

    // =========================================================================
    // DISTRIBUTED mode
    // =========================================================================

    @Nested
    @DisplayName("DISTRIBUTED mode")
    class DistributedMode {

        @Test
        void throwsWhenClusterUrlsIsMissing() {
            Map<String, String> env = new HashMap<>();
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "DISTRIBUTED");
            env.put(DataCloudEnvConfig.APP_ENV, "development");
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DC_CLUSTER_URLS");
        }

        @Test
        void throwsWhenOnlyOneNodeProvided() {
            Map<String, String> env = new HashMap<>();
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "DISTRIBUTED");
            env.put(DataCloudEnvConfig.DC_CLUSTER_URLS, "http://node1:8080");
            env.put(DataCloudEnvConfig.APP_ENV, "development");
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 2");
        }

        @Test
        void createsClientInDevelopmentWithTwoHttpNodes() {
            Map<String, String> env = new HashMap<>();
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "DISTRIBUTED");
            env.put(DataCloudEnvConfig.DC_CLUSTER_URLS, "http://node1:8080,http://node2:8080");
            env.put(DataCloudEnvConfig.APP_ENV, "development");
            DataCloudClient client = DataCloudClientFactory.fromEnvironment(env);
            assertThat(client).isNotNull();
        }

        @Test
        void throwsInProductionWithHttpNodes() {
            Map<String, String> env = new HashMap<>();
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "DISTRIBUTED");
            env.put(DataCloudEnvConfig.DC_CLUSTER_URLS,
                "http://node1:8080,http://node2:8080");
            env.put(DataCloudEnvConfig.DATACLOUD_HTTP_AUTH_TOKEN, AUTH_TOKEN);
            // APP_ENV = production (default) → https required
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("https://");
        }

        @Test
        void createsClientInProductionWithHttpsNodesAndAuthToken() {
            Map<String, String> env = new HashMap<>();
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "DISTRIBUTED");
            env.put(DataCloudEnvConfig.DC_CLUSTER_URLS,
                "https://node1.prod.example.com,https://node2.prod.example.com,https://node3.prod.example.com");
            env.put(DataCloudEnvConfig.DATACLOUD_HTTP_AUTH_TOKEN, AUTH_TOKEN);
            DataCloudClient client = DataCloudClientFactory.fromEnvironment(env);
            assertThat(client).isNotNull();
        }
    }

    // =========================================================================
    // Invalid mode
    // =========================================================================

    @Nested
    @DisplayName("invalid mode")
    class InvalidMode {

        @Test
        void throwsForUnrecognisedDeploymentMode() {
            Map<String, String> env = new HashMap<>();
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "CLUSTER");
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLUSTER");
        }
    }
}
