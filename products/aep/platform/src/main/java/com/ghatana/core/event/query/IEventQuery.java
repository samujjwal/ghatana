package com.ghatana.core.event.query;


import com.ghatana.platform.domain.domain.event.Event;

/**
 * Interface for querying events in the EventCloud system.
 * Provides a flexible way to filter events based on various criteria.
 * 
 * <p>Example usage:
 * <pre>{@code
 * IEventQuery query = event -> event.getEventType().equals("user.created") && 
 *                             event.getTimestamp().isAfter(Instant.now().minus(Duration.ofHours(1)));
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Functional interface for filtering events by arbitrary criteria with composable AND/OR/NOT combinators
 * @doc.layer core
 * @doc.pattern Port
 */
public interface IEventQuery {
    
    /**
     * Determines if the given event matches the query criteria.
     *
     * @param event the event to evaluate
     * @return true if the event matches the query criteria, false otherwise
     */
    boolean matches(Event event);
    
    /**
     * Gets the polling interval for the query in milliseconds.
     * This is used for streaming queries to determine how often to check for new events.
     * 
     * @return the polling interval in milliseconds, or 0 if not applicable
     */
    default long getPollInterval() {
        return 0;
    }
    
    /**
     * Creates a new query that matches if both this query and the other query match.
     * 
     * @param other the other query to combine with
     * @return a new query representing the logical AND of both queries
     */
    default IEventQuery and(IEventQuery other) {
        return event -> this.matches(event) && other.matches(event);
    }
    
    /**
     * Creates a new query that matches if either this query or the other query matches.
     * 
     * @param other the other query to combine with
     * @return a new query representing the logical OR of both queries
     */
    default IEventQuery or(IEventQuery other) {
        return event -> this.matches(event) || other.matches(event);
    }
    
    /**
     * Creates a new query that matches if this query does not match.
     * 
     * @return a new query representing the logical NOT of this query
     */
    default IEventQuery negate() {
        return event -> !this.matches(event);
    }
    
    /**
     * Creates a query that matches all events.
     * 
     * @return a query that matches all events
     */
    static IEventQuery all() {
        return event -> true;
    }
    
    /**
     * Creates a query that matches no events.
     * 
     * @return a query that matches no events
     */
    static IEventQuery none() {
        return event -> false;
    }
}
