/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Unit tests for {@link DataCloudClientFactory#fromEnvironment(Map)}. // GH-90000
 *
 * <p>Uses {@link DataCloudClientFactory#fromEnvironment(Map)} so no system // GH-90000
 * environment mutation is required. Every test controls env vars via a plain
 * {@code HashMap}.</p>
 *
 * @doc.type class
 * @doc.purpose Unit tests for DataCloudClientFactory.fromEnvironment() // GH-90000
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("DataCloudClientFactory.fromEnvironment() [GH-90000]")
class DataCloudClientFactoryTest {

    private static final String AUTH_TOKEN = "test-bearer-token-abc";

    // =========================================================================
    // EMBEDDED mode (default) // GH-90000
    // =========================================================================

    @Nested
    @DisplayName("EMBEDDED mode — startup validation [GH-90000]")
    class EmbeddedMode {

        @Test
        void validatorPassesForEmptyEnv() { // GH-90000
            // Validator should NOT throw for EMBEDDED mode even with no URLs
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of()); // GH-90000
            assertThatCode(() -> DataCloudStartupValidator.validate(config)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        void validatorPassesForExplicitEmbeddedMode() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "EMBEDDED")); // GH-90000
            assertThatCode(() -> DataCloudStartupValidator.validate(config)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }

        @Test
        void validatorDoesNotRequireServerUrlForEmbeddedMode() { // GH-90000
            // EMBEDDED mode never needs DC_SERVER_URL
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "EMBEDDED")); // GH-90000
            assertThatCode(() -> DataCloudStartupValidator.validate(config)) // GH-90000
                .doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // STANDALONE mode
    // =========================================================================

    @Nested
    @DisplayName("STANDALONE mode [GH-90000]")
    class StandaloneMode {

        @Test
        void throwsWhenServerUrlIsMissing() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "STANDALONE"); // GH-90000
            env.put(DataCloudEnvConfig.APP_ENV, "development"); // GH-90000
            // DC_SERVER_URL not set → validator should fail fast
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DC_SERVER_URL [GH-90000]");
        }

        @Test
        void createsClientInDevelopmentModeWithHttpUrl() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "STANDALONE"); // GH-90000
            env.put(DataCloudEnvConfig.DC_SERVER_URL, "http://dc-dev:8080"); // GH-90000
            env.put(DataCloudEnvConfig.APP_ENV, "development"); // GH-90000
            DataCloudClient client = DataCloudClientFactory.fromEnvironment(env); // GH-90000
            assertThat(client).isNotNull(); // GH-90000
            assertThat(client.isRunning()).isTrue(); // GH-90000
            client.close(); // GH-90000
            assertThat(client.isRunning()).isFalse(); // GH-90000
        }

        @Test
        void throwsInProductionWithoutAuthToken() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "STANDALONE"); // GH-90000
            env.put(DataCloudEnvConfig.DC_SERVER_URL, "https://dc.prod.example.com"); // GH-90000
            // APP_ENV defaults to production; auth token missing
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DATACLOUD_HTTP_AUTH_TOKEN [GH-90000]");
        }

        @Test
        void createsClientInProductionWithHttpsAndAuthToken() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "STANDALONE"); // GH-90000
            env.put(DataCloudEnvConfig.DC_SERVER_URL, "https://dc.prod.example.com"); // GH-90000
            env.put(DataCloudEnvConfig.DATACLOUD_HTTP_AUTH_TOKEN, AUTH_TOKEN); // GH-90000
            DataCloudClient client = DataCloudClientFactory.fromEnvironment(env); // GH-90000
            assertThat(client).isNotNull(); // GH-90000
            assertThat(client.isRunning()).isTrue(); // GH-90000
            client.close(); // GH-90000
            assertThat(client.isRunning()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // DISTRIBUTED mode
    // =========================================================================

    @Nested
    @DisplayName("DISTRIBUTED mode [GH-90000]")
    class DistributedMode {

        @Test
        void throwsWhenClusterUrlsIsMissing() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "DISTRIBUTED"); // GH-90000
            env.put(DataCloudEnvConfig.APP_ENV, "development"); // GH-90000
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DC_CLUSTER_URLS [GH-90000]");
        }

        @Test
        void throwsWhenOnlyOneNodeProvided() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "DISTRIBUTED"); // GH-90000
            env.put(DataCloudEnvConfig.DC_CLUSTER_URLS, "http://node1:8080"); // GH-90000
            env.put(DataCloudEnvConfig.APP_ENV, "development"); // GH-90000
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("at least 2 [GH-90000]");
        }

        @Test
        void createsClientInDevelopmentWithTwoHttpNodes() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "DISTRIBUTED"); // GH-90000
            env.put(DataCloudEnvConfig.DC_CLUSTER_URLS, "http://node1:8080,http://node2:8080"); // GH-90000
            env.put(DataCloudEnvConfig.APP_ENV, "development"); // GH-90000
            DataCloudClient client = DataCloudClientFactory.fromEnvironment(env); // GH-90000
            assertThat(client).isNotNull(); // GH-90000
            assertThat(client.isRunning()).isTrue(); // GH-90000
            client.close(); // GH-90000
            assertThat(client.isRunning()).isFalse(); // GH-90000
        }

        @Test
        void throwsInProductionWithHttpNodes() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "DISTRIBUTED"); // GH-90000
            env.put(DataCloudEnvConfig.DC_CLUSTER_URLS, // GH-90000
                "http://node1:8080,http://node2:8080");
            env.put(DataCloudEnvConfig.DATACLOUD_HTTP_AUTH_TOKEN, AUTH_TOKEN); // GH-90000
            // APP_ENV = production (default) → https required // GH-90000
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("https:// [GH-90000]");
        }

        @Test
        void createsClientInProductionWithHttpsNodesAndAuthToken() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "DISTRIBUTED"); // GH-90000
            env.put(DataCloudEnvConfig.DC_CLUSTER_URLS, // GH-90000
                "https://node1.prod.example.com,https://node2.prod.example.com,https://node3.prod.example.com");
            env.put(DataCloudEnvConfig.DATACLOUD_HTTP_AUTH_TOKEN, AUTH_TOKEN); // GH-90000
            DataCloudClient client = DataCloudClientFactory.fromEnvironment(env); // GH-90000
            assertThat(client).isNotNull(); // GH-90000
            assertThat(client.isRunning()).isTrue(); // GH-90000
            client.close(); // GH-90000
            assertThat(client.isRunning()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // Invalid mode
    // =========================================================================

    @Nested
    @DisplayName("invalid mode [GH-90000]")
    class InvalidMode {

        @Test
        void throwsForUnrecognisedDeploymentMode() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "CLUSTER"); // GH-90000
            assertThatThrownBy(() -> DataCloudClientFactory.fromEnvironment(env)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("CLUSTER [GH-90000]");
        }
    }
}
