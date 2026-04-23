/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DataCloudEnvConfig}.
 *
 * <p>Exercises every public accessor and error path without touching the system
 * environment, using {@link DataCloudEnvConfig#fromMap(Map)}. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Unit tests for DataCloudEnvConfig
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("DataCloudEnvConfig")
class DataCloudEnvConfigTest {

    @Nested
    @DisplayName("get()")
    class Get {

        @Test
        void returnsDefaultWhenKeyAbsent() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of()); // GH-90000
            assertThat(config.get("SOME_KEY", "fallback")).isEqualTo("fallback");
        }

        @Test
        void returnsValueWhenKeyPresent() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of("KEY", "value")); // GH-90000
            assertThat(config.get("KEY", "fallback")).isEqualTo("value");
        }

        @Test
        void returnsDefaultWhenValueIsBlank() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of("KEY", "   ")); // GH-90000
            assertThat(config.get("KEY", "fallback")).isEqualTo("fallback");
        }

        @Test
        void returnsDefaultWhenValueIsEmpty() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put("KEY", ""); // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000
            assertThat(config.get("KEY", "fallback")).isEqualTo("fallback");
        }
    }

    @Nested
    @DisplayName("getInt()")
    class GetInt {

        @Test
        void returnsDefaultWhenKeyAbsent() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of()); // GH-90000
            assertThat(config.getInt("PORT", 9090)).isEqualTo(9090); // GH-90000
        }

        @Test
        void parsesIntegerValue() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of("PORT", "8080")); // GH-90000
            assertThat(config.getInt("PORT", 9090)).isEqualTo(8080); // GH-90000
        }

        @Test
        void throwsForNonIntegerValue() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of("PORT", "not-a-number")); // GH-90000
            assertThatThrownBy(() -> config.getInt("PORT", 9090)) // GH-90000
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("PORT")
                .hasMessageContaining("not-a-number");
        }

        @Test
        void returnsDefaultWhenValueIsBlank() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of("PORT", "  ")); // GH-90000
            assertThat(config.getInt("PORT", 42)).isEqualTo(42); // GH-90000
        }
    }

    @Nested
    @DisplayName("require()")
    class Require {

        @Test
        void returnsValueWhenPresent() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of("DC_SERVER_URL", "https://dc.example.com")); // GH-90000
            assertThat(config.require("DC_SERVER_URL")).isEqualTo("https://dc.example.com");
        }

        @Test
        void throwsWhenKeyAbsent() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of()); // GH-90000
            assertThatThrownBy(() -> config.require("DC_SERVER_URL"))
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DC_SERVER_URL");
        }

        @Test
        void throwsWhenValueIsBlank() { // GH-90000
            Map<String, String> env = new HashMap<>(); // GH-90000
            env.put("DC_SERVER_URL", "   "); // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(env); // GH-90000
            assertThatThrownBy(() -> config.require("DC_SERVER_URL"))
                .isInstanceOf(IllegalStateException.class) // GH-90000
                .hasMessageContaining("DC_SERVER_URL");
        }
    }

    @Nested
    @DisplayName("typed accessors (defaults)")
    class TypedAccessorDefaults {

        private final DataCloudEnvConfig config = DataCloudEnvConfig.fromMap(Map.of()); // GH-90000

        @Test
        void deploymentModeDefaultsToEmbedded() { // GH-90000
            assertThat(config.deploymentMode()).isEqualTo("EMBEDDED");
        }

        @Test
        void serverUrlDefaultsToEmpty() { // GH-90000
            assertThat(config.serverUrl()).isEmpty(); // GH-90000
        }

        @Test
        void clusterUrlsDefaultsToEmpty() { // GH-90000
            assertThat(config.clusterUrls()).isEmpty(); // GH-90000
        }

        @Test
        void pgUrlHasDefaultJdbcUrl() { // GH-90000
            assertThat(config.pgUrl()).contains("postgresql").contains("5432");
        }

        @Test
        void pgUserDefaultsToDatacloud() { // GH-90000
            assertThat(config.pgUser()).isEqualTo("datacloud");
        }

        @Test
        void pgPasswordDefaultsToEmpty() { // GH-90000
            assertThat(config.pgPassword()).isEmpty(); // GH-90000
        }

        @Test
        void pgPoolSizeDefaultsTen() { // GH-90000
            assertThat(config.pgPoolSize()).isEqualTo(10); // GH-90000
        }

        @Test
        void pgValidationTimeoutDefaultsFiveSeconds() { // GH-90000
            assertThat(config.pgValidationTimeoutMillis()).isEqualTo(5_000); // GH-90000
        }

        @Test
        void pgConnectionTestQueryDefaultsToSelectOne() { // GH-90000
            assertThat(config.pgConnectionTestQuery()).isEqualTo("SELECT 1");
        }

        @Test
        void pgLeakDetectionDefaultsToOneMinute() { // GH-90000
            assertThat(config.pgLeakDetectionThresholdMillis()).isEqualTo(60_000); // GH-90000
        }

        @Test
        void redisHostDefaultsToLocalhost() { // GH-90000
            assertThat(config.redisHost()).isEqualTo("localhost");
        }

        @Test
        void redisPortDefaultsTo6379() { // GH-90000
            assertThat(config.redisPort()).isEqualTo(6379); // GH-90000
        }

        @Test
        void s3RegionDefaultsToUsEast1() { // GH-90000
            assertThat(config.s3Region()).isEqualTo("us-east-1");
        }

        @Test
        void s3ArchiveBucketHasDefault() { // GH-90000
            assertThat(config.s3ArchiveBucket()).isNotBlank(); // GH-90000
        }

        @Test
        void isDevelopmentFalseByDefault() { // GH-90000
            assertThat(config.isDevelopment()).isFalse(); // GH-90000
        }
    }

    @Nested
    @DisplayName("typed accessors (with env overrides)")
    class TypedAccessorOverrides {

        @Test
        void deploymentModeNormalisedToUpperCase() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.DC_DEPLOYMENT_MODE, "standalone")); // GH-90000
            assertThat(config.deploymentMode()).isEqualTo("STANDALONE");
        }

        @Test
        void serverUrlFromEnv() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.DC_SERVER_URL, "https://dc.prod.example.com")); // GH-90000
            assertThat(config.serverUrl()).isEqualTo("https://dc.prod.example.com");
        }

        @Test
        void pgPoolSizeParsedFromEnv() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.DATACLOUD_PG_POOL_SIZE, "20")); // GH-90000
            assertThat(config.pgPoolSize()).isEqualTo(20); // GH-90000
        }

        @Test
        void pgValidationTimeoutParsedFromEnv() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.DATACLOUD_PG_VALIDATION_TIMEOUT_MS, "7500")); // GH-90000
            assertThat(config.pgValidationTimeoutMillis()).isEqualTo(7_500); // GH-90000
        }

        @Test
        void pgConnectionTestQueryParsedFromEnv() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.DATACLOUD_PG_CONNECTION_TEST_QUERY, "SELECT current_database()")); // GH-90000
            assertThat(config.pgConnectionTestQuery()).isEqualTo("SELECT current_database()");
        }

        @Test
        void pgLeakDetectionParsedFromEnv() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.DATACLOUD_PG_LEAK_DETECTION_MS, "120000")); // GH-90000
            assertThat(config.pgLeakDetectionThresholdMillis()).isEqualTo(120_000); // GH-90000
        }

        @Test
        void redisPortParsedFromEnv() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.REDIS_PORT, "6380")); // GH-90000
            assertThat(config.redisPort()).isEqualTo(6380); // GH-90000
        }

        @Test
        void isDevelopmentTrueWhenAppEnvIsDevelopment() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.APP_ENV, "development")); // GH-90000
            assertThat(config.isDevelopment()).isTrue(); // GH-90000
        }

        @Test
        void isDevelopmentTrueIsCaseInsensitive() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.APP_ENV, "DEVELOPMENT")); // GH-90000
            assertThat(config.isDevelopment()).isTrue(); // GH-90000
        }

        @Test
        void authTokenFromEnv() { // GH-90000
            DataCloudEnvConfig config = DataCloudEnvConfig.fromMap( // GH-90000
                Map.of(DataCloudEnvConfig.DATACLOUD_HTTP_AUTH_TOKEN, "bearer-tok-abc")); // GH-90000
            assertThat(config.authToken()).isEqualTo("bearer-tok-abc");
        }
    }
}
