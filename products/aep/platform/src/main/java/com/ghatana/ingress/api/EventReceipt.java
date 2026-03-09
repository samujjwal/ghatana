package com.ghatana.ingress.api;

/**
 * Minimal event receipt used for idempotency tests.
 
 *
 * @doc.type class
 * @doc.purpose Event receipt
 * @doc.layer core
 * @doc.pattern Component
*/
public class EventReceipt {
    private final String eventId;
    private final boolean duplicate;

    public EventReceipt(String eventId, boolean duplicate) {
        this.eventId = eventId;
        this.duplicate = duplicate;
    }

    public String getEventId() {
        return eventId;
    }

    public boolean isDuplicate() {
        return duplicate;
    }
}
