/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
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
    void setUp() { // GH-90000
        connector = new KafkaConnector("test-kafka-connector");
        eventloop = EventloopTestUtil.newRunnerBuilder().build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (connector != null) { // GH-90000
            try {
                eventloop.runPromise(() -> connector.stop()); // GH-90000
            } catch (Exception ignored) { // GH-90000
                // Best-effort cleanup
            }
        }
        if (eventloop != null) { // GH-90000
            eventloop.close(); // GH-90000
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
        void shouldInitializeAndStartSinkConnector() { // GH-90000
            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() // GH-90000
                    .name("test-sink")
                    .bootstrapServers(KAFKA.getBootstrapServers()) // GH-90000
                    .topic(TEST_TOPIC) // GH-90000
                    .sinkEnabled(true) // GH-90000
                    .build(); // GH-90000

            eventloop.runPromise(() -> connector.initialize(config)); // GH-90000
            eventloop.runPromise(() -> connector.start()); // GH-90000

            assertThat(connector.getStatus()).isEqualTo(Connector.ConnectorStatus.RUNNING); // GH-90000
            assertThat(connector.isSinkCapable()).isTrue(); // GH-90000
            assertThat(connector.isSourceCapable()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should initialize and start source connector successfully")
        void shouldInitializeAndStartSourceConnector() { // GH-90000
            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() // GH-90000
                    .name("test-source")
                    .bootstrapServers(KAFKA.getBootstrapServers()) // GH-90000
                    .topic(TEST_TOPIC) // GH-90000
                    .sourceEnabled(true) // GH-90000
                    .property("consumer.group.id", "test-consumer-group-" + System.nanoTime()) // GH-90000
                    .build(); // GH-90000

            eventloop.runPromise(() -> connector.initialize(config)); // GH-90000
            eventloop.runPromise(() -> connector.start()); // GH-90000

            assertThat(connector.getStatus()).isEqualTo(Connector.ConnectorStatus.RUNNING); // GH-90000
            assertThat(connector.isSourceCapable()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should transition from started to stopped cleanly")
        void shouldStopCleanly() { // GH-90000
            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() // GH-90000
                    .name("test-lifecycle")
                    .bootstrapServers(KAFKA.getBootstrapServers()) // GH-90000
                    .topic(TEST_TOPIC) // GH-90000
                    .sinkEnabled(true) // GH-90000
                    .build(); // GH-90000

            eventloop.runPromise(() -> connector.initialize(config)); // GH-90000
            eventloop.runPromise(() -> connector.start()); // GH-90000

            assertThat(connector.getStatus()).isEqualTo(Connector.ConnectorStatus.RUNNING); // GH-90000

            eventloop.runPromise(() -> connector.stop()); // GH-90000

            assertThat(connector.getStatus()).isEqualTo(Connector.ConnectorStatus.STOPPED); // GH-90000
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
        void shouldSendMessageToTopic() throws Exception { // GH-90000
            String uniqueTopic = "producer-test-" + System.nanoTime(); // GH-90000

            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() // GH-90000
                    .name("producer-sink")
                    .bootstrapServers(KAFKA.getBootstrapServers()) // GH-90000
                    .topic(uniqueTopic) // GH-90000
                    .sinkEnabled(true) // GH-90000
                    .build(); // GH-90000

            eventloop.runPromise(() -> connector.initialize(config)); // GH-90000
            eventloop.runPromise(() -> connector.start()); // GH-90000

            KafkaProducer<String, String> producer = connector.getProducer(); // GH-90000
            assertThat(producer).isNotNull(); // GH-90000

            producer.send(new ProducerRecord<>(uniqueTopic, "key-1", "value-1")).get(); // GH-90000
            producer.flush(); // GH-90000

            // Verify by consuming the message
            List<String> received = consumeMessages(uniqueTopic, 1, Duration.ofSeconds(10)); // GH-90000
            assertThat(received).containsExactly("value-1");
        }

        @Test
        @DisplayName("should send multiple messages in sequence")
        void shouldSendMultipleMessages() throws Exception { // GH-90000
            String uniqueTopic = "multi-send-test-" + System.nanoTime(); // GH-90000

            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() // GH-90000
                    .name("multi-producer-sink")
                    .bootstrapServers(KAFKA.getBootstrapServers()) // GH-90000
                    .topic(uniqueTopic) // GH-90000
                    .sinkEnabled(true) // GH-90000
                    .build(); // GH-90000

            eventloop.runPromise(() -> connector.initialize(config)); // GH-90000
            eventloop.runPromise(() -> connector.start()); // GH-90000

            KafkaProducer<String, String> producer = connector.getProducer(); // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                producer.send(new ProducerRecord<>(uniqueTopic, "key-" + i, "value-" + i)).get(); // GH-90000
            }
            producer.flush(); // GH-90000

            List<String> received = consumeMessages(uniqueTopic, 5, Duration.ofSeconds(10)); // GH-90000
            assertThat(received).hasSize(5) // GH-90000
                    .containsExactlyInAnyOrder("value-0", "value-1", "value-2", "value-3", "value-4"); // GH-90000
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
        void shouldConsumeMessageFromTopic() throws Exception { // GH-90000
            String uniqueTopic = "consumer-test-" + System.nanoTime(); // GH-90000
            String groupId = "consumer-group-" + System.nanoTime(); // GH-90000

            // Publish a message before subscribing
            publishMessage(uniqueTopic, "test-key", "test-value"); // GH-90000

            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() // GH-90000
                    .name("consumer-source")
                    .bootstrapServers(KAFKA.getBootstrapServers()) // GH-90000
                    .topic(uniqueTopic) // GH-90000
                    .sourceEnabled(true) // GH-90000
                    .property("consumer.group.id", groupId) // GH-90000
                    .property("consumer.auto.offset.reset", "earliest") // GH-90000
                    .build(); // GH-90000

            eventloop.runPromise(() -> connector.initialize(config)); // GH-90000
            eventloop.runPromise(() -> connector.start()); // GH-90000

            KafkaConsumer<String, String> consumer = connector.getConsumer(); // GH-90000
            assertThat(consumer).isNotNull(); // GH-90000

            List<String> messages = new ArrayList<>(); // GH-90000
            long deadline = System.currentTimeMillis() + 10_000; // GH-90000
            while (messages.size() < 1 && System.currentTimeMillis() < deadline) { // GH-90000
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500)); // GH-90000
                for (ConsumerRecord<String, String> r : records) { // GH-90000
                    messages.add(r.value()); // GH-90000
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
        void startedSinkConnectorShouldBeHealthy() { // GH-90000
            KafkaConnector.KafkaConnectorConfig config = KafkaConnector.KafkaConnectorConfig.builder() // GH-90000
                    .name("health-sink")
                    .bootstrapServers(KAFKA.getBootstrapServers()) // GH-90000
                    .topic(TEST_TOPIC) // GH-90000
                    .sinkEnabled(true) // GH-90000
                    .build(); // GH-90000

            eventloop.runPromise(() -> connector.initialize(config)); // GH-90000
            eventloop.runPromise(() -> connector.start()); // GH-90000

            HealthCheck.HealthCheckResult result =
                    eventloop.runPromise(() -> connector.check()); // GH-90000

            assertThat(result.isHealthy()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("uninitialized connector should report unhealthy")
        void uninitializedConnectorShouldBeUnhealthy() { // GH-90000
            // Connector not initialized — health check should not throw, just report unhealthy
            HealthCheck.HealthCheckResult result =
                    eventloop.runPromise(() -> connector.check()); // GH-90000

            assertThat(result.isHealthy()).isFalse(); // GH-90000
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /** Publish a single message directly (no connector lifecycle involved). */ // GH-90000
    private void publishMessage(String topic, String key, String value) throws Exception { // GH-90000
        Properties props = new Properties(); // GH-90000
        props.put("bootstrap.servers", KAFKA.getBootstrapServers()); // GH-90000
        props.put("key.serializer", StringSerializer.class.getName()); // GH-90000
        props.put("value.serializer", StringSerializer.class.getName()); // GH-90000
        props.put("acks", "all"); // GH-90000

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) { // GH-90000
            producer.send(new ProducerRecord<>(topic, key, value)).get(); // GH-90000
            producer.flush(); // GH-90000
        }
    }

    /** Consume up to {@code maxMessages} from a topic, waiting up to {@code timeout}. */
    private List<String> consumeMessages(String topic, int maxMessages, Duration timeout) { // GH-90000
        Properties props = new Properties(); // GH-90000
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()); // GH-90000
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "verifier-group-" + System.nanoTime()); // GH-90000
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()); // GH-90000
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()); // GH-90000
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // GH-90000
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // GH-90000

        List<String> messages = new ArrayList<>(); // GH-90000
        long deadline = System.currentTimeMillis() + timeout.toMillis(); // GH-90000

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) { // GH-90000
            consumer.subscribe(List.of(topic)); // GH-90000
            while (messages.size() < maxMessages && System.currentTimeMillis() < deadline) { // GH-90000
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(200)); // GH-90000
                for (ConsumerRecord<String, String> r : records) { // GH-90000
                    messages.add(r.value()); // GH-90000
                }
            }
        }

        return messages;
    }
}
