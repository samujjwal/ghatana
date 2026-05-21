package com.ghatana.kernel.interaction;

import io.activej.promise.Promise;

/**
 * Evidence sink used by the broker after each product interaction reaches a terminal outcome.
 *
 * @doc.type interface
 * @doc.purpose Persist auditable product interaction evidence without coupling Kernel to a storage product
 * @doc.layer kernel
 * @doc.pattern Port
 */
@FunctionalInterface
public interface ProductInteractionEvidenceWriter {

    Promise<Void> write(ProductInteractionRequest<?> request, ProductInteractionOutcome<?> outcome);

    static ProductInteractionEvidenceWriter noop() {
        return (request, outcome) -> Promise.complete();
    }
}
