package com.ghatana.kernel.plugin;

import io.activej.promise.Promise;

/**
 * Subscriber for plugin interaction events.
 *
 * <p>Subscribers receive plugin interaction events and process them according to their
 * contract logic. They are registered with the PluginInteractionEventBroker for specific
 * contract IDs.</p>
 *
 * @doc.type interface
 * @doc.purpose Subscriber contract for plugin interaction event processing
 * @doc.layer kernel
 * @doc.pattern Observer
 */
@FunctionalInterface
public interface PluginInteractionSubscriber {

    /**
     * Handles a plugin interaction event.
     *
     * @param envelope the event envelope
     * @return a Promise that completes when handling is done
     */
    Promise<Void> handle(PluginInteractionEventEnvelope<?> envelope);
}
