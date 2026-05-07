/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.aep.observability;

import com.ghatana.aep.eventcloud.store.EventCloudRunLedger;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RunLedgerService}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for the run-ledger facade that wraps EventCloudRunLedger
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("RunLedgerService")
@ExtendWith(MockitoExtension.class) 
class RunLedgerServiceTest extends EventloopTestBase {

    @Mock
    private EventLogStore mockEventLogStore;

    @Captor
    private ArgumentCaptor<EventEntry> entryCaptor;

    @Captor
    private ArgumentCaptor<TenantContext> tenantCaptor;

    private RunLedgerService serviceWithLedger;
    private RunLedgerService noopService;

    @BeforeEach
    void setUp() { 
        EventCloudRunLedger ledger = new EventCloudRunLedger(mockEventLogStore); 
        serviceWithLedger = new RunLedgerService(ledger); 
        noopService = new RunLedgerService(); // no-arg = noop 
    }

    @Nested
    @DisplayName("recordRunStarted")
    class RecordRunStarted {

        @Test
        @DisplayName("appends run.started event to ledger")
        void appendsRunStartedEvent() { 
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) 
                .thenReturn(Promise.of(Offset.of("1")));

            runPromise(() -> serviceWithLedger.recordRunStarted( 
                "run-1", "tenant-1", "pipeline-1", "trace-abc", Instant.now())); 

            verify(mockEventLogStore).append(tenantCaptor.capture(), entryCaptor.capture()); 
            assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("tenant-1");
            EventEntry entry = entryCaptor.getValue(); 
            assertThat(entry.eventType()).isEqualTo("run.started");
            assertThat(entry.headers().get("runId")).isEqualTo("run-1");
            assertThat(entry.headers().get("pipelineId")).isEqualTo("pipeline-1");
        }

        @Test
        @DisplayName("noop service does not call EventLogStore")
        void noopServiceDoesNotCallStore() { 
            runPromise(() -> noopService.recordRunStarted( 
                "run-1", "tenant-1", null, null, Instant.now())); 
            verify(mockEventLogStore, never()).append(any(), any()); 
        }

        @Test
        @DisplayName("uses 'event' as pipelineId when null")
        void usesFallbackPipelineId() { 
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) 
                .thenReturn(Promise.of(Offset.of("1")));

            runPromise(() -> serviceWithLedger.recordRunStarted( 
                "run-1", "tenant-1", null, null, Instant.now())); 

            verify(mockEventLogStore).append(any(), entryCaptor.capture()); 
            assertThat(entryCaptor.getValue().headers().get("pipelineId")).isEqualTo("event");
        }
    }

    @Nested
    @DisplayName("recordRunCompleted")
    class RecordRunCompleted {

        @Test
        @DisplayName("appends run.completed event with SUCCEEDED status in JSON")
        void appendsRunCompletedEvent() { 
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) 
                .thenReturn(Promise.of(Offset.of("2")));
            Instant start = Instant.now().minusMillis(200); 

            runPromise(() -> serviceWithLedger.recordRunCompleted( 
                "run-2", "tenant-1", "pipeline-1", "trace-abc", start, 3));

            verify(mockEventLogStore).append(tenantCaptor.capture(), entryCaptor.capture()); 
            EventEntry entry = entryCaptor.getValue(); 
            assertThat(entry.eventType()).isEqualTo("run.completed");
            String payloadJson = new String(entry.payload().array(), java.nio.charset.StandardCharsets.UTF_8); 
            assertThat(payloadJson).contains("SUCCEEDED");
            assertThat(payloadJson).contains("run-2");
        }

        @Test
        @DisplayName("noop service does not call EventLogStore")
        void noopServiceDoesNotCallStore() { 
            runPromise(() -> noopService.recordRunCompleted( 
                "run-2", "tenant-1", null, null, Instant.now(), 0)); 
            verify(mockEventLogStore, never()).append(any(), any()); 
        }
    }

    @Nested
    @DisplayName("recordRunFailed")
    class RecordRunFailed {

        @Test
        @DisplayName("appends run.failed event with FAILED status in JSON")
        void appendsRunFailedEvent() { 
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) 
                .thenReturn(Promise.of(Offset.of("3")));
            Instant start = Instant.now().minusMillis(100); 

            runPromise(() -> serviceWithLedger.recordRunFailed( 
                "run-3", "tenant-1", "pipeline-1", "trace-abc",
                start, "timeout", "execution timed out"));

            verify(mockEventLogStore).append(any(), entryCaptor.capture()); 
            EventEntry entry = entryCaptor.getValue(); 
            assertThat(entry.eventType()).isEqualTo("run.failed");
            String payloadJson = new String(entry.payload().array(), java.nio.charset.StandardCharsets.UTF_8); 
            assertThat(payloadJson).contains("FAILED");
            assertThat(payloadJson).contains("timeout");
        }
    }

    @Nested
    @DisplayName("error resilience")
    class ErrorResilience {

        @Test
        @DisplayName("ledger errors are swallowed and do not propagate")
        void ledgerErrorsDoNotPropagate() { 
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) 
                .thenReturn(Promise.ofException(new RuntimeException("store failure")));

            // Should complete without throwing
            runPromise(() -> serviceWithLedger.recordRunStarted( 
                "run-err", "tenant-1", "pipeline-1", null, Instant.now())); 
        }
    }

    @Nested
    @DisplayName("recordReviewDecision")
    class RecordReviewDecision {

        @Test
        @DisplayName("appends a step-completed event with review.decision type")
        void appendsStepCompletedEvent() { 
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) 
                .thenReturn(Promise.of(Offset.of("4")));

            runPromise(() -> serviceWithLedger.recordReviewDecision( 
                "item-1", "run-1", "tenant-1", "skill-abc", "APPROVED", Instant.now())); 

            verify(mockEventLogStore).append(any(), entryCaptor.capture()); 
            String payloadJson = new String( 
                entryCaptor.getValue().payload().array(), 
                java.nio.charset.StandardCharsets.UTF_8);
            assertThat(payloadJson).contains("review.decision");
            assertThat(payloadJson).contains("APPROVED");
        }
    }
}
