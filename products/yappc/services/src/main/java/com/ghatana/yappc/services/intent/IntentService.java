package com.ghatana.yappc.services.intent;

import io.activej.promise.Promise;

/**
 * Service for managing software intents within the YAPPC system.
 *
 * @doc.type interface
 * @doc.purpose Contract for intent management operations
 * @doc.layer product
 * @doc.pattern Service
 */
public interface IntentService {

    /**
     * Counts the total number of intents.
     *
     * @return promise of the total intent count
     */
    Promise<Long> count();
}
