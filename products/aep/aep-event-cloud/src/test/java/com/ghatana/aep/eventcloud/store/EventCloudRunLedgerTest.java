/*
 * Copyright (c) 2026 Ghatana Inc.
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
@DisplayName("EventCloudRunLedger")
@ExtendWith(MockitoExtension.class)
class EventCloudRunLedgerTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Captor
    private ArgumentCaptor<EventEntry> entryCaptor;

    @Captor
    private ArgumentCaptor<TenantContext> tenantCaptor;

    private EventCloudRunLedger runLedger;

    @BeforeEach
    void setUp() {
        runLedger = new EventCloudRunLedger(eventLogStore);
    }

    @Test
    void shouldRecordRunStarted() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("1")));

        byte[] payload = "{\"config\":\"test\"}".getBytes(StandardCharsets.UTF_8);

        // WHEN
        Offset offset = runPromise(() ->
            runLedger.recordRunStarted("tenant-1", "run-1", "pipeline-1", payload));

        // THEN
        assertThat(offset.value()).isEqualTo("1");
        verify(eventLogStore).append(tenantCaptor.capture(), entryCaptor.capture());

        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("tenant-1");

        EventEntry entry = entryCaptor.getValue();
        assertThat(entry.eventType()).isEqualTo("run.started");
        assertThat(entry.headers().get("runId")).isEqualTo("run-1");
        assertThat(entry.headers().get("pipelineId")).isEqualTo("pipeline-1");
    }

    @Test
    void shouldRecordRunCompleted() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("2")));

        // WHEN
        Offset offset = runPromise(() ->
            runLedger.recordRunCompleted("t1", "run-1", "p1",
                "{}".getBytes(StandardCharsets.UTF_8)));

        // THEN
        assertThat(offset).isNotNull();
        verify(eventLogStore).append(any(), entryCaptor.capture());
        assertThat(entryCaptor.getValue().eventType()).isEqualTo("run.completed");
    }

    @Test
    void shouldRecordRunFailed() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("3")));

        // WHEN
        Offset offset = runPromise(() ->
            runLedger.recordRunFailed("t1", "run-2", "p1",
                "{\"error\":\"timeout\"}".getBytes(StandardCharsets.UTF_8)));

        // THEN
        assertThat(offset).isNotNull();
        verify(eventLogStore).append(any(), entryCaptor.capture());
        assertThat(entryCaptor.getValue().eventType()).isEqualTo("run.failed");
    }

    @Test
    void shouldRecordStepCompleted() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("4")));

        // WHEN
        Offset offset = runPromise(() ->
            runLedger.recordStepCompleted("t1", "run-1", "p1",
                "{\"step\":\"transform\"}".getBytes(StandardCharsets.UTF_8)));

        // THEN
        assertThat(offset).isNotNull();
        verify(eventLogStore).append(any(), entryCaptor.capture());
        assertThat(entryCaptor.getValue().eventType()).isEqualTo("run.step.completed");
    }

    @Test
    void shouldRecordCheckpoint() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("5")));

        // WHEN
        Offset offset = runPromise(() ->
            runLedger.recordCheckpoint("t1", "run-1", "p1",
                "{\"state\":\"snapshot\"}".getBytes(StandardCharsets.UTF_8)));

        // THEN
        assertThat(offset).isNotNull();
        verify(eventLogStore).append(any(), entryCaptor.capture());
        assertThat(entryCaptor.getValue().eventType()).isEqualTo("run.checkpoint");
    }

    @Test
    void shouldIncludeIdempotencyKey() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("1")));

        // WHEN
        runPromise(() -> runLedger.recordRunStarted("t1", "run-abc", "p1", new byte[0]));

        // THEN
        verify(eventLogStore).append(any(), entryCaptor.capture());
        assertThat(entryCaptor.getValue().idempotencyKey()).isPresent();
        assertThat(entryCaptor.getValue().idempotencyKey().get())
            .startsWith("run-abc:run.started:");
    }
}
