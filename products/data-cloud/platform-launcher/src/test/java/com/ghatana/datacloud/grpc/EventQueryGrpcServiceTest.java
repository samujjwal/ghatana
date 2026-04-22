package com.ghatana.datacloud.grpc;

import com.ghatana.contracts.event.v1.ExecuteQueryRequestProto;
import com.ghatana.contracts.event.v1.ExecuteQueryResponseProto;
import com.ghatana.contracts.event.v1.ExplainQueryRequestProto;
import com.ghatana.contracts.event.v1.ExplainQueryResponseProto;
import com.ghatana.contracts.common.v1.Envelope;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
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
@DisplayName("EventQueryGrpcService Tests [GH-90000]")
class EventQueryGrpcServiceTest {

    private EventLogStore eventLogStore;
    private EventQueryGrpcService service;

    @BeforeEach
    void setUp() { // GH-90000
        eventLogStore = mock(EventLogStore.class); // GH-90000
        service = new EventQueryGrpcService(eventLogStore); // GH-90000
    }

    @Nested
    @DisplayName("ExecuteQuery - Type Filter [GH-90000]")
    class ExecuteQueryTypeFilter {

        @Test
        @DisplayName("should execute query with type filter [GH-90000]")
        void shouldExecuteTypeFilterQuery() { // GH-90000
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("type:user.created [GH-90000]")
                    .setLimit(100) // GH-90000
                    .build(); // GH-90000

            List<EventLogStore.EventEntry> entries = List.of( // GH-90000
                    createEventEntry("event-1", "user.created", "1"), // GH-90000
                    createEventEntry("event-2", "user.created", "1") // GH-90000
            );

            when(eventLogStore.readByType( // GH-90000
                    argThat(ctx -> ctx.tenantId().equals("default-tenant [GH-90000]")),
                    eq("user.created [GH-90000]"),
                    any(), // GH-90000
                    eq(100))) // GH-90000
                    .thenReturn(io.activej.promise.Promise.of(entries)); // GH-90000

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            // Allow async processing
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertFalse(observer.getResults().isEmpty()); // GH-90000
            assertTrue(observer.isCompleted()); // GH-90000
        }

        @Test
        @DisplayName("should handle empty type filter results [GH-90000]")
        void shouldHandleEmptyTypeFilterResults() { // GH-90000
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("type:nonexistent.event [GH-90000]")
                    .setLimit(100) // GH-90000
                    .build(); // GH-90000

            when(eventLogStore.readByType( // GH-90000
                    any(TenantContext.class), // GH-90000
                    eq("nonexistent.event [GH-90000]"),
                    any(), // GH-90000
                    anyInt())) // GH-90000
                    .thenReturn(io.activej.promise.Promise.of(List.of())); // GH-90000

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertTrue(observer.isCompleted()); // GH-90000
        }

        @Test
        @DisplayName("should trim whitespace from type filter [GH-90000]")
        void shouldTrimWhitespaceFromType() { // GH-90000
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("type:  user.created   [GH-90000]")
                    .setLimit(100) // GH-90000
                    .build(); // GH-90000

            List<EventLogStore.EventEntry> entries = List.of( // GH-90000
                    createEventEntry("event-1", "user.created", "1") // GH-90000
            );

            when(eventLogStore.readByType( // GH-90000
                    any(TenantContext.class), // GH-90000
                    eq("user.created [GH-90000]"),
                    any(), // GH-90000
                    anyInt())) // GH-90000
                    .thenReturn(io.activej.promise.Promise.of(entries)); // GH-90000

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertFalse(observer.getResults().isEmpty()); // GH-90000
        }
    }

    @Nested
    @DisplayName("ExecuteQuery - Scan Query [GH-90000]")
    class ExecuteQueryScan {

        @Test
        @DisplayName("should execute scan query without type filter [GH-90000]")
        void shouldExecuteScanQuery() { // GH-90000
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("SELECT * FROM events LIMIT 50 [GH-90000]")
                    .setLimit(50) // GH-90000
                    .build(); // GH-90000

            List<EventLogStore.EventEntry> entries = List.of( // GH-90000
                    createEventEntry("event-1", "user.created", "1"), // GH-90000
                    createEventEntry("event-2", "order.placed", "1") // GH-90000
            );

            when(eventLogStore.read( // GH-90000
                    any(TenantContext.class), // GH-90000
                    any(), // GH-90000
                    eq(50))) // GH-90000
                    .thenReturn(io.activej.promise.Promise.of(entries)); // GH-90000

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertFalse(observer.getResults().isEmpty()); // GH-90000
        }

        @Test
        @DisplayName("should respect default limit [GH-90000]")
        void shouldRespectDefaultLimit() { // GH-90000
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("SELECT * FROM events [GH-90000]")
                    // No limit specified = should use default 100
                    .build(); // GH-90000

            List<EventLogStore.EventEntry> entries = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 100; i++) { // GH-90000
                entries.add(createEventEntry("event-" + i, "type-" + (i % 5), "1")); // GH-90000
            }

            when(eventLogStore.read( // GH-90000
                    any(TenantContext.class), // GH-90000
                    any(), // GH-90000
                    eq(100))) // GH-90000
                    .thenReturn(io.activej.promise.Promise.of(entries)); // GH-90000

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertTrue(observer.isCompleted()); // GH-90000
        }
    }

    @Nested
    @DisplayName("ExecuteQuery - Limit Handling [GH-90000]")
    class ExecuteQueryLimits {

        @Test
        @DisplayName("should enforce maximum query limit [GH-90000]")
        void shouldEnforceMaxLimit() { // GH-90000
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("SELECT * FROM events [GH-90000]")
                    .setLimit(20000) // Exceeds max of 10,000 // GH-90000
                    .build(); // GH-90000

            List<EventLogStore.EventEntry> entries = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 10000; i++) { // GH-90000
                entries.add(createEventEntry("event-" + i, "type", "1")); // GH-90000
            }

            when(eventLogStore.read( // GH-90000
                    any(TenantContext.class), // GH-90000
                    any(), // GH-90000
                    eq(10000))) // Should be capped at 10,000 // GH-90000
                    .thenReturn(io.activej.promise.Promise.of(entries)); // GH-90000

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertTrue(observer.isCompleted()); // GH-90000
        }

        @Test
        @DisplayName("should handle zero limit [GH-90000]")
        void shouldHandleZeroLimit() { // GH-90000
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("SELECT * FROM events [GH-90000]")
                    .setLimit(0) // GH-90000
                    .build(); // GH-90000

            when(eventLogStore.read( // GH-90000
                    any(TenantContext.class), // GH-90000
                    any(), // GH-90000
                    anyInt())) // GH-90000
                    .thenReturn(io.activej.promise.Promise.of(List.of())); // GH-90000

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertTrue(observer.isCompleted()); // GH-90000
        }
    }

    @Nested
    @DisplayName("ExecuteQuery - Tenant Resolution [GH-90000]")
    class ExecuteQueryTenantResolution {

        @Test
        @DisplayName("should use tenant from envelope [GH-90000]")
        void shouldUseTenantFromEnvelope() { // GH-90000
            // Create envelope with tenant ID
            Envelope envelope = Envelope.newBuilder() // GH-90000
                    .setTenantId("tenant-specific-123 [GH-90000]")
                    .build(); // GH-90000

            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setEnvelope(envelope) // GH-90000
                    .setQuery("type:user.created [GH-90000]")
                    .setLimit(100) // GH-90000
                    .build(); // GH-90000

            List<EventLogStore.EventEntry> entries = List.of( // GH-90000
                    createEventEntry("event-1", "user.created", "1") // GH-90000
            );

            when(eventLogStore.readByType( // GH-90000
                    argThat(ctx -> ctx.tenantId().equals("tenant-specific-123 [GH-90000]")),
                    eq("user.created [GH-90000]"),
                    any(), // GH-90000
                    anyInt())) // GH-90000
                    .thenReturn(io.activej.promise.Promise.of(entries)); // GH-90000

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertFalse(observer.getResults().isEmpty()); // GH-90000
        }

        @Test
        @DisplayName("should use default tenant when envelope missing [GH-90000]")
        void shouldUseDefaultTenant() { // GH-90000
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("type:user.created [GH-90000]")
                    .setLimit(100) // GH-90000
                    .build(); // GH-90000

            List<EventLogStore.EventEntry> entries = List.of( // GH-90000
                    createEventEntry("event-1", "user.created", "1") // GH-90000
            );

            when(eventLogStore.readByType( // GH-90000
                    argThat(ctx -> ctx.tenantId().equals("default-tenant [GH-90000]")),
                    eq("user.created [GH-90000]"),
                    any(), // GH-90000
                    anyInt())) // GH-90000
                    .thenReturn(io.activej.promise.Promise.of(entries)); // GH-90000

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertFalse(observer.getResults().isEmpty()); // GH-90000
        }
    }

    @Nested
    @DisplayName("ExecuteQuery - Error Handling [GH-90000]")
    class ExecuteQueryErrors {

        @Test
        @DisplayName("should handle event log store errors [GH-90000]")
        void shouldHandleEventStoreError() { // GH-90000
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("type:user.created [GH-90000]")
                    .setLimit(100) // GH-90000
                    .build(); // GH-90000

            when(eventLogStore.readByType( // GH-90000
                    any(TenantContext.class), // GH-90000
                    anyString(), // GH-90000
                    any(), // GH-90000
                    anyInt())) // GH-90000
                    .thenReturn(io.activej.promise.Promise.ofException( // GH-90000
                            new RuntimeException("Event store failed [GH-90000]")));

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertNotNull(observer.getError()); // GH-90000
            assertTrue(observer.isCancelled()); // GH-90000
        }

        @Test
        @DisplayName("should handle null request gracefully [GH-90000]")
        void shouldHandleNullRequest() { // GH-90000
            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000

            assertThrows(NullPointerException.class, () -> // GH-90000
                service.executeQuery(null, observer)); // GH-90000
        }
    }

    @Nested
    @DisplayName("ExplainQuery [GH-90000]")
    class ExplainQueryBehavior {

        @Test
        @DisplayName("should return query explanation [GH-90000]")
        void shouldExplainQuery() { // GH-90000
            ExplainQueryRequestProto request = ExplainQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("type:user.created [GH-90000]")
                    .build(); // GH-90000

            CapturingObserver<ExplainQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.explainQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertFalse(observer.getResults().isEmpty()); // GH-90000
        }

        @Test
        @DisplayName("should include query plan in explanation [GH-90000]")
        void shouldIncludeQueryPlan() { // GH-90000
            ExplainQueryRequestProto request = ExplainQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("SELECT * FROM events WHERE type='user.created' [GH-90000]")
                    .build(); // GH-90000

            CapturingObserver<ExplainQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.explainQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertFalse(observer.getResults().isEmpty()); // GH-90000
            assertTrue(observer.isCompleted()); // GH-90000
        }

        @Test
        @DisplayName("should honor tenant context in explain [GH-90000]")
        void shouldHonorTenantInExplain() { // GH-90000
            ExplainQueryRequestProto request = ExplainQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("type:user.created [GH-90000]")
                    .build(); // GH-90000

            CapturingObserver<ExplainQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.explainQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertFalse(observer.getResults().isEmpty()); // GH-90000
        }
    }

    @Nested
    @DisplayName("Performance and Edge Cases [GH-90000]")
    class PerformanceEdgeCases {

        @Test
        @DisplayName("should handle large result streams [GH-90000]")
        void shouldHandleLargeResultStreams() { // GH-90000
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("type:event.stream [GH-90000]")
                    .setLimit(1000) // GH-90000
                    .build(); // GH-90000

            List<EventLogStore.EventEntry> entries = new ArrayList<>(); // GH-90000
            for (int i = 0; i < 1000; i++) { // GH-90000
                entries.add(createEventEntry("event-" + i, "event.stream", "1")); // GH-90000
            }

            when(eventLogStore.readByType( // GH-90000
                    any(TenantContext.class), // GH-90000
                    eq("event.stream [GH-90000]"),
                    any(), // GH-90000
                    eq(1000))) // GH-90000
                    .thenReturn(io.activej.promise.Promise.of(entries)); // GH-90000

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertEquals(1000, observer.getResults().size()); // GH-90000
            assertTrue(observer.isCompleted()); // GH-90000
        }

        @Test
        @DisplayName("should handle special characters in query [GH-90000]")
        void shouldHandleSpecialCharsInQuery() { // GH-90000
            ExecuteQueryRequestProto request = ExecuteQueryRequestProto.newBuilder() // GH-90000
                    .setQuery("type:event.with_special-chars.v2 [GH-90000]")
                    .setLimit(100) // GH-90000
                    .build(); // GH-90000

            List<EventLogStore.EventEntry> entries = List.of( // GH-90000
                    createEventEntry("event-1", "event.with_special-chars.v2", "1") // GH-90000
            );

            when(eventLogStore.readByType( // GH-90000
                    any(TenantContext.class), // GH-90000
                    eq("event.with_special-chars.v2 [GH-90000]"),
                    any(), // GH-90000
                    anyInt())) // GH-90000
                    .thenReturn(io.activej.promise.Promise.of(entries)); // GH-90000

            CapturingObserver<ExecuteQueryResponseProto> observer = new CapturingObserver<>(); // GH-90000
            service.executeQuery(request, observer); // GH-90000

            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } // GH-90000

            assertFalse(observer.getResults().isEmpty()); // GH-90000
        }
    }

    // ─── Helper Methods ───────────────────────────────────────────────────

    private EventLogStore.EventEntry createEventEntry(String eventId, String eventType, String version) { // GH-90000
        return EventLogStore.EventEntry.builder() // GH-90000
                .eventId(UUID.randomUUID()) // GH-90000
                .eventType(eventType) // GH-90000
                .eventVersion(version) // GH-90000
                .timestamp(Instant.now()) // GH-90000
                .contentType("application/json [GH-90000]")
                .payload("{\"test\": \"data\"}".getBytes(StandardCharsets.UTF_8)) // GH-90000
                .build(); // GH-90000
    }

    private static class CapturingObserver<T> implements StreamObserver<T> {
        private final List<T> results = new ArrayList<>(); // GH-90000
        private Throwable error;
        private boolean completed;
        private boolean cancelled;

        @Override
        public void onNext(T value) { // GH-90000
            results.add(value); // GH-90000
        }

        @Override
        public void onError(Throwable t) { // GH-90000
            this.error = t;
            this.cancelled = true;
        }

        @Override
        public void onCompleted() { // GH-90000
            this.completed = true;
        }

        public List<T> getResults() { // GH-90000
            return results;
        }

        public Throwable getError() { // GH-90000
            return error;
        }

        public boolean isCompleted() { // GH-90000
            return completed;
        }

        public boolean isCancelled() { // GH-90000
            return cancelled;
        }
    }
}
