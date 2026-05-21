package com.ghatana.kernel.interaction;

import java.time.Instant;
import java.util.List;

/**
 * Canonical outcome returned by product interaction event publication.
 *
 * @doc.type record
 * @doc.purpose Product interaction event publication outcome with evidence references
 * @doc.layer kernel
 * @doc.pattern Result
 */
public record ProductInteractionEventOutcome(
        String schemaVersion,
        String eventId,
        ProductInteractionStatus status,
        String reasonCode,
        List<String> evidenceRefs,
        List<String> deliveredSubscriberIds,
        Instant completedAt
) {
    public static ProductInteractionEventOutcome succeeded(
            String eventId,
            List<String> evidenceRefs,
            List<String> deliveredSubscriberIds) {
        return new ProductInteractionEventOutcome(
                "1.0.0",
                eventId,
                ProductInteractionStatus.SUCCEEDED,
                null,
                List.copyOf(evidenceRefs),
                List.copyOf(deliveredSubscriberIds),
                Instant.now());
    }

    public static ProductInteractionEventOutcome blocked(
            String eventId,
            String reasonCode,
            List<String> evidenceRefs) {
        return new ProductInteractionEventOutcome(
                "1.0.0",
                eventId == null ? "unknown" : eventId,
                ProductInteractionStatus.BLOCKED,
                reasonCode,
                List.copyOf(evidenceRefs),
                List.of(),
                Instant.now());
    }
}
