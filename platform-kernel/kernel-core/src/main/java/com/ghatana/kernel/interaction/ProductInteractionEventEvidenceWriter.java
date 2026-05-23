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

    /**
     * Returns true if this is a no-op evidence writer that does not persist evidence.
     * No-op writers are only allowed in test mode.
     *
     * @return true if this is a no-op writer
     */
    default boolean isNoop() {
        return false;
    }

    static ProductInteractionEventEvidenceWriter noop() {
        return new NoopEventEvidenceWriter();
    }

    /**
     * No-op implementation that does not persist evidence.
     * Only for use in test mode.
     */
    class NoopEventEvidenceWriter implements ProductInteractionEventEvidenceWriter {
        @Override
        public Promise<Void> write(ProductInteractionEventEnvelope<?> envelope, ProductInteractionEventOutcome outcome) {
            return Promise.complete();
        }

        @Override
        public boolean isNoop() {
            return true;
        }
    }
}
