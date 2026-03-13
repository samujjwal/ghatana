/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DataCloudEnvConfig}.
 *
 * <p>Exercises every public accessor and error path without touching the system
 * environment, using {@link DataCloudEnvConfig#fromMap(Map)}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for DataCloudEnvConfig
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("DataCloudEnvConfig")
class DataCloudEnvConfigTest {

    // =========================================================================
    // get()
    // =========================================================================

    @Nested
    @DisplayName("get()")
    class Get {

        @Test
        void returnsDefaultWhenKeyAbsent() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of());
            assertThat(config.get("SOME_KEY", "fallback")).isEqualTo("fallback");
        }

        @Test
        void returnsValueWhenKeyPresent() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of("KEY", "value"));
            assertThat(config.get("KEY", "fallback")).isEqualTo("value");
        }

        @Test
        void returnsDefaultWhenValueIsBlank() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of("KEY", "   "));
            assertThat(config.get("KEY", "fallback")).isEqualTo("fallback");
        }

        @Test
        void returnsDefaultWhenValueIsEmpty() {
            Map<String, String> env = new HashMap<>();
            env.put("KEY", "");
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env);
            assertThat(config.get("KEY", "fallback")).isEqualTo("fallback");
        }
    }

    // =========================================================================
    // getInt()
    // =========================================================================

    @Nested
    @DisplayName("getInt()")
    class GetInt {

        @Test
        void returnsDefaultWhenKeyAbsent() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of());
            assertThat(config.getInt("PORT", 9090)).isEqualTo(9090);
        }

        @Test
        void parsesIntegerValue() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of("PORT", "8080"));
            assertThat(config.getInt("PORT", 9090)).isEqualTo(8080);
        }

        @Test
        void throwsForNonIntegerValue() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of("PORT", "not-a-number"));
            assertThatThrownBy(() -> config.getInt("PORT", 9090))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PORT")
                .hasMessageContaining("not-a-number");
        }

        @Test
        void returnsDefaultWhenValueIsBlank() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of("PORT", "  "));
            assertThat(config.getInt("PORT", 42)).isEqualTo(42);
        }
    }

    // =========================================================================
    // require()
    // =========================================================================

    @Nested
    @DisplayName("require()")
    class Require {

        @Test
        void returnsValueWhenPresent() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(
                Map.of("DC_SERVER_URL", "https://dc.example.com"));
            assertThat(config.require("DC_SERVER_URL")).isEqualTo("https://dc.example.com");
        }

        @Test
        void throwsWhenKeyAbsent() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of());
            assertThatThrownBy(() -> config.require("DC_SERVER_URL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DC_SERVER_URL");
        }

        @Test
        void throwsWhenValueIsBlank() {
            Map<String, String> env = new HashMap<>();
            env.put("DC_SERVER_URL", "   ");
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env);
            assertThatThrownBy(() -> config.require("DC_SERVER_URL"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DC_SERVER_URL");
        }
    }

    // =========================================================================
    // Typed accessors — defaults
    // =========================================================================

    @Nested
    @DisplayName("typed accessors (defaults)")
    class TypedAccessorDefaults {

        private final DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of());

        @Test
        void deploymentModeDefaultsToEmbedded() {
            assertThat(config.deploymentMode()).isEqualTo("EMBEDDED");
        }

        @Test
        void serverUrlDefaultsToEmpty() {
            assertThat(config.serverUrl()).isEmpty();
        }

        @Test
        void clusterUrlsDefaultsToEmpty() {
            assertThat(config.clusterUrls()).isEmpty();
        }

        @Test
        void pgUrlHasDefaultJdbcUrl() {
            assertThat(config.pgUrl()).contains("postgresql").contains("5432");
        }

        @Test
        void pgUserDefaultsToDatacloud() {
            assertThat(config.pgUser()).isEqualTo("datacloud");
        }

        @Test
        void pgPasswordDefaultsToEmpty() {
            assertThat(config.pgPassword()).isEmpty();
        }

        @Test
        void pgPoolSizeDefaultsTen() {
            assertThat(config.pgPoolSize()).isEqualTo(10);
        }

        @Test
        void redisHostDefaultsToLocalhost() {
            assertThat(config.redisHost()).isEqualTo("localhost");
        }

        @Test
        void redisPortDefaultsTo6379() {
            assertThat(config.redisPort()).isEqualTo(6379);
        }

        @Test
        void s3RegionDefaultsToUsEast1() {
            assertThat(config.s3Region()).isEqualTo("us-east-1");
        }

        @Test
        void s3ArchiveBucketHasDefault() {
            assertThat(config.s3ArchiveBucket()).isNotBlank();
        }

        @Test
        void isDevelopmentFalseByDefault() {
            assertThat(config.isDevelopment()).isFalse();
        }
    }

    // =========================================================================
    // Typed accessors — overrides
    // =========================================================================

    @Nested
    @DisplayName("typed accessors (with env overrides)")
    class TypedAccessorOverrides {

        @Test
        void deploymentModeNormalisedToUpperCase() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(
                Map.of(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "standalone"));
            assertThat(config.deploymentMode()).isEqualTo("STANDALONE");
        }

        @Test
        void serverUrlFromEnv() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(
                Map.of(DataCloudEnvConfig.DC_SERVER_URL, "https://dc.prod.example.com"));
            assertThat(config.serverUrl()).isEqualTo("https://dc.prod.example.com");
        }

        @Test
        void pgPoolSizeParsedFromEnv() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(
                Map.of(DataCloudEnvConfig.DATACLOUD_PG_POOL_SIZE, "20"));
            assertThat(config.pgPoolSize()).isEqualTo(20);
        }

        @Test
        void redisPortParsedFromEnv() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(
                Map.of(DataCloudEnvConfig.REDIS_PORT, "6380"));
            assertThat(config.redisPort()).isEqualTo(6380);
        }

        @Test
        void isDevelopmentTrueWhenAppEnvIsDevelopment() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(
                Map.of(DataCloudEnvConfig.APP_ENV, "development"));
            assertThat(config.isDevelopment()).isTrue();
        }

        @Test
        void isDevelopmentTrueIsCaseInsensitive() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(
                Map.of(DataCloudEnvConfig.APP_ENV, "DEVELOPMENT"));
            assertThat(config.isDevelopment()).isTrue();
        }

        @Test
        void authTokenFromEnv() {
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(
                Map.of(DataCloudEnvConfig.DATACLOUD_HTTP_AUTH_TOKEN, "bearer-tok-abc"));
            assertThat(config.authToken()).isEqualTo("bearer-tok-abc");
        }
    }
}
