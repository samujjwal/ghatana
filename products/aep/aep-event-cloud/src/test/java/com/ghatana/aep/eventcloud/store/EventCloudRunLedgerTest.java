/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud.store;

import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link EventCloudRunLedger}.
 */
@DisplayName("EventCloudRunLedger [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class EventCloudRunLedgerTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Captor
    private ArgumentCaptor<EventEntry> entryCaptor;

    @Captor
    private ArgumentCaptor<TenantContext> tenantCaptor;

    private EventCloudRunLedger runLedger;

    @BeforeEach
    void setUp() { // GH-90000
        runLedger = new EventCloudRunLedger(eventLogStore); // GH-90000
    }

    @Test
    void shouldRecordRunStarted() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("1 [GH-90000]")));

        byte[] payload = "{\"config\":\"test\"}".getBytes(StandardCharsets.UTF_8); // GH-90000

        // WHEN
        Offset offset = runPromise(() -> // GH-90000
            runLedger.recordRunStarted("tenant-1", "run-1", "pipeline-1", payload)); // GH-90000

        // THEN
        assertThat(offset.value()).isEqualTo("1 [GH-90000]");
        verify(eventLogStore).append(tenantCaptor.capture(), entryCaptor.capture()); // GH-90000

        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("tenant-1 [GH-90000]");

        EventEntry entry = entryCaptor.getValue(); // GH-90000
        assertThat(entry.eventType()).isEqualTo("run.started [GH-90000]");
        assertThat(entry.headers().get("runId [GH-90000]")).isEqualTo("run-1 [GH-90000]");
        assertThat(entry.headers().get("pipelineId [GH-90000]")).isEqualTo("pipeline-1 [GH-90000]");
    }

    @Test
    void shouldRecordRunCompleted() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("2 [GH-90000]")));

        // WHEN
        Offset offset = runPromise(() -> // GH-90000
            runLedger.recordRunCompleted("t1", "run-1", "p1", // GH-90000
                "{}".getBytes(StandardCharsets.UTF_8))); // GH-90000

        // THEN
        assertThat(offset).isNotNull(); // GH-90000
        verify(eventLogStore).append(any(), entryCaptor.capture()); // GH-90000
        assertThat(entryCaptor.getValue().eventType()).isEqualTo("run.completed [GH-90000]");
    }

    @Test
    void shouldRecordRunFailed() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("3 [GH-90000]")));

        // WHEN
        Offset offset = runPromise(() -> // GH-90000
            runLedger.recordRunFailed("t1", "run-2", "p1", // GH-90000
                "{\"error\":\"timeout\"}".getBytes(StandardCharsets.UTF_8))); // GH-90000

        // THEN
        assertThat(offset).isNotNull(); // GH-90000
        verify(eventLogStore).append(any(), entryCaptor.capture()); // GH-90000
        assertThat(entryCaptor.getValue().eventType()).isEqualTo("run.failed [GH-90000]");
    }

    @Test
    void shouldRecordStepCompleted() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("4 [GH-90000]")));

        // WHEN
        Offset offset = runPromise(() -> // GH-90000
            runLedger.recordStepCompleted("t1", "run-1", "p1", // GH-90000
                "{\"step\":\"transform\"}".getBytes(StandardCharsets.UTF_8))); // GH-90000

        // THEN
        assertThat(offset).isNotNull(); // GH-90000
        verify(eventLogStore).append(any(), entryCaptor.capture()); // GH-90000
        assertThat(entryCaptor.getValue().eventType()).isEqualTo("run.step.completed [GH-90000]");
    }

    @Test
    void shouldRecordCheckpoint() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("5 [GH-90000]")));

        // WHEN
        Offset offset = runPromise(() -> // GH-90000
            runLedger.recordCheckpoint("t1", "run-1", "p1", // GH-90000
                "{\"state\":\"snapshot\"}".getBytes(StandardCharsets.UTF_8))); // GH-90000

        // THEN
        assertThat(offset).isNotNull(); // GH-90000
        verify(eventLogStore).append(any(), entryCaptor.capture()); // GH-90000
        assertThat(entryCaptor.getValue().eventType()).isEqualTo("run.checkpoint [GH-90000]");
    }

    @Test
    void shouldIncludeIdempotencyKey() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("1 [GH-90000]")));

        // WHEN
        runPromise(() -> runLedger.recordRunStarted("t1", "run-abc", "p1", new byte[0])); // GH-90000

        // THEN
        verify(eventLogStore).append(any(), entryCaptor.capture()); // GH-90000
        assertThat(entryCaptor.getValue().idempotencyKey()).isPresent(); // GH-90000
        assertThat(entryCaptor.getValue().idempotencyKey().get()) // GH-90000
            .startsWith("run-abc:run.started: [GH-90000]");
    }
}
