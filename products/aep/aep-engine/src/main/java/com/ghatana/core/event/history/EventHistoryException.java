package com.ghatana.core.event.history;

import com.ghatana.platform.core.exception.ServiceException;

/**
 * Thrown when an event history operation fails.
 *
 * @doc.type class
 * @doc.purpose Exception for event history operation failures
 * @doc.layer product
 * @doc.pattern Exception
 */
public class EventHistoryException extends ServiceException {
    public EventHistoryException(String message) {
        super(message);
    }

    public EventHistoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
