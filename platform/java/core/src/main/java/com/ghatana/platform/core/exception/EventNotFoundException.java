package com.ghatana.platform.core.exception;

/**
 * Exception thrown when an event cannot be found.
 * 
 * <p>This is a specialized form of {@link ResourceNotFoundException}
 * specifically for event lookups.</p>
 *
 * @doc.type exception
 * @doc.purpose Specialized exception for event not found scenarios
 * @doc.layer core
 * @doc.pattern Exception, Specialization
 */
public class EventNotFoundException extends ResourceNotFoundException {
    
    private static final long serialVersionUID = 1L;
    
    public EventNotFoundException(String message) {
        super(message);
    }

    public EventNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Creates an exception with a formatted message for a specific event ID.
     * 
     * @param eventId The ID of the event that was not found
     * @return A new EventNotFoundException with formatted message
     */
    public static EventNotFoundException forEventId(String eventId) {
        return new EventNotFoundException("Event not found: " + eventId);
    }
}

