package com.ghatana.datacloud.storage;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Kernel lifecycle event ingestion")
class KernelLifecycleEventIngestionServiceTest extends EventloopTestBase {

    private KernelLifecycleEventIngestionService service;
    private KernelLifecycleEventIngestionService.LifecycleScope tenantA;
    private KernelLifecycleEventIngestionService.LifecycleScope tenantB;

    @BeforeEach
    void setUp() {
        service = new KernelLifecycleEventIngestionService(new InMemoryEventLogStore());
        tenantA = new KernelLifecycleEventIngestionService.LifecycleScope("tenant-a", "workspace-1");
        tenantB = new KernelLifecycleEventIngestionService.LifecycleScope("tenant-b", "workspace-1");
    }

    @Test
    @DisplayName("lifecycle event round trips with correlation and run IDs preserved")
    void lifecycleEventRoundTripPreservesCorrelationAndRunIds() {
        var event = event("event-1", tenantA, "product-1", "run-1", 1, "corr-1");

        var result = runPromise(() -> service.ingestLifecycleEvent(event));
        var events = runPromise(() -> service.getLifecycleEvents(tenantA, "run-1"));

        assertThat(result.accepted()).isTrue();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventId()).isEqualTo("event-1");
        assertThat(events.get(0).runId()).isEqualTo("run-1");
        assertThat(events.get(0).correlationId()).contains("corr-1");
    }

    @Test
    @DisplayName("duplicate event IDs are idempotent and stale run sequence is rejected")
    void duplicateAndStaleEventsAreHandled() {
        var first = event("event-1", tenantA, "product-1", "run-1", 2, "corr-1");
        var duplicate = event("event-1", tenantA, "product-1", "run-1", 2, "corr-1");
        var stale = event("event-2", tenantA, "product-1", "run-1", 1, "corr-1");

        assertThat(runPromise(() -> service.ingestLifecycleEvent(first)).accepted()).isTrue();
        var duplicateResult = runPromise(() -> service.ingestLifecycleEvent(duplicate));
        var staleResult = runPromise(() -> service.ingestLifecycleEvent(stale));

        assertThat(duplicateResult.duplicate()).isTrue();
        assertThat(staleResult.accepted()).isFalse();
        assertThat(staleResult.reason()).isEqualTo("stale-event");
        assertThat(runPromise(() -> service.getLifecycleEvents(tenantA, "run-1"))).hasSize(1);
    }

    @Test
    @DisplayName("artifact reference round trips and links event, deployment, and health refs")
    void artifactReferenceRoundTripLinksRuntimeRefs() {
        var event = event("event-1", tenantA, "product-1", "run-1", 1, "corr-1");
        runPromise(() -> service.ingestLifecycleEvent(event));
        var reference = new KernelLifecycleEventIngestionService.ArtifactReference(
            tenantA,
            "artifact-ref-1",
            "artifact-1",
            "product-1",
            "run-1",
            "static-web-bundle",
            "artifact://bundle",
            Optional.of("event-1"),
            Optional.of("deployment-1"),
            Optional.of("health-1"),
            Instant.parse("2026-01-01T00:01:00.000Z"),
            Map.of(
                "artifactId", "artifact-1",
                "productUnitId", "product-1",
                "runId", "run-1",
                "kind", "static-web-bundle",
                "uri", "artifact://bundle",
                "deploymentId", "deployment-1",
                "healthRef", "health-1"));

        var result = runPromise(() -> service.recordArtifactReference(reference));
        var byRun = runPromise(() -> service.getArtifactReferencesByRun(tenantA, "run-1"));
        var lineage = runPromise(() -> service.getArtifactLineage(tenantA, "artifact-1"));

        assertThat(result.accepted()).isTrue();
        assertThat(byRun).hasSize(1);
        assertThat(byRun.get(0).eventId()).contains("event-1");
        assertThat(byRun.get(0).deploymentId()).contains("deployment-1");
        assertThat(byRun.get(0).healthRef()).contains("health-1");
        assertThat(lineage).hasSize(1);
    }

    @Test
    @DisplayName("tenant A cannot read tenant B events")
    void tenantIsolationPreventsCrossTenantRead() {
        runPromise(() -> service.ingestLifecycleEvent(event("event-a", tenantA, "product-1", "run-a", 1, "corr-a")));
        runPromise(() -> service.ingestLifecycleEvent(event("event-b", tenantB, "product-1", "run-b", 1, "corr-b")));

        assertThat(runPromise(() -> service.getLifecycleEvents(tenantA, "run-b"))).isEmpty();
        assertThat(runPromise(() -> service.getLifecycleEvents(tenantB, "run-b"))).hasSize(1);
    }

    @Test
    @DisplayName("malformed events are rejected")
    void malformedEventRejected() {
        var malformed = event("event-bad", tenantA, "", "run-1", 1, "corr-1");

        assertThatThrownBy(() -> runPromise(() -> service.ingestLifecycleEvent(malformed)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("productUnitId");
    }

    @Test
    @DisplayName("product lifecycle timeline is sorted deterministically")
    void timelineSortedDeterministically() {
        runPromise(() -> service.ingestLifecycleEvent(eventAt("event-2", tenantA, "product-1", "run-2", 2, "2026-01-01T00:00:00.000Z")));
        runPromise(() -> service.ingestLifecycleEvent(eventAt("event-1", tenantA, "product-1", "run-1", 1, "2026-01-01T00:00:00.000Z")));
        runPromise(() -> service.ingestLifecycleEvent(eventAt("event-3", tenantA, "product-1", "run-3", 3, "2026-01-01T00:02:00.000Z")));

        var timeline = runPromise(() -> service.getProductLifecycleTimeline(tenantA, "product-1"));

        assertThat(timeline).extracting(KernelLifecycleEventIngestionService.LifecycleEvent::eventId)
            .containsExactly("event-1", "event-2", "event-3");
    }

    private static KernelLifecycleEventIngestionService.LifecycleEvent event(
            String eventId,
            KernelLifecycleEventIngestionService.LifecycleScope scope,
            String productUnitId,
            String runId,
            long sequence,
            String correlationId) {
        return eventAt(eventId, scope, productUnitId, runId, sequence, "2026-01-01T00:00:00.000Z", correlationId);
    }

    private static KernelLifecycleEventIngestionService.LifecycleEvent eventAt(
            String eventId,
            KernelLifecycleEventIngestionService.LifecycleScope scope,
            String productUnitId,
            String runId,
            long sequence,
            String occurredAt) {
        return eventAt(eventId, scope, productUnitId, runId, sequence, occurredAt, "corr-" + eventId);
    }

    private static KernelLifecycleEventIngestionService.LifecycleEvent eventAt(
            String eventId,
            KernelLifecycleEventIngestionService.LifecycleScope scope,
            String productUnitId,
            String runId,
            long sequence,
            String occurredAt,
            String correlationId) {
        return new KernelLifecycleEventIngestionService.LifecycleEvent(
            scope,
            eventId,
            "1.0.0",
            productUnitId,
            runId,
            "build",
            "phase.completed",
            sequence,
            Instant.parse(occurredAt),
            Optional.of(correlationId),
            Map.of(
                "productUnitId", productUnitId,
                "runId", runId,
                "phase", "build",
                "eventName", "phase.completed",
                "sequence", sequence));
    }
}
