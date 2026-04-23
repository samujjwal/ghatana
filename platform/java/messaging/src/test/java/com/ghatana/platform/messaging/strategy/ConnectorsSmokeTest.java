package com.ghatana.platform.messaging.strategy;

import com.ghatana.platform.messaging.config.ConnectorConfig;
import com.ghatana.platform.messaging.config.RetryConfig;
import com.ghatana.platform.messaging.config.TlsConfig;
import com.ghatana.platform.messaging.strategy.http.HttpIngressConfig;
import com.ghatana.platform.messaging.strategy.kafka.KafkaConsumerConfig;
import com.ghatana.platform.messaging.strategy.kafka.KafkaProducerConfig;
import com.ghatana.platform.messaging.strategy.rabbitmq.RabbitMQConfig;
import com.ghatana.platform.messaging.strategy.s3.S3Config;
import com.ghatana.platform.messaging.strategy.sqs.SqsConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for AEP connector configuration classes.
 * Validates the immutable builder pattern, inheritance from {@link ConnectorConfig},
 * and default values for each connector type.
 *
 * @doc.type class
 * @doc.purpose Tests for connector config builder API and ConnectorConfig inheritance
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Connector Configuration Tests")
class ConnectorsSmokeTest {

    // ── QueueMessage ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("QueueMessage can be constructed and fields accessed")
    void queueMessageFields() { // GH-90000
        var msg = new QueueMessage("msg-1", "payload body", Map.of("header1", "value1")); // GH-90000
        assertThat(msg.getId()).isEqualTo("msg-1");
        assertThat(msg.getBody()).isEqualTo("payload body");
        assertThat(msg.getHeaders().get("header1")).isEqualTo("value1");
        assertThat(msg.getTimestamp()).isPositive(); // GH-90000
    }

    // ── TlsConfig ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TlsConfig")
    class TlsConfigTests {
        @Test
        @DisplayName("DISABLED constant has TLS off")
        void disabledConstant() { // GH-90000
            assertThat(TlsConfig.DISABLED.enabled()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("DEFAULT_ENABLED constant has TLS on")
        void defaultEnabledConstant() { // GH-90000
            assertThat(TlsConfig.DEFAULT_ENABLED.enabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("Builder produces correct TlsConfig")
        void builder() { // GH-90000
            TlsConfig config = TlsConfig.builder() // GH-90000
                .enabled(true) // GH-90000
                .truststorePath("/certs/truststore.jks")
                .build(); // GH-90000
            assertThat(config.enabled()).isTrue(); // GH-90000
            assertThat(config.truststorePath()).isEqualTo("/certs/truststore.jks");
        }
    }

    // ── RetryConfig ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("RetryConfig")
    class RetryConfigTests {
        @Test
        @DisplayName("NO_RETRY constant has maxAttempts=1")
        void noRetryConstant() { // GH-90000
            assertThat(RetryConfig.NO_RETRY.maxAttempts()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("DEFAULT constant has maxAttempts=3")
        void defaultConstant() { // GH-90000
            assertThat(RetryConfig.DEFAULT.maxAttempts()).isEqualTo(3); // GH-90000
        }

        @Test
        @DisplayName("Builder produces correct RetryConfig")
        void builder() { // GH-90000
            RetryConfig config = RetryConfig.builder() // GH-90000
                .maxAttempts(5) // GH-90000
                .initialDelay(Duration.ofMillis(200)) // GH-90000
                .backoffMultiplier(3.0) // GH-90000
                .maxDelay(Duration.ofSeconds(60)) // GH-90000
                .build(); // GH-90000
            assertThat(config.maxAttempts()).isEqualTo(5); // GH-90000
            assertThat(config.initialDelay()).isEqualTo(Duration.ofMillis(200)); // GH-90000
            assertThat(config.backoffMultiplier()).isEqualTo(3.0); // GH-90000
        }

        @Test
        @DisplayName("Validation rejects maxAttempts < 1")
        void rejectsZeroAttempts() { // GH-90000
            assertThatThrownBy(() -> RetryConfig.builder().maxAttempts(0).build()) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ── KafkaConsumerConfig ──────────────────────────────────────────────────

    @Nested
    @DisplayName("KafkaConsumerConfig")
    class KafkaConsumerConfigTests {
        @Test
        @DisplayName("Builder builds config with correct fields")
        void buildsCorrectly() { // GH-90000
            KafkaConsumerConfig config = KafkaConsumerConfig.builder() // GH-90000
                .bootstrapServers("localhost:9092")
                .topic("events")
                .groupId("test-group")
                .build(); // GH-90000

            assertThat(config.bootstrapServers()).isEqualTo("localhost:9092");
            assertThat(config.topic()).isEqualTo("events");
            assertThat(config.groupId()).isEqualTo("test-group");
            assertThat(config.pollTimeoutMs()).isEqualTo(1000); // GH-90000
        }

        @Test
        @DisplayName("Inherits TLS and retry from ConnectorConfig base")
        void inheritsBaseConfig() { // GH-90000
            KafkaConsumerConfig config = KafkaConsumerConfig.builder() // GH-90000
                .bootstrapServers("kafka:9094")
                .topic("secure-events")
                .groupId("secure-group")
                .tlsConfig(TlsConfig.DEFAULT_ENABLED) // GH-90000
                .retryConfig(RetryConfig.builder().maxAttempts(5).build()) // GH-90000
                .build(); // GH-90000

            assertThat(config).isInstanceOf(ConnectorConfig.class); // GH-90000
            assertThat(config.isTlsEnabled()).isTrue(); // GH-90000
            assertThat(config.retryConfig().maxAttempts()).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("Requires non-null bootstrapServers")
        void requiresBootstrapServers() { // GH-90000
            assertThatThrownBy(() -> KafkaConsumerConfig.builder() // GH-90000
                .topic("events").groupId("g").build())
                .isInstanceOf(NullPointerException.class); // GH-90000
        }
    }

    // ── KafkaProducerConfig ──────────────────────────────────────────────────

    @Nested
    @DisplayName("KafkaProducerConfig")
    class KafkaProducerConfigTests {
        @Test
        @DisplayName("Builder builds config with defaults")
        void buildsWithDefaults() { // GH-90000
            KafkaProducerConfig config = KafkaProducerConfig.builder() // GH-90000
                .bootstrapServers("localhost:9092")
                .topic("output")
                .build(); // GH-90000

            assertThat(config.bootstrapServers()).isEqualTo("localhost:9092");
            assertThat(config.batchSize()).isEqualTo(16384); // GH-90000
            assertThat(config.isTlsEnabled()).isFalse(); // GH-90000
        }
    }

    // ── RabbitMQConfig ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("RabbitMQConfig")
    class RabbitMQConfigTests {
        @Test
        @DisplayName("Builder sets defaults and fields correctly")
        void buildsCorrectly() { // GH-90000
            RabbitMQConfig config = RabbitMQConfig.builder() // GH-90000
                .host("rabbitmq.internal")
                .username("user")
                .password("pass")
                .queueName("events")
                .build(); // GH-90000

            assertThat(config.rabbitHost()).isEqualTo("rabbitmq.internal");
            assertThat(config.rabbitPort()).isEqualTo(5672); // GH-90000
            assertThat(config.virtualHost()).isEqualTo("/");
            assertThat(config.queueName()).isEqualTo("events");
            assertThat(config.maxDeliveryAttempts()).isEqualTo(Integer.MAX_VALUE); // GH-90000
            assertThat(config.isTlsEnabled()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("Builder allows configuring max delivery attempts")
        void buildsWithConfiguredMaxDeliveryAttempts() { // GH-90000
            RabbitMQConfig config = RabbitMQConfig.builder() // GH-90000
                .host("rabbitmq.internal")
                .username("user")
                .password("pass")
                .queueName("events")
                .maxDeliveryAttempts(2) // GH-90000
                .build(); // GH-90000

            assertThat(config.maxDeliveryAttempts()).isEqualTo(2); // GH-90000
        }
    }

    // ── SqsConfig ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SqsConfig")
    class SqsConfigTests {
        @Test
        @DisplayName("Builder sets fields and defaults correctly")
        void buildsCorrectly() { // GH-90000
            SqsConfig config = SqsConfig.builder() // GH-90000
                .queueUrl("https://sqs.us-east-1.amazonaws.com/123/queue")
                .region("us-east-1")
                .build(); // GH-90000

            assertThat(config.queueUrl()).isNotNull(); // GH-90000
            assertThat(config.region()).isEqualTo("us-east-1");
            assertThat(config.maxMessages()).isEqualTo(10); // GH-90000
            assertThat(config.waitTimeSeconds()).isEqualTo(20); // GH-90000
        }
    }

    // ── S3Config ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S3Config")
    class S3ConfigTests {
        @Test
        @DisplayName("Builder sets fields and defaults correctly")
        void buildsCorrectly() { // GH-90000
            S3Config config = S3Config.builder() // GH-90000
                .bucketName("my-bucket")
                .region("us-west-2")
                .build(); // GH-90000

            assertThat(config.bucketName()).isEqualTo("my-bucket");
            assertThat(config.region()).isEqualTo("us-west-2");
            assertThat(config.prefix()).isEmpty(); // GH-90000
            assertThat(config.isTlsEnabled()).isFalse(); // GH-90000
        }
    }

    // ── HttpIngressConfig ────────────────────────────────────────────────────

    @Nested
    @DisplayName("HttpIngressConfig")
    class HttpIngressConfigTests {
        @Test
        @DisplayName("Builder sets fields and defaults correctly")
        void buildsCorrectly() { // GH-90000
            HttpIngressConfig config = HttpIngressConfig.builder() // GH-90000
                .endpoint("https://api.example.com")
                .httpPort(8443) // GH-90000
                .path("/events")
                .tlsConfig(TlsConfig.DEFAULT_ENABLED) // GH-90000
                .build(); // GH-90000

            assertThat(config.endpoint()).isEqualTo("https://api.example.com");
            assertThat(config.httpPort()).isEqualTo(8443); // GH-90000
            assertThat(config.path()).isEqualTo("/events");
            assertThat(config.method()).isEqualTo("POST");
            assertThat(config.isTlsEnabled()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("timeoutMs convenience method sets readTimeout")
        void timeoutMsConvenienceMethod() { // GH-90000
            HttpIngressConfig config = HttpIngressConfig.builder() // GH-90000
                .endpoint("http://example.com")
                .path("/events")
                .timeoutMs(5000) // GH-90000
                .build(); // GH-90000

            assertThat(config.readTimeout()).isEqualTo(Duration.ofMillis(5000)); // GH-90000
        }
    }
}
