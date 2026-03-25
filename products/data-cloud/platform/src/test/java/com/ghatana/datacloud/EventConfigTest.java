package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link EventConfig} factory methods, fluent API, and builder defaults.
 */
@DisplayName("EventConfig")
class EventConfigTest {

    // ── factory methods ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("hashPartitioned()")
    class HashPartitioned {

        @Test
        void setsStrategyAndPartitionCount() {
            EventConfig cfg = EventConfig.hashPartitioned("orderId", 16);

            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.HASH);
            assertThat(cfg.getPartitionKeyField()).isEqualTo("orderId");
            assertThat(cfg.getPartitionCount()).isEqualTo(16);
        }

        @Test
        void defaultsToPerPartitionOrdering() {
            EventConfig cfg = EventConfig.hashPartitioned("id", 4);

            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.PER_PARTITION);
        }
    }

    @Nested
    @DisplayName("timePartitioned()")
    class TimePartitioned {

        @Test
        void setsStrategyAndTimeBucket() {
            Duration bucket = Duration.ofDays(1);
            EventConfig cfg = EventConfig.timePartitioned(bucket);

            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.TIME_BASED);
            assertThat(cfg.getPartitionTimeBucket()).isEqualTo(bucket);
        }

        @Test
        void defaultsToPerPartitionOrdering() {
            EventConfig cfg = EventConfig.timePartitioned(Duration.ofHours(6));

            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.PER_PARTITION);
        }
    }

    @Nested
    @DisplayName("singlePartition()")
    class SinglePartition {

        @Test
        void setsNoneStrategyAndGlobalOrdering() {
            EventConfig cfg = EventConfig.singlePartition();

            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.NONE);
            assertThat(cfg.getPartitionCount()).isEqualTo(1);
            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.GLOBAL);
        }
    }

    @Nested
    @DisplayName("highThroughput()")
    class HighThroughput {

        @Test
        void setsRoundRobinAndCompression() {
            EventConfig cfg = EventConfig.highThroughput(32);

            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.ROUND_ROBIN);
            assertThat(cfg.getPartitionCount()).isEqualTo(32);
            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.NONE);
            assertThat(cfg.getCompressPayload()).isTrue();
        }

        @Test
        void setsBatchConfig() {
            EventConfig cfg = EventConfig.highThroughput(8);

            assertThat(cfg.getBatchSize()).isEqualTo(1000);
            assertThat(cfg.getBatchLingerTime()).isEqualTo(Duration.ofMillis(50));
        }
    }

    // ── fluent builder ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("withDeduplication()")
    class WithDeduplication {

        @Test
        void enablesDeduplicationWithDefaultKeyField() {
            Duration window = Duration.ofMinutes(5);
            EventConfig cfg = EventConfig.singlePartition().withDeduplication(window);

            assertThat(cfg.getDeduplicationEnabled()).isTrue();
            assertThat(cfg.getDeduplicationKeyField()).isEqualTo("idempotencyKey");
            assertThat(cfg.getDeduplicationWindow()).isEqualTo(window);
        }
    }

    @Nested
    @DisplayName("withIdempotency()")
    class WithIdempotency {

        @Test
        void enablesDeduplicationWithCustomKeyField() {
            EventConfig cfg = EventConfig.hashPartitioned("userId", 8)
                    .withIdempotency("eventId", Duration.ofHours(1));

            assertThat(cfg.getDeduplicationEnabled()).isTrue();
            assertThat(cfg.getDeduplicationKeyField()).isEqualTo("eventId");
            assertThat(cfg.getDeduplicationWindow()).isEqualTo(Duration.ofHours(1));
        }

        @Test
        void returnsThisForChaining() {
            EventConfig original = EventConfig.singlePartition();
            EventConfig chained = original.withIdempotency("key", Duration.ofMinutes(30));

            assertThat(chained).isSameAs(original);
        }
    }

    @Nested
    @DisplayName("withStrictOrdering()")
    class WithStrictOrdering {

        @Test
        void setsOrderingToPerPartition() {
            EventConfig cfg = EventConfig.highThroughput(4) // starts with NONE ordering
                    .withStrictOrdering();

            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.PER_PARTITION);
        }
    }

    @Nested
    @DisplayName("withGlobalOrdering()")
    class WithGlobalOrdering {

        @Test
        void collapsesToSinglePartitionWithGlobalGuarantee() {
            EventConfig cfg = EventConfig.hashPartitioned("id", 16).withGlobalOrdering();

            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.GLOBAL);
            assertThat(cfg.getPartitionCount()).isEqualTo(1);
            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.NONE);
        }
    }

    @Nested
    @DisplayName("withCompression()")
    class WithCompression {

        @Test
        void enablesCompressionWithDefaultLz4Codec() {
            EventConfig cfg = EventConfig.singlePartition().withCompression();

            assertThat(cfg.getCompressPayload()).isTrue();
            assertThat(cfg.getCompressionCodec()).isEqualTo("lz4");
        }

        @Test
        void enablesCompressionWithCustomCodec() {
            EventConfig cfg = EventConfig.singlePartition().withCompression("snappy");

            assertThat(cfg.getCompressPayload()).isTrue();
            assertThat(cfg.getCompressionCodec()).isEqualTo("snappy");
        }
    }

    // ── builder defaults ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("builder defaults")
    class BuilderDefaults {

        @Test
        void defaultBuilderProducesValidConfig() {
            EventConfig cfg = EventConfig.builder().build();

            assertThat(cfg.getPartitionCount()).isEqualTo(1);
            assertThat(cfg.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.HASH);
            assertThat(cfg.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.PER_PARTITION);
            assertThat(cfg.getDeduplicationEnabled()).isFalse();
            assertThat(cfg.getCompressPayload()).isFalse();
            assertThat(cfg.getCompressionCodec()).isEqualTo("lz4");
            assertThat(cfg.getBatchSize()).isEqualTo(100);
            assertThat(cfg.getBatchLingerTime()).isEqualTo(Duration.ofMillis(10));
            assertThat(cfg.getTailingEnabled()).isTrue();
            assertThat(cfg.getReplayEnabled()).isTrue();
            assertThat(cfg.getProperties()).isEmpty();
        }
    }

    // ── enums ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("PartitionStrategy enum")
    class PartitionStrategyEnum {

        @Test
        void hasAllExpectedValues() {
            assertThat(EventConfig.PartitionStrategy.values())
                    .containsExactlyInAnyOrder(
                            EventConfig.PartitionStrategy.HASH,
                            EventConfig.PartitionStrategy.ROUND_ROBIN,
                            EventConfig.PartitionStrategy.TIME_BASED,
                            EventConfig.PartitionStrategy.RANGE,
                            EventConfig.PartitionStrategy.NONE);
        }
    }

    @Nested
    @DisplayName("OrderingGuarantee enum")
    class OrderingGuaranteeEnum {

        @Test
        void hasAllExpectedValues() {
            assertThat(EventConfig.OrderingGuarantee.values())
                    .containsExactlyInAnyOrder(
                            EventConfig.OrderingGuarantee.NONE,
                            EventConfig.OrderingGuarantee.PER_PARTITION,
                            EventConfig.OrderingGuarantee.GLOBAL);
        }
    }
}
