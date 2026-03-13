/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.api.config;

import com.ghatana.datacloud.client.DataCloudClient;
import com.ghatana.datacloud.client.DataCloudClientFactory;
import com.ghatana.datacloud.config.DataCloudEnvConfig;
import com.ghatana.datacloud.config.DataCloudStartupValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for {@link DataCloudModule} and the underlying
 * {@link DataCloudClientFactory} environment-driven factory.
 *
 * <p>Tests the three deployment modes driven by {@code DC_DEPLOYMENT_MODE}:
 * <ul>
 *   <li>EMBEDDED (default) — configuration validates successfully; no URL required</li>
 *   <li>STANDALONE — requires {@code DC_SERVER_URL}; throws {@link IllegalStateException} when absent</li>
 *   <li>DISTRIBUTED — requires {@code DC_CLUSTER_URLS} with ≥2 nodes</li>
 * </ul>
 *
 * <p>Note: EMBEDDED-mode client creation (plugin loading) is tested separately
 * via the data-cloud platform test suite ({@code DataCloudClientFactoryTest}).
 * These tests focus on startup-validation and remote-mode configuration enforcement.
 *
 * @doc.type class
 * @doc.purpose Integration tests for DataCloudModule / DataCloudClientFactory env-driven creation
 * @doc.layer product
 * @doc.pattern Test, Integration
 */
@DisplayName("DataCloudModule – environment-driven client creation")
class DataCloudModuleIntegrationTest {

    // ==================== EMBEDDED mode ====================

    @Nested
    @DisplayName("EMBEDDED mode")
    class EmbeddedMode {

        @Test
        @DisplayName("DC_DEPLOYMENT_MODE=EMBEDDED → startup validation passes (no config ISE)")
        void embeddedMode_validationPassesSuccessfully() {
            // Embedded mode needs no URL — DataCloudStartupValidator must not throw.
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(
                    Map.of("DC_DEPLOYMENT_MODE", "EMBEDDED"));

            assertThatNoException().isThrownBy(() -> DataCloudStartupValidator.validate(config));
        }

        @Test
        @DisplayName("missing DC_DEPLOYMENT_MODE (default) → treated as EMBEDDED, validation passes")
        void defaultMode_treatedAsEmbedded_validationPasses() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of());

            assertThatNoException().isThrownBy(() -> DataCloudStartupValidator.validate(config));
        }
    }

    // ==================== STANDALONE mode ====================

    @Nested
    @DisplayName("STANDALONE mode")
    class StandaloneMode {

        @Test
        @DisplayName("DC_DEPLOYMENT_MODE=STANDALONE without DC_SERVER_URL → IllegalStateException")
        void standaloneWithoutServerUrl_throwsIse() {
            assertThatThrownBy(() ->
                DataCloudClientFactory.fromEnvironment(
                        Map.of("DC_DEPLOYMENT_MODE", "STANDALONE")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DC_SERVER_URL");
        }

        @Test
        @DisplayName("DC_DEPLOYMENT_MODE=STANDALONE with https:// server URL → client created")
        void standaloneWithServerUrl_createsClient() {
            DataCloudClient client = DataCloudClientFactory.fromEnvironment(Map.of(
                    "DC_DEPLOYMENT_MODE", "STANDALONE",
                    "DC_SERVER_URL",      "https://dc.example.com",
                    "APP_ENV",            "development"));

            assertThat(client).isNotNull();
        }
    }

    // ==================== DISTRIBUTED mode ====================

    @Nested
    @DisplayName("DISTRIBUTED mode")
    class DistributedMode {

        @Test
        @DisplayName("DISTRIBUTED with fewer than 2 nodes → IllegalStateException / IllegalArgumentException")
        void distributedWithOneNode_throws() {
            assertThatThrownBy(() ->
                DataCloudClientFactory.fromEnvironment(Map.of(
                        "DC_DEPLOYMENT_MODE", "DISTRIBUTED",
                        "DC_CLUSTER_URLS",    "https://node1.example.com",
                        "APP_ENV",            "development")))
                .isInstanceOfAny(IllegalStateException.class, IllegalArgumentException.class);
        }

        @Test
        @DisplayName("DISTRIBUTED with 2 nodes → client created")
        void distributedWithTwoNodes_createsClient() {
            DataCloudClient client = DataCloudClientFactory.fromEnvironment(Map.of(
                    "DC_DEPLOYMENT_MODE", "DISTRIBUTED",
                    "DC_CLUSTER_URLS",    "https://node1.example.com,https://node2.example.com",
                    "APP_ENV",            "development"));

            assertThat(client).isNotNull();
        }
    }

    // ==================== Invalid mode ====================

    @Nested
    @DisplayName("Invalid mode")
    class InvalidMode {

        @Test
        @DisplayName("unknown DC_DEPLOYMENT_MODE → IllegalStateException")
        void unknownMode_throwsIse() {
            assertThatThrownBy(() ->
                DataCloudClientFactory.fromEnvironment(
                        Map.of("DC_DEPLOYMENT_MODE", "UNKNOWN_MODE")))
                .isInstanceOf(IllegalStateException.class);
        }
    }
}
