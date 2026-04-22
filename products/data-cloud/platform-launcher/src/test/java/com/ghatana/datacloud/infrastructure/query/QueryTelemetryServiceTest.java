package com.ghatana.datacloud.infrastructure.query;

import com.ghatana.platform.observability.MetricsCollector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link QueryTelemetryService}.
 *
 * @doc.type test
 * @doc.purpose Validate query tracking, slow query detection, statistics, and telemetry summary
 * @doc.layer infrastructure
 */
@DisplayName("QueryTelemetryService Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class QueryTelemetryServiceTest {

    @Mock
    private MetricsCollector metrics;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry(); // GH-90000

    private QueryTelemetryService telemetry;

    @BeforeEach
    void setUp() { // GH-90000
        lenient().when(metrics.getMeterRegistry()).thenReturn(meterRegistry); // GH-90000
        telemetry = new QueryTelemetryService(metrics); // GH-90000
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction [GH-90000]")
    class Construction {

        @Test
        @DisplayName("should create with default slow query threshold [GH-90000]")
        void shouldCreateWithDefaults() { // GH-90000
            assertThatCode(() -> new QueryTelemetryService(metrics)).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("should create with custom slow query threshold [GH-90000]")
        void shouldCreateWithCustomThreshold() { // GH-90000
            assertThatCode(() -> new QueryTelemetryService(metrics, Duration.ofMillis(100))) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // QUERY TRACKING
    // =========================================================================

    @Nested
    @DisplayName("Query Tracking [GH-90000]")
    class QueryTracking {

        @Test
        @DisplayName("should start and finish a query context successfully [GH-90000]")
        void shouldStartAndFinishQuery() { // GH-90000
            QueryTelemetryService.QueryContext ctx = telemetry.startQuery("findById", "SELECT * FROM entities WHERE id = ?"); // GH-90000
            assertThat(ctx).isNotNull(); // GH-90000
            assertThatCode(() -> ctx.finish(1, true)).doesNotThrowAnyException(); // GH-90000
        }

        @Test
        @DisplayName("should record successful query execution [GH-90000]")
        void shouldRecordSuccessfulExecution() { // GH-90000
            QueryTelemetryService.QueryContext ctx = telemetry.startQuery("query1", "SELECT 1"); // GH-90000
            ctx.finish(10, true); // GH-90000

            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("query1 [GH-90000]");
            assertThat(stats).isNotNull(); // GH-90000
            assertThat(stats.totalExecutions()).isEqualTo(1); // GH-90000
            assertThat(stats.successfulExecutions()).isEqualTo(1); // GH-90000
            assertThat(stats.failedExecutions()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should record failed query execution [GH-90000]")
        void shouldRecordFailedExecution() { // GH-90000
            QueryTelemetryService.QueryContext ctx = telemetry.startQuery("query1", "SELECT 1"); // GH-90000
            ctx.finish(0, false); // GH-90000

            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("query1 [GH-90000]");
            assertThat(stats.failedExecutions()).isEqualTo(1); // GH-90000
            assertThat(stats.successfulExecutions()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should record multiple executions of same query [GH-90000]")
        void shouldAccumulateStats() { // GH-90000
            for (int i = 0; i < 5; i++) { // GH-90000
                telemetry.startQuery("countQuery", "SELECT COUNT(*) FROM t").finish(1, true); // GH-90000
            }
            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("countQuery [GH-90000]");
            assertThat(stats.totalExecutions()).isEqualTo(5); // GH-90000
        }

        @Test
        @DisplayName("should record table scan information [GH-90000]")
        void shouldRecordTableScan() { // GH-90000
            telemetry.startQuery("fullScan", "SELECT * FROM t").finish(100, true, true, null); // GH-90000

            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("fullScan [GH-90000]");
            assertThat(stats.tableScans()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("should record index usage information [GH-90000]")
        void shouldRecordIndexUsage() { // GH-90000
            telemetry.startQuery("indexQuery", "SELECT * FROM t WHERE id = 1").finish(1, true, false, "idx_id"); // GH-90000

            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("indexQuery [GH-90000]");
            assertThat(stats.indexesUsed()).contains("idx_id [GH-90000]");
        }

        @Test
        @DisplayName("should return null statistics for unknown query [GH-90000]")
        void shouldReturnNullForUnknownQuery() { // GH-90000
            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("unknownQuery [GH-90000]");
            assertThat(stats).isNull(); // GH-90000
        }
    }

    // =========================================================================
    // SLOW QUERY DETECTION
    // =========================================================================

    @Nested
    @DisplayName("Slow Query Detection [GH-90000]")
    class SlowQueryDetection {

        @Test
        @DisplayName("should detect slow queries above threshold [GH-90000]")
        void shouldDetectSlowQueries() throws Exception { // GH-90000
            // Use 10ms threshold so we can trigger it reliably
            QueryTelemetryService fastTelemetry = new QueryTelemetryService(metrics, Duration.ofMillis(10)); // GH-90000
            QueryTelemetryService.QueryContext ctx = fastTelemetry.startQuery("slowQuery", "SELECT * FROM big_table"); // GH-90000
            Thread.sleep(15); // GH-90000
            ctx.finish(1000, true); // GH-90000

            List<QueryTelemetryService.SlowQuery> slow = fastTelemetry.getSlowQueries(Duration.ofMillis(10)); // GH-90000
            assertThat(slow).isNotEmpty(); // GH-90000
            assertThat(slow.get(0).queryName()).isEqualTo("slowQuery [GH-90000]");
        }

        @Test
        @DisplayName("should not report fast queries as slow [GH-90000]")
        void shouldNotReportFastQueriesAsSlow() { // GH-90000
            telemetry.startQuery("fastQuery", "SELECT 1").finish(1, true); // GH-90000

            List<QueryTelemetryService.SlowQuery> slow = telemetry.getSlowQueries(Duration.ofMillis(500)); // GH-90000
            assertThat(slow).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("getSlowQueries should return empty list when no queries recorded [GH-90000]")
        void shouldReturnEmptySlowQueriesWhenNone() { // GH-90000
            assertThat(telemetry.getSlowQueries(Duration.ofMillis(100))).isEmpty(); // GH-90000
        }
    }

    // =========================================================================
    // SUMMARY
    // =========================================================================

    @Nested
    @DisplayName("Telemetry Summary [GH-90000]")
    class TelemetrySummaryTests {

        @Test
        @DisplayName("should return summary with correct total queries [GH-90000]")
        void shouldReturnCorrectTotalCount() { // GH-90000
            telemetry.startQuery("q1", "SELECT 1").finish(1, true); // GH-90000
            telemetry.startQuery("q2", "SELECT 2").finish(2, true); // GH-90000

            QueryTelemetryService.TelemetrySummary summary = telemetry.getSummary(); // GH-90000
            assertThat(summary.totalQueries()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("should return summary with unique query count [GH-90000]")
        void shouldCountUniqueQueries() { // GH-90000
            telemetry.startQuery("q1", "SELECT 1").finish(1, true); // GH-90000
            telemetry.startQuery("q1", "SELECT 1").finish(1, true); // same name // GH-90000
            telemetry.startQuery("q2", "SELECT 2").finish(1, true); // GH-90000

            QueryTelemetryService.TelemetrySummary summary = telemetry.getSummary(); // GH-90000
            assertThat(summary.uniqueQueries()).isEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("should return zero summary when no queries [GH-90000]")
        void shouldReturnZeroWhenNone() { // GH-90000
            QueryTelemetryService.TelemetrySummary summary = telemetry.getSummary(); // GH-90000
            assertThat(summary.totalQueries()).isEqualTo(0); // GH-90000
        }
    }

    // =========================================================================
    // CLEAR
    // =========================================================================

    @Nested
    @DisplayName("Clear [GH-90000]")
    class ClearTests {

        @Test
        @DisplayName("clear should reset all tracking data [GH-90000]")
        void shouldClearAllData() { // GH-90000
            telemetry.startQuery("q1", "SELECT 1").finish(1, true); // GH-90000
            telemetry.clear(); // GH-90000

            QueryTelemetryService.TelemetrySummary summary = telemetry.getSummary(); // GH-90000
            assertThat(summary.totalQueries()).isEqualTo(0); // GH-90000
        }
    }
}
