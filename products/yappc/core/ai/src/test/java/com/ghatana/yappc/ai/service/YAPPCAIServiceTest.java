package com.ghatana.yappc.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.router.AIModelRouter;
import com.ghatana.yappc.ai.router.AIRequest;
import com.ghatana.yappc.ai.router.AIResponse;
import com.ghatana.yappc.ai.router.CacheStatistics;
import com.ghatana.yappc.ai.router.ModelConfig;
import io.activej.promise.Promise;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test suite for YAPPCAIService.
 * 
 * @doc.type test
 * @doc.purpose AI service functionality validation
 */
/**
 * @doc.type class
 * @doc.purpose Handles yappcai service test operations
 * @doc.layer core
 * @doc.pattern Test
 */
public class YAPPCAIServiceTest extends EventloopTestBase {

    private AIModelRouter router;
    private YAPPCAIService aiService;

    @BeforeEach
    void setUp() {
        router = mock(AIModelRouter.class);
        when(router.initialize()).thenReturn(Promise.complete());
        when(router.shutdown()).thenReturn(Promise.complete());
        when(router.getAvailableModels()).thenReturn(Map.of(
            "llama3.2", ModelConfig.builder()
                .modelId("llama3.2")
                .displayName("Llama 3.2")
                .provider("ollama")
                .build()));

        aiService = YAPPCAIService.builder()
            .router(router)
            .build();

        runPromise(aiService::initialize);
    }

    @Test
    @DisplayName("Should initialize through injected router")
    void shouldInitializeThroughInjectedRouter() {
        verify(router).initialize();
        assertThat(aiService.getAvailableModels()).containsKey("llama3.2");
    }

    @Test
    @DisplayName("Should generate code through router and strip markdown fences")
    void shouldGenerateCodeThroughRouterAndStripMarkdownFences() {
        when(router.route(any())).thenAnswer(invocation -> {
            AIRequest request = invocation.getArgument(0);
            assertThat(request.getTaskType()).isEqualTo(AIRequest.TaskType.CODE_GENERATION);
            assertThat(request.getContext()).containsEntry("language", "Java");
            return Promise.of(responseFor(request, "```java\npublic class Demo {}\n```"));
        });

        String result = runPromise(() -> aiService.generateCode(
            "Build a Java demo class",
            Map.of("language", "Java")));

        assertThat(result).isEqualTo("public class Demo {}");
        assertThat(aiService.getTotalRequests()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should parse code analysis content")
    void shouldParseCodeAnalysisContent() {
        when(router.route(any())).thenAnswer(invocation -> {
            AIRequest request = invocation.getArgument(0);
            assertThat(request.getTaskType()).isEqualTo(AIRequest.TaskType.CODE_ANALYSIS);
            return Promise.of(responseFor(request, "Null pointer risk in processData()"));
        });

        YAPPCAIService.CodeAnalysis analysis = runPromise(() -> aiService.analyzeCode("class Demo {}"));

        assertThat(analysis.getSummary()).contains("Null pointer risk");
        assertThat(analysis.getFindings()).containsEntry("raw", "Null pointer risk in processData()");
    }

    @Test
    @DisplayName("Should route reasoning requests and return raw content")
    void shouldRouteReasoningRequestsAndReturnRawContent() {
        when(router.route(any())).thenAnswer(invocation -> {
            AIRequest request = invocation.getArgument(0);
            assertThat(request.getTaskType()).isEqualTo(AIRequest.TaskType.REASONING);
            return Promise.of(responseFor(request, "Use a modular monolith first."));
        });

        String answer = runPromise(() -> aiService.reason("How should YAPPC structure a new feature?"));

        assertThat(answer).isEqualTo("Use a modular monolith first.");
    }

    @Test
    @DisplayName("Should expose cache statistics and output generator")
    void shouldExposeCacheStatisticsAndOutputGenerator() {
        CacheStatistics stats = mock(CacheStatistics.class);
        when(router.getCacheStatistics()).thenReturn(stats);

        assertThat(aiService.getCacheStatistics()).isSameAs(stats);
        assertThat(aiService.<Map<String, Object>, Map<String, Object>>createOutputGenerator().getRouter())
            .isSameAs(router);
    }

    private AIResponse responseFor(AIRequest request, String content) {
        return AIResponse.builder()
            .requestId(request.getRequestId())
            .modelId("codellama")
            .content(content)
            .metrics(AIResponse.ResponseMetrics.builder()
                .latencyMs(10)
                .tokenCount(25)
                .promptTokens(10)
                .completionTokens(15)
                .cost(0.0)
                .build())
            .build();
    }
}
