package com.ghatana.yappc.ai.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.metrics.AIMetricsCollector;
import com.ghatana.yappc.ai.router.AIModelRouter;
import com.ghatana.yappc.ai.router.AIRequest.TaskType;
import com.ghatana.yappc.ai.router.AIResponse;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration-style tests for the AI call path:
 * {@code YAPPCAIService → AIModelRouter → ModelAdapter → AIResponse}.
 *
 * <p>Uses a mocked {@link AIModelRouter} to keep tests deterministic
 * without a live LLM or Testcontainers setup.
 *
 * @doc.type class
 * @doc.purpose End-to-end call path verification for the YAPPC AI service facade
 * @doc.layer product
 * @doc.pattern Integration Test
 */
@DisplayName("AI Call Path Integration Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class AICallPathTest extends EventloopTestBase {

    @Mock
    private AIModelRouter router;

    private YAPPCAIService service;
    private AIMetricsCollector metricsCollector;

    @BeforeEach
    void setUp() { // GH-90000
        metricsCollector = new AIMetricsCollector(new SimpleMeterRegistry()); // GH-90000
        service = YAPPCAIService.builder() // GH-90000
            .router(router) // GH-90000
            .metricsCollector(metricsCollector) // GH-90000
            .build(); // GH-90000

        lenient().when(router.initialize()).thenReturn(Promise.of(null)); // GH-90000
        lenient().when(router.shutdown()).thenReturn(Promise.of(null)); // GH-90000
    }

    // ── initialize ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initialize delegates to router and marks service ready [GH-90000]")
    void initialize_delegatesToRouter() { // GH-90000
        runPromise(() -> service.initialize()); // GH-90000
        verify(router).initialize(); // GH-90000
    }

    // ── generateCode ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateCode routes CODE_GENERATION request and returns content [GH-90000]")
    void generateCode_routesRequest_returnsContent() { // GH-90000
        AIResponse fakeResponse = buildResponse("CODE_GENERATION", "public class Foo {}"); // GH-90000
        when(router.route(any())).thenReturn(Promise.of(fakeResponse)); // GH-90000
        runPromise(() -> service.initialize()); // GH-90000

        String code = runPromise(() -> service.generateCode("Generate a Foo class [GH-90000]"));

        assertThat(code).contains("class Foo [GH-90000]");
        verify(router).route(argThat(req -> // GH-90000
            req.getTaskType() == TaskType.CODE_GENERATION && // GH-90000
            req.getPrompt().contains("Generate a Foo class [GH-90000]")));
    }

    // ── analyzeCode ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("analyzeCode routes CODE_ANALYSIS request and parses summary [GH-90000]")
    void analyzeCode_routesRequest_parsesSummary() { // GH-90000
        AIResponse fakeResponse = buildResponse("CODE_ANALYSIS", "No issues found."); // GH-90000
        when(router.route(any())).thenReturn(Promise.of(fakeResponse)); // GH-90000
        runPromise(() -> service.initialize()); // GH-90000

        YAPPCAIService.CodeAnalysis analysis = runPromise(() -> service.analyzeCode("int x = 0; [GH-90000]"));

        assertThat(analysis.getSummary()).isEqualTo("No issues found. [GH-90000]");
        verify(router).route(argThat(req -> req.getTaskType() == TaskType.CODE_ANALYSIS)); // GH-90000
    }

    // ── generateTests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateTests routes TEST_GENERATION request and returns trimmed code [GH-90000]")
    void generateTests_routesRequest_returnsCode() { // GH-90000
        AIResponse fakeResponse = buildResponse("TEST_GENERATION", // GH-90000
            "@Test void shouldPass() { assertThat(1).isEqualTo(1); }"); // GH-90000
        when(router.route(any())).thenReturn(Promise.of(fakeResponse)); // GH-90000
        runPromise(() -> service.initialize()); // GH-90000

        String tests = runPromise(() -> service.generateTests("class under test [GH-90000]"));

        assertThat(tests).contains("shouldPass [GH-90000]");
        verify(router).route(argThat(req -> req.getTaskType() == TaskType.TEST_GENERATION)); // GH-90000
    }

    // ── error propagation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("generateCode propagates router failure as Promise exception [GH-90000]")
    void generateCode_propagatesRouterFailure() { // GH-90000
        when(router.route(any())) // GH-90000
            .thenReturn(Promise.ofException(new RuntimeException("LLM timeout [GH-90000]")));
        runPromise(() -> service.initialize()); // GH-90000

        assertThatThrownBy(() -> runPromise(() -> service.generateCode("anything [GH-90000]")))
            .hasMessageContaining("LLM timeout [GH-90000]");
    }

    @Test
    @DisplayName("service not initialized throws IllegalStateException [GH-90000]")
    void notInitialized_throwsIllegalState() { // GH-90000
        // NOTE: do NOT call service.initialize() here // GH-90000
        assertThatThrownBy(() -> runPromise(() -> service.generateCode("anything [GH-90000]")))
            .isInstanceOf(IllegalStateException.class) // GH-90000
            .hasMessageContaining("not initialized [GH-90000]");
    }

    // ── metrics integration ───────────────────────────────────────────────────

    @Test
    @DisplayName("successful call records LLM call metric via AIMetricsCollector [GH-90000]")
    void successfulCall_recordsMetric() { // GH-90000
        AIResponse fakeResponse = buildResponse("CODE_GENERATION", "result"); // GH-90000
        when(router.route(any())).thenReturn(Promise.of(fakeResponse)); // GH-90000

        // Use a spy to verify metric recording without stubbing all internals
        AIMetricsCollector spyCollector = spy(new AIMetricsCollector(new SimpleMeterRegistry())); // GH-90000
        YAPPCAIService monitoredService = YAPPCAIService.builder() // GH-90000
            .router(router) // GH-90000
            .metricsCollector(spyCollector) // GH-90000
            .build(); // GH-90000

        runPromise(() -> monitoredService.initialize()); // GH-90000
        runPromise(() -> monitoredService.generateCode("test [GH-90000]"));

        verify(spyCollector).recordLlmCall( // GH-90000
            anyString(), anyString(), anyString(), // GH-90000
            eq(true), anyLong(), anyInt(), anyInt(), anyDouble()); // GH-90000
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static AIResponse buildResponse(String modelId, String content) { // GH-90000
        return AIResponse.builder() // GH-90000
            .requestId("req-test [GH-90000]")
            .modelId(modelId) // GH-90000
            .content(content) // GH-90000
            .metrics(AIResponse.ResponseMetrics.builder() // GH-90000
                .latencyMs(42) // GH-90000
                .promptTokens(30) // GH-90000
                .completionTokens(20) // GH-90000
                .cost(0.001) // GH-90000
                .build()) // GH-90000
            .cacheHit(false) // GH-90000
            .build(); // GH-90000
    }
}
