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
 * <p>Tests cover lifecycle management (start/stop/close) and stats retrieval.
 * The {@code migrateStream()} method requires real {@link StoragePlugin} and
 * {@link ColdTierArchivePlugin} instances; lifecycle tests use a minimal builder
 * without those to validate core orchestration logic.
 *
 * @doc.type test
 * @doc.purpose Validate scheduler lifecycle, stats tracking, and idempotent start/stop behavior
 * @doc.layer product
 */
@DisplayName("ArchiveMigrationScheduler Tests")
@ExtendWith(MockitoExtension.class)
class ArchiveMigrationSchedulerTest {

    @Mock
    private StoragePlugin sourcePlugin;

    @Mock
    private ColdTierArchivePlugin targetPlugin;

    private SimpleMeterRegistry meterRegistry;
    private ArchiveMigrationScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = ArchiveMigrationScheduler.builder()
                .sourcePlugin(sourcePlugin)
                .targetPlugin(targetPlugin)
                .meterRegistry(meterRegistry)
                .retentionThreshold(Duration.ofDays(365))
                .batchSize(1000)
                .dryRunMode(true)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should build scheduler without errors")
        void shouldBuildScheduler() {
            assertThatCode(() -> ArchiveMigrationScheduler.builder()
                    .sourcePlugin(sourcePlugin)
                    .targetPlugin(targetPlugin)
                    .meterRegistry(new SimpleMeterRegistry())
                    .build()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should not be running by default")
        void shouldNotBeRunningByDefault() {
            MigrationStats stats = scheduler.getStats();
            assertThat(stats.running()).isFalse();
        }
    }

    // =========================================================================
    // LIFECYCLE
    // =========================================================================

    @Nested
    @DisplayName("start and stop lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should start successfully")
        void shouldStartSuccessfully() {
            assertThatCode(() -> scheduler.start()).doesNotThrowAnyException();

            MigrationStats stats = scheduler.getStats();
            assertThat(stats.running()).isTrue();
        }

        @Test
        @DisplayName("should stop successfully after start")
        void shouldStopSuccessfully() {
            scheduler.start();
            assertThatCode(() -> scheduler.stop()).doesNotThrowAnyException();

            MigrationStats stats = scheduler.getStats();
            assertThat(stats.running()).isFalse();
        }

        @Test
        @DisplayName("start should be idempotent (no error when started twice)")
        void startShouldBeIdempotent() {
            scheduler.start();
            assertThatCode(() -> scheduler.start()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("stop should be safe when not running")
        void stopShouldBeSafeWhenNotRunning() {
            assertThatCode(() -> scheduler.stop()).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("close should stop the scheduler")
        void closeShouldStopScheduler() {
            scheduler.start();
            assertThatCode(() -> scheduler.close()).doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // STATS
    // =========================================================================

    @Nested
    @DisplayName("getStats")
    class Stats {

        @Test
        @DisplayName("should return stats with zero counts when no migration has occurred")
        void shouldReturnZeroStatsInitially() {
            MigrationStats stats = scheduler.getStats();

            assertThat(stats).isNotNull();
            assertThat(stats.totalEventsMigrated()).isZero();
            assertThat(stats.totalBatchesMigrated()).isZero();
            assertThat(stats.migrationInProgress()).isFalse();
        }

        @Test
        @DisplayName("should reflect running state in stats")
        void shouldReflectRunningStateInStats() {
            scheduler.start();
            assertThat(scheduler.getStats().running()).isTrue();

            scheduler.stop();
            assertThat(scheduler.getStats().running()).isFalse();
        }
    }

    // =========================================================================
    // CONFIGURATION
    // =========================================================================

    @Nested
    @DisplayName("Builder configuration")
    class BuilderConfiguration {

        @Test
        @DisplayName("should accept dry-run mode")
        void shouldAcceptDryRunMode() {
            ArchiveMigrationScheduler dryRunScheduler = ArchiveMigrationScheduler.builder()
                    .sourcePlugin(sourcePlugin)
                    .targetPlugin(targetPlugin)
                    .meterRegistry(new SimpleMeterRegistry())
                    .dryRunMode(true)
                    .build();

            assertThat(dryRunScheduler.getStats()).isNotNull();
            dryRunScheduler.close();
        }

        @Test
        @DisplayName("should accept custom retention threshold")
        void shouldAcceptCustomRetentionThreshold() {
            ArchiveMigrationScheduler customScheduler = ArchiveMigrationScheduler.builder()
                    .sourcePlugin(sourcePlugin)
                    .targetPlugin(targetPlugin)
                    .meterRegistry(new SimpleMeterRegistry())
                    .retentionThreshold(Duration.ofDays(30))
                    .batchSize(500)
                    .parallelStreams(2)
                    .build();

            assertThat(customScheduler.getStats()).isNotNull();
            customScheduler.close();
        }
    }
}
