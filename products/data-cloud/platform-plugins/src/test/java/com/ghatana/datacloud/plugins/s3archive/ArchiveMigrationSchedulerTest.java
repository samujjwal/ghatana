package com.ghatana.datacloud.plugins.s3archive;

import com.ghatana.datacloud.event.spi.StoragePlugin;
import com.ghatana.datacloud.plugins.s3archive.ArchiveMigrationScheduler.MigrationStats;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link ArchiveMigrationScheduler}.
 *
 * <p>Tests cover lifecycle management (start/stop/close) and stats retrieval. // GH-90000
 * The {@code migrateStream()} method requires real {@link StoragePlugin} and // GH-90000
 * {@link ColdTierArchivePlugin} instances; lifecycle tests use a minimal builder
 * without those to validate core orchestration logic.
 *
 * @doc.type test
 * @doc.purpose Validate scheduler lifecycle, stats tracking, and idempotent start/stop behavior
 * @doc.layer product
 */
@DisplayName("ArchiveMigrationScheduler Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class ArchiveMigrationSchedulerTest {

    @Mock
    private StoragePlugin sourcePlugin;

    @Mock
    private ColdTierArchivePlugin targetPlugin;

    private SimpleMeterRegistry meterRegistry;
    private ArchiveMigrationScheduler scheduler;

    @BeforeEach
    void setUp() { // GH-90000
        meterRegistry = new SimpleMeterRegistry(); // GH-90000
        scheduler = ArchiveMigrationScheduler.builder() // GH-90000
                .sourcePlugin(sourcePlugin) // GH-90000
                .targetPlugin(targetPlugin) // GH-90000
                .meterRegistry(meterRegistry) // GH-90000
                .retentionThreshold(Duration.ofDays(365)) // GH-90000
                .batchSize(1000) // GH-90000
                .dryRunMode(true) // GH-90000
                .build(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        if (scheduler != null) { // GH-90000
            scheduler.close(); // GH-90000
        }
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class Construction {

        @Test
        @DisplayName("should build scheduler without errors [GH-90000]")
        void shouldBuildScheduler() { // GH-90000
            assertThatCode(() -> ArchiveMigrationScheduler.builder() // GH-90000
                    .sourcePlugin(sourcePlugin) // GH-90000
                    .targetPlugin(targetPlugin) // GH-90000
                    .meterRegistry(new SimpleMeterRegistry()) // GH-90000
                    .build()).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("should not be running by default [GH-90000]")
        void shouldNotBeRunningByDefault() { // GH-90000
            MigrationStats stats = scheduler.getStats(); // GH-90000
            assertThat(stats.running()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Nested
    @DisplayName("start and stop lifecycle [GH-90000]")
    class Lifecycle {

        @Test
        @DisplayName("should start successfully [GH-90000]")
        void shouldStartSuccessfully() { // GH-90000
            assertThatCode(() -> scheduler.start()).doesNotThrowAnyException(); // GH-90000

            MigrationStats stats = scheduler.getStats(); // GH-90000
            assertThat(stats.running()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should stop successfully after start [GH-90000]")
        void shouldStopSuccessfully() { // GH-90000
            scheduler.start(); // GH-90000
            assertThatCode(() -> scheduler.stop()).doesNotThrowAnyException(); // GH-90000

            MigrationStats stats = scheduler.getStats(); // GH-90000
            assertThat(stats.running()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("start should be idempotent (no error when started twice) [GH-90000]")
        void startShouldBeIdempotent() { // GH-90000
            scheduler.start(); // GH-90000
            assertThatCode(() -> scheduler.start()).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("stop should be safe when not running [GH-90000]")
        void stopShouldBeSafeWhenNotRunning() { // GH-90000
            assertThatCode(() -> scheduler.stop()).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("close should stop the scheduler [GH-90000]")
        void closeShouldStopScheduler() { // GH-90000
            scheduler.start(); // GH-90000
            assertThatCode(() -> scheduler.close()).doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // STATS
    // =========================================================================

    @Nested
    @DisplayName("getStats [GH-90000]")
    class Stats {

        @Test
        @DisplayName("should return stats with zero counts when no migration has occurred [GH-90000]")
        void shouldReturnZeroStatsInitially() { // GH-90000
            MigrationStats stats = scheduler.getStats(); // GH-90000

            assertThat(stats).isNotNull(); // GH-90000
            assertThat(stats.totalEventsMigrated()).isZero(); // GH-90000
            assertThat(stats.totalBatchesMigrated()).isZero(); // GH-90000
            assertThat(stats.migrationInProgress()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should reflect running state in stats [GH-90000]")
        void shouldReflectRunningStateInStats() { // GH-90000
            scheduler.start(); // GH-90000
            assertThat(scheduler.getStats().running()).isTrue(); // GH-90000

            scheduler.stop(); // GH-90000
            assertThat(scheduler.getStats().running()).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    @Nested
    @DisplayName("Builder configuration [GH-90000]")
    class BuilderConfiguration {

        @Test
        @DisplayName("should accept dry-run mode [GH-90000]")
        void shouldAcceptDryRunMode() { // GH-90000
            ArchiveMigrationScheduler dryRunScheduler = ArchiveMigrationScheduler.builder() // GH-90000
                    .sourcePlugin(sourcePlugin) // GH-90000
                    .targetPlugin(targetPlugin) // GH-90000
                    .meterRegistry(new SimpleMeterRegistry()) // GH-90000
                    .dryRunMode(true) // GH-90000
                    .build(); // GH-90000

            assertThat(dryRunScheduler.getStats()).isNotNull(); // GH-90000
            dryRunScheduler.close(); // GH-90000
        }

        @Test
        @DisplayName("should accept custom retention threshold [GH-90000]")
        void shouldAcceptCustomRetentionThreshold() { // GH-90000
            ArchiveMigrationScheduler customScheduler = ArchiveMigrationScheduler.builder() // GH-90000
                    .sourcePlugin(sourcePlugin) // GH-90000
                    .targetPlugin(targetPlugin) // GH-90000
                    .meterRegistry(new SimpleMeterRegistry()) // GH-90000
                    .retentionThreshold(Duration.ofDays(30)) // GH-90000
                    .batchSize(500) // GH-90000
                    .parallelStreams(2) // GH-90000
                    .build(); // GH-90000

            assertThat(customScheduler.getStats()).isNotNull(); // GH-90000
            customScheduler.close(); // GH-90000
        }
    }
}
