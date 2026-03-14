package com.ghatana.appplatform.audit.integration;

import com.ghatana.appplatform.audit.adapter.PostgresAuditTrailStore;
import com.ghatana.appplatform.audit.chain.HashChainService;
import com.ghatana.appplatform.audit.domain.AuditEntry;
import com.ghatana.appplatform.audit.domain.AuditEntry.Actor;
import com.ghatana.appplatform.audit.domain.AuditEntry.Outcome;
import com.ghatana.appplatform.audit.domain.AuditEntry.Resource;
import com.ghatana.appplatform.audit.enrichment.AuditEntryEnricher;
import com.ghatana.appplatform.eventstore.adapter.PostgresAggregateEventStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link AuditedAggregateEventStore}.
 *
 * <p>Validates the K-05→K-07 audit hook (Sprint 2 exit gate):
 * <ul>
 *   <li>appendEvent delegates to wrapped store AND emits audit entry</li>
 *   <li>Audit failures do not propagate to the caller</li>
 *   <li>Read operations bypass audit (reads not audited)</li>
 *   <li>Actor is resolved from metadata or falls back to systemActor</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Unit tests for K-05→K-07 audit hook decorator
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AuditedAggregateEventStore Tests (K-05→K-07 hook)")
@ExtendWith(MockitoExtension.class)
class AuditedAggregateEventStoreTest extends EventloopTestBase {

    @Mock
    private com.ghatana.appplatform.eventstore.port.AggregateEventStore delegate;
    @Mock
    private com.ghatana.appplatform.audit.port.AuditTrailStore auditTrailStore;

    private AuditedAggregateEventStore audited;

    @BeforeEach
    void setUp() {
        audited = new AuditedAggregateEventStore(
                delegate, auditTrailStore, "event-bus-system", "EventBus");
    }

    @Test
    @DisplayName("appendEvent delegates to wrapped store and emits audit entry")
    void appendEvent_delegatesAndAudits() {
        UUID agg = UUID.randomUUID();
        var record = com.ghatana.appplatform.eventstore.domain.AggregateEventRecord.builder()
                .aggregateId(agg)
                .aggregateType("Order")
                .eventType("OrderPlaced")
                .sequenceNumber(0)
                .build();

        var mockReceipt = new com.ghatana.appplatform.audit.domain.AuditReceipt(
                UUID.randomUUID().toString(), "hash", 0L);

        org.mockito.Mockito.when(delegate.appendEvent(any(), any(), any(), any(), any()))
                .thenReturn(io.activej.promise.Promise.of(record));
        org.mockito.Mockito.when(auditTrailStore.log(any()))
                .thenReturn(io.activej.promise.Promise.of(mockReceipt));

        var result = runPromise(() ->
                audited.appendEvent(agg, "Order", "OrderPlaced", Map.of(), Map.of(
                        "tenant_id", "tenant-1",
                        "trace_id",  "trace-abc")));

        assertThat(result.eventId()).isNotNull();
        verify(delegate).appendEvent(any(), any(), any(), any(), any());
        verify(auditTrailStore).log(any());
    }

    @Test
    @DisplayName("Audit failure does not fail the appendEvent result")
    void appendEvent_auditFailure_doesNotPropagate() {
        UUID agg = UUID.randomUUID();
        var record = com.ghatana.appplatform.eventstore.domain.AggregateEventRecord.builder()
                .aggregateId(agg).aggregateType("Order").eventType("OrderPlaced")
                .sequenceNumber(0).build();

        org.mockito.Mockito.when(delegate.appendEvent(any(), any(), any(), any(), any()))
                .thenReturn(io.activej.promise.Promise.of(record));
        org.mockito.Mockito.when(auditTrailStore.log(any()))
                .thenReturn(io.activej.promise.Promise.ofException(new RuntimeException("audit down")));

        var result = runPromise(() ->
                audited.appendEvent(agg, "Order", "OrderPlaced", Map.of(), Map.of("tenant_id", "t1")));

        // Event append succeeded despite audit failure
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Read operations bypass audit trail")
    void getEventsByAggregate_doesNotAudit() {
        UUID agg = UUID.randomUUID();
        org.mockito.Mockito.when(delegate.getEventsByAggregate(any(), any(Long.class), any()))
                .thenReturn(io.activej.promise.Promise.of(java.util.List.of()));

        runPromise(() -> audited.getEventsByAggregate(agg, 0L, null));

        verify(delegate).getEventsByAggregate(any(), any(Long.class), any());
        verifyNoInteractions(auditTrailStore);
    }

    @Test
    @DisplayName("Actor resolved from metadata when actor_id present")
    void appendEvent_actorFromMetadata() {
        UUID agg = UUID.randomUUID();
        var record = com.ghatana.appplatform.eventstore.domain.AggregateEventRecord.builder()
                .aggregateId(agg).aggregateType("Tx").eventType("TxCreated")
                .sequenceNumber(0).build();

        org.mockito.Mockito.when(delegate.appendEvent(any(), any(), any(), any(), any()))
                .thenReturn(io.activej.promise.Promise.of(record));

        var receipt = new com.ghatana.appplatform.audit.domain.AuditReceipt("id", "hash", 0);
        var capturedAuditEntry = new java.util.concurrent.atomic.AtomicReference<AuditEntry>();

        org.mockito.Mockito.when(auditTrailStore.log(any())).thenAnswer(inv -> {
            capturedAuditEntry.set(inv.getArgument(0));
            return io.activej.promise.Promise.of(receipt);
        });

        runPromise(() -> audited.appendEvent(agg, "Tx", "TxCreated", Map.of(), Map.of(
                "tenant_id",  "t1",
                "actor_id",   "user-42",
                "actor_role", "Trader")));

        AuditEntry captured = capturedAuditEntry.get();
        assertThat(captured).isNotNull();
        assertThat(captured.actor().userId()).isEqualTo("user-42");
        assertThat(captured.actor().role()).isEqualTo("Trader");
    }
}
