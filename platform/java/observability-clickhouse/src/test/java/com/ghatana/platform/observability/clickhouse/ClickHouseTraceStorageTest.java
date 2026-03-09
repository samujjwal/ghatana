package com.ghatana.platform.observability.clickhouse;

import com.ghatana.platform.observability.clickhouse.ClickHouseTraceStorage;
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
    void setUp() {
        storage = ClickHouseTraceStorage.builder()
                .withHost("localhost")
                .withPort(8123)
                .withDatabase("observability")
                .withBatchSize(5000)
                .withFlushInterval(Duration.ofSeconds(5))
                .build();
        
        baseTime = Instant.now();
    }

    @Test
    @DisplayName("Should create storage with builder")
    void testBuilderCreatesStorage() {
        assertThat(storage).isNotNull();
    }

    @Test
    @DisplayName("Should accept valid span configuration")
    void testValidSpanConfiguration() {
        SpanData span = createTestSpan("trace-1", "span-1", null, "GET /users", "OK");
        assertThat(span).isNotNull();
        assertThat(span.traceId()).isEqualTo("trace-1");
        assertThat(span.spanId()).isEqualTo("span-1");
    }

    @Test
    @DisplayName("Should build trace query with all filters")
    void testBuildTraceQueryWithAllFilters() {
        TraceQuery query = TraceQuery.builder()
                .withServiceName("api-service")
                .withOperationName("GET /users")
                .withStatus("OK")
                .withStartTime(baseTime)
                .withEndTime(baseTime.plusSeconds(3600))
                .withMinDurationMs(100)
                .withMaxDurationMs(5000)
                .withLimit(100)
                .withOffset(0)
                .build();
        
        assertThat(query).isNotNull();
        assertThat(query.getServiceName()).contains("api-service");
        assertThat(query.getOperationName()).contains("GET /users");
        assertThat(query.getStatus()).contains("OK");
        assertThat(query.getStartTime()).contains(baseTime);
        assertThat(query.getLimit()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should build statistics query")
    void testBuildStatisticsQuery() {
        TraceQuery query = TraceQuery.builder()
                .withServiceName("api-service")
                .withStatus("ERROR")
                .withMinDurationMs(50)
                .withMaxDurationMs(10000)
                .build();
        
        assertThat(query).isNotNull();
        assertThat(query.getServiceName()).contains("api-service");
        assertThat(query.getStatus()).contains("ERROR");
        assertThat(query.getMinDurationMs()).contains(50L);
        assertThat(query.getMaxDurationMs()).contains(10000L);
    }

    @Test
    @DisplayName("Should build query without filters")
    void testBuildQueryWithoutFilters() {
        TraceQuery query = TraceQuery.builder().build();
        
        assertThat(query).isNotNull();
        assertThat(query.getServiceName()).isEmpty();
        assertThat(query.getOperationName()).isEmpty();
        assertThat(query.getStatus()).isEmpty();
    }

    @Test
    @DisplayName("Should close storage")
    void testClose() {
        // Should not throw
        storage.close();
        assertThat(storage).isNotNull();
    }

    @Test
    @DisplayName("Should handle multiple close calls")
    void testMultipleCloses() {
        storage.close();
        storage.close();
        assertThat(storage).isNotNull();
    }

    @Test
    @DisplayName("Should create span with required fields")
    void testCreateSpanWithRequiredFields() {
        SpanData span = SpanData.builder()
                .withTraceId("trace-1")
                .withSpanId("span-1")
                .withServiceName("test-service")
                .withOperationName("test-operation")
                .withName("test-operation")
                .withStartTime(baseTime)
                .withEndTime(baseTime.plusMillis(100))
                .withStatus("OK")
                .build();
        
        assertThat(span).isNotNull();
        assertThat(span.traceId()).isEqualTo("trace-1");
        assertThat(span.spanId()).isEqualTo("span-1");
        assertThat(span.serviceName()).isEqualTo("test-service");
        assertThat(span.operationName()).isEqualTo("test-operation");
    }

    @Test
    @DisplayName("Should create span with parent")
    void testCreateSpanWithParent() {
        SpanData span = SpanData.builder()
                .withTraceId("trace-1")
                .withSpanId("span-2")
                .withParentSpanId("span-1")
                .withServiceName("test-service")
                .withOperationName("child-operation")
                .withName("child-operation")
                .withStartTime(baseTime.plusMillis(50))
                .withEndTime(baseTime.plusMillis(150))
                .withStatus("OK")
                .build();
        
        assertThat(span).isNotNull();
        assertThat(span.parentSpanId()).contains("span-1");
    }

    @Test
    @DisplayName("Should create trace info")
    void testCreateTraceInfo() {
        TraceInfo trace = TraceInfo.builder()
                .withTraceId("trace-1")
                .withServiceName("api-service")
                .withStartTime(baseTime)
                .withEndTime(baseTime.plusSeconds(1))
                .withDurationMs(1000)
                .withStatus("OK")
                .build();
        
        assertThat(trace).isNotNull();
        assertThat(trace.traceId()).isEqualTo("trace-1");
        assertThat(trace.serviceName()).isEqualTo("api-service");
        assertThat(trace.durationMs()).isEqualTo(1000);
    }

    @Test
    @DisplayName("Should create trace statistics")
    void testCreateTraceStatistics() {
        TraceStatistics stats = TraceStatistics.builder()
                .withTotalTraces(100)
                .withTotalSpans(500)
                .withErrorCount(5)
                .withMinDurationMs(10)
                .withMaxDurationMs(5000)
                .withAvgDurationMs(1000)
                .withP50DurationMs(500)
                .withP95DurationMs(4000)
                .withP99DurationMs(4500)
                .build();
        
        assertThat(stats).isNotNull();
        assertThat(stats.totalTraces()).isEqualTo(100);
        assertThat(stats.totalSpans()).isEqualTo(500);
        assertThat(stats.errorCount()).isEqualTo(5);
    }

    // Helper method
    private SpanData createTestSpan(String traceId, String spanId, String parentSpanId, 
                                    String operationName, String status) {
        return SpanData.builder()
                .withTraceId(traceId)
                .withSpanId(spanId)
                .withParentSpanId(parentSpanId)
                .withServiceName("test-service")
                .withOperationName(operationName)
                .withName(operationName)
                .withStartTime(baseTime)
                .withEndTime(baseTime.plusMillis(100))
                .withStatus(status)
                .build();
    }
}
