/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugins.trino;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EventCloudConnectorConfig} and {@link EventCloudConnectorFactory}.
 *
 * <p>Validates that configuration parsing from a Trino properties map produces
 * correct defaults, that required fields are enforced, and that the factory
 * reports the canonical connector name.
 *
 * @doc.type class
 * @doc.purpose Tests for EventCloud Trino connector configuration and factory wiring
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EventCloud Trino Connector — Config and Factory")
class EventCloudConnectorConfigTest {

    // =========================================================================
    // EventCloudConnectorConfig — fromMap parsing
    // =========================================================================

    @Nested
    @DisplayName("EventCloudConnectorConfig.fromMap()")
    class FromMap {

        @Test
        @DisplayName("defaults are applied when no optional properties are provided")
        void defaultsAppliedWhenEmpty() {
            Map<String, String> props = Map.of(
                    EventCloudConnectorConfig.URL_KEY, "http://eventcloud-host:8080"
            );
            EventCloudConnectorConfig config = EventCloudConnectorConfig.fromMap(props);

            assertThat(config.getUrl()).isEqualTo("http://eventcloud-host:8080");
            assertThat(config.isCacheEnabled()).isTrue();                        // DEFAULT_CACHE_ENABLED
            assertThat(config.getCacheTtl()).isEqualTo(Duration.ofMinutes(5));   // DEFAULT_CACHE_TTL
            assertThat(config.getMaxSplits()).isEqualTo(16);                     // DEFAULT_MAX_SPLITS
            assertThat(config.getFetchSize()).isEqualTo(10_000);                 // DEFAULT_FETCH_SIZE
        }

        @Test
        @DisplayName("URL defaults to localhost when not provided")
        void urlDefaultsToLocalhost() {
            EventCloudConnectorConfig config = EventCloudConnectorConfig.fromMap(Map.of());

            assertThat(config.getUrl()).isEqualTo("http://localhost:8080");
        }

        @Test
        @DisplayName("cache can be disabled via property")
        void cacheCanBeDisabled() {
            EventCloudConnectorConfig config = EventCloudConnectorConfig.fromMap(Map.of(
                    EventCloudConnectorConfig.URL_KEY, "http://host:8080",
                    EventCloudConnectorConfig.CACHE_ENABLED_KEY, "false"
            ));

            assertThat(config.isCacheEnabled()).isFalse();
        }

        @Test
        @DisplayName("cache TTL is parsed from seconds")
        void cacheTtlParsedFromSeconds() {
            EventCloudConnectorConfig config = EventCloudConnectorConfig.fromMap(Map.of(
                    EventCloudConnectorConfig.URL_KEY, "http://host:8080",
                    EventCloudConnectorConfig.CACHE_TTL_KEY, "120"
            ));

            assertThat(config.getCacheTtl()).isEqualTo(Duration.ofSeconds(120));
        }

        @Test
        @DisplayName("maxSplits is parsed from property")
        void maxSplitsParsed() {
            EventCloudConnectorConfig config = EventCloudConnectorConfig.fromMap(Map.of(
                    EventCloudConnectorConfig.URL_KEY, "http://host:8080",
                    EventCloudConnectorConfig.MAX_SPLITS_KEY, "32"
            ));

            assertThat(config.getMaxSplits()).isEqualTo(32);
        }

        @Test
        @DisplayName("fetchSize is parsed from property")
        void fetchSizeParsed() {
            EventCloudConnectorConfig config = EventCloudConnectorConfig.fromMap(Map.of(
                    EventCloudConnectorConfig.URL_KEY, "http://host:8080",
                    EventCloudConnectorConfig.FETCH_SIZE_KEY, "5000"
            ));

            assertThat(config.getFetchSize()).isEqualTo(5_000);
        }

        @Test
        @DisplayName("authToken is present when provided")
        void authTokenParsed() {
            EventCloudConnectorConfig config = EventCloudConnectorConfig.fromMap(Map.of(
                    EventCloudConnectorConfig.URL_KEY, "http://host:8080",
                    EventCloudConnectorConfig.AUTH_TOKEN_KEY, "secret-token-xyz"
            ));

            assertThat(config.getAuthToken()).isPresent();
            assertThat(config.getAuthToken()).hasValue("secret-token-xyz");
        }

        @Test
        @DisplayName("authToken is empty when not provided")
        void authTokenEmptyWhenAbsent() {
            EventCloudConnectorConfig config = EventCloudConnectorConfig.fromMap(Map.of(
                    EventCloudConnectorConfig.URL_KEY, "http://host:8080"
            ));

            assertThat(config.getAuthToken()).isEmpty();
        }

        @Test
        @DisplayName("defaultTenantId is set when provided")
        void defaultTenantIdParsed() {
            EventCloudConnectorConfig config = EventCloudConnectorConfig.fromMap(Map.of(
                    EventCloudConnectorConfig.URL_KEY, "http://host:8080",
                    EventCloudConnectorConfig.TENANT_ID_KEY, "tenant-trino-001"
            ));

            assertThat(config.getDefaultTenantId()).isPresent();
            assertThat(config.getDefaultTenantId()).hasValue("tenant-trino-001");
        }
    }

    // =========================================================================
    // EventCloudConnectorConfig — builder
    // =========================================================================

    @Nested
    @DisplayName("EventCloudConnectorConfig.builder()")
    class Builder {

        @Test
        @DisplayName("builder requires url — NullPointerException when null")
        void builderRequiresUrl() {
            assertThatThrownBy(() ->
                EventCloudConnectorConfig.builder().url(null).build()
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("url is required");
        }

        @Test
        @DisplayName("builder produces config with all fields set")
        void builderProducesCompleteConfig() {
            EventCloudConnectorConfig config = EventCloudConnectorConfig.builder()
                    .url("http://custom-host:9000")
                    .authToken("auth-token-builder")
                    .defaultTenantId("tenant-builder")
                    .cacheEnabled(false)
                    .cacheTtl(Duration.ofSeconds(60))
                    .maxSplits(8)
                    .fetchSize(2_000)
                    .build();

            assertThat(config.getUrl()).isEqualTo("http://custom-host:9000");
            assertThat(config.getAuthToken()).hasValue("auth-token-builder");
            assertThat(config.getDefaultTenantId()).hasValue("tenant-builder");
            assertThat(config.isCacheEnabled()).isFalse();
            assertThat(config.getCacheTtl()).isEqualTo(Duration.ofSeconds(60));
            assertThat(config.getMaxSplits()).isEqualTo(8);
            assertThat(config.getFetchSize()).isEqualTo(2_000);
        }
    }

    // =========================================================================
    // EventCloudConnectorFactory
    // =========================================================================

    @Nested
    @DisplayName("EventCloudConnectorFactory")
    class Factory {

        @Test
        @DisplayName("getName() returns canonical connector name 'eventcloud'")
        void factoryReturnsCanonicalName() {
            EventCloudConnectorFactory factory = new EventCloudConnectorFactory();

            assertThat(factory.getName()).isEqualTo(EventCloudConnectorFactory.CONNECTOR_NAME);
            assertThat(factory.getName()).isEqualTo("eventcloud");
        }

        @Test
        @DisplayName("CONNECTOR_NAME constant is 'eventcloud'")
        void connectorNameConstantIsEventCloud() {
            assertThat(EventCloudConnectorFactory.CONNECTOR_NAME).isEqualTo("eventcloud");
        }
    }
}
