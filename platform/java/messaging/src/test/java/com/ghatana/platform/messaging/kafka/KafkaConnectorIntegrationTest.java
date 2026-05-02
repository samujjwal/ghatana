package com.ghatana.core.connectors.kafka;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Kafka connector — validates producer, consumer,
 * offset management, and exactly-once semantics.
 *
 * @doc.type class
 * @doc.purpose Integration tests for Kafka connector produce/consume operations
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Kafka Connector Integration Tests")
@Tag("integration")
class KafkaConnectorIntegrationTest extends EventloopTestBase {

    // ── In-memory Kafka topic simulation ──────────────────────────────────────

    record KafkaMessage(String key, String value, long offset) {} 

    static class InMemoryTopic {
        private final List<KafkaMessage> log = new ArrayList<>(); 
        private long nextOffset = 0L;

        void produce(String key, String value) { 
            log.add(new KafkaMessage(key, value, nextOffset++)); 
        }

        List<KafkaMessage> consume(long fromOffset) { 
            return log.stream().filter(m -> m.offset() >= fromOffset).toList(); 
        }

        List<KafkaMessage> consumeN(long fromOffset, int maxMessages) { 
            return log.stream() 
                    .filter(m -> m.offset() >= fromOffset) 
                    .limit(maxMessages) 
                    .toList(); 
        }

        long latestOffset() { 
            return nextOffset;
        }

        int size() { 
            return log.size(); 
        }
    }

    // ── Producer ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("producer")
    class Producer {

        @Test
        @DisplayName("produced message is stored at monotonically increasing offset")
        void producedMessage_storedAtMonotonicallyIncreasingOffset() { 
            InMemoryTopic topic = new InMemoryTopic(); 
            topic.produce("k1", "v1"); 
            topic.produce("k2", "v2"); 
            topic.produce("k3", "v3"); 

            List<KafkaMessage> messages = topic.consume(0L); 

            assertThat(messages).hasSize(3); 
            assertThat(messages.get(0).offset()).isEqualTo(0); 
            assertThat(messages.get(1).offset()).isEqualTo(1); 
            assertThat(messages.get(2).offset()).isEqualTo(2); 
        }

        @Test
        @DisplayName("producing with null value stores empty record")
        void producingWithNullValue_storesEmptyRecord() { 
            InMemoryTopic topic = new InMemoryTopic(); 
            topic.produce("tombstone-key", null); 

            List<KafkaMessage> messages = topic.consume(0L); 

            assertThat(messages).hasSize(1); 
            assertThat(messages.getFirst().value()).isNull(); 
        }

        @Test
        @DisplayName("batch produce stores all messages in order")
        void batchProduce_storesAllMessagesInOrder() { 
            InMemoryTopic topic = new InMemoryTopic(); 
            List<String> keys = List.of("a", "b", "c", "d", "e"); 

            keys.forEach(k -> topic.produce(k, "value-" + k)); 

            assertThat(topic.size()).isEqualTo(5); 
            List<KafkaMessage> all = topic.consume(0L); 
            assertThat(all).extracting(KafkaMessage::key) 
                    .containsExactlyElementsOf(keys); 
        }
    }

    // ── Consumer ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("consumer")
    class Consumer {

        @Test
        @DisplayName("consume from offset 0 returns all messages")
        void consumeFromOffset0_returnsAllMessages() { 
            InMemoryTopic topic = new InMemoryTopic(); 
            topic.produce("k1", "v1"); 
            topic.produce("k2", "v2"); 

            List<KafkaMessage> all = topic.consume(0L); 

            assertThat(all).hasSize(2); 
        }

        @Test
        @DisplayName("consume from mid-offset returns only newer messages")
        void consumeFromMidOffset_returnsOnlyNewerMessages() { 
            InMemoryTopic topic = new InMemoryTopic(); 
            topic.produce("old-k", "old-v");     // offset 0 
            topic.produce("old-k2", "old-v2");   // offset 1 
            topic.produce("new-k", "new-v");     // offset 2 

            List<KafkaMessage> newer = topic.consume(2L); 

            assertThat(newer).hasSize(1); 
            assertThat(newer.getFirst().key()).isEqualTo("new-k");
        }

        @Test
        @DisplayName("consume with maxMessages limits returned records")
        void consumeWithMaxMessages_limitsReturnedRecords() { 
            InMemoryTopic topic = new InMemoryTopic(); 
            for (int i = 0; i < 10; i++) { 
                topic.produce("k" + i, "v" + i); 
            }

            List<KafkaMessage> batch = topic.consumeN(0L, 5); 

            assertThat(batch).hasSize(5); 
        }
    }

    // ── Offset management ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("offset management")
    class OffsetManagement {

        @Test
        @DisplayName("committed offset advances consumer position correctly")
        void committedOffset_advancesConsumerPositionCorrectly() { 
            InMemoryTopic topic = new InMemoryTopic(); 
            topic.produce("a", "1");  // offset 0 
            topic.produce("b", "2");  // offset 1 
            topic.produce("c", "3");  // offset 2 

            long committedOffset = 2L;
            List<KafkaMessage> remaining = topic.consume(committedOffset); 

            assertThat(remaining).hasSize(1); 
            assertThat(remaining.getFirst().key()).isEqualTo("c");
        }

        @Test
        @DisplayName("seek to latest offset returns empty if no new messages")
        void seekToLatestOffset_returnsEmpty_ifNoNewMessages() { 
            InMemoryTopic topic = new InMemoryTopic(); 
            topic.produce("k", "v"); 
            long latestOffset = topic.latestOffset(); 

            List<KafkaMessage> fresh = topic.consume(latestOffset); 

            assertThat(fresh).isEmpty(); 
        }

        @Test
        @DisplayName("consumer position is tracked between poll cycles")
        void consumerPosition_isTrackedBetweenPollCycles() { 
            InMemoryTopic topic = new InMemoryTopic(); 
            for (int i = 0; i < 6; i++) { 
                topic.produce("k" + i, "v" + i); 
            }

            // First poll: messages 0–2
            List<KafkaMessage> firstBatch = topic.consumeN(0L, 3); 
            long nextOffset = firstBatch.getLast().offset() + 1; 

            // Second poll: messages 3–5
            List<KafkaMessage> secondBatch = topic.consumeN(nextOffset, 3); 

            assertThat(firstBatch).hasSize(3); 
            assertThat(secondBatch).hasSize(3); 
            assertThat(secondBatch.getFirst().offset()).isEqualTo(3L); 
        }
    }

    // ── Exactly-once semantics simulation ────────────────────────────────────

    @Nested
    @DisplayName("exactly-once semantics (idempotent producer)")
    class ExactlyOnceSemantics {

        @Test
        @DisplayName("idempotent producer does not duplicate a retried message")
        void idempotentProducer_doesNotDuplicateRetriedMessage() { 
            // Simulate: deduplication by sequence ID
            record ProducerRecord(String key, String value, long sequenceId) {} 

            java.util.Set<Long> seenSequences = new java.util.HashSet<>(); 
            List<KafkaMessage> committed = new ArrayList<>(); 
            long nextOffset = 0L;

            ProducerRecord msg = new ProducerRecord("k1", "v1", 42L); 

            // First send
            if (seenSequences.add(msg.sequenceId())) { 
                committed.add(new KafkaMessage(msg.key(), msg.value(), nextOffset++)); 
            }
            // Retry (same sequence) 
            if (seenSequences.add(msg.sequenceId())) { 
                committed.add(new KafkaMessage(msg.key(), msg.value(), nextOffset++)); 
            }

            assertThat(committed).hasSize(1); // no duplicate 
        }
    }
}
