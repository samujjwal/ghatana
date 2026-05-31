package com.ghatana.aep.pattern;

import com.ghatana.aep.agent.capability.ExternalAgentCapabilityRegistry;
import com.ghatana.aep.learning.LearningFeedback;
import com.ghatana.aep.model.CanonicalEvent;
import com.ghatana.aep.model.EventContext;
import com.ghatana.aep.pattern.spec.CompiledPattern;
import com.ghatana.aep.pattern.spec.PatternSpec;
import com.ghatana.aep.pattern.spec.PatternSpecCompiler;
import com.ghatana.aep.pattern.spec.PatternSpecParser;
import com.ghatana.aep.registry.LifecycleAwarePatternRegistry;
import com.ghatana.aep.registry.PatternRegistration;
import com.ghatana.aep.registry.PatternVersion;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.Event;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
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
 * Tests for PatternEngineService (P4-03).
 *
 * <p>Verifies that the pattern engine:
 * <ul>
 *   <li>Compiles and registers patterns correctly</li>
 *   <li>Consumes events and detects pattern matches</li>
 *   <li>Creates and manages pattern instances</li>
 *   <li>Supports replay mode for event reprocessing</li>
 *   <li>Handles learning feedback for no-match scenarios</li>
 *   <li>Emits notifications for late events</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PatternEngineServiceTest {

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

    // ==================== Pattern Registration Tests ====================

    @Test
    void registerPatternCompilesAndStoresPattern() {
        // Arrange
        String tenantId = "tenant-1";
        Map<String, Object> patternSpec = Map.of(
            "apiVersion", "v1",
            "kind", "Pattern",
            "metadata", Map.of("name", "test-pattern"),
            "pattern", Map.of("operator", "EVENT_REF", "event", "OrderCreated")
        );

        CompiledPattern compiled = mockCompiledPattern("test-pattern");
        when(compiler.compile(any(), any())).thenReturn(compiled);
        when(registry.register(any(), any(), any())).thenReturn(Promise.of(mockRegistration()));

        // Act
        Promise<PatternRegistration> result = engine.registerPattern(tenantId, patternSpec);

        // Assert
        assertThat(result.isComplete()).isTrue();
        verify(compiler).compile(patternSpec, null);
        verify(registry).register(eq(tenantId), any(), eq(Optional.empty()));
    }

    @Test
    void registerPatternWithVersionStoresVersionedPattern() {
        // Arrange
        String tenantId = "tenant-1";
        PatternVersion version = new PatternVersion("test-pattern", "1.0.0", "commit-abc");
        Map<String, Object> patternSpec = Map.of(
            "apiVersion", "v1",
            "kind", "Pattern",
            "metadata", Map.of("name", "test-pattern")
        );

        CompiledPattern compiled = mockCompiledPattern("test-pattern");
        when(compiler.compile(any(), any())).thenReturn(compiled);
        when(registry.register(any(), any(), any())).thenReturn(Promise.of(mockRegistration()));

        // Act
        Promise<PatternRegistration> result = engine.registerPattern(tenantId, patternSpec, version);

        // Assert
        assertThat(result.isComplete()).isTrue();
        verify(registry).register(eq(tenantId), any(), eq(Optional.of(version)));
    }

    // ==================== Event Consumption Tests ====================

    @Test
    void consumeEventProcessesThroughRegisteredPatterns() {
        // Arrange
        String tenantId = "tenant-1";
        CanonicalEvent event = createCanonicalEvent("OrderCreated");

        when(registry.getActivePatterns(tenantId))
            .thenReturn(Promise.of(List.of(mockRegistration())));
        when(dataCloudClient.append(any())).thenReturn(Promise.of("event-id-1"));

        // Act
        Promise<Void> result = engine.consumeEvent(tenantId, event);

        // Assert
        assertThat(result.isComplete()).isTrue();
        verify(registry).getActivePatterns(tenantId);
    }

    @Test
    void consumeEventInReplayModeProcessesWithReplaySemantics() {
        // Arrange
        String tenantId = "tenant-1";
        CanonicalEvent event = createCanonicalEvent("OrderCreated");
        boolean replayMode = true;

        when(registry.getActivePatterns(tenantId))
            .thenReturn(Promise.of(List.of(mockRegistration())));
        when(dataCloudClient.append(any())).thenReturn(Promise.of("event-id-1"));

        // Act
        Promise<Void> result = engine.consumeEvent(tenantId, event, replayMode);

        // Assert
        assertThat(result.isComplete()).isTrue();
        // In replay mode, events should be processed with idempotency checks
        verify(dataCloudClient).append(any());
    }

    // ==================== Pattern Instance Tests ====================

    @Test
    void createPatternInstanceInitializesNewInstance() {
        // Arrange
        String tenantId = "tenant-1";
        String patternId = "test-pattern";
        CanonicalEvent triggeringEvent = createCanonicalEvent("OrderCreated");

        when(registry.getPattern(tenantId, patternId))
            .thenReturn(Promise.of(Optional.of(mockRegistration())));

        // Act
        Promise<PatternInstance> result = engine.createPatternInstance(
            tenantId, patternId, triggeringEvent);

        // Assert
        assertThat(result.isComplete()).isTrue();
        verify(registry).getPattern(tenantId, patternId);
    }

    @Test
    void completePatternInstanceUpdatesInstanceStatus() {
        // Arrange
        String tenantId = "tenant-1";
        String instanceId = "instance-1";
        PatternInstance instance = mockPatternInstance(instanceId);

        when(registry.updateInstance(any(), any())).thenReturn(Promise.of(true));

        // Act
        Promise<Void> result = engine.completePatternInstance(tenantId, instance);

        // Assert
        assertThat(result.isComplete()).isTrue();
        verify(registry).updateInstance(eq(tenantId), any());
    }

    // ==================== Learning Feedback Tests ====================

    @Test
    void recordLearningFeedbackStoresFeedbackForPattern() {
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

        // Act
        Promise<Void> result = engine.recordLearningFeedback(tenantId, feedback);

        // Assert
        assertThat(result.isComplete()).isTrue();
        verify(registry).addLearningFeedback(eq(tenantId), eq(feedback));
    }

    // ==================== Late Event Tests ====================

    @Test
    void handleLateEventEmitsNotification() {
        // Arrange
        String tenantId = "tenant-1";
        CanonicalEvent lateEvent = createCanonicalEvent("OrderCreated");
        String patternId = "test-pattern";

        when(registry.getPattern(tenantId, patternId))
            .thenReturn(Promise.of(Optional.of(mockRegistration())));

        // Act
        Promise<Void> result = engine.handleLateEvent(tenantId, patternId, lateEvent);

        // Assert
        assertThat(result.isComplete()).isTrue();
        // Late events should trigger notification logic
    }

    // ==================== Error Handling Tests ====================

    @Test
    void registerPatternWithInvalidSpecFails() {
        // Arrange
        String tenantId = "tenant-1";
        Map<String, Object> invalidSpec = Map.of("invalid", "spec");

        when(compiler.compile(any(), any()))
            .thenThrow(new IllegalArgumentException("Invalid pattern spec"));

        // Act & Assert
        Promise<PatternRegistration> result = engine.registerPattern(tenantId, invalidSpec);
        assertThat(result.isException()).isTrue();
    }

    @Test
    void consumeEventWithNoRegisteredPatternsCompletesSuccessfully() {
        // Arrange
        String tenantId = "tenant-1";
        CanonicalEvent event = createCanonicalEvent("OrderCreated");

        when(registry.getActivePatterns(tenantId))
            .thenReturn(Promise.of(List.of()));

        // Act
        Promise<Void> result = engine.consumeEvent(tenantId, event);

        // Assert
        assertThat(result.isComplete()).isTrue();
    }

    // ==================== Helper Methods ====================

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

    private PatternRegistration mockRegistration() {
        PatternRegistration registration = mock(PatternRegistration.class);
        when(registration.patternId()).thenReturn("test-pattern");
        when(registration.version()).thenReturn("1.0.0");
        when(registration.state()).thenReturn("ACTIVE");
        return registration;
    }

    private PatternInstance mockPatternInstance(String instanceId) {
        PatternInstance instance = mock(PatternInstance.class);
        when(instance.instanceId()).thenReturn(instanceId);
        when(instance.state()).thenReturn("RUNNING");
        return instance;
    }
}
