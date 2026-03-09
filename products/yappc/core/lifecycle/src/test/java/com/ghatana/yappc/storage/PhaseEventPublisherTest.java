package com.ghatana.yappc.storage;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.ActorType;
import com.ghatana.yappc.domain.PhaseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @doc.type class
 * @doc.purpose Tests for PhaseEventPublisher
 * @doc.layer test
 * @doc.pattern Test
 */
class PhaseEventPublisherTest extends EventloopTestBase {
    
    private PhaseEventPublisher publisher;
    private InMemoryEventPublisher eventPublisher;
    
    @BeforeEach
    void setUp() {
        eventPublisher = new InMemoryEventPublisher();
        publisher = new PhaseEventPublisher(eventPublisher);
    }
    
    @Test
    void shouldPublishPhaseStartedEvent() {
        // GIVEN
        String phaseId = "phase-123";
        PhaseType phaseType = PhaseType.INTENT;
        String inputSpecRef = "input-ref";
        ActorType actor = ActorType.HUMAN;
        String correlationId = "corr-123";
        String tenantId = "tenant-123";
        String productId = "product-123";
        
        // WHEN
        runPromise(() -> publisher.publishPhaseStarted(
                phaseId, phaseType, inputSpecRef, actor, correlationId, tenantId, productId));
        
        // THEN
        List<Map<String, Object>> events = eventPublisher.getEvents("PhaseStartedEvent");
        assertEquals(1, events.size());
        assertEquals(phaseId, events.get(0).get("phase_id"));
        assertEquals(phaseType.name(), events.get(0).get("phase_type"));
    }
    
    @Test
    void shouldPublishPhaseCompletedEvent() {
        // GIVEN
        String phaseId = "phase-123";
        PhaseType phaseType = PhaseType.SHAPE;
        
        // WHEN
        runPromise(() -> publisher.publishPhaseCompleted(
                phaseId, phaseType, "input-ref", "output-ref", 
                ActorType.AI_ASSISTED, "corr-123", "tenant-123", "product-123", Map.of()));
        
        // THEN
        List<Map<String, Object>> events = eventPublisher.getEvents("PhaseCompletedEvent");
        assertEquals(1, events.size());
        assertEquals("output-ref", events.get(0).get("output_artifact_ref"));
    }
    
    @Test
    void shouldPublishPhaseFailedEvent() {
        // GIVEN
        String phaseId = "phase-123";
        PhaseType phaseType = PhaseType.VALIDATE;
        String errorMessage = "Validation failed";
        String errorCode = "VAL_001";
        
        // WHEN
        runPromise(() -> publisher.publishPhaseFailed(
                phaseId, phaseType, errorMessage, errorCode, 
                "corr-123", "tenant-123", "product-123", Map.of()));
        
        // THEN
        List<Map<String, Object>> events = eventPublisher.getEvents("PhaseFailedEvent");
        assertEquals(1, events.size());
        assertEquals(errorMessage, events.get(0).get("error_message"));
        assertEquals(errorCode, events.get(0).get("error_code"));
    }
    
    @Test
    void shouldPublishLifecycleEvent() {
        // GIVEN
        String lifecycleId = "lifecycle-123";
        String productId = "product-123";
        String status = "completed";
        
        // WHEN
        runPromise(() -> publisher.publishLifecycleEvent(
                lifecycleId, productId, status, "tenant-123", Map.of()));
        
        // THEN
        List<Map<String, Object>> events = eventPublisher.getEvents("LifecycleExecutionEvent");
        assertEquals(1, events.size());
        assertEquals(lifecycleId, events.get(0).get("lifecycle_id"));
        assertEquals(status, events.get(0).get("status"));
    }
    
    @Test
    void shouldTrackMultipleEvents() {
        // GIVEN/WHEN
        runPromise(() -> publisher.publishPhaseStarted(
                "phase-1", PhaseType.INTENT, "input", ActorType.HUMAN, 
                "corr-1", "tenant-1", "product-1"));
        runPromise(() -> publisher.publishPhaseCompleted(
                "phase-1", PhaseType.INTENT, "input", "output", ActorType.HUMAN,
                "corr-1", "tenant-1", "product-1", Map.of()));
        
        // THEN
        assertEquals(2, eventPublisher.size());
        assertEquals(1, eventPublisher.getEvents("PhaseStartedEvent").size());
        assertEquals(1, eventPublisher.getEvents("PhaseCompletedEvent").size());
    }
}
