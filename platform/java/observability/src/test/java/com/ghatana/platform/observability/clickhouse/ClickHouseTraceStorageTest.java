package com.ghatana.platform.observability.clickhouse;

import com.ghatana.platform.observability.trace.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ClickHouseTraceStorage.
 *
 * Tests core functionality and configuration validation.
 */
@DisplayName("ClickHouseTraceStorage Unit Tests")
class ClickHouseTraceStorageTest {

    private ClickHouseTraceStorage storage;
    private Instant baseTime;

    @BeforeEach
    void setUp() { // GH-90000
        storage = ClickHouseTraceStorage.builder() // GH-90000
                .withHost("localhost")
                .withPort(8123) // GH-90000
                .withDatabase("observability")
                .withBatchSize(5000) // GH-90000
                .withFlushInterval(Duration.ofSeconds(5)) // GH-90000
                .build(); // GH-90000

        baseTime = Instant.now(); // GH-90000
    }

    @Test
    @DisplayName("Should create storage with builder")
    void testBuilderCreatesStorage() { // GH-90000
        assertThat(storage).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should accept valid span configuration")
    void testValidSpanConfiguration() { // GH-90000
        SpanData span = createTestSpan("trace-1", "span-1", null, "GET /users", "OK"); // GH-90000
        assertThat(span).isNotNull(); // GH-90000
        assertThat(span.traceId()).isEqualTo("trace-1");
        assertThat(span.spanId()).isEqualTo("span-1");
    }

    @Test
    @DisplayName("Should build trace query with all filters")
    void testBuildTraceQueryWithAllFilters() { // GH-90000
        TraceQuery query = TraceQuery.builder() // GH-90000
                .withServiceName("api-service")
                .withOperationName("GET /users")
                .withStatus("OK")
                .withStartTime(baseTime) // GH-90000
                .withEndTime(baseTime.plusSeconds(3600)) // GH-90000
                .withMinDurationMs(100) // GH-90000
                .withMaxDurationMs(5000) // GH-90000
                .withLimit(100) // GH-90000
                .withOffset(0) // GH-90000
                .build(); // GH-90000

        assertThat(query).isNotNull(); // GH-90000
        assertThat(query.getServiceName()).contains("api-service");
        assertThat(query.getOperationName()).contains("GET /users");
        assertThat(query.getStatus()).contains("OK");
        assertThat(query.getStartTime()).contains(baseTime); // GH-90000
        assertThat(query.getLimit()).isEqualTo(100); // GH-90000
    }

    @Test
    @DisplayName("Should build statistics query")
    void testBuildStatisticsQuery() { // GH-90000
        TraceQuery query = TraceQuery.builder() // GH-90000
                .withServiceName("api-service")
                .withStatus("ERROR")
                .withMinDurationMs(50) // GH-90000
                .withMaxDurationMs(10000) // GH-90000
                .build(); // GH-90000

        assertThat(query).isNotNull(); // GH-90000
        assertThat(query.getServiceName()).contains("api-service");
        assertThat(query.getStatus()).contains("ERROR");
        assertThat(query.getMinDurationMs()).contains(50L); // GH-90000
        assertThat(query.getMaxDurationMs()).contains(10000L); // GH-90000
    }

    @Test
    @DisplayName("Should build query without filters")
    void testBuildQueryWithoutFilters() { // GH-90000
        TraceQuery query = TraceQuery.builder().build(); // GH-90000

        assertThat(query).isNotNull(); // GH-90000
        assertThat(query.getServiceName()).isEmpty(); // GH-90000
        assertThat(query.getOperationName()).isEmpty(); // GH-90000
        assertThat(query.getStatus()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should close storage")
    void testClose() { // GH-90000
        // Should not throw
        storage.close(); // GH-90000
        assertThat(storage).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should handle multiple close calls")
    void testMultipleCloses() { // GH-90000
        storage.close(); // GH-90000
        storage.close(); // GH-90000
        assertThat(storage).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should create span with required fields")
    void testCreateSpanWithRequiredFields() { // GH-90000
        SpanData span = SpanData.builder() // GH-90000
                .withTraceId("trace-1")
                .withSpanId("span-1")
                .withServiceName("test-service")
                .withOperationName("test-operation")
                .withName("test-operation")
                .withStartTime(baseTime) // GH-90000
                .withEndTime(baseTime.plusMillis(100)) // GH-90000
                .withStatus("OK")
                .build(); // GH-90000

        assertThat(span).isNotNull(); // GH-90000
        assertThat(span.traceId()).isEqualTo("trace-1");
        assertThat(span.spanId()).isEqualTo("span-1");
        assertThat(span.serviceName()).isEqualTo("test-service");
        assertThat(span.operationName()).isEqualTo("test-operation");
    }

    @Test
    @DisplayName("Should create span with parent")
    void testCreateSpanWithParent() { // GH-90000
        SpanData span = SpanData.builder() // GH-90000
                .withTraceId("trace-1")
                .withSpanId("span-2")
                .withParentSpanId("span-1")
                .withServiceName("test-service")
                .withOperationName("child-operation")
                .withName("child-operation")
                .withStartTime(baseTime.plusMillis(50)) // GH-90000
                .withEndTime(baseTime.plusMillis(150)) // GH-90000
                .withStatus("OK")
                .build(); // GH-90000

        assertThat(span).isNotNull(); // GH-90000
        assertThat(span.parentSpanId()).contains("span-1");
    }

    @Test
    @DisplayName("Should create trace info")
    void testCreateTraceInfo() { // GH-90000
        TraceInfo trace = TraceInfo.builder() // GH-90000
                .withTraceId("trace-1")
                .withServiceName("api-service")
                .withStartTime(baseTime) // GH-90000
                .withEndTime(baseTime.plusSeconds(1)) // GH-90000
                .withDurationMs(1000) // GH-90000
                .withStatus("OK")
                .build(); // GH-90000

        assertThat(trace).isNotNull(); // GH-90000
        assertThat(trace.traceId()).isEqualTo("trace-1");
        assertThat(trace.serviceName()).isEqualTo("api-service");
        assertThat(trace.durationMs()).isEqualTo(1000); // GH-90000
    }

    @Test
    @DisplayName("Should create trace statistics")
    void testCreateTraceStatistics() { // GH-90000
        TraceStatistics stats = TraceStatistics.builder() // GH-90000
                .withTotalTraces(100) // GH-90000
                .withTotalSpans(500) // GH-90000
                .withErrorCount(5) // GH-90000
                .withMinDurationMs(10) // GH-90000
                .withMaxDurationMs(5000) // GH-90000
                .withAvgDurationMs(1000) // GH-90000
                .withP50DurationMs(500) // GH-90000
                .withP95DurationMs(4000) // GH-90000
                .withP99DurationMs(4500) // GH-90000
                .build(); // GH-90000

        assertThat(stats).isNotNull(); // GH-90000
        assertThat(stats.totalTraces()).isEqualTo(100); // GH-90000
        assertThat(stats.totalSpans()).isEqualTo(500); // GH-90000
        assertThat(stats.errorCount()).isEqualTo(5); // GH-90000
    }

    // Helper method
    private SpanData createTestSpan(String traceId, String spanId, String parentSpanId, // GH-90000
                                    String operationName, String status) {
        return SpanData.builder() // GH-90000
                .withTraceId(traceId) // GH-90000
                .withSpanId(spanId) // GH-90000
                .withParentSpanId(parentSpanId) // GH-90000
                .withServiceName("test-service")
                .withOperationName(operationName) // GH-90000
                .withName(operationName) // GH-90000
                .withStartTime(baseTime) // GH-90000
                .withEndTime(baseTime.plusMillis(100)) // GH-90000
                .withStatus(status) // GH-90000
                .build(); // GH-90000
    }
}
