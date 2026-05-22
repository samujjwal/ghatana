package com.ghatana.kernel.plugin;

import io.activej.promise.Promise;

/**
 * Handler for plugin interaction events.
 *
 * <p>This interface mirrors {@link com.ghatana.kernel.interaction.ProductInteractionHandler}
 * but for plugin interactions. Handlers implement specific contract logic for processing
 * plugin-to-plugin events.</p>
 *
 * @doc.type interface
 * @doc.purpose Handler contract for plugin interaction event processing
 * @doc.layer kernel
 * @doc.pattern Handler
 */
public interface PluginInteractionHandler<Request, Response> {

    /**
     * Gets the contract ID this handler implements.
     *
     * @return the contract ID
     */
    String contractId();

    /**
     * Gets the request type.
     *
     * @return the request class
     */
    Class<Request> requestType();

    /**
     * Gets the response type.
     *
     * @return the response class
     */
    Class<Response> responseType();

    /**
     * Handles a plugin interaction request.
     *
     * @param request the interaction request
     * @return a Promise resolving to the interaction outcome
     */
    Promise<PluginInteractionOutcome<Response>> handle(PluginInteractionRequest<Request> request);
}
