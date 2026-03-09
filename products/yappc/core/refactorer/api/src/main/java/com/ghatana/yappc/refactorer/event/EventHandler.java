package com.ghatana.yappc.refactorer.event;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;

/**
 * Event handler port for asynchronous event processing.
 *
 * <p>Defines contract for components that react to events with Promise-based
 * async processing. Handlers declare supported event types and process matching
 * events asynchronously.</p>
 
 * @doc.type interface
 * @doc.purpose Defines the contract for event handler
 * @doc.layer core
 * @doc.pattern Handler
*/
public interface EventHandler {

    /**
     * Handles the supplied event asynchronously.
     */
    Promise<Void> handleEvent(Event event);

    /**
     * Declares the event types supported by this handler.
     */
    String[] getSupportedEventTypes();

    /**
     * Convenience helper for checking if the handler can process the event.
     */
    default boolean canHandle(Event event) {
        if (event == null || event.getType() == null) {
            return false;
        }
        final String eventType = event.getType();
        for (String supportedType : getSupportedEventTypes()) {
            if (supportedType.equals(eventType) || supportedType.equals("*")) {
                return true;
            }
        }
        return false;
    }
}
