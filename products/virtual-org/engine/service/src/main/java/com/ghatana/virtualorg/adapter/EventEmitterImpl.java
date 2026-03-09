package com.ghatana.virtualorg.adapter;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Default event emitter implementation with async delivery and listener support.
 *
 * <p><b>Purpose</b><br>
 * Concrete implementation of EventEmitter providing asynchronous event delivery with
 * multiple listener support for testing, monitoring, and debugging. Events are dispatched
 * on ActiveJ Eventloop to avoid blocking agent task processing.
 *
 * <p><b>Architecture Role</b><br>
 * Adapter implementation in hexagonal architecture:
 * <ul>
 *   <li>Implements EventEmitter port for event publishing</li>
 *   <li>Delegates to registered listeners (testing, monitoring, EventCloud client)</li>
 *   <li>Uses ActiveJ Eventloop for non-blocking async dispatch</li>
 *   <li>Provides health monitoring for circuit breaker patterns</li>
 * </ul>
 *
 * <p><b>Features</b><br>
 * Key capabilities:
 * <ul>
 *   <li>Fire-and-forget: {@link #emit(Event)} queues event without blocking</li>
 *   <li>Guaranteed delivery: {@link #emitAsync(Event)} returns Promise for tracking</li>
 *   <li>Multiple listeners: Test harnesses, metrics collectors, audit loggers</li>
 *   <li>Async dispatch: Events dispatched on eventloop, never block caller</li>
 *   <li>Health monitoring: {@link #isHealthy()} for circuit breaker integration</li>
 *   <li>Listener management: {@link #addListener(Consumer)}, {@link #removeListener(Consumer)}</li>
 * </ul>
 *
 * <p><b>Delivery Semantics</b><br>
 * Fire-and-forget (emit):
 * <ul>
 *   <li>Queues event on eventloop immediately</li>
 *   <li>Returns without waiting for delivery</li>
 *   <li>Errors logged but not propagated to caller</li>
 *   <li>Dropped if emitter unhealthy</li>
 * </ul>
 *
 * Guaranteed delivery (emitAsync):
 * <ul>
 *   <li>Returns Promise that completes after all listeners notified</li>
 *   <li>Caller can wait for delivery confirmation</li>
 *   <li>Errors propagated via Promise failure</li>
 *   <li>Rejected if emitter unhealthy</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Setup
 * Eventloop eventloop = Eventloop.getCurrentEventloop();
 * EventEmitterImpl emitter = new EventEmitterImpl(eventloop);
 * 
 * // Add listener for EventCloud client
 * emitter.addListener(event -> {
 *     eventCloudClient.append(event);
 * });
 * 
 * // Add listener for metrics
 * emitter.addListener(event -> {
 *     metrics.incrementCounter("events.emitted",
 *         "type", event.getType());
 * });
 * 
 * // Fire-and-forget emission
 * emitter.emit(taskStartedEvent);
 * 
 * // Guaranteed delivery
 * emitter.emitAsync(criticalDecisionEvent)
 *     .whenComplete(() -> log.info("Event delivered"))
 *     .whenException(ex -> log.error("Delivery failed", ex));
 * 
 * // Health check before critical emission
 * if (emitter.isHealthy()) {
 *     emitter.emit(event);
 * } else {
 *     log.warn("Emitter unhealthy, queueing for retry");
 * }
 * 
 * // Cleanup
 * emitter.removeListener(listener);
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe using CopyOnWriteArrayList for listeners and AtomicBoolean for health status.
 * All event dispatch occurs on single-threaded Eventloop.
 *
 * @see EventEmitter
 * @see VirtualOrgEventFactory
 * @doc.type class
 * @doc.purpose Event emitter with async delivery and listener support
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class EventEmitterImpl implements EventEmitter {
    private static final Logger log = LoggerFactory.getLogger(EventEmitterImpl.class);
    
    private final Eventloop eventloop;
    private final CopyOnWriteArrayList<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isHealthy = new AtomicBoolean(true);
    
    public EventEmitterImpl(Eventloop eventloop) {
        this.eventloop = eventloop;
    }
    
    @Override
    public void emit(Event event) {
        // Fire-and-forget: emit asynchronously without blocking
        if (!isHealthy.get()) {
            log.warn("EventEmitter is not healthy, dropping event: {}", event.getType());
            return;
        }
        
        try {
            eventloop.execute(() -> {
                try {
                    notifyListeners(event);
                    log.debug("Event emitted: type={}, correlationId={}",
                        event.getType(), event.getCorrelationId());
                } catch (Exception e) {
                    log.error("Failed to emit event: {}", event.getType(), e);
                }
            });
        } catch (Exception e) {
            log.error("Failed to schedule event emission: {}", event.getType(), e);
        }
    }
    
    @Override
    public Promise<Void> emitAsync(Event event) {
        if (!isHealthy.get()) {
            log.warn("EventEmitter is not healthy, rejecting event: {}", event.getType());
            return Promise.ofException(new IllegalStateException("EventEmitter is not healthy"));
        }
        
        return Promise.complete().then(() -> {
            try {
                notifyListeners(event);
                log.debug("Event emitted (async): type={}, correlationId={}",
                    event.getType(), event.getCorrelationId());
                return Promise.complete();
            } catch (Exception e) {
                log.error("Failed to emit event (async): {}", event.getType(), e);
                return Promise.ofException(e);
            }
        });
    }
    
    @Override
    public boolean isHealthy() {
        return isHealthy.get();
    }
    
    /**
     * Registers a listener to receive events.
     * Used for testing and monitoring purposes.
     */
    public void addListener(Consumer<Event> listener) {
        listeners.add(listener);
    }
    
    /**
     * Removes a previously registered listener.
     */
    public void removeListener(Consumer<Event> listener) {
        listeners.remove(listener);
    }
    
    /**
     * Gets the current listener count.
     */
    public int getListenerCount() {
        return listeners.size();
    }
    
    /**
     * Marks the emitter as unhealthy (e.g., after connection failure).
     */
    public void setUnhealthy() {
        isHealthy.set(false);
        log.warn("EventEmitter marked as unhealthy");
    }
    
    /**
     * Marks the emitter as healthy again.
     */
    public void setHealthy() {
        isHealthy.set(true);
        log.info("EventEmitter marked as healthy");
    }
    
    private void notifyListeners(Event event) {
        for (Consumer<Event> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.warn("Listener failed to process event: {}", event.getType(), e);
            }
        }
    }
}