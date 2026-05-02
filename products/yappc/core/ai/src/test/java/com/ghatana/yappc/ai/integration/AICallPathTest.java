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
@DisplayName("AI Call Path Integration Tests")
@ExtendWith(MockitoExtension.class) 
class AICallPathTest extends EventloopTestBase {

    @Mock
    private AIModelRouter router;

    private YAPPCAIService service;
    private AIMetricsCollector metricsCollector;

    @BeforeEach
    void setUp() { 
        metricsCollector = new AIMetricsCollector(new SimpleMeterRegistry()); 
        service = YAPPCAIService.builder() 
            .router(router) 
            .metricsCollector(metricsCollector) 
            .build(); 

        lenient().when(router.initialize()).thenReturn(Promise.of(null)); 
        lenient().when(router.shutdown()).thenReturn(Promise.of(null)); 
    }

    // ── initialize ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("initialize delegates to router and marks service ready")
    void initialize_delegatesToRouter() { 
        runPromise(() -> service.initialize()); 
        verify(router).initialize(); 
    }

    // ── generateCode ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateCode routes CODE_GENERATION request and returns content")
    void generateCode_routesRequest_returnsContent() { 
        AIResponse fakeResponse = buildResponse("CODE_GENERATION", "public class Foo {}"); 
        when(router.route(any())).thenReturn(Promise.of(fakeResponse)); 
        runPromise(() -> service.initialize()); 

        String code = runPromise(() -> service.generateCode("Generate a Foo class"));

        assertThat(code).contains("class Foo");
        verify(router).route(argThat(req -> 
            req.getTaskType() == TaskType.CODE_GENERATION && 
            req.getPrompt().contains("Generate a Foo class")));
    }

    // ── analyzeCode ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("analyzeCode routes CODE_ANALYSIS request and parses summary")
    void analyzeCode_routesRequest_parsesSummary() { 
        AIResponse fakeResponse = buildResponse("CODE_ANALYSIS", "No issues found. Code structure is clear, naming is consistent, and there are no security or performance concerns in this snippet."); 
        when(router.route(any())).thenReturn(Promise.of(fakeResponse)); 
        runPromise(() -> service.initialize()); 

        YAPPCAIService.CodeAnalysis analysis = runPromise(() -> service.analyzeCode("int x = 0;"));

        assertThat(analysis.getSummary()).contains("No issues found.");
        verify(router).route(argThat(req -> req.getTaskType() == TaskType.CODE_ANALYSIS)); 
    }

    // ── generateTests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateTests routes TEST_GENERATION request and returns trimmed code")
    void generateTests_routesRequest_returnsCode() { 
        AIResponse fakeResponse = buildResponse("TEST_GENERATION", 
            "@Test void shouldPass() { assertThat(1).isEqualTo(1); }"); 
        when(router.route(any())).thenReturn(Promise.of(fakeResponse)); 
        runPromise(() -> service.initialize()); 

        String tests = runPromise(() -> service.generateTests("class under test"));

        assertThat(tests).contains("shouldPass");
        verify(router).route(argThat(req -> req.getTaskType() == TaskType.TEST_GENERATION)); 
    }

    // ── error propagation ─────────────────────────────────────────────────────

    @Test
    @DisplayName("generateCode propagates router failure as Promise exception")
    void generateCode_propagatesRouterFailure() { 
        when(router.route(any())) 
            .thenReturn(Promise.ofException(new RuntimeException("LLM timeout")));
        runPromise(() -> service.initialize()); 

        assertThatThrownBy(() -> runPromise(() -> service.generateCode("anything")))
            .hasMessageContaining("LLM timeout");
    }

    @Test
    @DisplayName("service not initialized throws IllegalStateException")
    void notInitialized_throwsIllegalState() { 
        // NOTE: do NOT call service.initialize() here 
        assertThatThrownBy(() -> runPromise(() -> service.generateCode("anything")))
            .isInstanceOf(IllegalStateException.class) 
            .hasMessageContaining("not initialized");
    }

    // ── metrics integration ───────────────────────────────────────────────────

    @Test
    @DisplayName("successful call records LLM call metric via AIMetricsCollector")
    void successfulCall_recordsMetric() { 
        AIResponse fakeResponse = buildResponse("CODE_GENERATION", "public class GeneratedService { public String run() { return \"ok\"; } }"); 
        when(router.route(any())).thenReturn(Promise.of(fakeResponse)); 

        // Use a spy to verify metric recording without stubbing all internals
        AIMetricsCollector spyCollector = spy(new AIMetricsCollector(new SimpleMeterRegistry())); 
        YAPPCAIService monitoredService = YAPPCAIService.builder() 
            .router(router) 
            .metricsCollector(spyCollector) 
            .build(); 

        runPromise(() -> monitoredService.initialize()); 
        runPromise(() -> monitoredService.generateCode("test"));

        verify(spyCollector).recordLlmCall( 
            anyString(), anyString(), anyString(), 
            eq(true), anyLong(), anyInt(), anyInt(), anyDouble()); 
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static AIResponse buildResponse(String modelId, String content) { 
        return AIResponse.builder() 
            .requestId("req-test")
            .modelId(modelId) 
            .content(content) 
            .metrics(AIResponse.ResponseMetrics.builder() 
                .latencyMs(42) 
                .promptTokens(30) 
                .completionTokens(20) 
                .cost(0.001) 
                .build()) 
            .cacheHit(false) 
            .build(); 
    }
}
