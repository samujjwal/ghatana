/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("RunLedgerService [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
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
    void setUp() { // GH-90000
        EventCloudRunLedger ledger = new EventCloudRunLedger(mockEventLogStore); // GH-90000
        serviceWithLedger = new RunLedgerService(ledger); // GH-90000
        noopService = new RunLedgerService(); // no-arg = noop // GH-90000
    }

    @Nested
    @DisplayName("recordRunStarted [GH-90000]")
    class RecordRunStarted {

        @Test
        @DisplayName("appends run.started event to ledger [GH-90000]")
        void appendsRunStartedEvent() { // GH-90000
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
                .thenReturn(Promise.of(Offset.of("1 [GH-90000]")));

            runPromise(() -> serviceWithLedger.recordRunStarted( // GH-90000
                "run-1", "tenant-1", "pipeline-1", "trace-abc", Instant.now())); // GH-90000

            verify(mockEventLogStore).append(tenantCaptor.capture(), entryCaptor.capture()); // GH-90000
            assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("tenant-1 [GH-90000]");
            EventEntry entry = entryCaptor.getValue(); // GH-90000
            assertThat(entry.eventType()).isEqualTo("run.started [GH-90000]");
            assertThat(entry.headers().get("runId [GH-90000]")).isEqualTo("run-1 [GH-90000]");
            assertThat(entry.headers().get("pipelineId [GH-90000]")).isEqualTo("pipeline-1 [GH-90000]");
        }

        @Test
        @DisplayName("noop service does not call EventLogStore [GH-90000]")
        void noopServiceDoesNotCallStore() { // GH-90000
            runPromise(() -> noopService.recordRunStarted( // GH-90000
                "run-1", "tenant-1", null, null, Instant.now())); // GH-90000
            verify(mockEventLogStore, never()).append(any(), any()); // GH-90000
        }

        @Test
        @DisplayName("uses 'event' as pipelineId when null [GH-90000]")
        void usesFallbackPipelineId() { // GH-90000
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
                .thenReturn(Promise.of(Offset.of("1 [GH-90000]")));

            runPromise(() -> serviceWithLedger.recordRunStarted( // GH-90000
                "run-1", "tenant-1", null, null, Instant.now())); // GH-90000

            verify(mockEventLogStore).append(any(), entryCaptor.capture()); // GH-90000
            assertThat(entryCaptor.getValue().headers().get("pipelineId [GH-90000]")).isEqualTo("event [GH-90000]");
        }
    }

    @Nested
    @DisplayName("recordRunCompleted [GH-90000]")
    class RecordRunCompleted {

        @Test
        @DisplayName("appends run.completed event with SUCCEEDED status in JSON [GH-90000]")
        void appendsRunCompletedEvent() { // GH-90000
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
                .thenReturn(Promise.of(Offset.of("2 [GH-90000]")));
            Instant start = Instant.now().minusMillis(200); // GH-90000

            runPromise(() -> serviceWithLedger.recordRunCompleted( // GH-90000
                "run-2", "tenant-1", "pipeline-1", "trace-abc", start, 3));

            verify(mockEventLogStore).append(tenantCaptor.capture(), entryCaptor.capture()); // GH-90000
            EventEntry entry = entryCaptor.getValue(); // GH-90000
            assertThat(entry.eventType()).isEqualTo("run.completed [GH-90000]");
            String payloadJson = new String(entry.payload().array(), java.nio.charset.StandardCharsets.UTF_8); // GH-90000
            assertThat(payloadJson).contains("SUCCEEDED [GH-90000]");
            assertThat(payloadJson).contains("run-2 [GH-90000]");
        }

        @Test
        @DisplayName("noop service does not call EventLogStore [GH-90000]")
        void noopServiceDoesNotCallStore() { // GH-90000
            runPromise(() -> noopService.recordRunCompleted( // GH-90000
                "run-2", "tenant-1", null, null, Instant.now(), 0)); // GH-90000
            verify(mockEventLogStore, never()).append(any(), any()); // GH-90000
        }
    }

    @Nested
    @DisplayName("recordRunFailed [GH-90000]")
    class RecordRunFailed {

        @Test
        @DisplayName("appends run.failed event with FAILED status in JSON [GH-90000]")
        void appendsRunFailedEvent() { // GH-90000
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
                .thenReturn(Promise.of(Offset.of("3 [GH-90000]")));
            Instant start = Instant.now().minusMillis(100); // GH-90000

            runPromise(() -> serviceWithLedger.recordRunFailed( // GH-90000
                "run-3", "tenant-1", "pipeline-1", "trace-abc",
                start, "timeout", "execution timed out"));

            verify(mockEventLogStore).append(any(), entryCaptor.capture()); // GH-90000
            EventEntry entry = entryCaptor.getValue(); // GH-90000
            assertThat(entry.eventType()).isEqualTo("run.failed [GH-90000]");
            String payloadJson = new String(entry.payload().array(), java.nio.charset.StandardCharsets.UTF_8); // GH-90000
            assertThat(payloadJson).contains("FAILED [GH-90000]");
            assertThat(payloadJson).contains("timeout [GH-90000]");
        }
    }

    @Nested
    @DisplayName("error resilience [GH-90000]")
    class ErrorResilience {

        @Test
        @DisplayName("ledger errors are swallowed and do not propagate [GH-90000]")
        void ledgerErrorsDoNotPropagate() { // GH-90000
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("store failure [GH-90000]")));

            // Should complete without throwing
            runPromise(() -> serviceWithLedger.recordRunStarted( // GH-90000
                "run-err", "tenant-1", "pipeline-1", null, Instant.now())); // GH-90000
        }
    }

    @Nested
    @DisplayName("recordReviewDecision [GH-90000]")
    class RecordReviewDecision {

        @Test
        @DisplayName("appends a step-completed event with review.decision type [GH-90000]")
        void appendsStepCompletedEvent() { // GH-90000
            when(mockEventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
                .thenReturn(Promise.of(Offset.of("4 [GH-90000]")));

            runPromise(() -> serviceWithLedger.recordReviewDecision( // GH-90000
                "item-1", "run-1", "tenant-1", "skill-abc", "APPROVED", Instant.now())); // GH-90000

            verify(mockEventLogStore).append(any(), entryCaptor.capture()); // GH-90000
            String payloadJson = new String( // GH-90000
                entryCaptor.getValue().payload().array(), // GH-90000
                java.nio.charset.StandardCharsets.UTF_8);
            assertThat(payloadJson).contains("review.decision [GH-90000]");
            assertThat(payloadJson).contains("APPROVED [GH-90000]");
        }
    }
}
