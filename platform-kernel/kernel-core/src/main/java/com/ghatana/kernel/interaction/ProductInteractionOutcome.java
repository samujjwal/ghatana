package com.ghatana.kernel.interaction;

import java.time.Instant;
import java.util.List;

/**
 * Canonical response envelope returned by product interaction handlers.
 *
 * @param <Res> response payload type
 *
 * @doc.type record
 * @doc.purpose Product interaction handler outcome with evidence references
 * @doc.layer kernel
 * @doc.pattern Result
 */
public record ProductInteractionOutcome<Res>(
        String schemaVersion,
        String interactionId,
        ProductInteractionStatus status,
        String reasonCode,
        List<String> evidenceRefs,
        List<String> provenanceRefs,
        Instant completedAt,
        Res payload
) {
    public static <Res> ProductInteractionOutcome<Res> succeeded(
            String interactionId,
            List<String> evidenceRefs,
            Res payload) {
        return new ProductInteractionOutcome<>(
                "1.0.0",
                interactionId,
                ProductInteractionStatus.SUCCEEDED,
                null,
                List.copyOf(evidenceRefs),
                List.of(),
                Instant.now(),
                payload);
    }

    public static <Res> ProductInteractionOutcome<Res> failed(
            String interactionId,
            ProductInteractionStatus status,
            String reasonCode,
            List<String> evidenceRefs) {
        return new ProductInteractionOutcome<>(
                "1.0.0",
                interactionId,
                status,
                reasonCode,
                List.copyOf(evidenceRefs),
                List.of(),
                Instant.now(),
                null);
    }
}
