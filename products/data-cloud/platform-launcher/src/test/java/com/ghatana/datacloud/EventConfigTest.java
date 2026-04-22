package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link EventConfig} factory methods, fluent API, and builder defaults.
 */
@DisplayName("EventConfig [GH-90000]")
class EventConfigTest {

    // ── factory methods ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("hashPartitioned() [GH-90000]")
    class HashPartitioned {

        @Test
        void setsStrategyAndPartitionCount() { // GH-90000
            EventConfig cfg = EventConfig.hashPartitioned("orderId", 16); // GH-90000

            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.HASH); // GH-90000
            assertThat(cfg.getPartitionKeyField()).isEqualTo("orderId [GH-90000]");
            assertThat(cfg.getPartitionCount()).isEqualTo(16); // GH-90000
        }

        @Test
        void defaultsToPerPartitionOrdering() { // GH-90000
            EventConfig cfg = EventConfig.hashPartitioned("id", 4); // GH-90000

            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.PER_PARTITION); // GH-90000
        }
    }

    @Nested
    @DisplayName("timePartitioned() [GH-90000]")
    class TimePartitioned {

        @Test
        void setsStrategyAndTimeBucket() { // GH-90000
            Duration bucket = Duration.ofDays(1); // GH-90000
            EventConfig cfg = EventConfig.timePartitioned(bucket); // GH-90000

            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.TIME_BASED); // GH-90000
            assertThat(cfg.getPartitionTimeBucket()).isEqualTo(bucket); // GH-90000
        }

        @Test
        void defaultsToPerPartitionOrdering() { // GH-90000
            EventConfig cfg = EventConfig.timePartitioned(Duration.ofHours(6)); // GH-90000

            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.PER_PARTITION); // GH-90000
        }
    }

    @Nested
    @DisplayName("singlePartition() [GH-90000]")
    class SinglePartition {

        @Test
        void setsNoneStrategyAndGlobalOrdering() { // GH-90000
            EventConfig cfg = EventConfig.singlePartition(); // GH-90000

            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.NONE); // GH-90000
            assertThat(cfg.getPartitionCount()).isEqualTo(1); // GH-90000
            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.GLOBAL); // GH-90000
        }
    }

    @Nested
    @DisplayName("highThroughput() [GH-90000]")
    class HighThroughput {

        @Test
        void setsRoundRobinAndCompression() { // GH-90000
            EventConfig cfg = EventConfig.highThroughput(32); // GH-90000

            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.ROUND_ROBIN); // GH-90000
            assertThat(cfg.getPartitionCount()).isEqualTo(32); // GH-90000
            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.NONE); // GH-90000
            assertThat(cfg.getCompressPayload()).isTrue(); // GH-90000
        }

        @Test
        void setsBatchConfig() { // GH-90000
            EventConfig cfg = EventConfig.highThroughput(8); // GH-90000

            assertThat(cfg.getBatchSize()).isEqualTo(1000); // GH-90000
            assertThat(cfg.getBatchLingerTime()).isEqualTo(Duration.ofMillis(50)); // GH-90000
        }
    }

    // ── fluent builder ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("withDeduplication() [GH-90000]")
    class WithDeduplication {

        @Test
        void enablesDeduplicationWithDefaultKeyField() { // GH-90000
            Duration window = Duration.ofMinutes(5); // GH-90000
            EventConfig cfg = EventConfig.singlePartition().withDeduplication(window); // GH-90000

            assertThat(cfg.getDeduplicationEnabled()).isTrue(); // GH-90000
            assertThat(cfg.getDeduplicationKeyField()).isEqualTo("idempotencyKey [GH-90000]");
            assertThat(cfg.getDeduplicationWindow()).isEqualTo(window); // GH-90000
        }
    }

    @Nested
    @DisplayName("withIdempotency() [GH-90000]")
    class WithIdempotency {

        @Test
        void enablesDeduplicationWithCustomKeyField() { // GH-90000
            EventConfig cfg = EventConfig.hashPartitioned("userId", 8) // GH-90000
                    .withIdempotency("eventId", Duration.ofHours(1)); // GH-90000

            assertThat(cfg.getDeduplicationEnabled()).isTrue(); // GH-90000
            assertThat(cfg.getDeduplicationKeyField()).isEqualTo("eventId [GH-90000]");
            assertThat(cfg.getDeduplicationWindow()).isEqualTo(Duration.ofHours(1)); // GH-90000
        }

        @Test
        void returnsThisForChaining() { // GH-90000
            EventConfig original = EventConfig.singlePartition(); // GH-90000
            EventConfig chained = original.withIdempotency("key", Duration.ofMinutes(30)); // GH-90000

            assertThat(chained).isSameAs(original); // GH-90000
        }
    }

    @Nested
    @DisplayName("withStrictOrdering() [GH-90000]")
    class WithStrictOrdering {

        @Test
        void setsOrderingToPerPartition() { // GH-90000
            EventConfig cfg = EventConfig.highThroughput(4) // starts with NONE ordering // GH-90000
                    .withStrictOrdering(); // GH-90000

            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.PER_PARTITION); // GH-90000
        }
    }

    @Nested
    @DisplayName("withGlobalOrdering() [GH-90000]")
    class WithGlobalOrdering {

        @Test
        void collapsesToSinglePartitionWithGlobalGuarantee() { // GH-90000
            EventConfig cfg = EventConfig.hashPartitioned("id", 16).withGlobalOrdering(); // GH-90000

            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.GLOBAL); // GH-90000
            assertThat(cfg.getPartitionCount()).isEqualTo(1); // GH-90000
            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.NONE); // GH-90000
        }
    }

    @Nested
    @DisplayName("withCompression() [GH-90000]")
    class WithCompression {

        @Test
        void enablesCompressionWithDefaultLz4Codec() { // GH-90000
            EventConfig cfg = EventConfig.singlePartition().withCompression(); // GH-90000

            assertThat(cfg.getCompressPayload()).isTrue(); // GH-90000
            assertThat(cfg.getCompressionCodec()).isEqualTo("lz4 [GH-90000]");
        }

        @Test
        void enablesCompressionWithCustomCodec() { // GH-90000
            EventConfig cfg = EventConfig.singlePartition().withCompression("snappy [GH-90000]");

            assertThat(cfg.getCompressPayload()).isTrue(); // GH-90000
            assertThat(cfg.getCompressionCodec()).isEqualTo("snappy [GH-90000]");
        }
    }

    // ── builder defaults ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("builder defaults [GH-90000]")
    class BuilderDefaults {

        @Test
        void defaultBuilderProducesValidConfig() { // GH-90000
            EventConfig cfg = EventConfig.builder().build(); // GH-90000

            assertThat(cfg.getPartitionCount()).isEqualTo(1); // GH-90000
            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.HASH); // GH-90000
            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.PER_PARTITION); // GH-90000
            assertThat(cfg.getDeduplicationEnabled()).isFalse(); // GH-90000
            assertThat(cfg.getCompressPayload()).isFalse(); // GH-90000
            assertThat(cfg.getCompressionCodec()).isEqualTo("lz4 [GH-90000]");
            assertThat(cfg.getBatchSize()).isEqualTo(100); // GH-90000
            assertThat(cfg.getBatchLingerTime()).isEqualTo(Duration.ofMillis(10)); // GH-90000
            assertThat(cfg.getTailingEnabled()).isTrue(); // GH-90000
            assertThat(cfg.getReplayEnabled()).isTrue(); // GH-90000
            assertThat(cfg.getProperties()).isEmpty(); // GH-90000
        }
    }

    // ── enums ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PartitionStrategy enum [GH-90000]")
    class PartitionStrategyEnum {

        @Test
        void hasAllExpectedValues() { // GH-90000
            assertThat(EventConfig.PartitionStrategy.values()) // GH-90000
                    .containsExactlyInAnyOrder( // GH-90000
                            EventConfig.PartitionStrategy.HASH,
                            EventConfig.PartitionStrategy.ROUND_ROBIN,
                            EventConfig.PartitionStrategy.TIME_BASED,
                            EventConfig.PartitionStrategy.RANGE,
                            EventConfig.PartitionStrategy.NONE);
        }
    }

    @Nested
    @DisplayName("OrderingGuarantee enum [GH-90000]")
    class OrderingGuaranteeEnum {

        @Test
        void hasAllExpectedValues() { // GH-90000
            assertThat(EventConfig.OrderingGuarantee.values()) // GH-90000
                    .containsExactlyInAnyOrder( // GH-90000
                            EventConfig.OrderingGuarantee.NONE,
                            EventConfig.OrderingGuarantee.PER_PARTITION,
                            EventConfig.OrderingGuarantee.GLOBAL);
        }
    }
}
