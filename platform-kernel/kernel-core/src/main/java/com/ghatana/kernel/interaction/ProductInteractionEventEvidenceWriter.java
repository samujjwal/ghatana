package com.ghatana.kernel.interaction;

import io.activej.promise.Promise;

/**
 * Evidence persistence port for product interaction event publication.
 *
 * @doc.type interface
 * @doc.purpose Persist product interaction event publication evidence
 * @doc.layer kernel
 * @doc.pattern Port
 */
@FunctionalInterface
public interface ProductInteractionEventEvidenceWriter {

    Promise<Void> write(ProductInteractionEventEnvelope<?> envelope, ProductInteractionEventOutcome outcome);

    static ProductInteractionEventEvidenceWriter noop() {
        return (envelope, outcome) -> Promise.complete();
    }
}
