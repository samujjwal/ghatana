package com.ghatana.security.alert;

import com.ghatana.security.metrics.SecurityMetrics;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages security alerts and notifications.
 */
public class SecurityAlertManager {
    private static final Logger logger = LoggerFactory.getLogger(SecurityAlertManager.class);
    
    private final Eventloop eventloop;
    private final SecurityMetrics metrics;
    private final List<SecurityAlertHandler> handlers = new ArrayList<>();
    private final Map<String, AtomicLong> alertCounts = new ConcurrentHashMap<>();
    private final Duration alertAggregationWindow = Duration.ofMinutes(5);
    private final int maxAlertsPerWindow = 100;
    
    /**
     * Creates a new AlertManager.
     *
     * @param eventloop The event loop to use for scheduling tasks
     * @param metrics The metrics instance to record alert statistics
     */
    public SecurityAlertManager(Eventloop eventloop, SecurityMetrics metrics) {
        this.eventloop = eventloop;
        this.metrics = metrics;
        scheduleAlertAggregationReset();
    }
    
    /**
     * Adds an alert handler to process alerts.
     *
     * @param handler The handler to add
     * @return This AlertManager instance for method chaining
     */
    public SecurityAlertManager addHandler(SecurityAlertHandler handler) {
        handlers.add(handler);
        return this;
    }
    
    /**
     * Handles a security alert by dispatching it to all registered handlers.
     *
     * @param alert The alert to handle
     * @return A promise that completes when all handlers have processed the alert
     */
    public Promise<Void> handleAlert(SecurityAlert alert) {
        // Update alert counts and check rate limits
        if (isRateLimited(alert.getType())) {
            recordDroppedAlert(alert);
            return Promise.complete();
        }
        
        recordAlert(alert);
        
        // If no handlers, return completed promise
        if (handlers.isEmpty()) {
            return Promise.complete();
        }
        
        // Dispatch to all handlers in parallel using a list of promises
        List<Promise<Void>> handlerPromises = new ArrayList<>(handlers.size());
        for (SecurityAlertHandler handler : handlers) {
            try {
                Promise<Void> handlerPromise = handler.handle(alert)
                        .whenComplete((result, e) -> {
                            if (e != null) {
                                logger.error("Error in alert handler: {}", handler.getClass().getSimpleName(), e);
                                metrics.recordAlertHandlerError(handler.getClass().getSimpleName());
                            } else {
                                logger.debug("Alert processed by handler: {}", handler.getClass().getSimpleName());
                            }
                        });
                handlerPromises.add(handlerPromise);
            } catch (Exception e) {
                logger.error("Error dispatching alert to handler: {}", handler.getClass().getSimpleName(), e);
                metrics.recordAlertHandlerError(handler.getClass().getSimpleName());
                handlerPromises.add(Promise.ofException(e));
            }
        }
        
        // Wait for all handlers to complete
        if (handlerPromises.isEmpty()) {
            return Promise.complete();
        }
        
        // Use Promise.all with List<Promise<Void>>
        return Promises.all(handlerPromises).toVoid();
    }
    
    /**
     * Checks if alerts of the given type are being rate limited.
     *
     * @param alertType The type of alert to check
     * @return true if rate limited, false otherwise
     */
    private boolean isRateLimited(String alertType) {
        // Simple rate limiting by counting alerts per type in the current window
        long count = alertCounts.computeIfAbsent(alertType, k -> new AtomicLong())
                .incrementAndGet();
        
        // Don't record the alert here, it will be recorded in handleAlert
        return count > maxAlertsPerWindow;
    }
    
    /**
     * Records a dropped alert due to rate limiting.
     *
     * @param alert The alert that was dropped
     */
    private void recordDroppedAlert(SecurityAlert alert) {
        metrics.recordDroppedAlert(alert);
        logger.warn("Alert rate limit exceeded for type: {}", alert.getType());
    }
    
    /**
     * Records an alert in metrics.
     *
     * @param alert The alert to record
     */
    private void recordAlert(SecurityAlert alert) {
        metrics.recordAlert(alert);
    }
    
    /**
     * Schedules periodic reset of alert counts.
     */
    private void scheduleAlertAggregationReset() {
        eventloop.delay(alertAggregationWindow, () -> {
            alertCounts.clear();
            scheduleAlertAggregationReset();
        });
    }
    
    /**
     * Interface for alert handlers.
     
 *
 * @doc.type interface
 * @doc.purpose Security alert handler
 * @doc.layer core
 * @doc.pattern Handler
*/
    public interface SecurityAlertHandler {
        /**
         * Handles an alert.
         *
         * @param alert The alert to handle
         * @return A promise that completes when the alert has been handled
         */
        Promise<Void> handle(SecurityAlert alert);
    }
}
