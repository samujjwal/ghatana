package com.ghatana.yappc.storage;

import com.ghatana.yappc.domain.ActorType;
import com.ghatana.yappc.domain.PhaseType;
import com.ghatana.yappc.sdlc.agent.AepEventPublisher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Publishes phase lifecycle events through the canonical {@link AepEventPublisher} interface.
 *
 * @doc.type class
 * @doc.purpose Publishes phase events to data-cloud event store
 * @doc.layer infrastructure
 * @doc.pattern Publisher
 */
public class PhaseEventPublisher {
    
    private static final Logger log = LoggerFactory.getLogger(PhaseEventPublisher.class);
    
    private final AepEventPublisher eventPublisher;
    
    /**
     * Constructor with event publisher.
     *
     * @param eventPublisher AEP event publisher implementation
     */
    public PhaseEventPublisher(AepEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
    
    /**
     * Default constructor with in-memory publisher.
     */
    public PhaseEventPublisher() {
        this(new InMemoryEventPublisher());
    }
    
    /**
     * Publishes a phase started event.
     * 
     * @param phaseId Phase execution identifier
     * @param phaseType Phase type
     * @param inputSpecRef Reference to input specification
     * @param actor Actor type
     * @param correlationId Correlation ID for tracing
     * @param tenantId Tenant identifier
     * @param productId Product identifier
     * @return Promise of completion
     */
    public Promise<Void> publishPhaseStarted(
            String phaseId,
            PhaseType phaseType,
            String inputSpecRef,
            ActorType actor,
            String correlationId,
            String tenantId,
            String productId) {
        
        Map<String, Object> event = Map.of(
            "event_type", "PhaseStartedEvent",
            "phase_id", phaseId,
            "phase_type", phaseType.name(),
            "input_spec_ref", inputSpecRef,
            "actor", actor.name(),
            "timestamp_ms", Instant.now().toEpochMilli(),
            "correlation_id", correlationId,
            "tenant_id", tenantId,
            "product_id", productId
        );
        
        log.info("Publishing PhaseStartedEvent: {}", phaseType);
        
        return eventPublisher.publish("PhaseStartedEvent", tenantId, event);
    }
    
    /**
     * Publishes a phase completed event.
     * 
     * @param phaseId Phase execution identifier
     * @param phaseType Phase type
     * @param inputSpecRef Reference to input specification
     * @param outputArtifactRef Reference to output artifact
     * @param actor Actor type
     * @param correlationId Correlation ID for tracing
     * @param tenantId Tenant identifier
     * @param productId Product identifier
     * @param metadata Additional metadata
     * @return Promise of completion
     */
    public Promise<Void> publishPhaseCompleted(
            String phaseId,
            PhaseType phaseType,
            String inputSpecRef,
            String outputArtifactRef,
            ActorType actor,
            String correlationId,
            String tenantId,
            String productId,
            Map<String, String> metadata) {
        
        Map<String, Object> event = Map.ofEntries(
            entry("event_type", "PhaseCompletedEvent"),
            entry("phase_id", phaseId),
            entry("phase_type", phaseType.name()),
            entry("input_spec_ref", inputSpecRef),
            entry("output_artifact_ref", outputArtifactRef),
            entry("actor", actor.name()),
            entry("timestamp_ms", Instant.now().toEpochMilli()),
            entry("correlation_id", correlationId),
            entry("tenant_id", tenantId),
            entry("product_id", productId),
            entry("metadata", metadata)
        );
        
        log.info("Publishing PhaseCompletedEvent: {}", phaseType);
        
        return eventPublisher.publish("PhaseCompletedEvent", tenantId, event);
    }
    
    /**
     * Publishes a phase failed event.
     * 
     * @param phaseId Phase execution identifier
     * @param phaseType Phase type
     * @param errorMessage Error message
     * @param errorCode Error code
     * @param correlationId Correlation ID for tracing
     * @param tenantId Tenant identifier
     * @param productId Product identifier
     * @param errorDetails Additional error details
     * @return Promise of completion
     */
    public Promise<Void> publishPhaseFailed(
            String phaseId,
            PhaseType phaseType,
            String errorMessage,
            String errorCode,
            String correlationId,
            String tenantId,
            String productId,
            Map<String, String> errorDetails) {
        
        Map<String, Object> event = Map.ofEntries(
            entry("event_type", "PhaseFailedEvent"),
            entry("phase_id", phaseId),
            entry("phase_type", phaseType.name()),
            entry("error_message", errorMessage),
            entry("error_code", errorCode),
            entry("timestamp_ms", Instant.now().toEpochMilli()),
            entry("correlation_id", correlationId),
            entry("tenant_id", tenantId),
            entry("product_id", productId),
            entry("error_details", errorDetails)
        );
        
        log.error("Publishing PhaseFailedEvent: {} - {}", phaseType, errorMessage);
        
        return eventPublisher.publish("PhaseFailedEvent", tenantId, event);
    }
    
    /**
     * Publishes a lifecycle execution event.
     * 
     * @param lifecycleId Lifecycle execution identifier
     * @param productId Product identifier
     * @param status Lifecycle status
     * @param tenantId Tenant identifier
     * @param metadata Additional metadata
     * @return Promise of completion
     */
    public Promise<Void> publishLifecycleEvent(
            String lifecycleId,
            String productId,
            String status,
            String tenantId,
            Map<String, String> metadata) {
        
        Map<String, Object> event = Map.of(
            "event_type", "LifecycleExecutionEvent",
            "lifecycle_id", lifecycleId,
            "product_id", productId,
            "status", status,
            "timestamp_ms", Instant.now().toEpochMilli(),
            "tenant_id", tenantId,
            "metadata", metadata
        );
        
        log.info("Publishing LifecycleExecutionEvent: {} - {}", lifecycleId, status);
        
        return eventPublisher.publish("LifecycleExecutionEvent", tenantId, event);
    }
}
