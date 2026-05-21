package com.ghatana.kernel.interaction;

import java.time.Instant;
import java.util.Map;

/**
 * Broker-scoped request envelope for product-to-product interaction handlers.
 *
 * @param <Req> request payload type
 *
 * @doc.type record
 * @doc.purpose Product interaction request metadata for bridge handlers
 * @doc.layer kernel
 * @doc.pattern Envelope
 */
public record ProductInteractionRequest<Req>(
        String schemaVersion,
        String interactionId,
        String contractId,
        String contractVersion,
        String providerProductId,
        String consumerProductId,
        String productUnitId,
        String tenantId,
        String workspaceId,
        String runId,
        String correlationId,
        Instant requestedAt,
        Map<String, String> policyContext,
        Req payload
) {
}
