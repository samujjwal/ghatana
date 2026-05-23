package com.ghatana.kernel.timeline;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Kernel timeline event producer for trace and evidence linking.
 *
 * <p>Produces timeline events for lifecycle phases, linking traces to evidence,
 * and maintaining a chronological record of all kernel-level operations for
 * observability, debugging, and compliance auditing.</p>
 *
 * @doc.type class
 * @doc.purpose Produce timeline events with trace/evidence linking
 * @doc.layer platform
 * @doc.pattern Producer
 */
public class KernelTimelineEventProducer {

    private static final Logger logger = LoggerFactory.getLogger(KernelTimelineEventProducer.class);

    private final TimelineEventStore eventStore;
    private final TraceLinker traceLinker;

    public KernelTimelineEventProducer(
        TimelineEventStore eventStore,
        TraceLinker traceLinker
    ) {
        this.eventStore = eventStore;
        this.traceLinker = traceLinker;
    }

    /**
     * Produce a timeline event for a lifecycle phase.
     *
     * @param productId The product ID
     * @param phase The lifecycle phase
     * @param traceId The trace ID for linking
     * @param metadata Additional event metadata
     * @return Promise containing the event ID
     */
    public Promise<String> produceLifecycleEvent(
        String productId,
        String phase,
        String traceId,
        Map<String, Object> metadata
    ) {
        logger.info("Producing lifecycle event for product: {}, phase: {}, trace: {}", productId, phase, traceId);

        TimelineEvent event = new TimelineEvent(
            UUID.randomUUID().toString(),
            productId,
            EventType.LIFECYCLE_PHASE,
            phase,
            Instant.now(),
            traceId,
            metadata
        );

        return eventStore.store(event)
            .thenMap(eventId -> {
                // Link trace to evidence
                traceLinker.linkToEvidence(traceId, eventId, "lifecycle_phase", phase);
                return Promise.of(eventId);
            });
    }

    /**
     * Produce a timeline event for evidence collection.
     *
     * @param productId The product ID
     * @param evidenceType The evidence type
     * @param traceId The trace ID for linking
     * @param evidenceRef The evidence reference
     * @return Promise containing the event ID
     */
    public Promise<String> produceEvidenceEvent(
        String productId,
        String evidenceType,
        String traceId,
        String evidenceRef
    ) {
        logger.info("Producing evidence event for product: {}, type: {}, trace: {}", productId, evidenceType, traceId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("evidenceRef", evidenceRef);
        metadata.put("evidenceType", evidenceType);

        TimelineEvent event = new TimelineEvent(
            UUID.randomUUID().toString(),
            productId,
            EventType.EVIDENCE_COLLECTION,
            evidenceType,
            Instant.now(),
            traceId,
            metadata
        );

        return eventStore.store(event)
            .thenMap(eventId -> {
                // Link trace to evidence
                traceLinker.linkToEvidence(traceId, eventId, "evidence", evidenceRef);
                return Promise.of(eventId);
            });
    }

    /**
     * Produce a timeline event for a gate validation.
     *
     * @param productId The product ID
     * @param gateName The gate name
     * @param traceId The trace ID for linking
     * @param gateStatus The gate status
     * @return Promise containing the event ID
     */
    public Promise<String> produceGateEvent(
        String productId,
        String gateName,
        String traceId,
        String gateStatus
    ) {
        logger.info("Producing gate event for product: {}, gate: {}, status: {}, trace: {}", productId, gateName, gateStatus, traceId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("gateName", gateName);
        metadata.put("gateStatus", gateStatus);

        TimelineEvent event = new TimelineEvent(
            UUID.randomUUID().toString(),
            productId,
            EventType.GATE_VALIDATION,
            gateName,
            Instant.now(),
            traceId,
            metadata
        );

        return eventStore.store(event)
            .thenMap(eventId -> {
                // Link trace to evidence
                traceLinker.linkToEvidence(traceId, eventId, "gate", gateName);
                return Promise.of(eventId);
            });
    }

    /**
     * Produce a timeline event for asset promotion.
     *
     * @param productId The product ID
     * @param assetId The asset ID
     * @param traceId The trace ID for linking
     * @param promotionStatus The promotion status
     * @return Promise containing the event ID
     */
    public Promise<String> producePromotionEvent(
        String productId,
        String assetId,
        String traceId,
        String promotionStatus
    ) {
        logger.info("Producing promotion event for product: {}, asset: {}, status: {}, trace: {}", productId, assetId, promotionStatus, traceId);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("assetId", assetId);
        metadata.put("promotionStatus", promotionStatus);

        TimelineEvent event = new TimelineEvent(
            UUID.randomUUID().toString(),
            productId,
            EventType.ASSET_PROMOTION,
            assetId,
            Instant.now(),
            traceId,
            metadata
        );

        return eventStore.store(event)
            .thenMap(eventId -> {
                // Link trace to evidence
                traceLinker.linkToEvidence(traceId, eventId, "promotion", assetId);
                return Promise.of(eventId);
            });
    }

    /**
     * Timeline event record.
     */
    public record TimelineEvent(
        String eventId,
        String productId,
        EventType eventType,
        String eventSubtype,
        Instant timestamp,
        String traceId,
        Map<String, Object> metadata
    ) {}

    /**
     * Event type enum.
     */
    public enum EventType {
        LIFECYCLE_PHASE,
        EVIDENCE_COLLECTION,
        GATE_VALIDATION,
        ASSET_PROMOTION,
        TENANT_CONTEXT_CHANGE,
        SECURITY_EVENT,
        ERROR_EVENT
    }

    /**
     * Timeline event store interface.
     */
    public interface TimelineEventStore {
        Promise<String> store(TimelineEvent event);
    }

    /**
     * Trace linker interface for linking traces to evidence.
     */
    public interface TraceLinker {
        void linkToEvidence(String traceId, String eventId, String evidenceType, String evidenceRef);
    }
}
