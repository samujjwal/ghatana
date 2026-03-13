/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EnvConfig} — core accessor methods and
 * connector-specific typed accessors.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AEP EnvConfig
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EnvConfig")
class EnvConfigTest {

    private static EnvConfig empty() {
        return EnvConfig.fromMap(Map.of());
    }

    private static EnvConfig with(String key, String value) {
        return EnvConfig.fromMap(Map.of(key, value));
    }

    // ==================== Core accessors ====================

    @Nested
    @DisplayName("get(key, default)")
    class Get {

        @Test
        @DisplayName("returns default when key is absent")
        void absentKey_returnsDefault() {
            assertThat(empty().get("MISSING_KEY", "default-val")).isEqualTo("default-val");
        }

        @Test
        @DisplayName("returns env value when key is present")
        void presentKey_returnsValue() {
            EnvConfig env = with("MY_KEY", "my-value");
            assertThat(env.get("MY_KEY", "default")).isEqualTo("my-value");
        }
    }

    @Nested
    @DisplayName("getInt(key, default)")
    class GetInt {

        @Test
        @DisplayName("returns default when key is absent")
        void absentKey_returnsDefault() {
            assertThat(empty().getInt("MISSING_INT", 42)).isEqualTo(42);
        }

        @Test
        @DisplayName("returns parsed int when key is present and valid")
        void presentKey_parsedCorrectly() {
            EnvConfig env = with("MY_INT", "99");
            assertThat(env.getInt("MY_INT", 0)).isEqualTo(99);
        }

        @Test
        @DisplayName("throws IllegalStateException when value is not a valid integer")
        void invalidInt_throwsIse() {
            EnvConfig env = with("BAD_INT", "not-a-number");
            assertThatThrownBy(() -> env.getInt("BAD_INT", 7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BAD_INT");
        }
    }

    @Nested
    @DisplayName("require(key)")
    class Require {

        @Test
        @DisplayName("returns value when key is present")
        void presentKey_returnsValue() {
            EnvConfig env = with("REQ_KEY", "req-value");
            assertThat(env.require("REQ_KEY")).isEqualTo("req-value");
        }

        @Test
        @DisplayName("throws IllegalStateException when key is absent")
        void absentKey_throwsIse() {
            assertThatThrownBy(() -> empty().require("MISSING_REQ"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MISSING_REQ");
        }
    }

    @Nested
    @DisplayName("isDevelopment()")
    class IsDevelopment {

        @Test
        @DisplayName("returns false by default (production)")
        void defaultIsFalse() {
            assertThat(empty().isDevelopment()).isFalse();
        }

        @Test
        @DisplayName("returns true when APP_ENV=development")
        void developmentEnv_returnsTrue() {
            EnvConfig env = with(EnvConfig.APP_ENV, "development");
            assertThat(env.isDevelopment()).isTrue();
        }

        @Test
        @DisplayName("returns false when APP_ENV=production")
        void productionEnv_returnsFalse() {
            EnvConfig env = with(EnvConfig.APP_ENV, "production");
            assertThat(env.isDevelopment()).isFalse();
        }
    }

    // ==================== Connector typed accessors (defaults) ====================

    @Nested
    @DisplayName("Kafka defaults")
    class KafkaDefaults {

        @Test
        void bootstrapServers_defaultsToLocalhost() {
            assertThat(empty().kafkaBootstrapServers()).isEqualTo("localhost:9092");
        }

        @Test
        void consumerGroup_defaultsToAepGroup() {
            assertThat(empty().kafkaConsumerGroup()).isEqualTo("aep-consumer-group");
        }

        @Test
        void inputTopic_defaultsToEvents() {
            assertThat(empty().kafkaInputTopic()).isEqualTo("events");
        }

        @Test
        void outputTopic_defaultsToEventsOut() {
            assertThat(empty().kafkaOutputTopic()).isEqualTo("events-out");
        }
    }

    @Nested
    @DisplayName("Redis defaults")
    class RedisDefaults {

        @Test
        void host_defaultsToLocalhost() {
            assertThat(empty().redisHost()).isEqualTo("localhost");
        }

        @Test
        void port_defaultsTo6379() {
            assertThat(empty().redisPort()).isEqualTo(6379);
        }
    }

    @Nested
    @DisplayName("RabbitMQ defaults")
    class RabbitMqDefaults {

        @Test
        void host_defaultsToLocalhost() {
            assertThat(empty().rabbitMqHost()).isEqualTo("localhost");
        }

        @Test
        void port_defaultsTo5672() {
            assertThat(empty().rabbitMqPort()).isEqualTo(5672);
        }

        @Test
        void queue_defaultsToAepEvents() {
            assertThat(empty().rabbitMqQueue()).isEqualTo("aep-events");
        }
    }

    @Nested
    @DisplayName("S3 defaults")
    class S3Defaults {

        @Test
        void region_defaultsToUsEast1() {
            assertThat(empty().s3Region()).isEqualTo("us-east-1");
        }

        @Test
        void bucket_defaultsToAepStorage() {
            assertThat(empty().s3Bucket()).isEqualTo("aep-storage");
        }
    }

    @Nested
    @DisplayName("overrides are picked up")
    class Overrides {

        @Test
        void kafkaBootstrapServers_override() {
            EnvConfig env = with(EnvConfig.KAFKA_BOOTSTRAP_SERVERS, "broker1:9092,broker2:9092");
            assertThat(env.kafkaBootstrapServers()).isEqualTo("broker1:9092,broker2:9092");
        }

        @Test
        void redisHost_override() {
            EnvConfig env = with(EnvConfig.REDIS_HOST, "redis.prod.example.com");
            assertThat(env.redisHost()).isEqualTo("redis.prod.example.com");
        }

        @Test
        void redisPort_override() {
            EnvConfig env = with(EnvConfig.REDIS_PORT, "6380");
            assertThat(env.redisPort()).isEqualTo(6380);
        }
    }
}
