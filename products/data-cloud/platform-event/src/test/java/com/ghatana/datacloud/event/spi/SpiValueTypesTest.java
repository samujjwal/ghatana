/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.event.spi;

import com.ghatana.datacloud.event.common.Offset;
import com.ghatana.datacloud.event.common.PartitionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for SPI value types: {@link RoutingPlugin.Strategy},
 * {@link ArchivePlugin.ArchiveResult}, {@link ArchivePlugin.CompactionResult},
 * {@link ArchivePlugin.VacuumResult}, {@link ArchivePlugin.ArchiveStatistics},
 * and {@link StreamingPlugin.SubscriptionOptions}.
 */
@DisplayName("SPI value types")
class SpiValueTypesTest {

    // ─── RoutingPlugin.Strategy ───────────────────────────────────────────────

    @Nested
    @DisplayName("RoutingPlugin.Strategy")
    class RoutingStrategy {

        @Test
        void allValuesPresent() { // GH-90000
            assertThat(RoutingPlugin.Strategy.values()).containsExactlyInAnyOrder( // GH-90000
                    RoutingPlugin.Strategy.HASH,
                    RoutingPlugin.Strategy.CONSISTENT_HASH,
                    RoutingPlugin.Strategy.KEY_BASED,
                    RoutingPlugin.Strategy.ROUND_ROBIN,
                    RoutingPlugin.Strategy.STICKY,
                    RoutingPlugin.Strategy.CUSTOM
            );
        }

        @Test
        void valueOfByName() { // GH-90000
            assertThat(RoutingPlugin.Strategy.valueOf("HASH")).isSameAs(RoutingPlugin.Strategy.HASH);
            assertThat(RoutingPlugin.Strategy.valueOf("ROUND_ROBIN")).isSameAs(RoutingPlugin.Strategy.ROUND_ROBIN);
        }
    }

    // ─── ArchivePlugin records ────────────────────────────────────────────────

    @Nested
    @DisplayName("ArchivePlugin.ArchiveResult")
    class ArchiveResultTest {

        @Test
        void successFactory() { // GH-90000
            ArchivePlugin.ArchiveResult r = ArchivePlugin.ArchiveResult.success(100, 5000L, 200L, "s3://bucket/path"); // GH-90000
            assertThat(r.success()).isTrue(); // GH-90000
            assertThat(r.eventsArchived()).isEqualTo(100); // GH-90000
            assertThat(r.bytesWritten()).isEqualTo(5000L); // GH-90000
            assertThat(r.durationMillis()).isEqualTo(200L); // GH-90000
            assertThat(r.archiveLocation()).contains("s3://bucket/path");
            assertThat(r.errorMessage()).isEmpty(); // GH-90000
        }

        @Test
        void failureFactory() { // GH-90000
            ArchivePlugin.ArchiveResult r = ArchivePlugin.ArchiveResult.failure("disk full");
            assertThat(r.success()).isFalse(); // GH-90000
            assertThat(r.eventsArchived()).isZero(); // GH-90000
            assertThat(r.errorMessage()).contains("disk full");
        }

        @Test
        void emptyFactory() { // GH-90000
            ArchivePlugin.ArchiveResult r = ArchivePlugin.ArchiveResult.empty(); // GH-90000
            assertThat(r.success()).isTrue(); // GH-90000
            assertThat(r.eventsArchived()).isZero(); // GH-90000
            assertThat(r.archiveLocation()).isEmpty(); // GH-90000
        }
    }

    @Nested
    @DisplayName("ArchivePlugin.CompactionResult")
    class CompactionResultTest {

        @Test
        void successFactory() { // GH-90000
            ArchivePlugin.CompactionResult r = ArchivePlugin.CompactionResult.success(5, 3, 1024L, 100L); // GH-90000
            assertThat(r.success()).isTrue(); // GH-90000
            assertThat(r.filesCompacted()).isEqualTo(5); // GH-90000
            assertThat(r.filesRemoved()).isEqualTo(3); // GH-90000
            assertThat(r.bytesReclaimed()).isEqualTo(1024L); // GH-90000
            assertThat(r.errorMessage()).isEmpty(); // GH-90000
        }

        @Test
        void failureFactory() { // GH-90000
            ArchivePlugin.CompactionResult r = ArchivePlugin.CompactionResult.failure("io error");
            assertThat(r.success()).isFalse(); // GH-90000
            assertThat(r.errorMessage()).contains("io error");
        }
    }

    @Nested
    @DisplayName("ArchivePlugin.VacuumResult")
    class VacuumResultTest {

        @Test
        void successFactory() { // GH-90000
            ArchivePlugin.VacuumResult r = ArchivePlugin.VacuumResult.success(7, 2048L, 50L); // GH-90000
            assertThat(r.success()).isTrue(); // GH-90000
            assertThat(r.filesDeleted()).isEqualTo(7); // GH-90000
            assertThat(r.bytesReclaimed()).isEqualTo(2048L); // GH-90000
            assertThat(r.errorMessage()).isEmpty(); // GH-90000
        }

        @Test
        void failureFactory() { // GH-90000
            ArchivePlugin.VacuumResult r = ArchivePlugin.VacuumResult.failure("permission denied");
            assertThat(r.success()).isFalse(); // GH-90000
            assertThat(r.errorMessage()).contains("permission denied");
        }
    }

    @Nested
    @DisplayName("ArchivePlugin.ArchiveStatistics")
    class ArchiveStatisticsTest {

        @Test
        void recordConstruction() { // GH-90000
            Instant oldest = Instant.parse("2026-01-01T00:00:00Z");
            Instant newest = Instant.parse("2026-04-01T00:00:00Z");
            ArchivePlugin.ArchiveStatistics stats = new ArchivePlugin.ArchiveStatistics( // GH-90000
                    1000L, 50000L, 10, 4, 42L, oldest, newest
            );
            assertThat(stats.totalEventsArchived()).isEqualTo(1000L); // GH-90000
            assertThat(stats.totalBytesStored()).isEqualTo(50000L); // GH-90000
            assertThat(stats.totalFiles()).isEqualTo(10); // GH-90000
            assertThat(stats.totalPartitions()).isEqualTo(4); // GH-90000
            assertThat(stats.currentVersion()).isEqualTo(42L); // GH-90000
            assertThat(stats.oldestEvent()).isEqualTo(oldest); // GH-90000
            assertThat(stats.newestEvent()).isEqualTo(newest); // GH-90000
        }
    }

    // ─── StreamingPlugin.SubscriptionOptions ─────────────────────────────────

    @Nested
    @DisplayName("StreamingPlugin.SubscriptionOptions builder")
    class SubscriptionOptionsTest {

        @Test
        void defaultsFromBuilder() { // GH-90000
            StreamingPlugin.SubscriptionOptions opts = StreamingPlugin.SubscriptionOptions.builder().build(); // GH-90000
            assertThat(opts.partitionId()).isSameAs(PartitionId.ALL); // GH-90000
            assertThat(opts.startOffset()).isSameAs(Offset.LATEST); // GH-90000
            assertThat(opts.consumerGroup()).isNull(); // GH-90000
            assertThat(opts.batchSize()).isEqualTo(100); // GH-90000
            assertThat(opts.batchTimeout()).isEqualTo(Duration.ofMillis(100)); // GH-90000
            assertThat(opts.autoCommit()).isFalse(); // GH-90000
            assertThat(opts.autoCommitInterval()).isEqualTo(Duration.ofSeconds(5)); // GH-90000
        }

        @Test
        void customValues() { // GH-90000
            StreamingPlugin.SubscriptionOptions opts = StreamingPlugin.SubscriptionOptions.builder() // GH-90000
                    .partitionId(PartitionId.of(2)) // GH-90000
                    .startOffset(Offset.EARLIEST) // GH-90000
                    .consumerGroup("my-group")
                    .batchSize(50) // GH-90000
                    .batchTimeout(Duration.ofMillis(200)) // GH-90000
                    .autoCommit(true) // GH-90000
                    .autoCommitInterval(Duration.ofSeconds(10)) // GH-90000
                    .build(); // GH-90000
            assertThat(opts.partitionId()).isEqualTo(PartitionId.of(2)); // GH-90000
            assertThat(opts.startOffset()).isSameAs(Offset.EARLIEST); // GH-90000
            assertThat(opts.consumerGroup()).isEqualTo("my-group");
            assertThat(opts.batchSize()).isEqualTo(50); // GH-90000
            assertThat(opts.autoCommit()).isTrue(); // GH-90000
        }

        @Test
        void recordEquality() { // GH-90000
            StreamingPlugin.SubscriptionOptions a = StreamingPlugin.SubscriptionOptions.builder() // GH-90000
                    .consumerGroup("g").batchSize(200).build();
            StreamingPlugin.SubscriptionOptions b = StreamingPlugin.SubscriptionOptions.builder() // GH-90000
                    .consumerGroup("g").batchSize(200).build();
            assertThat(a).isEqualTo(b); // GH-90000
        }
    }
}
