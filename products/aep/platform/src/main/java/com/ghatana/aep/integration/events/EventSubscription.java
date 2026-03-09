package com.ghatana.aep.integration.events;

import io.activej.promise.Promise;

/**
 * Represents an active event subscription.
 * 
 * <p>Subscriptions track the state of event consumption and allow
 * for graceful cancellation.</p>
 * 
 * @doc.type interface
 * @doc.purpose Event subscription management
 * @doc.layer infrastructure
 * @doc.pattern Port
 */
public interface EventSubscription {
    
    /**
     * Returns the unique subscription ID.
     */
    String getSubscriptionId();
    
    /**
     * Returns the event type(s) this subscription is listening to.
     */
    String[] getEventTypes();
    
    /**
     * Returns true if the subscription is active.
     */
    boolean isActive();
    
    /**
     * Returns the number of events processed by this subscription.
     */
    long getEventsProcessed();
    
    /**
     * Returns the number of events that failed processing.
     */
    long getEventsFailed();
    
    /**
     * Returns the last processed event ID (for resume capability).
     */
    String getLastProcessedEventId();
    
    /**
     * Pauses the subscription (stops receiving new events).
     * 
     * @return A promise that completes when paused
     */
    Promise<Void> pause();
    
    /**
     * Resumes a paused subscription.
     * 
     * @return A promise that completes when resumed
     */
    Promise<Void> resume();
    
    /**
     * Cancels the subscription permanently.
     * 
     * @return A promise that completes when cancelled
     */
    Promise<Void> cancel();
}
