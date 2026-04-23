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
    void setUp() { // GH-90000
        router = mock(AIModelRouter.class); // GH-90000
        when(router.initialize()).thenReturn(Promise.complete()); // GH-90000
        when(router.shutdown()).thenReturn(Promise.complete()); // GH-90000
        when(router.getAvailableModels()).thenReturn(Map.of( // GH-90000
            "llama3.2", ModelConfig.builder() // GH-90000
                .modelId("llama3.2")
                .displayName("Llama 3.2")
                .provider("ollama")
                .build())); // GH-90000

        aiService = YAPPCAIService.builder() // GH-90000
            .router(router) // GH-90000
            .build(); // GH-90000

        runPromise(aiService::initialize); // GH-90000
    }

    @Test
    @DisplayName("Should initialize through injected router")
    void shouldInitializeThroughInjectedRouter() { // GH-90000
        verify(router).initialize(); // GH-90000
        assertThat(aiService.getAvailableModels()).containsKey("llama3.2");
    }

    @Test
    @DisplayName("Should generate code through router and strip markdown fences")
    void shouldGenerateCodeThroughRouterAndStripMarkdownFences() { // GH-90000
        when(router.route(any())).thenAnswer(invocation -> { // GH-90000
            AIRequest request = invocation.getArgument(0); // GH-90000
            assertThat(request.getTaskType()).isEqualTo(AIRequest.TaskType.CODE_GENERATION); // GH-90000
            assertThat(request.getContext()).containsEntry("language", "Java"); // GH-90000
            return Promise.of(responseFor(request, "```java\npublic class Demo {}\n```")); // GH-90000
        });

        String result = runPromise(() -> aiService.generateCode( // GH-90000
            "Build a Java demo class",
            Map.of("language", "Java"))); // GH-90000

        assertThat(result).isEqualTo("public class Demo {}");
        assertThat(aiService.getTotalRequests()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should parse code analysis content")
    void shouldParseCodeAnalysisContent() { // GH-90000
        when(router.route(any())).thenAnswer(invocation -> { // GH-90000
            AIRequest request = invocation.getArgument(0); // GH-90000
            assertThat(request.getTaskType()).isEqualTo(AIRequest.TaskType.CODE_ANALYSIS); // GH-90000
            return Promise.of(responseFor(request, "Null pointer risk in processData()")); // GH-90000
        });

        YAPPCAIService.CodeAnalysis analysis = runPromise(() -> aiService.analyzeCode("class Demo {}"));

        assertThat(analysis.getSummary()).contains("Null pointer risk");
        assertThat(analysis.getFindings()).containsEntry("raw", "Null pointer risk in processData()"); // GH-90000
    }

    @Test
    @DisplayName("Should route reasoning requests and return raw content")
    void shouldRouteReasoningRequestsAndReturnRawContent() { // GH-90000
        when(router.route(any())).thenAnswer(invocation -> { // GH-90000
            AIRequest request = invocation.getArgument(0); // GH-90000
            assertThat(request.getTaskType()).isEqualTo(AIRequest.TaskType.REASONING); // GH-90000
            return Promise.of(responseFor(request, "Use a modular monolith first.")); // GH-90000
        });

        String answer = runPromise(() -> aiService.reason("How should YAPPC structure a new feature?"));

        assertThat(answer).isEqualTo("Use a modular monolith first.");
    }

    @Test
    @DisplayName("Should expose cache statistics and output generator")
    void shouldExposeCacheStatisticsAndOutputGenerator() { // GH-90000
        CacheStatistics stats = mock(CacheStatistics.class); // GH-90000
        when(router.getCacheStatistics()).thenReturn(stats); // GH-90000

        assertThat(aiService.getCacheStatistics()).isSameAs(stats); // GH-90000
        assertThat(aiService.<Map<String, Object>, Map<String, Object>>createOutputGenerator().getRouter()) // GH-90000
            .isSameAs(router); // GH-90000
    }

    private AIResponse responseFor(AIRequest request, String content) { // GH-90000
        return AIResponse.builder() // GH-90000
            .requestId(request.getRequestId()) // GH-90000
            .modelId("codellama")
            .content(content) // GH-90000
            .metrics(AIResponse.ResponseMetrics.builder() // GH-90000
                .latencyMs(10) // GH-90000
                .tokenCount(25) // GH-90000
                .promptTokens(10) // GH-90000
                .completionTokens(15) // GH-90000
                .cost(0.0) // GH-90000
                .build()) // GH-90000
            .build(); // GH-90000
    }
}
