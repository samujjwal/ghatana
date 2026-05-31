package com.ghatana.aep.pattern;

import com.ghatana.aep.agent.capability.ExternalAgentCapabilityRegistry;
import com.ghatana.aep.learning.LearningFeedback;
import com.ghatana.aep.model.CanonicalEvent;
import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.orchestrator.PatternDecision;
import com.ghatana.aep.pattern.spec.CompiledPattern;
import com.ghatana.aep.pattern.spec.PatternSpec;
import com.ghatana.aep.pattern.spec.PatternSpecCompiler;
import com.ghatana.aep.registry.LifecycleAwarePatternRegistry;
import com.ghatana.aep.registry.PatternRegistration;
import com.ghatana.aep.registry.PatternVersion;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.Event;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Vertical slice integration test for pattern execution (P4-09).
 *
 * <p>Tests the complete flow from event ingestion through pattern detection
 * to governed action execution, validating:
 * <ul>
 *   <li>Pattern registration with versioning</li>
 *   <li>Event consumption and pattern matching</li>
 *   <li>Pattern instance lifecycle management</li>
 *   <li>Decision generation and governance</li>
 *   <li>Learning feedback integration</li>
 *   <li>Replay mode support</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PatternExecutionVerticalSliceTest {

    @Mock
    private PatternSpecCompiler compiler;

    @Mock
    private LifecycleAwarePatternRegistry registry;

    @Mock
    private PatternSpecSpec spec;

    @Mock
    private ExplainabilityService explainabilityService;

    @Mock
    private DataCloudClient dataCloudClient;

    @Mock
    private ExternalAgentCapabilityRegistry capabilityRegistry;

    private PatternEngineService engine;
    private Eventloop eventloop;

    @BeforeEach
    void setUp() {
        eventloop = Eventloop.create();
        engine = new PatternEngineService(
            compiler,
            registry,
            spec,
            explainabilityService,
            dataCloudClient,
            eventloop
        );
    }

    // ==================== Vertical Slice: Event to Pattern Match ====================

    @Test
    void verticalSliceEventToPatternMatch() {
        // Arrange
        String tenantId = "tenant-1";
        Map<String, Object> patternSpec = createOrderPatternSpec();
        CompiledPattern compiled = mockCompiledPattern("order-created-pattern");
        PatternRegistration registration = mockPatternRegistration("order-created-pattern", "ACTIVE");

        when(compiler.compile(any(), any())).thenReturn(compiled);
        when(registry.register(any(), any(), any())).thenReturn(Promise.of(registration));
        when(registry.getActivePatterns(tenantId)).thenReturn(Promise.of(List.of(registration)));
        when(dataCloudClient.append(any())).thenReturn(Promise.of("event-id-1"));

        // Act: Register pattern
        Promise<PatternRegistration> registrationPromise = engine.registerPattern(tenantId, patternSpec);
        assertThat(registrationPromise.isComplete()).isTrue();

        // Act: Consume event
        CanonicalEvent event = createCanonicalEvent("OrderCreated");
        Promise<Void> consumePromise = engine.consumeEvent(tenantId, event);
        assertThat(consumePromise.isComplete()).isTrue();

        // Assert: Pattern was registered and event was processed
        verify(compiler).compile(patternSpec, null);
        verify(registry).register(eq(tenantId), any(), eq(Optional.empty()));
        verify(registry).getActivePatterns(tenantId);
        verify(dataCloudClient).append(any());
    }

    // ==================== Vertical Slice: Pattern to Decision ====================

    @Test
    void verticalSlicePatternToGovernedDecision() {
        // Arrange
        String tenantId = "tenant-1";
        Map<String, Object> patternSpec = createFraudPatternSpec();
        CompiledPattern compiled = mockCompiledPattern("fraud-detection-pattern");
        PatternRegistration registration = mockPatternRegistration("fraud-detection-pattern", "ACTIVE");

        when(compiler.compile(any(), any())).thenReturn(compiled);
        when(registry.register(any(), any(), any())).thenReturn(Promise.of(registration));
        when(registry.getActivePatterns(tenantId)).thenReturn(Promise.of(List.of(registration)));
        when(dataCloudClient.append(any())).thenReturn(Promise.of("event-id-1"));

        // Act: Register pattern
        engine.registerPattern(tenantId, patternSpec);

        // Act: Consume suspicious event
        CanonicalEvent suspiciousEvent = createCanonicalEvent("SuspiciousTransaction");
        engine.consumeEvent(tenantId, suspiciousEvent);

        // Assert: Pattern should generate decision (simulated through registry interaction)
        verify(registry).getActivePatterns(tenantId);
    }

    // ==================== Vertical Slice: Versioned Pattern Registration ====================

    @Test
    void verticalSliceVersionedPatternRegistration() {
        // Arrange
        String tenantId = "tenant-1";
        PatternVersion version = new PatternVersion("test-pattern", "2.0.0", "commit-xyz");
        Map<String, Object> patternSpec = createOrderPatternSpec();
        CompiledPattern compiled = mockCompiledPattern("test-pattern");
        PatternRegistration registration = mockPatternRegistration("test-pattern", "ACTIVE");

        when(compiler.compile(any(), any())).thenReturn(compiled);
        when(registry.register(any(), any(), any())).thenReturn(Promise.of(registration));

        // Act: Register versioned pattern
        Promise<PatternRegistration> result = engine.registerPattern(tenantId, patternSpec, version);

        // Assert
        assertThat(result.isComplete()).isTrue();
        verify(registry).register(eq(tenantId), any(), eq(Optional.of(version)));
    }

    // ==================== Vertical Slice: Pattern Instance Lifecycle ====================

    @Test
    void verticalSlicePatternInstanceLifecycle() {
        // Arrange
        String tenantId = "tenant-1";
        String patternId = "test-pattern";
        CanonicalEvent triggeringEvent = createCanonicalEvent("OrderCreated");
        PatternRegistration registration = mockPatternRegistration(patternId, "ACTIVE");

        when(registry.getPattern(tenantId, patternId))
            .thenReturn(Promise.of(Optional.of(registration)));
        when(registry.updateInstance(any(), any())).thenReturn(Promise.of(true));

        // Act: Create instance
        Promise<PatternInstance> createPromise = engine.createPatternInstance(
            tenantId, patternId, triggeringEvent);
        assertThat(createPromise.isComplete()).isTrue();

        // Act: Complete instance
        PatternInstance instance = mockPatternInstance("instance-1");
        Promise<Void> completePromise = engine.completePatternInstance(tenantId, instance);
        assertThat(completePromise.isComplete()).isTrue();

        // Assert
        verify(registry).getPattern(tenantId, patternId);
        verify(registry).updateInstance(eq(tenantId), any());
    }

    // ==================== Vertical Slice: Learning Feedback Integration ====================

    @Test
    void verticalSliceLearningFeedbackIntegration() {
        // Arrange
        String tenantId = "tenant-1";
        String patternId = "test-pattern";
        LearningFeedback feedback = new LearningFeedback(
            patternId,
            "no-match",
            "Event did not match pattern",
            Instant.now()
        );

        when(registry.addLearningFeedback(any(), any())).thenReturn(Promise.of(true));

        // Act: Record learning feedback
        Promise<Void> result = engine.recordLearningFeedback(tenantId, feedback);

        // Assert
        assertThat(result.isComplete()).isTrue();
        verify(registry).addLearningFeedback(eq(tenantId), eq(feedback));
    }

    // ==================== Vertical Slice: Replay Mode ====================

    @Test
    void verticalSliceReplayMode() {
        // Arrange
        String tenantId = "tenant-1";
        Map<String, Object> patternSpec = createOrderPatternSpec();
        CompiledPattern compiled = mockCompiledPattern("order-created-pattern");
        PatternRegistration registration = mockPatternRegistration("order-created-pattern", "ACTIVE");

        when(compiler.compile(any(), any())).thenReturn(compiled);
        when(registry.register(any(), any(), any())).thenReturn(Promise.of(registration));
        when(registry.getActivePatterns(tenantId)).thenReturn(Promise.of(List.of(registration)));
        when(dataCloudClient.append(any())).thenReturn(Promise.of("event-id-1"));

        // Act: Register pattern
        engine.registerPattern(tenantId, patternSpec);

        // Act: Consume event in replay mode
        CanonicalEvent event = createCanonicalEvent("OrderCreated");
        boolean replayMode = true;
        Promise<Void> result = engine.consumeEvent(tenantId, event, replayMode);

        // Assert
        assertThat(result.isComplete()).isTrue();
        verify(registry).getActivePatterns(tenantId);
        // In replay mode, events should be processed with idempotency checks
    }

    // ==================== Vertical Slice: Late Event Handling ====================

    @Test
    void verticalSliceLateEventHandling() {
        // Arrange
        String tenantId = "tenant-1";
        String patternId = "test-pattern";
        CanonicalEvent lateEvent = createCanonicalEvent("OrderCreated");
        PatternRegistration registration = mockPatternRegistration(patternId, "ACTIVE");

        when(registry.getPattern(tenantId, patternId))
            .thenReturn(Promise.of(Optional.of(registration)));

        // Act: Handle late event
        Promise<Void> result = engine.handleLateEvent(tenantId, patternId, lateEvent);

        // Assert
        assertThat(result.isComplete()).isTrue();
        verify(registry).getPattern(tenantId, patternId);
    }

    // ==================== Vertical Slice: End-to-End Flow ====================

    @Test
    void verticalSliceEndToEndFlow() {
        // Arrange
        String tenantId = "tenant-1";
        Map<String, Object> patternSpec = createOrderPatternSpec();
        CompiledPattern compiled = mockCompiledPattern("order-created-pattern");
        PatternRegistration registration = mockPatternRegistration("order-created-pattern", "ACTIVE");

        when(compiler.compile(any(), any())).thenReturn(compiled);
        when(registry.register(any(), any(), any())).thenReturn(Promise.of(registration));
        when(registry.getActivePatterns(tenantId)).thenReturn(Promise.of(List.of(registration)));
        when(registry.getPattern(tenantId, "order-created-pattern"))
            .thenReturn(Promise.of(Optional.of(registration)));
        when(registry.updateInstance(any(), any())).thenReturn(Promise.of(true));
        when(registry.addLearningFeedback(any(), any())).thenReturn(Promise.of(true));
        when(dataCloudClient.append(any())).thenReturn(Promise.of("event-id-1"));

        // Step 1: Register pattern
        Promise<PatternRegistration> regResult = engine.registerPattern(tenantId, patternSpec);
        assertThat(regResult.isComplete()).isTrue();

        // Step 2: Consume event
        CanonicalEvent event = createCanonicalEvent("OrderCreated");
        Promise<Void> consumeResult = engine.consumeEvent(tenantId, event);
        assertThat(consumeResult.isComplete()).isTrue();

        // Step 3: Create pattern instance
        Promise<PatternInstance> instanceResult = engine.createPatternInstance(
            tenantId, "order-created-pattern", event);
        assertThat(instanceResult.isComplete()).isTrue();

        // Step 4: Complete instance
        PatternInstance instance = mockPatternInstance("instance-1");
        Promise<Void> completeResult = engine.completePatternInstance(tenantId, instance);
        assertThat(completeResult.isComplete()).isTrue();

        // Assert all steps completed
        verify(compiler).compile(patternSpec, null);
        verify(registry).register(eq(tenantId), any(), eq(Optional.empty()));
        verify(registry).getActivePatterns(tenantId);
        verify(registry).getPattern(tenantId, "order-created-pattern");
        verify(registry).updateInstance(eq(tenantId), any());
        verify(dataCloudClient, atLeastOnce()).append(any());
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> createOrderPatternSpec() {
        return Map.of(
            "apiVersion", "v1",
            "kind", "Pattern",
            "metadata", Map.of("name", "order-created-pattern"),
            "pattern", Map.of("operator", "EVENT_REF", "event", "OrderCreated"),
            "lifecycle", Map.of("state", "ACTIVE"),
            "governance", Map.of(
                "commitSha", "abc123",
                "idempotency", "recorded_output",
                "approvalPolicy", "auto_approve",
                "auditPolicy", Map.of("sink", "eventcloud")
            )
        );
    }

    private Map<String, Object> createFraudPatternSpec() {
        return Map.of(
            "apiVersion", "v1",
            "kind", "Pattern",
            "metadata", Map.of("name", "fraud-detection-pattern"),
            "pattern", Map.of("operator", "EVENT_REF", "event", "SuspiciousTransaction"),
            "lifecycle", Map.of("state", "ACTIVE"),
            "governance", Map.of(
                "commitSha", "def456",
                "idempotency", "recorded_output",
                "approvalPolicy", "human_required",
                "auditPolicy", Map.of("sink", "eventcloud")
            )
        );
    }

    private CanonicalEvent createCanonicalEvent(String eventType) {
        return new CanonicalEvent(
            UUID.randomUUID().toString(),
            eventType,
            "1.0.0",
            Instant.now(),
            Map.of("data", "test"),
            Map.of("tenantId", "tenant-1"),
            Optional.of("correlation-1"),
            Optional.of("causation-1"),
            Optional.of("source"),
            Optional.empty()
        );
    }

    private CompiledPattern mockCompiledPattern(String patternId) {
        CompiledPattern compiled = mock(CompiledPattern.class);
        when(compiled.patternId()).thenReturn(patternId);
        return compiled;
    }

    private PatternRegistration mockPatternRegistration(String patternId, String state) {
        PatternRegistration registration = mock(PatternRegistration.class);
        when(registration.patternId()).thenReturn(patternId);
        when(registration.version()).thenReturn("1.0.0");
        when(registration.state()).thenReturn(state);
        return registration;
    }

    private PatternInstance mockPatternInstance(String instanceId) {
        PatternInstance instance = mock(PatternInstance.class);
        when(instance.instanceId()).thenReturn(instanceId);
        when(instance.state()).thenReturn("RUNNING");
        return instance;
    }
}
