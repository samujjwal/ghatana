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
    void queueMessageFields() { 
        var msg = new QueueMessage("msg-1", "payload body", Map.of("header1", "value1")); 
        assertThat(msg.getId()).isEqualTo("msg-1");
        assertThat(msg.getBody()).isEqualTo("payload body");
        assertThat(msg.getHeaders().get("header1")).isEqualTo("value1");
        assertThat(msg.getTimestamp()).isPositive(); 
    }

    // ── TlsConfig ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("TlsConfig")
    class TlsConfigTests {
        @Test
        @DisplayName("DISABLED constant has TLS off")
        void disabledConstant() { 
            assertThat(TlsConfig.DISABLED.enabled()).isFalse(); 
        }

        @Test
        @DisplayName("DEFAULT_ENABLED constant has TLS on")
        void defaultEnabledConstant() { 
            assertThat(TlsConfig.DEFAULT_ENABLED.enabled()).isTrue(); 
        }

        @Test
        @DisplayName("Builder produces correct TlsConfig")
        void builder() { 
            TlsConfig config = TlsConfig.builder() 
                .enabled(true) 
                .truststorePath("/certs/truststore.jks")
                .build(); 
            assertThat(config.enabled()).isTrue(); 
            assertThat(config.truststorePath()).isEqualTo("/certs/truststore.jks");
        }
    }

    // ── RetryConfig ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("RetryConfig")
    class RetryConfigTests {
        @Test
        @DisplayName("NO_RETRY constant has maxAttempts=1")
        void noRetryConstant() { 
            assertThat(RetryConfig.NO_RETRY.maxAttempts()).isEqualTo(1); 
        }

        @Test
        @DisplayName("DEFAULT constant has maxAttempts=3")
        void defaultConstant() { 
            assertThat(RetryConfig.DEFAULT.maxAttempts()).isEqualTo(3); 
        }

        @Test
        @DisplayName("Builder produces correct RetryConfig")
        void builder() { 
            RetryConfig config = RetryConfig.builder() 
                .maxAttempts(5) 
                .initialDelay(Duration.ofMillis(200)) 
                .backoffMultiplier(3.0) 
                .maxDelay(Duration.ofSeconds(60)) 
                .build(); 
            assertThat(config.maxAttempts()).isEqualTo(5); 
            assertThat(config.initialDelay()).isEqualTo(Duration.ofMillis(200)); 
            assertThat(config.backoffMultiplier()).isEqualTo(3.0); 
        }

        @Test
        @DisplayName("Validation rejects maxAttempts < 1")
        void rejectsZeroAttempts() { 
            assertThatThrownBy(() -> RetryConfig.builder().maxAttempts(0).build()) 
                .isInstanceOf(IllegalArgumentException.class); 
        }
    }

    // ── KafkaConsumerConfig ──────────────────────────────────────────────────

    @Nested
    @DisplayName("KafkaConsumerConfig")
    class KafkaConsumerConfigTests {
        @Test
        @DisplayName("Builder builds config with correct fields")
        void buildsCorrectly() { 
            KafkaConsumerConfig config = KafkaConsumerConfig.builder() 
                .bootstrapServers("localhost:9092")
                .topic("events")
                .groupId("test-group")
                .build(); 

            assertThat(config.bootstrapServers()).isEqualTo("localhost:9092");
            assertThat(config.topic()).isEqualTo("events");
            assertThat(config.groupId()).isEqualTo("test-group");
            assertThat(config.pollTimeoutMs()).isEqualTo(1000); 
        }

        @Test
        @DisplayName("Inherits TLS and retry from ConnectorConfig base")
        void inheritsBaseConfig() { 
            KafkaConsumerConfig config = KafkaConsumerConfig.builder() 
                .bootstrapServers("kafka:9094")
                .topic("secure-events")
                .groupId("secure-group")
                .tlsConfig(TlsConfig.DEFAULT_ENABLED) 
                .retryConfig(RetryConfig.builder().maxAttempts(5).build()) 
                .build(); 

            assertThat(config).isInstanceOf(ConnectorConfig.class); 
            assertThat(config.isTlsEnabled()).isTrue(); 
            assertThat(config.retryConfig().maxAttempts()).isEqualTo(5); 
        }

        @Test
        @DisplayName("Requires non-null bootstrapServers")
        void requiresBootstrapServers() { 
            assertThatThrownBy(() -> KafkaConsumerConfig.builder() 
                .topic("events").groupId("g").build())
                .isInstanceOf(NullPointerException.class); 
        }
    }

    // ── KafkaProducerConfig ──────────────────────────────────────────────────

    @Nested
    @DisplayName("KafkaProducerConfig")
    class KafkaProducerConfigTests {
        @Test
        @DisplayName("Builder builds config with defaults")
        void buildsWithDefaults() { 
            KafkaProducerConfig config = KafkaProducerConfig.builder() 
                .bootstrapServers("localhost:9092")
                .topic("output")
                .build(); 

            assertThat(config.bootstrapServers()).isEqualTo("localhost:9092");
            assertThat(config.batchSize()).isEqualTo(16384); 
            assertThat(config.isTlsEnabled()).isFalse(); 
        }
    }

    // ── RabbitMQConfig ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("RabbitMQConfig")
    class RabbitMQConfigTests {
        @Test
        @DisplayName("Builder sets defaults and fields correctly")
        void buildsCorrectly() { 
            RabbitMQConfig config = RabbitMQConfig.builder() 
                .host("rabbitmq.internal")
                .username("user")
                .password("pass")
                .queueName("events")
                .build(); 

            assertThat(config.rabbitHost()).isEqualTo("rabbitmq.internal");
            assertThat(config.rabbitPort()).isEqualTo(5672); 
            assertThat(config.virtualHost()).isEqualTo("/");
            assertThat(config.queueName()).isEqualTo("events");
            assertThat(config.maxDeliveryAttempts()).isEqualTo(Integer.MAX_VALUE); 
            assertThat(config.isTlsEnabled()).isFalse(); 
        }

        @Test
        @DisplayName("Builder allows configuring max delivery attempts")
        void buildsWithConfiguredMaxDeliveryAttempts() { 
            RabbitMQConfig config = RabbitMQConfig.builder() 
                .host("rabbitmq.internal")
                .username("user")
                .password("pass")
                .queueName("events")
                .maxDeliveryAttempts(2) 
                .build(); 

            assertThat(config.maxDeliveryAttempts()).isEqualTo(2); 
        }
    }

    // ── SqsConfig ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SqsConfig")
    class SqsConfigTests {
        @Test
        @DisplayName("Builder sets fields and defaults correctly")
        void buildsCorrectly() { 
            SqsConfig config = SqsConfig.builder() 
                .queueUrl("https://sqs.us-east-1.amazonaws.com/123/queue")
                .region("us-east-1")
                .build(); 

            assertThat(config.queueUrl()).isNotNull(); 
            assertThat(config.region()).isEqualTo("us-east-1");
            assertThat(config.maxMessages()).isEqualTo(10); 
            assertThat(config.waitTimeSeconds()).isEqualTo(20); 
        }
    }

    // ── S3Config ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("S3Config")
    class S3ConfigTests {
        @Test
        @DisplayName("Builder sets fields and defaults correctly")
        void buildsCorrectly() { 
            S3Config config = S3Config.builder() 
                .bucketName("my-bucket")
                .region("us-west-2")
                .build(); 

            assertThat(config.bucketName()).isEqualTo("my-bucket");
            assertThat(config.region()).isEqualTo("us-west-2");
            assertThat(config.prefix()).isEmpty(); 
            assertThat(config.isTlsEnabled()).isFalse(); 
        }
    }

    // ── HttpIngressConfig ────────────────────────────────────────────────────

    @Nested
    @DisplayName("HttpIngressConfig")
    class HttpIngressConfigTests {
        @Test
        @DisplayName("Builder sets fields and defaults correctly")
        void buildsCorrectly() { 
            HttpIngressConfig config = HttpIngressConfig.builder() 
                .endpoint("https://api.example.com")
                .httpPort(8443) 
                .path("/events")
                .tlsConfig(TlsConfig.DEFAULT_ENABLED) 
                .build(); 

            assertThat(config.endpoint()).isEqualTo("https://api.example.com");
            assertThat(config.httpPort()).isEqualTo(8443); 
            assertThat(config.path()).isEqualTo("/events");
            assertThat(config.method()).isEqualTo("POST");
            assertThat(config.isTlsEnabled()).isTrue(); 
        }

        @Test
        @DisplayName("timeoutMs convenience method sets readTimeout")
        void timeoutMsConvenienceMethod() { 
            HttpIngressConfig config = HttpIngressConfig.builder() 
                .endpoint("http://example.com")
                .path("/events")
                .timeoutMs(5000) 
                .build(); 

            assertThat(config.readTimeout()).isEqualTo(Duration.ofMillis(5000)); 
        }
    }
}
