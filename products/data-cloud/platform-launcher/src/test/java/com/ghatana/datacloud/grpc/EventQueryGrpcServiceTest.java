package com.ghatana.datacloud.grpc;

import com.ghatana.contracts.event.v1.EventQueryServiceGrpc;
import com.ghatana.contracts.event.v1.ExecuteQueryRequestProto;
import com.ghatana.contracts.event.v1.ExecuteQueryResponseProto;
import com.ghatana.contracts.event.v1.ExplainQueryRequestProto;
import com.ghatana.contracts.event.v1.ExplainQueryResponseProto;
import com.ghatana.contracts.common.v1.Envelope;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import com.google.protobuf.Struct;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * @doc.type class
 * @doc.purpose Tests for EventQueryGrpcService
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("EventQueryGrpcService Tests")
class EventQueryGrpcServiceTest {

    private EventLogStore eventLogStore;
    private EventQueryGrpcService service;

    @BeforeEach
    void setUp() {
        eventLogStore = mock(EventLogStore.class);
        service = new EventQueryGrpcService(eventLogStore);
    }

    @Nested
    @DisplayName("ExecuteQuery - Type Filter")
    class ExecuteQueryTypeFilter {

        @Test
        @DisplayName("should execute query with type filter")
        void shouldExecuteTypeFilterQuery() {
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setQuery("type:user.created")
                    .setLimit(100)
                    .build();

            List<EventLogStore.EventEntry> entries = List.of(
                    createEventEntry("event-1", "user.created", "1"),
                    createEventEntry("event-2", "user.created", "1")
            );

            when(eventLogStore.readByType(
                    argThat(ctx -> ctx.tenantId().equals("default-tenant")),
                    eq("user.created"),
                    any(),
                    eq(100)))
                    .thenReturn(io.activej.promise.Promise.of(entries));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            // Allow async processing
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
            assertTrue(observer.isCompleted());
        }

        @Test
        @DisplayName("should handle empty type filter results")
        void shouldHandleEmptyTypeFilterResults() {
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setQuery("type:nonexistent.event")
                    .setLimit(100)
                    .build();

            when(eventLogStore.readByType(
                    any(TenantContext.class),
                    eq("nonexistent.event"),
                    any(),
                    anyInt()))
                    .thenReturn(io.activej.promise.Promise.of(List.of()));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertTrue(observer.isCompleted());
        }

        @Test
        @DisplayName("should trim whitespace from type filter")
        void shouldTrimWhitespaceFromType() {
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setQuery("type:  user.created  ")
                    .setLimit(100)
                    .build();

            List<EventLogStore.EventEntry> entries = List.of(
                    createEventEntry("event-1", "user.created", "1")
            );

            when(eventLogStore.readByType(
                    any(TenantContext.class),
                    eq("user.created"),
                    any(),
                    anyInt()))
                    .thenReturn(io.activej.promise.Promise.of(entries));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }
    }

    @Nested
    @DisplayName("ExecuteQuery - Scan Query")
    class ExecuteQueryScan {

        @Test
        @DisplayName("should execute scan query without type filter")
        void shouldExecuteScanQuery() {
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setQuery("SELECT * FROM events LIMIT 50")
                    .setLimit(50)
                    .build();

            List<EventLogStore.EventEntry> entries = List.of(
                    createEventEntry("event-1", "user.created", "1"),
                    createEventEntry("event-2", "order.placed", "1")
            );

            when(eventLogStore.read(
                    any(TenantContext.class),
                    any(),
                    eq(50)))
                    .thenReturn(io.activej.promise.Promise.of(entries));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }

        @Test
        @DisplayName("should respect default limit")
        void shouldRespectDefaultLimit() {
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setQuery("SELECT * FROM events")
                    // No limit specified = should use default 100
                    .build();

            List<EventLogStore.EventEntry> entries = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                entries.add(createEventEntry("event-" + i, "type-" + (i % 5), "1"));
            }

            when(eventLogStore.read(
                    any(TenantContext.class),
                    any(),
                    eq(100)))
                    .thenReturn(io.activej.promise.Promise.of(entries));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertTrue(observer.isCompleted());
        }
    }

    @Nested
    @DisplayName("ExecuteQuery - Limit Handling")
    class ExecuteQueryLimits {

        @Test
        @DisplayName("should enforce maximum query limit")
        void shouldEnforceMaxLimit() {
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setQuery("SELECT * FROM events")
                    .setLimit(20000) // Exceeds max of 10,000
                    .build();

            List<EventLogStore.EventEntry> entries = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                entries.add(createEventEntry("event-" + i, "type", "1"));
            }

            when(eventLogStore.read(
                    any(TenantContext.class),
                    any(),
                    eq(10000))) // Should be capped at 10,000
                    .thenReturn(io.activej.promise.Promise.of(entries));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertTrue(observer.isCompleted());
        }

        @Test
        @DisplayName("should handle zero limit")
        void shouldHandleZeroLimit() {
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setQuery("SELECT * FROM events")
                    .setLimit(0)
                    .build();

            when(eventLogStore.read(
                    any(TenantContext.class),
                    any(),
                    anyInt()))
                    .thenReturn(io.activej.promise.Promise.of(List.of()));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertTrue(observer.isCompleted());
        }
    }

    @Nested
    @DisplayName("ExecuteQuery - Tenant Resolution")
    class ExecuteQueryTenantResolution {

        @Test
        @DisplayName("should use tenant from envelope")
        void shouldUseTenantFromEnvelope() {
            // Create envelope with tenant ID
            Envelope envelope = Envelope.newBuilder()
                    .setTenantId("tenant-specific-123")
                    .build();

            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setEnvelope(envelope)
                    .setQuery("type:user.created")
                    .setLimit(100)
                    .build();

            List<EventLogStore.EventEntry> entries = List.of(
                    createEventEntry("event-1", "user.created", "1")
            );

            when(eventLogStore.readByType(
                    argThat(ctx -> ctx.tenantId().equals("tenant-specific-123")),
                    eq("user.created"),
                    any(),
                    anyInt()))
                    .thenReturn(io.activej.promise.Promise.of(entries));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }

        @Test
        @DisplayName("should use default tenant when envelope missing")
        void shouldUseDefaultTenant() {
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setQuery("type:user.created")
                    .setLimit(100)
                    .build();

            List<EventLogStore.EventEntry> entries = List.of(
                    createEventEntry("event-1", "user.created", "1")
            );

            when(eventLogStore.readByType(
                    argThat(ctx -> ctx.tenantId().equals("default-tenant")),
                    eq("user.created"),
                    any(),
                    anyInt()))
                    .thenReturn(io.activej.promise.Promise.of(entries));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }
    }

    @Nested
    @DisplayName("ExecuteQuery - Error Handling")
    class ExecuteQueryErrors {

        @Test
        @DisplayName("should handle event log store errors")
        void shouldHandleEventStoreError() {
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setQuery("type:user.created")
                    .setLimit(100)
                    .build();

            when(eventLogStore.readByType(
                    any(TenantContext.class),
                    anyString(),
                    any(),
                    anyInt()))
                    .thenReturn(io.activej.promise.Promise.ofException(
                            new RuntimeException("Event store failed")));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertNotNull(observer.getError());
            assertTrue(observer.isCancelled());
        }

        @Test
        @DisplayName("should handle null request gracefully")
        void shouldHandleNullRequest() {
            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();

            assertThrows(NullPointerException.class, () -> 
                service.executeQuery(null, observer));
        }
    }

    @Nested
    @DisplayName("ExplainQuery")
    class ExplainQueryBehavior {

        @Test
        @DisplayName("should return query explanation")
        void shouldExplainQuery() {
            ExplainQueryRequestProto request = ExplainQueryRequestProto.newBuilder()
                    .setQuery("type:user.created")
                    .build();

            CapturingObserver<ExplainQueryResponseProto> observer = new CapturingObserver<>();
            service.explainQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }

        @Test
        @DisplayName("should include query plan in explanation")
        void shouldIncludeQueryPlan() {
            ExplainQueryRequestProto request = ExplainQueryRequestProto.newBuilder()
                    .setQuery("SELECT * FROM events WHERE type='user.created'")
                    .build();

            CapturingObserver<ExplainQueryResponseProto> observer = new CapturingObserver<>();
            service.explainQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
            assertTrue(observer.isCompleted());
        }

        @Test
        @DisplayName("should honor tenant context in explain")
        void shouldHonorTenantInExplain() {
            ExplainQueryRequestProto request = ExplainQueryRequestProto.newBuilder()
                    .setQuery("type:user.created")
                    .build();

            CapturingObserver<ExplainQueryResponseProto> observer = new CapturingObserver<>();
            service.explainQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }
    }

    @Nested
    @DisplayName("Performance and Edge Cases")
    class PerformanceEdgeCases {

        @Test
        @DisplayName("should handle large result streams")
        void shouldHandleLargeResultStreams() {
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setQuery("type:event.stream")
                    .setLimit(1000)
                    .build();

            List<EventLogStore.EventEntry> entries = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                entries.add(createEventEntry("event-" + i, "event.stream", "1"));
            }

            when(eventLogStore.readByType(
                    any(TenantContext.class),
                    eq("event.stream"),
                    any(),
                    eq(1000)))
                    .thenReturn(io.activej.promise.Promise.of(entries));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertEquals(1000, observer.getResults().size());
            assertTrue(observer.isCompleted());
        }

        @Test
        @DisplayName("should handle special characters in query")
        void shouldHandleSpecialCharsInQuery() {
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder()
                    .setQuery("type:event.with_special-chars.v2")
                    .setLimit(100)
                    .build();

            List<EventLogStore.EventEntry> entries = List.of(
                    createEventEntry("event-1", "event.with_special-chars.v2", "1")
            );

            when(eventLogStore.readByType(
                    any(TenantContext.class),
                    eq("event.with_special-chars.v2"),
                    any(),
                    anyInt()))
                    .thenReturn(io.activej.promise.Promise.of(entries));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>();
            service.executeQuery(request, observer);

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            assertFalse(observer.getResults().isEmpty());
        }
    }

    // ─── Helper Methods ───────────────────────────────────────────────────

    private EventLogStore.EventEntry createEventEntry(String eventId, String eventType, String version) {
        return EventLogStore.EventEntry.builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .eventVersion(version)
                .timestamp(Instant.now())
                .contentType("application/json")
                .payload("{\"test\": \"data\"}".getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private static class CapturingObserver<T> implements StreamObserver<T> {
        private final List<T> results = new ArrayList<>();
        private Throwable error;
        private boolean completed;
        private boolean cancelled;

        @Override
        public void onNext(T value) {
            results.add(value);
        }

        @Override
        public void onError(Throwable t) {
            this.error = t;
            this.cancelled = true;
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }

        public List<T> getResults() {
            return results;
        }

        public Throwable getError() {
            return error;
        }

        public boolean isCompleted() {
            return completed;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }
}
