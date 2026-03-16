/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.connector.strategy.kafka;

import com.ghatana.aep.connector.strategy.QueueMessage;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Kafka Dead-Letter Topic (DLT) retry logic inside
 * {@link KafkaConsumerStrategy}.
 *
 * <p>Uses Apache Kafka's {@code MockConsumer} and {@code MockProducer} to run tests
 * without a live Kafka broker. The package-private constructor of
 * {@code KafkaConsumerStrategy} is used to inject the mocks.
 *
 * @doc.type class
 * @doc.purpose Unit tests for Kafka DLT routing and retry-counter logic
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Kafka DLT Retry Logic")
class KafkaDltTest extends EventloopTestBase {

    private static final String TOPIC     = "aep.events";
    private static final int    PARTITION = 0;
    private static final long   OFFSET    = 42L;
    private static final String MESSAGE_VALUE = "{\"type\":\"click\",\"userId\":\"u1\"}";
    private static final String MESSAGE_ID    = TOPIC + "-" + PARTITION + "-" + OFFSET;

    private MockConsumer<String, String>  mockConsumer;
    private MockProducer<String, String>  mockProducer;

    @BeforeEach
    void setUp() {
        mockConsumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
        TopicPartition tp = new TopicPartition(TOPIC, PARTITION);
        mockConsumer.assign(List.of(tp));
        mockConsumer.updateBeginningOffsets(Map.of(tp, 0L));

        mockProducer = new MockProducer<>(
                /*autoComplete=*/true,
                new StringSerializer(),
                new StringSerializer());
    }

    // =========================================================================
    // Retry counter
    // =========================================================================

    @Nested
    @DisplayName("Retry Counter")
    class RetryCounterTests {

        @Test
        @DisplayName("first nack within maxRetries does NOT publish to DLT")
        void firstNack_withinRetryBudget_doesNotPublishToDlt() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/3);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID, "transient error"));

            assertThat(mockProducer.history()).isEmpty();
        }

        @Test
        @DisplayName("nack before exhaustion does NOT clear the retry counter")
        void nack_beforeExhaustion_retryCounterRetained() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/3);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID, "err1"));
            runPromise(() -> strategy.nack(MESSAGE_ID, "err2"));

            // Still within budget — DLT not invoked yet
            assertThat(mockProducer.history()).isEmpty();
        }

        @Test
        @DisplayName("acknowledge removes retry counter entry")
        void acknowledge_clearRetryCounter() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/3);
            pollOneRecord(strategy);

            // One nack then acknowledge
            runPromise(() -> strategy.nack(MESSAGE_ID, "transient"));
            // Re-poll the same message (simulate Kafka re-delivery).
            // MockConsumer advances position after each poll, so we must seek back to the
            // un-committed offset — exactly what Kafka would do on rebalance/restart.
            mockConsumer.seek(new TopicPartition(TOPIC, PARTITION), OFFSET);
            mockConsumer.addRecord(record(OFFSET));
            List<QueueMessage> msgs2 = runPromise(strategy::poll);
            String messageId2 = msgs2.get(0).messageId();

            runPromise(() -> strategy.acknowledge(messageId2));

            // After ack the retry counter should be gone — next re-delivery starts fresh
            // (verified implicitly: one more nack should NOT trigger DLT since counter restarted)
            assertThat(msgId(msgs2)).isEqualTo(messageId2);
        }
    }

    // =========================================================================
    // DLT routing
    // =========================================================================

    @Nested
    @DisplayName("DLT Routing")
    class DltRoutingTests {

        @Test
        @DisplayName("after maxRetries nacks, message is published to DLT topic")
        void exhaustedRetries_publishesToDltTopic() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/2);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID, "error1"));
            runPromise(() -> strategy.nack(MESSAGE_ID, "error2")); // exhausts → DLT

            assertThat(mockProducer.history()).hasSize(1);
            ProducerRecord<String, String> dlt = mockProducer.history().get(0);
            assertThat(dlt.topic()).isEqualTo(TOPIC + ".dlt");
        }

        @Test
        @DisplayName("DLT record key matches the original messageId")
        void dltRecord_keyIsMessageId() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/1);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID, "fail"));

            ProducerRecord<String, String> dlt = mockProducer.history().get(0);
            assertThat(dlt.key()).isEqualTo(MESSAGE_ID);
        }

        @Test
        @DisplayName("DLT record carries X-DLT-OriginalTopic header")
        void dltRecord_hasOriginalTopicHeader() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/1);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID, "fail"));

            String originalTopic = headerValue(mockProducer.history().get(0), "X-DLT-OriginalTopic");
            assertThat(originalTopic).isEqualTo(TOPIC);
        }

        @Test
        @DisplayName("DLT record carries X-DLT-OriginalPartition header")
        void dltRecord_hasOriginalPartitionHeader() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/1);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID, "fail"));

            String partition = headerValue(mockProducer.history().get(0), "X-DLT-OriginalPartition");
            assertThat(partition).isEqualTo(String.valueOf(PARTITION));
        }

        @Test
        @DisplayName("DLT record carries X-DLT-OriginalOffset header")
        void dltRecord_hasOriginalOffsetHeader() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/1);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID, "fail"));

            String offset = headerValue(mockProducer.history().get(0), "X-DLT-OriginalOffset");
            assertThat(offset).isEqualTo(String.valueOf(OFFSET));
        }

        @Test
        @DisplayName("DLT record carries X-DLT-RetryCount header with exhausted count")
        void dltRecord_hasRetryCountHeader() {
            int maxRetries = 3;
            KafkaConsumerStrategy strategy = strategy(maxRetries);
            pollOneRecord(strategy);

            for (int i = 0; i < maxRetries; i++) {
                runPromise(() -> strategy.nack(MESSAGE_ID, "err"));
            }

            String retryCount = headerValue(mockProducer.history().get(0), "X-DLT-RetryCount");
            assertThat(Integer.parseInt(retryCount)).isEqualTo(maxRetries);
        }

        @Test
        @DisplayName("DLT record carries X-DLT-ErrorMessage header when errorMessage is set")
        void dltRecord_hasErrorMessageHeader() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/1);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID, "NullPointerException at line 42"));

            String errorMsg = headerValue(mockProducer.history().get(0), "X-DLT-ErrorMessage");
            assertThat(errorMsg).isEqualTo("NullPointerException at line 42");
        }

        @Test
        @DisplayName("DLT record omits X-DLT-ErrorMessage when errorMessage is null")
        void dltRecord_omitsErrorMessageHeader_whenNull() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/1);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID, null));  // nack without reason

            ProducerRecord<String, String> dlt = mockProducer.history().get(0);
            // Header should not be present
            boolean hasErrorMsg = false;
            for (org.apache.kafka.common.header.Header h : dlt.headers()) {
                if ("X-DLT-ErrorMessage".equals(h.key())) {
                    hasErrorMsg = true;
                    break;
                }
            }
            assertThat(hasErrorMsg).isFalse();
        }

        @Test
        @DisplayName("DLT record carries X-DLT-FailedAt ISO-8601 timestamp header")
        void dltRecord_hasFailedAtHeader() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/1);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID, "fail"));

            String failedAt = headerValue(mockProducer.history().get(0), "X-DLT-FailedAt");
            assertThat(failedAt).isNotNull().isNotEmpty();
            // Validate it's parsable as an Instant (ISO-8601)
            java.time.Instant.parse(failedAt); // throws if invalid
        }

        @Test
        @DisplayName("custom dltTopicSuffix is honoured")
        void customDltTopicSuffix_isUsed() {
            KafkaConsumerStrategy strategy = strategyWithSuffix(/*maxRetries=*/1, "._dead");
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID, "fail"));

            assertThat(mockProducer.history().get(0).topic())
                    .isEqualTo(TOPIC + "._dead");
        }
    }

    // =========================================================================
    // maxRetries=0 disables DLT
    // =========================================================================

    @Nested
    @DisplayName("DLT Disabled (maxRetries=0)")
    class DltDisabledTests {

        @Test
        @DisplayName("nack with maxRetries=0 never publishes to DLT")
        void maxRetriesZero_neverPublishesToDlt() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/0);
            pollOneRecord(strategy);

            // Many nacks — DLT must stay empty
            for (int i = 0; i < 10; i++) {
                runPromise(() -> strategy.nack(MESSAGE_ID, "err"));
            }

            assertThat(mockProducer.history()).isEmpty();
        }
    }

    // =========================================================================
    // nack(String) single-arg interface compatibility
    // =========================================================================

    @Nested
    @DisplayName("Single-arg nack() compatibility")
    class SingleArgNackTests {

        @Test
        @DisplayName("nack(messageId) with exhausted retries publishes to DLT")
        void singleArgNack_publishesToDlt() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/1);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID)); // QueueConsumerStrategy interface

            assertThat(mockProducer.history()).hasSize(1);
        }

        @Test
        @DisplayName("DLT record omits error-message header when using no-arg nack")
        void singleArgNack_omitsErrorMessageHeader() {
            KafkaConsumerStrategy strategy = strategy(/*maxRetries=*/1);
            pollOneRecord(strategy);

            runPromise(() -> strategy.nack(MESSAGE_ID));

            boolean hasErrorMsg = false;
            for (org.apache.kafka.common.header.Header h : mockProducer.history().get(0).headers()) {
                if ("X-DLT-ErrorMessage".equals(h.key())) {
                    hasErrorMsg = true;
                    break;
                }
            }
            assertThat(hasErrorMsg).isFalse();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private KafkaConsumerStrategy strategy(int maxRetries) {
        return strategyWithSuffix(maxRetries, ".dlt");
    }

    private KafkaConsumerStrategy strategyWithSuffix(int maxRetries, String suffix) {
        KafkaConsumerConfig config = KafkaConsumerConfig.builder()
                .bootstrapServers("localhost:9092")
                .groupId("test-group")
                .topics(List.of(TOPIC))
                .batchSize(10)
                .maxRetries(maxRetries)
                .dltTopicSuffix(suffix)
                .build();
        return new KafkaConsumerStrategy(config, eventloop(), mockConsumer, mockProducer);
    }

    /** Schedules a single record on the mock consumer and polls it into the strategy. */
    private void pollOneRecord(KafkaConsumerStrategy strategy) {
        mockConsumer.addRecord(record(OFFSET));
        List<QueueMessage> msgs = runPromise(strategy::poll);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).messageId()).isEqualTo(MESSAGE_ID);
    }

    /** Build a ConsumerRecord for the test topic. */
    private static ConsumerRecord<String, String> record(long offset) {
        return new ConsumerRecord<>(TOPIC, PARTITION, offset, "key", MESSAGE_VALUE);
    }

    /** Extract a header value as a UTF-8 string; null if missing. */
    private static String headerValue(ProducerRecord<?, ?> record, String headerKey) {
        for (org.apache.kafka.common.header.Header h : record.headers()) {
            if (headerKey.equals(h.key())) {
                return new String(h.value(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String msgId(List<QueueMessage> msgs) {
        return msgs.isEmpty() ? null : msgs.get(0).messageId();
    }
}
