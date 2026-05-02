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
@DisplayName("QueryTelemetryService Tests")
@ExtendWith(MockitoExtension.class) 
class QueryTelemetryServiceTest {

    @Mock
    private MetricsCollector metrics;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry(); 

    private QueryTelemetryService telemetry;

    @BeforeEach
    void setUp() { 
        lenient().when(metrics.getMeterRegistry()).thenReturn(meterRegistry); 
        telemetry = new QueryTelemetryService(metrics); 
    }

    // =========================================================================
    // CONSTRUCTION
    // =========================================================================

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create with default slow query threshold")
        void shouldCreateWithDefaults() { 
            assertThatCode(() -> new QueryTelemetryService(metrics)).doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("should create with custom slow query threshold")
        void shouldCreateWithCustomThreshold() { 
            assertThatCode(() -> new QueryTelemetryService(metrics, Duration.ofMillis(100))) 
                    .doesNotThrowAnyException(); 
        }
    }

    // =========================================================================
    // QUERY TRACKING
    // =========================================================================

    @Nested
    @DisplayName("Query Tracking")
    class QueryTracking {

        @Test
        @DisplayName("should start and finish a query context successfully")
        void shouldStartAndFinishQuery() { 
            QueryTelemetryService.QueryContext ctx = telemetry.startQuery("findById", "SELECT * FROM entities WHERE id = ?"); 
            assertThat(ctx).isNotNull(); 
            assertThatCode(() -> ctx.finish(1, true)).doesNotThrowAnyException(); 
        }

        @Test
        @DisplayName("should record successful query execution")
        void shouldRecordSuccessfulExecution() { 
            QueryTelemetryService.QueryContext ctx = telemetry.startQuery("query1", "SELECT 1"); 
            ctx.finish(10, true); 

            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("query1");
            assertThat(stats).isNotNull(); 
            assertThat(stats.totalExecutions()).isEqualTo(1); 
            assertThat(stats.successfulExecutions()).isEqualTo(1); 
            assertThat(stats.failedExecutions()).isEqualTo(0); 
        }

        @Test
        @DisplayName("should record failed query execution")
        void shouldRecordFailedExecution() { 
            QueryTelemetryService.QueryContext ctx = telemetry.startQuery("query1", "SELECT 1"); 
            ctx.finish(0, false); 

            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("query1");
            assertThat(stats.failedExecutions()).isEqualTo(1); 
            assertThat(stats.successfulExecutions()).isEqualTo(0); 
        }

        @Test
        @DisplayName("should record multiple executions of same query")
        void shouldAccumulateStats() { 
            for (int i = 0; i < 5; i++) { 
                telemetry.startQuery("countQuery", "SELECT COUNT(*) FROM t").finish(1, true); 
            }
            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("countQuery");
            assertThat(stats.totalExecutions()).isEqualTo(5); 
        }

        @Test
        @DisplayName("should record table scan information")
        void shouldRecordTableScan() { 
            telemetry.startQuery("fullScan", "SELECT * FROM t").finish(100, true, true, null); 

            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("fullScan");
            assertThat(stats.tableScans()).isEqualTo(1); 
        }

        @Test
        @DisplayName("should record index usage information")
        void shouldRecordIndexUsage() { 
            telemetry.startQuery("indexQuery", "SELECT * FROM t WHERE id = 1").finish(1, true, false, "idx_id"); 

            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("indexQuery");
            assertThat(stats.indexesUsed()).contains("idx_id");
        }

        @Test
        @DisplayName("should return null statistics for unknown query")
        void shouldReturnNullForUnknownQuery() { 
            QueryTelemetryService.QueryStatistics stats = telemetry.getQueryStatistics("unknownQuery");
            assertThat(stats).isNull(); 
        }
    }

    // =========================================================================
    // SLOW QUERY DETECTION
    // =========================================================================

    @Nested
    @DisplayName("Slow Query Detection")
    class SlowQueryDetection {

        @Test
        @DisplayName("should detect slow queries above threshold")
        void shouldDetectSlowQueries() throws Exception { 
            // Use 10ms threshold so we can trigger it reliably
            QueryTelemetryService fastTelemetry = new QueryTelemetryService(metrics, Duration.ofMillis(10)); 
            QueryTelemetryService.QueryContext ctx = fastTelemetry.startQuery("slowQuery", "SELECT * FROM big_table"); 
            Thread.sleep(15); 
            ctx.finish(1000, true); 

            List<QueryTelemetryService.SlowQuery> slow = fastTelemetry.getSlowQueries(Duration.ofMillis(10)); 
            assertThat(slow).isNotEmpty(); 
            assertThat(slow.get(0).queryName()).isEqualTo("slowQuery");
        }

        @Test
        @DisplayName("should not report fast queries as slow")
        void shouldNotReportFastQueriesAsSlow() { 
            telemetry.startQuery("fastQuery", "SELECT 1").finish(1, true); 

            List<QueryTelemetryService.SlowQuery> slow = telemetry.getSlowQueries(Duration.ofMillis(500)); 
            assertThat(slow).isEmpty(); 
        }

        @Test
        @DisplayName("getSlowQueries should return empty list when no queries recorded")
        void shouldReturnEmptySlowQueriesWhenNone() { 
            assertThat(telemetry.getSlowQueries(Duration.ofMillis(100))).isEmpty(); 
        }
    }

    // =========================================================================
    // SUMMARY
    // =========================================================================

    @Nested
    @DisplayName("Telemetry Summary")
    class TelemetrySummaryTests {

        @Test
        @DisplayName("should return summary with correct total queries")
        void shouldReturnCorrectTotalCount() { 
            telemetry.startQuery("q1", "SELECT 1").finish(1, true); 
            telemetry.startQuery("q2", "SELECT 2").finish(2, true); 

            QueryTelemetryService.TelemetrySummary summary = telemetry.getSummary(); 
            assertThat(summary.totalQueries()).isEqualTo(2); 
        }

        @Test
        @DisplayName("should return summary with unique query count")
        void shouldCountUniqueQueries() { 
            telemetry.startQuery("q1", "SELECT 1").finish(1, true); 
            telemetry.startQuery("q1", "SELECT 1").finish(1, true); // same name 
            telemetry.startQuery("q2", "SELECT 2").finish(1, true); 

            QueryTelemetryService.TelemetrySummary summary = telemetry.getSummary(); 
            assertThat(summary.uniqueQueries()).isEqualTo(2); 
        }

        @Test
        @DisplayName("should return zero summary when no queries")
        void shouldReturnZeroWhenNone() { 
            QueryTelemetryService.TelemetrySummary summary = telemetry.getSummary(); 
            assertThat(summary.totalQueries()).isEqualTo(0); 
        }
    }

    // =========================================================================
    // CLEAR
    // =========================================================================

    @Nested
    @DisplayName("Clear")
    class ClearTests {

        @Test
        @DisplayName("clear should reset all tracking data")
        void shouldClearAllData() { 
            telemetry.startQuery("q1", "SELECT 1").finish(1, true); 
            telemetry.clear(); 

            QueryTelemetryService.TelemetrySummary summary = telemetry.getSummary(); 
            assertThat(summary.totalQueries()).isEqualTo(0); 
        }
    }
}
