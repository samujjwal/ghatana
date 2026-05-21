package com.ghatana.kernel.interaction;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Broker-scoped envelope for product interaction event publication.
 *
 * @param <Event> event payload type
 *
 * @doc.type record
 * @doc.purpose Product interaction event metadata for governed publish-subscribe delivery
 * @doc.layer kernel
 * @doc.pattern Envelope
 */
public record ProductInteractionEventEnvelope<Event>(
        String schemaVersion,
        String eventId,
        String contractId,
        String contractVersion,
        String providerProductId,
        List<String> consumerProductIds,
        String productUnitId,
        String tenantId,
        String workspaceId,
        String runId,
        String correlationId,
        Instant publishedAt,
        Map<String, String> policyContext,
        String topic,
        Event payload
) {
}
