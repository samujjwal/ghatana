package com.ghatana.yappc.services.observability;

import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.audit.AuditLogger;
import com.ghatana.governance.PolicyEngine;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.domain.intent.IntentInput;
import com.ghatana.yappc.domain.intent.IntentSpec;
import com.ghatana.yappc.domain.run.RunResult;
import com.ghatana.yappc.domain.run.RunStatus;
import com.ghatana.yappc.domain.shape.ShapeSpec;
import com.ghatana.yappc.services.intent.IntentService;
import com.ghatana.yappc.services.intent.IntentServiceImpl;
import com.ghatana.yappc.services.learn.LearningService;
import com.ghatana.yappc.services.learn.LearningServiceImpl;
import com.ghatana.yappc.services.observe.ObserveService;
import com.ghatana.yappc.services.observe.ObserveServiceImpl;
import com.ghatana.yappc.services.shape.ShapeService;
import com.ghatana.yappc.services.shape.ShapeServiceImpl;
import com.ghatana.yappc.services.validate.ValidationService;
import com.ghatana.yappc.services.validate.ValidationServiceImpl;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Observability validation tests for YAPPC lifecycle phase services.
 *
 * <p>Asserts that every lifecycle phase service:
 * <ul>
 *   <li>Calls {@code MetricsCollector.recordTimer()} on each successful operation</li>
 *   <li>Calls {@code MetricsCollector.incrementCounter()} with a {@code ".success"} metric on success</li>
 *   <li>Calls {@code MetricsCollector.incrementCounter()} with a {@code ".error"} metric on LLM failure</li>
 *   <li>Calls {@code AuditLogger.log()} at least once per operation</li>
 * </ul>
 *
 * <p>These tests confirm that observability hooks are wired — not that specific Prometheus
 * metric names are registered in a running registry. Use {@link BusinessMetricsTest} for
 * MeterRegistry integration assertions.
 *
 * @doc.type class
 * @doc.purpose Observability validation: verify metrics and audit hooks are invoked per lifecycle phase
 * @doc.layer test
 * @doc.pattern Test
 */
@DisplayName("YAPPC Lifecycle Phase Observability Validation")
class YappcLifecycleObservabilityTest extends EventloopTestBase {

    private CompletionService aiService;
    private AuditLogger auditLogger;
    private MetricsCollector metrics;
    private PolicyEngine policyEngine;

    @BeforeEach
    void setUp() {
        aiService    = mock(CompletionService.class);
        auditLogger  = mock(AuditLogger.class);
        metrics      = mock(MetricsCollector.class);
        policyEngine = mock(PolicyEngine.class);

        when(aiService.complete(any(CompletionRequest.class)))
                .thenReturn(Promise.of(CompletionResult.builder()
                        .text("Product: Obs Test\nDescription: observability test fixture")
                        .modelUsed("gpt-4")
                        .build()));
        when(auditLogger.log(anyMap())).thenReturn(Promise.complete());
        when(policyEngine.evaluate(any(), any())).thenReturn(Promise.of(true));
    }

    // =========================================================================
    // Phase 0: Intent
    // =========================================================================

    @Nested
    @DisplayName("Phase 0 — Intent metrics")
    class IntentObservability {

        private IntentService service;

        @BeforeEach
        void wire() {
            service = new IntentServiceImpl(aiService, auditLogger, metrics);
        }

        @Test
        @DisplayName("capture() records a timer metric on success")
        void captureRecordsTimer() {
            runPromise(() -> service.capture(IntentInput.of("Build auth service", "obs-tenant")));

            verify(metrics, atLeastOnce()).recordTimer(eq("yappc.intent.capture"), anyLong(), anyMap());
        }

        @Test
        @DisplayName("capture() increments success counter on success")
        void captureIncrementsSuccessCounter() {
            runPromise(() -> service.capture(IntentInput.of("Build auth service", "obs-tenant")));

            verify(metrics, atLeastOnce()).incrementCounter(eq("yappc.intent.capture.success"), anyMap());
        }

        @Test
        @DisplayName("capture() increments error counter when AI service fails")
        void captureIncrementsErrorCounterOnLlmFailure() {
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("llm down")));

            // Capture the failed promise — don't runPromise() to avoid unwrapping exception
            Promise<?> failed = service.capture(IntentInput.of("Build auth service", "obs-tenant"));
            assertThat(failed.isException()).isTrue();

            verify(metrics, atLeastOnce()).incrementCounter(eq("yappc.intent.capture.error"), anyMap());
        }

        @Test
        @DisplayName("capture() always emits an audit log event")
        void captureEmitsAuditLog() {
            runPromise(() -> service.capture(IntentInput.of("Build auth service", "obs-tenant")));

            verify(auditLogger, atLeastOnce()).log(anyMap());
        }

        @Test
        @DisplayName("analyze() records a timer metric on success")
        void analyzeRecordsTimer() {
            IntentSpec spec = runPromise(() -> service.capture(IntentInput.of("Build auth service", "obs-tenant")));
            runPromise(() -> service.analyze(spec));

            verify(metrics, atLeastOnce()).recordTimer(eq("yappc.intent.analyze"), anyLong(), anyMap());
        }
    }

    // =========================================================================
    // Phase 1: Shape
    // =========================================================================

    @Nested
    @DisplayName("Phase 1 — Shape metrics")
    class ShapeObservability {

        private IntentService intentService;
        private ShapeService shapeService;

        @BeforeEach
        void wire() {
            intentService = new IntentServiceImpl(aiService, auditLogger, metrics);
            shapeService  = new ShapeServiceImpl(aiService, auditLogger, metrics);
        }

        @Test
        @DisplayName("derive() records a timer metric on success")
        void deriveRecordsTimer() {
            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build analytics", "obs-shape-tenant")));
            runPromise(() -> shapeService.derive(intent));

            verify(metrics, atLeastOnce()).recordTimer(eq("yappc.shape.derive"), anyLong(), anyMap());
        }

        @Test
        @DisplayName("derive() increments success counter on success")
        void deriveIncrementsSuccessCounter() {
            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build analytics", "obs-shape-tenant")));
            runPromise(() -> shapeService.derive(intent));

            verify(metrics, atLeastOnce()).incrementCounter(eq("yappc.shape.derive.success"), anyMap());
        }

        @Test
        @DisplayName("derive() increments error counter on LLM failure")
        void deriveIncrementsErrorCounterOnLlmFailure() {
            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build analytics", "obs-shape-tenant")));
            when(aiService.complete(any(CompletionRequest.class)))
                    .thenReturn(Promise.ofException(new RuntimeException("shape llm down")));

            Promise<?> failed = shapeService.derive(intent);
            assertThat(failed.isException()).isTrue();

            verify(metrics, atLeastOnce()).incrementCounter(eq("yappc.shape.derive.error"), anyMap());
        }
    }

    // =========================================================================
    // Phase 2: Validate
    // =========================================================================

    @Nested
    @DisplayName("Phase 2 — Validate metrics")
    class ValidateObservability {

        private IntentService intentService;
        private ShapeService shapeService;
        private ValidationService validationService;

        @BeforeEach
        void wire() {
            intentService    = new IntentServiceImpl(aiService, auditLogger, metrics);
            shapeService     = new ShapeServiceImpl(aiService, auditLogger, metrics);
            validationService = new ValidationServiceImpl(policyEngine, auditLogger, metrics);
        }

        @Test
        @DisplayName("validate() records a timer metric on success")
        void validateRecordsTimer() {
            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build auth", "obs-validate-tenant")));
            ShapeSpec shape = runPromise(() -> shapeService.derive(intent));
            runPromise(() -> validationService.validate(shape));

            verify(metrics, atLeastOnce()).recordTimer(eq("yappc.validate"), anyLong(), anyMap());
        }

        @Test
        @DisplayName("validate() increments success counter on success")
        void validateIncrementsSuccessCounter() {
            IntentSpec intent = runPromise(() ->
                    intentService.capture(IntentInput.of("Build auth", "obs-validate-tenant")));
            ShapeSpec shape = runPromise(() -> shapeService.derive(intent));
            runPromise(() -> validationService.validate(shape));

            verify(metrics, atLeastOnce()).incrementCounter(eq("yappc.validate.success"), anyMap());
        }
    }

    // =========================================================================
    // Phase 6: Learn
    // =========================================================================

    @Nested
    @DisplayName("Phase 6 — Learn metrics")
    class LearnObservability {

        private ObserveService observeService;
        private LearningService learningService;

        @BeforeEach
        void wire() {
            observeService  = new ObserveServiceImpl(metrics, auditLogger);
            learningService = new LearningServiceImpl(aiService, auditLogger, metrics);
        }

        @Test
        @DisplayName("analyze() records a timer metric on success")
        void analyzeRecordsTimer() {
            RunResult run = RunResult.builder()
                    .id("run-obs-001")
                    .runSpecRef("spec-obs-001")
                    .status(RunStatus.SUCCEEDED)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(5))
                    .completedAt(Instant.now())
                    .metadata(Map.of())
                    .build();
            var obs = runPromise(() -> observeService.collect(run));
            runPromise(() -> learningService.analyze(obs));

            verify(metrics, atLeastOnce()).recordTimer(eq("yappc.learn.analyze"), anyLong(), anyMap());
        }

        @Test
        @DisplayName("analyze() increments success counter on success")
        void analyzeIncrementsSuccessCounter() {
            RunResult run = RunResult.builder()
                    .id("run-obs-002")
                    .runSpecRef("spec-obs-002")
                    .status(RunStatus.SUCCEEDED)
                    .taskResults(List.of())
                    .startedAt(Instant.now().minusSeconds(5))
                    .completedAt(Instant.now())
                    .metadata(Map.of())
                    .build();
            var obs = runPromise(() -> observeService.collect(run));
            runPromise(() -> learningService.analyze(obs));

            verify(metrics, atLeastOnce()).incrementCounter(eq("yappc.learn.analyze.success"), anyMap());
        }
    }
}
