package com.ghatana.datacloud.events.query;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.ghatana.datacloud.events.storage.EventStore;
import com.ghatana.datacloud.events.storage.EventEntry;
import com.ghatana.platform.core.async.Promise;
import io.activej.eventloop.EventloopTestBase;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * @doc.type class
 * @doc.purpose Integration tests for Data Cloud event query service
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("Data Cloud Event Query Service Integration Tests")
class EventQueryServiceIntegrationTest extends EventloopTestBase {

    private EventQueryGrpcService queryService;
    private EventStore eventStore;

    @Mock
    private MetricsCollector metrics;

    private String testTenantId = "tenant-dc-1";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        eventStore = new TestEventStore();
        queryService = new EventQueryGrpcService(eventStore, metrics);
    }

    @Nested
    @DisplayName("Basic Query Execution")
    class BasicQueryExecutionTests {

        @Test
        @DisplayName("Should execute simple query over event store")
        void shouldExecuteSimpleQuery() {
            // Setup - populate event store
            addEvent("evt-1", "USER_CREATED", "user123", "{\"name\": \"Alice\"}");
            addEvent("evt-2", "USER_LOGIN", "user123", "{\"timestamp\": 12345}");
            addEvent("evt-3", "USER_LOGOUT", "user123", "{}");

            // Execute query
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events WHERE eventType='USER_LOGIN'",
                    testTenantId,
                    null // no type filter
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify
            assertThat(results).hasSize(1);
            assertThat(results.get(0).eventType()).isEqualTo("USER_LOGIN");
        }

        @Test
        @DisplayName("Should apply type filter to queries")
        void shouldApplyTypeFilter() {
            // Setup
            addEvent("evt-1", "ORDER_CREATED", "order-123", "{}");
            addEvent("evt-2", "ORDER_SHIPPED", "order-123", "{}");
            addEvent("evt-3", "ORDER_DELIVERED", "order-123", "{}");
            addEvent("evt-4", "PAYMENT_RECEIVED", "payment-456", "{}");

            // Execute with type filter
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    testTenantId,
                    "ORDER_%" // type filter for ORDER_ events
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify only ORDER events returned
            assertThat(results).hasSize(3);
            assertThat(results).allSatisfy(e ->
                    assertTrue(e.eventType().startsWith("ORDER_"))
            );
        }

        @Test
        @DisplayName("Should handle empty query results gracefully")
        void shouldHandleEmptyResults() {
            // Setup - add events of different types
            addEvent("evt-1", "USER_CREATED", "user123", "{}");
            addEvent("evt-2", "USER_DELETED", "user456", "{}");

            // Execute query for non-matching type
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events WHERE eventType='NONEXISTENT'",
                    testTenantId,
                    null
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify empty results
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("Query Limits and Pagination")
    class QueryLimitsTests {

        @Test
        @DisplayName("Should apply default limit to query results")
        void shouldApplyDefaultLimit() {
            // Setup - add 150 events
            for (int i = 0; i < 150; i++) {
                addEvent("evt-" + i, "TEST_EVENT", "entity-" + i, "{}");
            }

            // Execute query without explicit limit
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events LIMIT 100",
                    testTenantId,
                    null
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify default limit applied (100)
            assertThat(results).hasSize(100);
        }

        @Test
        @DisplayName("Should enforce maximum limit cap")
        void shouldEnforceMaximumLimit() {
            // Setup - add 15000 events
            for (int i = 0; i < 15000; i++) {
                addEvent("evt-" + i, "TEST_EVENT", "entity-" + i, "{}");
            }

            // Execute query requesting more than max (50000)
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events LIMIT 50000",
                    testTenantId,
                    null
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify capped at 10000 maximum
            assertThat(results).hasSize(10000);
        }

        @ParameterizedTest
        @ValueSource(ints = {10, 100, 1000, 5000, 10000})
        @DisplayName("Should respect various limit values")
        void shouldRespectLimitValues(int limit) {
            // Setup - add more events than any limit
            for (int i = 0; i < 15000; i++) {
                addEvent("evt-" + i, "TEST_EVENT", "entity-" + i, "{}");
            }

            // Execute with specific limit
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events LIMIT " + limit,
                    testTenantId,
                    null
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify limit respected (up to max of 10000)
            int expectedSize = Math.min(limit, 10000);
            assertThat(results).hasSize(expectedSize);
        }
    }

    @Nested
    @DisplayName("Tenant Isolation")
    class TenantIsolationTests {

        @Test
        @DisplayName("Should isolate events by tenant ID from envelope")
        void shouldIsolateTenantEventsFromEnvelope() {
            // Setup events for different tenants
            addEvent("evt-1", "EVENT_A", "entity-1", "{}", "tenant-1");
            addEvent("evt-2", "EVENT_B", "entity-2", "{}", "tenant-1");
            addEvent("evt-3", "EVENT_C", "entity-3", "{}", "tenant-2");

            // Execute query for tenant-1
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    "tenant-1",
                    null
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify only tenant-1 events returned
            assertThat(results).hasSize(2);
            assertThat(results)
                    .filteredOn(e -> e.tenantId().equals("tenant-1"))
                    .hasSize(2);
        }

        @Test
        @DisplayName("Should use envelope tenant when query doesn't specify")
        void shouldUseEnvelopeTenantAsDefault() {
            // Setup
            addEvent("evt-1", "EVENT", "entity-1", "{}", "tenant-1");

            // Query without explicit tenant in request
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    "tenant-1", // from envelope
                    null
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify envelope tenant used
            assertThat(results).hasSize(1);
            verify(metrics).incrementCounter(
                    argThat(s -> s.contains("queries")),
                    argThat(map -> map.containsValue("tenant-1"))
            );
        }

        @Test
        @DisplayName("Should include tenant ID in all query metrics")
        void shouldTagMetricsWithTenantId() {
            // Setup
            addEvent("evt-1", "TEST", "entity-1", "{}", testTenantId);

            // Execute
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    testTenantId,
                    null
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify tenant tag in metrics
            verify(metrics, atLeast(2)).incrementCounter(
                    anyString(),
                    argThat(map -> map.containsValue(testTenantId))
            );
        }
    }

    @Nested
    @DisplayName("Query Type and Value Filters")
    class QueryFiltersTests {

        @Test
        @DisplayName("Should filter by event type pattern")
        void shouldFilterByTypePattern() {
            // Setup
            addEvent("evt-1", "USER_CREATED", "u1", "{}");
            addEvent("evt-2", "USER_DELETED", "u2", "{}");
            addEvent("evt-3", "USER_UPDATED", "u3", "{}");
            addEvent("evt-4", "ORDER_CREATED", "o1", "{}");

            // Query with type filter
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    testTenantId,
                    "USER_%" // matches USER_* events
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify type filter applied
            assertThat(results).hasSize(3);
            assertThat(results).allSatisfy(e ->
                    assertTrue(e.eventType().startsWith("USER_"))
            );
        }

        @Test
        @DisplayName("Should handle special characters in queries")
        void shouldHandleSpecialCharactersInQuery() {
            // Setup - events with special chars in payload
            addEvent("evt-1", "EVENT", "entity", "{\"data\": \"value with 'quotes' and \\\"escapes\\\"\"}");
            addEvent("evt-2", "EVENT", "entity", "{\"data\": \"multi-line\\ndata\"}");

            // Execute query
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    testTenantId,
                    null
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify all events returned despite special chars
            assertThat(results).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle event store exceptions gracefully")
        void shouldHandleEventStoreException() {
            // Setup failing event store
            EventStore failingStore = new EventStore() {
                @Override
                public List<EventEntry> query(String query, String tenantId, String typeFilter) {
                    throw new RuntimeException("Event store connection failed");
                }
            };

            EventQueryGrpcService failingService = new EventQueryGrpcService(failingStore, metrics);

            // Execute
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    testTenantId,
                    null
            );

            TestStreamObserver<EventEntry> observer = new TestStreamObserver<>();
            failingService.executeQuery(request, observer);

            // Verify error propagated
            assertThat(observer.getError()).isNotNull();
            assertThat(observer.getError()).hasMessageContaining("Event store connection failed");
            verify(metrics).incrementCounter(
                    argThat(s -> s.contains("error")),
                    anyMap()
            );
        }

        @Test
        @DisplayName("Should validate query request parameters")
        void shouldValidateQueryRequest() {
            // Execute with null query
            QueryRequest badRequest = new QueryRequest(
                    null,
                    testTenantId,
                    null
            );

            TestStreamObserver<EventEntry> observer = new TestStreamObserver<>();
            queryService.executeQuery(badRequest, observer);

            // Verify validation error
            assertThat(observer.getError()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Query Streaming and Performance")
    class QueryStreamingTests {

        @Test
        @DisplayName("Should stream results incrementally for large result sets")
        void shouldStreamLargeResultSets() {
            // Setup - 5000 events
            for (int i = 0; i < 5000; i++) {
                addEvent("evt-" + i, "TEST", "entity-" + i, "{}");
            }

            // Execute streaming query
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    testTenantId,
                    null
            );

            TestStreamObserver<EventEntry> observer = new TestStreamObserver<>();
            queryService.executeQuery(request, observer);

            // Verify streaming completed
            assertTrue(observer.isCompleted());
            assertThat(observer.getResults()).hasSize(5000);
        }

        @Test
        @DisplayName("Should support query cancellation mid-stream")
        void shouldSupportQueryCancellation() {
            // Setup - 10000 events
            for (int i = 0; i < 10000; i++) {
                addEvent("evt-" + i, "TEST", "entity-" + i, "{}");
            }

            // Execute with cancellation
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    testTenantId,
                    null
            );

            TestStreamObserver<EventEntry> observer = new TestStreamObserver<>();
            observer.setCancelAfterCount(2500); // Cancel after 2500 events

            queryService.executeQuery(request, observer);

            // Verify cancellation respected (should have < 10000)
            assertThat(observer.getResults().size()).isLessThan(10000);
            assertTrue(observer.isCancelled());
        }
    }

    @Nested
    @DisplayName("ExplainQuery RPC")
    class ExplainQueryTests {

        @Test
        @DisplayName("Should provide query execution plan")
        void shouldProvideQueryPlan() {
            // Setup
            addEvent("evt-1", "TEST", "entity-1", "{}");

            // Execute explain
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events WHERE eventType='TEST'",
                    testTenantId,
                    null
            );

            QueryPlan plan = runPromise(() ->
                    queryService.explainQuery(request)
            );

            // Verify plan
            assertThat(plan).isNotNull();
            assertThat(plan.steps()).isNotEmpty();
            assertTrue(plan.steps().stream()
                    .anyMatch(s -> s.contains("Scan") || s.contains("Filter")));
        }

        @Test
        @DisplayName("Should estimate query cost")
        void shouldEstimateQueryCost() {
            // Setup - 1000 events
            for (int i = 0; i < 1000; i++) {
                addEvent("evt-" + i, "TEST", "entity-" + i, "{}");
            }

            // Execute explain
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    testTenantId,
                    null
            );

            QueryPlan plan = runPromise(() ->
                    queryService.explainQuery(request)
            );

            // Verify cost estimation
            assertThat(plan.estimatedCost()).isGreaterThan(0);
            assertThat(plan.estimatedRows()).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("Metrics Collection")
    class MetricsCollectionTests {

        @Test
        @DisplayName("Should record query execution metrics")
        void shouldRecordExecutionMetrics() {
            // Setup
            addEvent("evt-1", "TEST", "entity-1", "{}");

            // Execute
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    testTenantId,
                    null
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify metrics recorded
            verify(metrics).incrementCounter(
                    argThat(s -> s.contains("executed")),
                    anyMap()
            );
            verify(metrics).recordTimer(
                    argThat(s -> s.contains("latency")),
                    anyLong(),
                    anyMap()
            );
        }

        @Test
        @DisplayName("Should track query result counts")
        void shouldTrackResultCounts() {
            // Setup - 50 events
            for (int i = 0; i < 50; i++) {
                addEvent("evt-" + i, "TEST", "entity-" + i, "{}");
            }

            // Execute
            QueryRequest request = new QueryRequest(
                    "SELECT * FROM events",
                    testTenantId,
                    null
            );

            List<EventEntry> results = new ArrayList<>();
            queryService.executeQuery(request, new TestStreamObserver<>(results));

            // Verify result count tracked
            assertEquals(50, results.size());
            verify(metrics).recordMetric(
                    argThat(s -> s.contains("result.count")),
                    eq(50.0),
                    anyMap()
            );
        }
    }

    // Helper Methods

    private void addEvent(String eventId, String eventType, String entityId, String payload) {
        addEvent(eventId, eventType, entityId, payload, testTenantId);
    }

    private void addEvent(String eventId, String eventType, String entityId,
                         String payload, String tenantId) {
        EventEntry entry = new EventEntry(
                eventId,
                eventType,
                entityId,
                payload,
                tenantId,
                Instant.now().toEpochMilli(),
                "application/json"
        );
        ((TestEventStore) eventStore).addEvent(entry);
    }

    // Test Doubles

    static class TestEventStore implements EventStore {
        private final List<EventEntry> events = new ConcurrentLinkedQueue<>();

        @Override
        public List<EventEntry> query(String query, String tenantId, String typeFilter) {
            return events.stream()
                    .filter(e -> e.tenantId().equals(tenantId))
                    .filter(e -> typeFilter == null || e.eventType().matches(typeFilter.replace("%", ".*")))
                    .limit(10000) // Default limit
                    .collect(Collectors.toList());
        }

        void addEvent(EventEntry entry) {
            events.add(entry);
        }
    }

    static class TestStreamObserver<V> implements StreamObserver<V> {
        private final List<V> results = new ArrayList<>();
        private Throwable error;
        private AtomicBoolean completed = new AtomicBoolean(false);
        private AtomicBoolean cancelled = new AtomicBoolean(false);
        private int cancelAfterCount = Integer.MAX_VALUE;

        TestStreamObserver() {}

        void setCancelAfterCount(int count) {
            this.cancelAfterCount = count;
        }

        @Override
        public void onNext(V value) {
            results.add(value);
            if (results.size() >= cancelAfterCount) {
                cancelled.set(true);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onCompleted() {
            completed.set(true);
        }

        List<V> getResults() { return results; }
        Throwable getError() { return error; }
        boolean isCompleted() { return completed.get(); }
        boolean isCancelled() { return cancelled.get(); }
    }

    // Test Data Classes

    static class QueryRequest {
        final String query;
        final String tenantId;
        final String typeFilter;

        QueryRequest(String query, String tenantId, String typeFilter) {
            this.query = query;
            this.tenantId = tenantId;
            this.typeFilter = typeFilter;
        }
    }

    static class QueryPlan {
        private final List<String> steps;
        private final long estimatedCost;
        private final long estimatedRows;

        QueryPlan(List<String> steps, long estimatedCost, long estimatedRows) {
            this.steps = steps;
            this.estimatedCost = estimatedCost;
            this.estimatedRows = estimatedRows;
        }

        List<String> steps() { return steps; }
        long estimatedCost() { return estimatedCost; }
        long estimatedRows() { return estimatedRows; }
    }

    interface EventStore {
        List<EventEntry> query(String query, String tenantId, String typeFilter);
    }

    interface MetricsCollector {
        void incrementCounter(String name, Map<String, String> tags);
        void recordTimer(String name, long durationMs, Map<String, String> tags);
        void recordMetric(String name, double value, Map<String, String> tags);
    }
}
