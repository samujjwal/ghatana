/*
 * Copyright (c) 2026 Ghatana Inc. 
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
        void allValuesPresent() { 
            assertThat(RoutingPlugin.Strategy.values()).containsExactlyInAnyOrder( 
                    RoutingPlugin.Strategy.HASH,
                    RoutingPlugin.Strategy.CONSISTENT_HASH,
                    RoutingPlugin.Strategy.KEY_BASED,
                    RoutingPlugin.Strategy.ROUND_ROBIN,
                    RoutingPlugin.Strategy.STICKY,
                    RoutingPlugin.Strategy.CUSTOM
            );
        }

        @Test
        void valueOfByName() { 
            assertThat(RoutingPlugin.Strategy.valueOf("HASH")).isSameAs(RoutingPlugin.Strategy.HASH);
            assertThat(RoutingPlugin.Strategy.valueOf("ROUND_ROBIN")).isSameAs(RoutingPlugin.Strategy.ROUND_ROBIN);
        }
    }

    // ─── ArchivePlugin records ────────────────────────────────────────────────

    @Nested
    @DisplayName("ArchivePlugin.ArchiveResult")
    class ArchiveResultTest {

        @Test
        void successFactory() { 
            ArchivePlugin.ArchiveResult r = ArchivePlugin.ArchiveResult.success(100, 5000L, 200L, "s3://bucket/path"); 
            assertThat(r.success()).isTrue(); 
            assertThat(r.eventsArchived()).isEqualTo(100); 
            assertThat(r.bytesWritten()).isEqualTo(5000L); 
            assertThat(r.durationMillis()).isEqualTo(200L); 
            assertThat(r.archiveLocation()).contains("s3://bucket/path");
            assertThat(r.errorMessage()).isEmpty(); 
        }

        @Test
        void failureFactory() { 
            ArchivePlugin.ArchiveResult r = ArchivePlugin.ArchiveResult.failure("disk full");
            assertThat(r.success()).isFalse(); 
            assertThat(r.eventsArchived()).isZero(); 
            assertThat(r.errorMessage()).contains("disk full");
        }

        @Test
        void emptyFactory() { 
            ArchivePlugin.ArchiveResult r = ArchivePlugin.ArchiveResult.empty(); 
            assertThat(r.success()).isTrue(); 
            assertThat(r.eventsArchived()).isZero(); 
            assertThat(r.archiveLocation()).isEmpty(); 
        }
    }

    @Nested
    @DisplayName("ArchivePlugin.CompactionResult")
    class CompactionResultTest {

        @Test
        void successFactory() { 
            ArchivePlugin.CompactionResult r = ArchivePlugin.CompactionResult.success(5, 3, 1024L, 100L); 
            assertThat(r.success()).isTrue(); 
            assertThat(r.filesCompacted()).isEqualTo(5); 
            assertThat(r.filesRemoved()).isEqualTo(3); 
            assertThat(r.bytesReclaimed()).isEqualTo(1024L); 
            assertThat(r.errorMessage()).isEmpty(); 
        }

        @Test
        void failureFactory() { 
            ArchivePlugin.CompactionResult r = ArchivePlugin.CompactionResult.failure("io error");
            assertThat(r.success()).isFalse(); 
            assertThat(r.errorMessage()).contains("io error");
        }
    }

    @Nested
    @DisplayName("ArchivePlugin.VacuumResult")
    class VacuumResultTest {

        @Test
        void successFactory() { 
            ArchivePlugin.VacuumResult r = ArchivePlugin.VacuumResult.success(7, 2048L, 50L); 
            assertThat(r.success()).isTrue(); 
            assertThat(r.filesDeleted()).isEqualTo(7); 
            assertThat(r.bytesReclaimed()).isEqualTo(2048L); 
            assertThat(r.errorMessage()).isEmpty(); 
        }

        @Test
        void failureFactory() { 
            ArchivePlugin.VacuumResult r = ArchivePlugin.VacuumResult.failure("permission denied");
            assertThat(r.success()).isFalse(); 
            assertThat(r.errorMessage()).contains("permission denied");
        }
    }

    @Nested
    @DisplayName("ArchivePlugin.ArchiveStatistics")
    class ArchiveStatisticsTest {

        @Test
        void recordConstruction() { 
            Instant oldest = Instant.parse("2026-01-01T00:00:00Z");
            Instant newest = Instant.parse("2026-04-01T00:00:00Z");
            ArchivePlugin.ArchiveStatistics stats = new ArchivePlugin.ArchiveStatistics( 
                    1000L, 50000L, 10, 4, 42L, oldest, newest
            );
            assertThat(stats.totalEventsArchived()).isEqualTo(1000L); 
            assertThat(stats.totalBytesStored()).isEqualTo(50000L); 
            assertThat(stats.totalFiles()).isEqualTo(10); 
            assertThat(stats.totalPartitions()).isEqualTo(4); 
            assertThat(stats.currentVersion()).isEqualTo(42L); 
            assertThat(stats.oldestEvent()).isEqualTo(oldest); 
            assertThat(stats.newestEvent()).isEqualTo(newest); 
        }
    }

    // ─── StreamingPlugin.SubscriptionOptions ─────────────────────────────────

    @Nested
    @DisplayName("StreamingPlugin.SubscriptionOptions builder")
    class SubscriptionOptionsTest {

        @Test
        void defaultsFromBuilder() { 
            StreamingPlugin.SubscriptionOptions opts = StreamingPlugin.SubscriptionOptions.builder().build(); 
            assertThat(opts.partitionId()).isSameAs(PartitionId.ALL); 
            assertThat(opts.startOffset()).isSameAs(Offset.LATEST); 
            assertThat(opts.consumerGroup()).isNull(); 
            assertThat(opts.batchSize()).isEqualTo(100); 
            assertThat(opts.batchTimeout()).isEqualTo(Duration.ofMillis(100)); 
            assertThat(opts.autoCommit()).isFalse(); 
            assertThat(opts.autoCommitInterval()).isEqualTo(Duration.ofSeconds(5)); 
        }

        @Test
        void customValues() { 
            StreamingPlugin.SubscriptionOptions opts = StreamingPlugin.SubscriptionOptions.builder() 
                    .partitionId(PartitionId.of(2)) 
                    .startOffset(Offset.EARLIEST) 
                    .consumerGroup("my-group")
                    .batchSize(50) 
                    .batchTimeout(Duration.ofMillis(200)) 
                    .autoCommit(true) 
                    .autoCommitInterval(Duration.ofSeconds(10)) 
                    .build(); 
            assertThat(opts.partitionId()).isEqualTo(PartitionId.of(2)); 
            assertThat(opts.startOffset()).isSameAs(Offset.EARLIEST); 
            assertThat(opts.consumerGroup()).isEqualTo("my-group");
            assertThat(opts.batchSize()).isEqualTo(50); 
            assertThat(opts.autoCommit()).isTrue(); 
        }

        @Test
        void recordEquality() { 
            StreamingPlugin.SubscriptionOptions a = StreamingPlugin.SubscriptionOptions.builder() 
                    .consumerGroup("g").batchSize(200).build();
            StreamingPlugin.SubscriptionOptions b = StreamingPlugin.SubscriptionOptions.builder() 
                    .consumerGroup("g").batchSize(200).build();
            assertThat(a).isEqualTo(b); 
        }
    }
}
