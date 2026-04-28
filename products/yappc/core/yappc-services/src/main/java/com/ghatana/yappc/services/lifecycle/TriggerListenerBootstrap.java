package com.ghatana.yappc.services.lifecycle;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.orchestrator.subsys.TriggerListener;
import com.ghatana.yappc.services.lifecycle.dlq.DlqPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @doc.type class
 * @doc.purpose Bootstrap for AEP TriggerListener that subscribes to YAPPC phase transition events
 * @doc.layer product
 * @doc.pattern Service, Bootstrapper
 * @doc.gaa.lifecycle perceive
 *
 * Subscribes the AEP TriggerListener to phase.transition.requested events from the YAPPC
 * event cloud. When events match, routes them through the AEP pipeline for processing.
 *
 * <p><b>Event Flow:</b><br>
 * <pre>
 * YAPPC Event Cloud
 *   | (phase.transition.requested)
 *   v
 * [TriggerListenerBootstrap]
 *   | (matches pattern: phase.transition.*)
 *   v
 * [AEP Pipeline]
 *   | (YappcAepPipelineBootstrapper)
 *   v
 * Phase transition processed &amp; state updated
 * </pre>
 *
 * <p><b>Startup Sequence:</b>
 * <ol>
 *   <li>Bootstrap created with TriggerListener, EventCloud, and YappcAepPipelineBootstrapper
 *   <li>start() called on service startup
 *   <li>Subscribes TriggerListener to YAPPC event cloud for phase.transition.* events
 *   <li>Returns ready promise; listener now receives all matching events
 * </ol>
 *
 * <p><b>Error Handling:</b><br>
 * Failures during event processing are published to the DLQ for later analysis:
 * <ul>
 *   <li>Invalid event format → DLQ + error log
 *   <li>Missing required fields → DLQ + validation error
 *   <li>Pipeline processing error → DLQ + exception
 * </ul>
 *
 * @since 2.4.0
 */
public class TriggerListenerBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(TriggerListenerBootstrap.class);

    // Event type patterns we subscribe to
    private static final String EVENT_PATTERN_PREFIX = "phase.transition.";
    private static final String PHASE_TRANSITION_REQUESTED = "phase.transition.requested";

    private final TriggerListener triggerListener;
    private final EventCloud eventCloud;
    private final YappcAepPipelineBootstrapper pipelineBootstrapper;
    private final DlqPublisher dlqPublisher;
    private final ObjectMapper objectMapper;
    private final List<String> tenantIds;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Map<String, EventCloud.Subscription> subscriptions = new ConcurrentHashMap<>();

    /**
     * Initialize TriggerListenerBootstrap with required dependencies.
     *
     * @param triggerListener AEP trigger listener for enqueueing pipeline executions
     * @param eventCloud AEP event cloud for subscribing to phase transition events
     * @param pipelineBootstrapper bootstrapper that starts the lifecycle pipeline
     * @param dlqPublisher publishes failed events to dead-letter queue
     * @param objectMapper Jackson mapper for JSON deserialization
     */
    public TriggerListenerBootstrap(
            TriggerListener triggerListener,
            EventCloud eventCloud,
            YappcAepPipelineBootstrapper pipelineBootstrapper,
            DlqPublisher dlqPublisher,
            ObjectMapper objectMapper) {
        this(triggerListener, eventCloud, pipelineBootstrapper, dlqPublisher, objectMapper, List.of());
    }

    /**
     * Initialize TriggerListenerBootstrap with explicit tenant scoping.
     *
     * @param triggerListener AEP trigger listener for enqueueing pipeline executions
     * @param eventCloud AEP event cloud for subscribing to phase transition events
     * @param pipelineBootstrapper bootstrapper that starts the lifecycle pipeline
     * @param dlqPublisher publishes failed events to dead-letter queue
     * @param objectMapper Jackson mapper for JSON deserialization
     * @param tenantIds list of tenant IDs to subscribe to; must not be empty
     */
    public TriggerListenerBootstrap(
            TriggerListener triggerListener,
            EventCloud eventCloud,
            YappcAepPipelineBootstrapper pipelineBootstrapper,
            DlqPublisher dlqPublisher,
            ObjectMapper objectMapper,
            List<String> tenantIds) {
        this.triggerListener = Objects.requireNonNull(triggerListener, "triggerListener");
        this.eventCloud = Objects.requireNonNull(eventCloud, "eventCloud");
        this.pipelineBootstrapper = Objects.requireNonNull(pipelineBootstrapper, "pipelineBootstrapper");
        this.dlqPublisher = Objects.requireNonNull(dlqPublisher, "dlqPublisher");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.tenantIds = Objects.requireNonNull(tenantIds, "tenantIds");
    }

    /**
     * Start the trigger listener and subscribe to phase transition events.
     *
     * <p>Idempotent — subsequent calls return the already-started promise.
     *
     * @return Promise completing when listener is ready to receive events
     */
    public Promise<Void> start() {
        if (!started.compareAndSet(false, true)) {
            logger.debug("TriggerListenerBootstrap already started");
            return Promise.complete();
        }

        logger.info("Starting TriggerListenerBootstrap — subscribing to phase.transition.* events");

        if (tenantIds.isEmpty()) {
            logger.error("No tenant IDs configured — cannot start TriggerListenerBootstrap without explicit tenant scoping");
            started.set(false);
            return Promise.ofException(new IllegalStateException(
                    "TriggerListenerBootstrap requires at least one tenant ID. "
                    + "Configure YAPPC_LIFECYCLE_TENANT_IDS to scope event subscriptions."));
        }

        try {
            for (String tenantId : tenantIds) {
                EventCloud.Subscription subscription = eventCloud.subscribe(
                    tenantId,
                    PHASE_TRANSITION_REQUESTED,
                    this::handleEvent
                );

                subscriptions.put(PHASE_TRANSITION_REQUESTED + ":" + tenantId, subscription);
                logger.info("✓ Subscribed to {} events for tenant {}", PHASE_TRANSITION_REQUESTED, tenantId);
            }

            return Promise.complete();
        } catch (Exception e) {
            logger.error("Failed to start TriggerListenerBootstrap: {}", e.getMessage(), e);
            started.set(false);
            return Promise.ofException(e);
        }
    }

    /**
     * Stop the listener and cancel all subscriptions.
     *
     * @return Promise completing when listener is stopped
     */
    public Promise<Void> stop() {
        if (!started.compareAndSet(true, false)) {
            logger.debug("TriggerListenerBootstrap already stopped");
            return Promise.complete();
        }

        logger.info("Stopping TriggerListenerBootstrap — cancelling {} subscription(s)", subscriptions.size());

        try {
            subscriptions.values().forEach(sub -> {
                try {
                    sub.cancel();
                    logger.debug("✓ Cancelled subscription");
                } catch (Exception e) {
                    logger.warn("Error cancelling subscription: {}", e.getMessage());
                }
            });
            subscriptions.clear();
            logger.info("✓ TriggerListenerBootstrap stopped");

            return Promise.complete();
        } catch (Exception e) {
            logger.error("Error stopping TriggerListenerBootstrap: {}", e.getMessage(), e);
            return Promise.ofException(e);
        }
    }

    /**
     * Return whether the bootstrap is running.
     */
    public boolean isRunning() {
        return started.get();
    }

    /**
     * Handle incoming event from the event cloud.
     *
     * <p>Processes the event through the AEP pipeline. If processing fails,
     * publishes to DLQ for later analysis.
     *
     * @param eventId unique event identifier
     * @param eventType event type string
     * @param payload JSON-encoded event payload
     */
    private void handleEvent(String eventId, String eventType, byte[] payload) {
        try {
            logger.debug("Received event: {} (type: {})", eventId, eventType);

            if (payload == null || payload.length == 0) {
                String errorMsg = "Received empty event payload for eventId: " + eventId;
                logger.error(errorMsg);
                dlqPublisher.publishErrorEvent(eventId, eventType, "EMPTY_PAYLOAD", errorMsg);
                return;
            }

            // Parse the event payload
            Map<String, Object> eventData = objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});

            // Extract required fields
            String tenantId = extractStringField(eventData, "tenantId", eventId);
            String pipelineId = YappcAepPipelineBootstrapper.PIPELINE_ID;
            String correlationId = extractStringField(eventData, "correlationId", eventId);

            logger.debug("Processing {} event for tenant: {}, correlation: {}",
                eventType, tenantId, correlationId);

            // Route the event through the AEP pipeline
            // The TriggerListener will enqueue the pipeline execution
            Promise<Void> result = triggerListener.handlePatternMatch(
                tenantId,
                pipelineId,
                eventId,  // Use eventId as pattern match ID for idempotency
                eventData
            );

            result.whenComplete((v, e) -> {
                if (e != null) {
                    logger.error("Pipeline processing failed for event {}: {}",
                            eventId, e.getMessage(), e);
                    dlqPublisher.publishErrorEvent(
                            eventId,
                            eventType,
                            "PIPELINE_ERROR",
                            e.getMessage()
                    );
                } else {
                    logger.debug("✓ Event {} processed successfully", eventId);
                }
            });

        } catch (Exception e) {
            String errorMsg = "Failed to process event " + eventId + ": " + e.getMessage();
            logger.error(errorMsg, e);
            dlqPublisher.publishErrorEvent(eventId, eventType, "PROCESSING_ERROR", errorMsg);
        }
    }

    /**
     * Extract a string field from event data, providing a default on missing value.
     */
    private String extractStringField(Map<String, Object> data, String fieldName, String defaultValue) {
        Object value = data.get(fieldName);
        if (value == null) {
            logger.warn("Field '{}' missing; using default: {}", fieldName, defaultValue);
            return defaultValue;
        }
        return value.toString();
    }

    /**
     * Return the number of active subscriptions.
     */
    public int getSubscriptionCount() {
        return subscriptions.size();
    }
}
