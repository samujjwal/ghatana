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

    /**
     * Returns true if this is a no-op evidence writer that does not persist evidence.
     * No-op writers are only allowed in test mode.
     *
     * @return true if this is a no-op writer
     */
    default boolean isNoop() {
        return false;
    }

    static ProductInteractionEvidenceWriter noop() {
        return new NoopEvidenceWriter();
    }

    /**
     * No-op implementation that does not persist evidence.
     * Only for use in test mode.
     */
    class NoopEvidenceWriter implements ProductInteractionEvidenceWriter {
        @Override
        public Promise<Void> write(ProductInteractionRequest<?> request, ProductInteractionOutcome<?> outcome) {
            return Promise.complete();
        }

        @Override
        public boolean isNoop() {
            return true;
        }
    }
}
