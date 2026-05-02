/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. 
 */

package com.ghatana.core.connectors.impl;

import com.ghatana.core.connectors.Connector;
import com.ghatana.platform.observability.health.HealthCheck;
import com.ghatana.platform.testing.activej.EventloopTestUtil;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link KafkaConnector} using a real Kafka container.
 * Covers connector lifecycle, producer publish, consumer subscribe, and health checks.
 *
 * @doc.type class
 * @doc.purpose Integration tests for KafkaConnector against a real Kafka broker
 * @doc.layer core
 * @doc.pattern Integration Test
 */
@Tag("integration")
@Tag("infrastructure-backed")
@Testcontainers
@DisplayName("KafkaConnector Integration Tests")
class KafkaConnectorIT {

    @Container
    @SuppressWarnings("resource")
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    private static final String TEST_TOPIC = "integration-test-topic";

    private KafkaConnector connector;
    private EventloopTestUtil.EventloopRunner eventloop;

    @BeforeEach
    void setUp() { 
        connector = new KafkaConnector("test-kafka-connector");
        eventloop = EventloopTestUtil.newRunnerBuilder().build(); 
    }

    @AfterEach
    void tearDown() { 
        if (connector != null) { 
            try {
                eventloop.runPromise(() -> connector.stop()); 
            } catch (Exception ignored) { 
                // Best-effort cleanup
            }
        }
        if (eventloop != null) { 
            eventloop.close(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should initialize and start sink connector successfully")
        void shouldInitializeAndStartSinkConnector() { 
            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() 
                    .name("test-sink")
                    .bootstrapServers(KAFKA.getBootstrapServers()) 
                    .topic(TEST_TOPIC) 
                    .sinkEnabled(true) 
                    .build(); 

            eventloop.runPromise(() -> connector.initialize(config)); 
            eventloop.runPromise(() -> connector.start()); 

            assertThat(connector.getStatus()).isEqualTo(Connector.ConnectorStatus.RUNNING); 
            assertThat(connector.isSinkCapable()).isTrue(); 
            assertThat(connector.isSourceCapable()).isFalse(); 
        }

        @Test
        @DisplayName("should initialize and start source connector successfully")
        void shouldInitializeAndStartSourceConnector() { 
            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() 
                    .name("test-source")
                    .bootstrapServers(KAFKA.getBootstrapServers()) 
                    .topic(TEST_TOPIC) 
                    .sourceEnabled(true) 
                    .property("consumer.group.id", "test-consumer-group-" + System.nanoTime()) 
                    .build(); 

            eventloop.runPromise(() -> connector.initialize(config)); 
            eventloop.runPromise(() -> connector.start()); 

            assertThat(connector.getStatus()).isEqualTo(Connector.ConnectorStatus.RUNNING); 
            assertThat(connector.isSourceCapable()).isTrue(); 
        }

        @Test
        @DisplayName("should transition from started to stopped cleanly")
        void shouldStopCleanly() { 
            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() 
                    .name("test-lifecycle")
                    .bootstrapServers(KAFKA.getBootstrapServers()) 
                    .topic(TEST_TOPIC) 
                    .sinkEnabled(true) 
                    .build(); 

            eventloop.runPromise(() -> connector.initialize(config)); 
            eventloop.runPromise(() -> connector.start()); 

            assertThat(connector.getStatus()).isEqualTo(Connector.ConnectorStatus.RUNNING); 

            eventloop.runPromise(() -> connector.stop()); 

            assertThat(connector.getStatus()).isEqualTo(Connector.ConnectorStatus.STOPPED); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Producer / Sink
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Producer publish")
    class ProducerPublish {

        @Test
        @DisplayName("should send message to Kafka topic")
        void shouldSendMessageToTopic() throws Exception { 
            String uniqueTopic = "producer-test-" + System.nanoTime(); 

            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() 
                    .name("producer-sink")
                    .bootstrapServers(KAFKA.getBootstrapServers()) 
                    .topic(uniqueTopic) 
                    .sinkEnabled(true) 
                    .build(); 

            eventloop.runPromise(() -> connector.initialize(config)); 
            eventloop.runPromise(() -> connector.start()); 

            KafkaProducer<String, String> producer = connector.getProducer(); 
            assertThat(producer).isNotNull(); 

            producer.send(new ProducerRecord<>(uniqueTopic, "key-1", "value-1")).get(); 
            producer.flush(); 

            // Verify by consuming the message
            List<String> received = consumeMessages(uniqueTopic, 1, Duration.ofSeconds(10)); 
            assertThat(received).containsExactly("value-1");
        }

        @Test
        @DisplayName("should send multiple messages in sequence")
        void shouldSendMultipleMessages() throws Exception { 
            String uniqueTopic = "multi-send-test-" + System.nanoTime(); 

            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() 
                    .name("multi-producer-sink")
                    .bootstrapServers(KAFKA.getBootstrapServers()) 
                    .topic(uniqueTopic) 
                    .sinkEnabled(true) 
                    .build(); 

            eventloop.runPromise(() -> connector.initialize(config)); 
            eventloop.runPromise(() -> connector.start()); 

            KafkaProducer<String, String> producer = connector.getProducer(); 
            for (int i = 0; i < 5; i++) { 
                producer.send(new ProducerRecord<>(uniqueTopic, "key-" + i, "value-" + i)).get(); 
            }
            producer.flush(); 

            List<String> received = consumeMessages(uniqueTopic, 5, Duration.ofSeconds(10)); 
            assertThat(received).hasSize(5) 
                    .containsExactlyInAnyOrder("value-0", "value-1", "value-2", "value-3", "value-4"); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Consumer / Source
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Consumer subscribe")
    class ConsumerSubscribe {

        @Test
        @DisplayName("should consume message from Kafka topic")
        void shouldConsumeMessageFromTopic() throws Exception { 
            String uniqueTopic = "consumer-test-" + System.nanoTime(); 
            String groupId = "consumer-group-" + System.nanoTime(); 

            // Publish a message before subscribing
            publishMessage(uniqueTopic, "test-key", "test-value"); 

            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() 
                    .name("consumer-source")
                    .bootstrapServers(KAFKA.getBootstrapServers()) 
                    .topic(uniqueTopic) 
                    .sourceEnabled(true) 
                    .property("consumer.group.id", groupId) 
                    .property("consumer.auto.offset.reset", "earliest") 
                    .build(); 

            eventloop.runPromise(() -> connector.initialize(config)); 
            eventloop.runPromise(() -> connector.start()); 

            KafkaConsumer<String, String> consumer = connector.getConsumer(); 
            assertThat(consumer).isNotNull(); 

            List<String> messages = new ArrayList<>(); 
            long deadline = System.currentTimeMillis() + 10_000; 
            while (messages.size() < 1 && System.currentTimeMillis() < deadline) { 
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500)); 
                for (ConsumerRecord<String, String> r : records) { 
                    messages.add(r.value()); 
                }
            }

            assertThat(messages).containsExactly("test-value");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Health check
    // ═══════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Health check")
    class HealthCheckTests {

        @Test
        @DisplayName("started sink connector should report healthy")
        void startedSinkConnectorShouldBeHealthy() { 
            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() 
                    .name("health-sink")
                    .bootstrapServers(KAFKA.getBootstrapServers()) 
                    .topic(TEST_TOPIC) 
                    .sinkEnabled(true) 
                    .build(); 

            eventloop.runPromise(() -> connector.initialize(config)); 
            eventloop.runPromise(() -> connector.start()); 

            HealthCheck.HealthCheckResult result =
                    eventloop.runPromise(() -> connector.check()); 

            assertThat(result.isHealthy()).isTrue(); 
        }

        @Test
        @DisplayName("uninitialized connector should report unhealthy")
        void uninitializedConnectorShouldBeUnhealthy() { 
            // Connector not initialized — health check should not throw, just report unhealthy
            HealthCheck.HealthCheckResult result =
                    eventloop.runPromise(() -> connector.check()); 

            assertThat(result.isHealthy()).isFalse(); 
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Publish a single message directly (no connector lifecycle involved). */ 
    private void publishMessage(String topic, String key, String value) throws Exception { 
        Properties props = new Properties(); 
        props.put("bootstrap.servers", KAFKA.getBootstrapServers()); 
        props.put("key.serializer", StringSerializer.class.getName()); 
        props.put("value.serializer", StringSerializer.class.getName()); 
        props.put("acks", "all"); 

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) { 
            producer.send(new ProducerRecord<>(topic, key, value)).get(); 
            producer.flush(); 
        }
    }

    /** Consume up to {@code maxMessages} from a topic, waiting up to {@code timeout}. */
    private List<String> consumeMessages(String topic, int maxMessages, Duration timeout) { 
        Properties props = new Properties(); 
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()); 
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "verifier-group-" + System.nanoTime()); 
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()); 
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()); 
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); 
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); 

        List<String> messages = new ArrayList<>(); 
        long deadline = System.currentTimeMillis() + timeout.toMillis(); 

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) { 
            consumer.subscribe(List.of(topic)); 
            while (messages.size() < maxMessages && System.currentTimeMillis() < deadline) { 
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200)); 
                for (ConsumerRecord<String, String> r : records) { 
                    messages.add(r.value()); 
                }
            }
        }

        return messages;
    }
}
