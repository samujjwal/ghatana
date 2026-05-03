package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link EventConfig}.
 */
@DisplayName("EventConfig")
class EventConfigTest {

    @Test
    @DisplayName("hashPartitioned creates hash-partitioned config")
    void hashPartitioned_createsHashPartitionedConfig() {
        EventConfig config = EventConfig.hashPartitioned("orderId", 16);

        assertThat(config.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.HASH);
        assertThat(config.getPartitionKeyField()).isEqualTo("orderId");
        assertThat(config.getPartitionCount()).isEqualTo(16);
        assertThat(config.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.PER_PARTITION);
    }

    @Test
    @DisplayName("timePartitioned creates time-partitioned config")
    void timePartitioned_createsTimePartitionedConfig() {
        EventConfig config = EventConfig.timePartitioned(Duration.ofDays(1));

        assertThat(config.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.TIME_BASED);
        assertThat(config.getPartitionTimeBucket()).isEqualTo(Duration.ofDays(1));
        assertThat(config.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.PER_PARTITION);
    }

    @Test
    @DisplayName("singlePartition creates single-partition config")
    void singlePartition_createsSinglePartitionConfig() {
        EventConfig config = EventConfig.singlePartition();

        assertThat(config.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.NONE);
        assertThat(config.getPartitionCount()).isEqualTo(1);
        assertThat(config.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.GLOBAL);
    }

    @Test
    @DisplayName("highThroughput creates high-throughput config")
    void highThroughput_createsHighThroughputConfig() {
        EventConfig config = EventConfig.highThroughput(32);

        assertThat(config.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.ROUND_ROBIN);
        assertThat(config.getPartitionCount()).isEqualTo(32);
        assertThat(config.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.NONE);
        assertThat(config.getCompressPayload()).isTrue();
        assertThat(config.getBatchSize()).isEqualTo(1000);
    }

    @Test
    @DisplayName("withDeduplication enables deduplication")
    void withDeduplication_enablesDeduplication() {
        EventConfig config = EventConfig.hashPartitioned("orderId", 8)
                .withDeduplication(Duration.ofMinutes(5));

        assertThat(config.getDeduplicationEnabled()).isTrue();
        assertThat(config.getDeduplicationKeyField()).isEqualTo("idempotencyKey");
        assertThat(config.getDeduplicationWindow()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("withIdempotency enables deduplication with custom key")
    void withIdempotency_enablesDeduplicationWithCustomKey() {
        EventConfig config = EventConfig.hashPartitioned("orderId", 8)
                .withIdempotency("eventId", Duration.ofHours(1));

        assertThat(config.getDeduplicationEnabled()).isTrue();
        assertThat(config.getDeduplicationKeyField()).isEqualTo("eventId");
        assertThat(config.getDeduplicationWindow()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    @DisplayName("withStrictOrdering enables per-partition ordering")
    void withStrictOrdering_enablesPerPartitionOrdering() {
        EventConfig config = EventConfig.hashPartitioned("orderId", 8)
                .withStrictOrdering();

        assertThat(config.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.PER_PARTITION);
    }

    @Test
    @DisplayName("withGlobalOrdering enables global ordering")
    void withGlobalOrdering_enablesGlobalOrdering() {
        EventConfig config = EventConfig.hashPartitioned("orderId", 8)
                .withGlobalOrdering();

        assertThat(config.getOrdering()).isEqualTo(EventConfig.OrderingGuarantee.GLOBAL);
        assertThat(config.getPartitionCount()).isEqualTo(1);
        assertThat(config.getPartitionStrategy()).isEqualTo(EventConfig.PartitionStrategy.NONE);
    }

    @Test
    @DisplayName("withCompression enables compression")
    void withCompression_enablesCompression() {
        EventConfig config = EventConfig.hashPartitioned("orderId", 8)
                .withCompression();

        assertThat(config.getCompressPayload()).isTrue();
        assertThat(config.getCompressionCodec()).isEqualTo("lz4");
    }

    @Test
    @DisplayName("withCompression with codec enables compression with custom codec")
    void withCompressionWithCodec_enablesCompressionWithCustomCodec() {
        EventConfig config = EventConfig.hashPartitioned("orderId", 8)
                .withCompression("gzip");

        assertThat(config.getCompressPayload()).isTrue();
        assertThat(config.getCompressionCodec()).isEqualTo("gzip");
    }

    @Test
    @DisplayName("PartitionStrategy enum contains all expected strategies")
    void partitionStrategyEnum_containsAllExpectedStrategies() {
        EventConfig.PartitionStrategy[] strategies = EventConfig.PartitionStrategy.values();
        assertThat(strategies).contains(
                EventConfig.PartitionStrategy.HASH,
                EventConfig.PartitionStrategy.ROUND_ROBIN,
                EventConfig.PartitionStrategy.TIME_BASED,
                EventConfig.PartitionStrategy.RANGE,
                EventConfig.PartitionStrategy.NONE
        );
    }

    @Test
    @DisplayName("OrderingGuarantee enum contains all expected guarantees")
    void orderingGuaranteeEnum_containsAllExpectedGuarantees() {
        EventConfig.OrderingGuarantee[] guarantees = EventConfig.OrderingGuarantee.values();
        assertThat(guarantees).contains(
                EventConfig.OrderingGuarantee.NONE,
                EventConfig.OrderingGuarantee.PER_PARTITION,
                EventConfig.OrderingGuarantee.GLOBAL
        );
    }
}
