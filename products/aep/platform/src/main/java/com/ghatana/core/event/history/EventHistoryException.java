package com.ghatana.core.event.history;

public class EventHistoryException extends RuntimeException {
    public EventHistoryException(String message) {
        super(message);
    }

    public EventHistoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
