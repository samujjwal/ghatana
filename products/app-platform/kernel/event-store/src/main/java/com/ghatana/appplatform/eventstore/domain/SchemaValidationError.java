package com.ghatana.appplatform.eventstore.domain;

/**
 * Thrown (as a promise failure) when an event's payload fails JSON schema validation.
 *
 * <p>Consumers appending events to a {@code ValidatingAggregateEventStore} receive this
 * exception through the {@code Promise} if the event data does not conform to the currently
 * ACTIVE schema for the event type.
 *
 * @doc.type class
 * @doc.purpose Domain exception for event schema validation failures
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class SchemaValidationError extends RuntimeException {

    private final String eventType;
    private final int schemaVersion;

    public SchemaValidationError(String eventType, int schemaVersion, String validationMessage) {
        super("Event data does not conform to schema eventType=" + eventType
            + " version=" + schemaVersion + ": " + validationMessage);
        this.eventType     = eventType;
        this.schemaVersion = schemaVersion;
    }

    public String eventType()    { return eventType; }
    public int schemaVersion()   { return schemaVersion; }
}
