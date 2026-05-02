package com.ghatana.aep;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.aep.event.InMemoryEventCloud;
import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for AepEngine core behavior.
 *
 * @doc.type class
 * @doc.purpose Verifies AepEngine event processing, pipeline execution, and lifecycle
 * @doc.layer product
 * @doc.pattern Unit Test
 */
@DisplayName("AepEngine Tests")
class AepEngineTest extends EventloopTestBase {

    private AepEngine engine;
    private EventCloud eventCloud;

    @BeforeEach
    void setUp() {
        eventCloud = new InMemoryEventCloud();
        engine = Aep.forTesting(eventCloud);
    }

    @Nested
    @DisplayName("Engine Lifecycle")
    class EngineLifecycle {

        @Test
        @DisplayName("should create engine with default configuration")
        void shouldCreateEngineWithDefaultConfiguration() {
            AepEngine defaultEngine = Aep.forTesting();
            
            assertThat(defaultEngine).isNotNull();
            assertThat(defaultEngine.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("should create engine with custom configuration")
        void shouldCreateEngineWithCustomConfiguration() {
            AepConfig config = AepConfig.builder()
                .instanceId("custom-engine")
                .workerThreads(2)
                .maxPipelinesPerTenant(50)
                .enableMetrics(true)
                .enableTracing(false)
                .anomalyThreshold(0.8)
                .build();
            
            AepEngine customEngine = Aep.create(config);
            
            assertThat(customEngine).isNotNull();
            assertThat(customEngine.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("should support embedded engine creation")
        void shouldSupportEmbeddedEngineCreation() {
            AepEngine embeddedEngine = Aep.embedded();
            
            assertThat(embeddedEngine).isNotNull();
            assertThat(embeddedEngine.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("should handle engine closure")
        void shouldHandleEngineClosure() {
            engine.close();
            
            assertThatThrownBy(() -> engine.process("tenant", createTestEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
        }
    }

    @Nested
    @DisplayName("Event Processing")
    class EventProcessing {

        @Test
        @DisplayName("should process single event successfully")
        void shouldProcessEventSuccessfully() {
            AepEngine.Event event = createTestEvent();
            
            AepEngine.ProcessingResult result = runPromise(() -> engine.process("test-tenant", event));
            
            assertThat(result).isNotNull();
            assertThat(result.success()).isTrue();
            assertThat(result.processedAt()).isBefore(Instant.now());
            assertThat(result.processingTimeMs()).isGreaterThan(0);
        }

        @Test
        @DisplayName("should reject null event")
        void shouldRejectNullEvent() {
            assertThatThrownBy(() -> engine.process("test-tenant", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("event must not be null");
        }

        @Test
        @DisplayName("should reject null tenant ID")
        void shouldRejectNullTenantId() {
            AepEngine.Event event = createTestEvent();
            
            assertThatThrownBy(() -> engine.process(null, event))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId must not be null");
        }

        @Test
        @DisplayName("should reject blank tenant ID")
        void shouldRejectBlankTenantId() {
            AepEngine.Event event = createTestEvent();
            
            assertThatThrownBy(() -> engine.process("   ", event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId must not be blank");
        }

        @Test
        @DisplayName("should handle processing errors gracefully")
        void shouldHandleProcessingErrorsGracefully() {
            AepEngine.Event invalidEvent = AepEngine.Event.builder()
                .type("invalid.type")
                .payload(Map.of("data", "test"))
                .timestamp(Instant.now())
                .build();
            
            AepEngine.ProcessingResult result = runPromise(() -> engine.process("test-tenant", invalidEvent));
            
            assertThat(result).isNotNull();
            assertThat(result.success()).isFalse();
            assertThat(result.errorMessage()).isNotEmpty();
        }

        @Test
        @DisplayName("should validate event tenant context")
        void shouldValidateEventTenantContext() {
            AepEngine.Event event = AepEngine.Event.builder()
                .type("test.event")
                .payload(Map.of("tenantId", "different-tenant"))
                .timestamp(Instant.now())
                .build();
            
            assertThatThrownBy(() -> engine.process("test-tenant", event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Event tenant context mismatch");
        }
    }

    @Nested
    @DisplayName("Pattern Management")
    class PatternManagement {

        @Test
        @DisplayName("should register and retrieve patterns")
        void shouldRegisterAndRetrievePatterns() {
            AepEngine.Pattern pattern = createTestPattern();
            
            engine.registerPattern("test-tenant", pattern);
            
            Optional<AepEngine.Pattern> retrieved = engine.getPattern("test-tenant", pattern.id());
            assertThat(retrieved).isPresent();
            assertThat(retrieved.get().id()).isEqualTo(pattern.id());
        }

        @Test
        @DisplayName("should list patterns by tenant")
        void shouldListPatternsByTenant() {
            AepEngine.Pattern pattern1 = createTestPattern("pattern-1");
            AepEngine.Pattern pattern2 = createTestPattern("pattern-2");
            
            engine.registerPattern("test-tenant", pattern1);
            engine.registerPattern("test-tenant", pattern2);
            
            List<AepEngine.Pattern> patterns = engine.listPatterns("test-tenant");
            assertThat(patterns).hasSize(2);
            assertThat(patterns).extracting(AepEngine.Pattern::id)
                .containsExactlyInAnyOrder("pattern-1", "pattern-2");
        }

        @Test
        @DisplayName("should remove patterns")
        void shouldRemovePatterns() {
            AepEngine.Pattern pattern = createTestPattern();
            
            engine.registerPattern("test-tenant", pattern);
            assertThat(engine.getPattern("test-tenant", pattern.id())).isPresent();
            
            engine.removePattern("test-tenant", pattern.id());
            assertThat(engine.getPattern("test-tenant", pattern.id())).isEmpty();
        }

        @Test
        @DisplayName("should reject invalid pattern registration")
        void shouldRejectInvalidPatternRegistration() {
            AepEngine.Pattern invalidPattern = AepEngine.Pattern.builder()
                .id("")  // Invalid empty ID
                .name("Invalid Pattern")
                .eventType("test.event")
                .condition("data.value > 0")
                .build();
            
            assertThatThrownBy(() -> engine.registerPattern("test-tenant", invalidPattern))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pattern ID must not be blank");
        }
    }

    @Nested
    @DisplayName("Pipeline Execution")
    class PipelineExecution {

        @Test
        @DisplayName("should execute simple pipeline")
        void shouldExecuteSimplePipeline() {
            AepEngine.Pipeline pipeline = AepEngine.Pipeline.builder()
                .id("simple-pipeline")
                .name("Simple Pipeline")
                .step(createTestStep("step-1"))
                .build();
            
            engine.submitPipeline("test-tenant", pipeline);
            
            // Pipeline execution should complete without errors
            assertThat(engine.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("should execute pipeline with dependencies")
        void shouldExecutePipelineWithDependencies() {
            AepEngine.Pipeline pipeline = AepEngine.Pipeline.builder()
                .id("dependency-pipeline")
                .name("Dependency Pipeline")
                .step(createTestStep("step-1"))
                .step(createTestStep("step-2", List.of("step-1")))
                .step(createTestStep("step-3", List.of("step-2")))
                .build();
            
            engine.submitPipeline("test-tenant", pipeline);
            
            assertThat(engine.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("should reject pipeline with cycles")
        void shouldRejectPipelineWithCycles() {
            AepEngine.Pipeline pipeline = AepEngine.Pipeline.builder()
                .id("cyclic-pipeline")
                .name("Cyclic Pipeline")
                .step(createTestStep("step-1", List.of("step-2")))
                .step(createTestStep("step-2", List.of("step-1")))
                .build();
            
            assertThatThrownBy(() -> engine.submitPipeline("test-tenant", pipeline))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycles");
        }

        @Test
        @DisplayName("should handle pipeline step failures")
        void shouldHandlePipelineStepFailures() {
            AepEngine.Pipeline pipeline = AepEngine.Pipeline.builder()
                .id("failing-pipeline")
                .name("Failing Pipeline")
                .step(createFailingStep("fail-step"))
                .build();
            
            assertThatThrownBy(() -> engine.submitPipeline("test-tenant", pipeline))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Pipeline execution failed");
        }
    }

    @Nested
    @DisplayName("Subscription Management")
    class SubscriptionManagement {

        @Test
        @DisplayName("should create and manage subscriptions")
        void shouldCreateAndManageSubscriptions() {
            AepEngine.Pattern pattern = createTestPattern();
            engine.registerPattern("test-tenant", pattern);
            
            AtomicReference<AepEngine.Detection> detection = new AtomicReference<>();
            AepEngine.Subscription subscription = engine.subscribe(
                "test-tenant", 
                pattern.id(), 
                detection::set
            );
            
            assertThat(subscription).isNotNull();
            assertThat(subscription.isActive()).isTrue();
            
            subscription.cancel();
            assertThat(subscription.isActive()).isFalse();
        }

        @Test
        @DisplayName("should reject subscription for non-existent pattern")
        void shouldRejectSubscriptionForNonExistentPattern() {
            assertThatThrownBy(() -> engine.subscribe(
                "test-tenant", 
                "non-existent-pattern", 
                detection -> {}
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Pattern not found");
        }

        @Test
        @DisplayName("should handle multiple subscriptions for same pattern")
        void shouldHandleMultipleSubscriptionsForSamePattern() {
            AepEngine.Pattern pattern = createTestPattern();
            engine.registerPattern("test-tenant", pattern);
            
            AepEngine.Subscription sub1 = engine.subscribe("test-tenant", pattern.id(), detection -> {});
            AepEngine.Subscription sub2 = engine.subscribe("test-tenant", pattern.id(), detection -> {});
            
            assertThat(sub1.isActive()).isTrue();
            assertThat(sub2.isActive()).isTrue();
            
            sub1.cancel();
            sub2.cancel();
        }
    }

    @Nested
    @DisplayName("Health and Metrics")
    class HealthAndMetrics {

        @Test
        @DisplayName("should report health status")
        void shouldReportHealthStatus() {
            assertThat(engine.isHealthy()).isTrue();
            
            Map<String, Object> health = engine.health();
            assertThat(health).containsKey("status");
            assertThat(health).containsKey("timestamp");
            assertThat(health.get("status")).isEqualTo("UP");
        }

        @Test
        @DisplayName("should provide metrics")
        void shouldProvideMetrics() {
            AepEngine.Event event = createTestEvent();
            runPromise(() -> engine.process("test-tenant", event));
            
            Map<String, Object> metrics = engine.getMetrics();
            assertThat(metrics).containsKey("events_processed");
            assertThat(metrics).containsKey("events_failed");
            assertThat(metrics).containsKey("patterns_registered");
            assertThat(metrics).containsKey("active_subscriptions");
        }

        @Test
        @DisplayName("should track processing time metrics")
        void shouldTrackProcessingTimeMetrics() {
            AepEngine.Event event = createTestEvent();
            runPromise(() -> engine.process("test-tenant", event));
            
            Map<String, Object> metrics = engine.getMetrics();
            assertThat(metrics.get("avg_processing_time_ms")).isInstanceOf(Double.class);
            assertThat((Double) metrics.get("avg_processing_time_ms")).isGreaterThan(0.0);
        }
    }

    @Nested
    @DisplayName("Configuration and Tuning")
    class ConfigurationAndTuning {

        @Test
        @DisplayName("should respect configuration limits")
        void shouldRespectConfigurationLimits() {
            AepConfig config = AepConfig.builder()
                .maxPipelinesPerTenant(2)
                .build();
            
            AepEngine limitedEngine = Aep.create(config);
            
            // Register more patterns than allowed
            for (int i = 0; i < 5; i++) {
                limitedEngine.registerPattern("test-tenant", createTestPattern("pattern-" + i));
            }
            
            List<AepEngine.Pattern> patterns = limitedEngine.listPatterns("test-tenant");
            // Should be limited by configuration
            assertThat(patterns.size()).isLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should handle configuration validation")
        void shouldHandleConfigurationValidation() {
            AepConfig invalidConfig = AepConfig.builder()
                .workerThreads(-1)  // Invalid negative value
                .build();
            
            assertThatThrownBy(() -> Aep.create(invalidConfig))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // Helper methods

    private AepEngine.Event createTestEvent() {
        return AepEngine.Event.builder()
            .type("test.event")
            .payload(Map.of(
                "id", "test-id",
                "value", 42,
                "timestamp", Instant.now().toString()
            ))
            .timestamp(Instant.now())
            .build();
    }

    private AepEngine.Pattern createTestPattern() {
        return createTestPattern("test-pattern");
    }

    private AepEngine.Pattern createTestPattern(String id) {
        return AepEngine.Pattern.builder()
            .id(id)
            .name("Test Pattern")
            .description("Pattern for testing")
            .eventType("test.event")
            .condition("data.value > 0")
            .action("log")
            .enabled(true)
            .build();
    }

    private AepEngine.PipelineStep createTestStep(String id) {
        return createTestStep(id, List.of());
    }

    private AepEngine.PipelineStep createTestStep(String id, List<String> dependencies) {
        return AepEngine.PipelineStep.builder()
            .id(id)
            .name("Test Step " + id)
            .type("transform")
            .config(Map.of("operation", "uppercase"))
            .dependsOn(dependencies)
            .build();
    }

    private AepEngine.PipelineStep createFailingStep(String id) {
        return AepEngine.PipelineStep.builder()
            .id(id)
            .name("Failing Step")
            .type("transform")
            .config(Map.of("operation", "fail"))
            .build();
    }
}
