package com.ghatana.yappc.ai.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.router.*;
import com.ghatana.yappc.ai.router.AIRequest.TaskType;
import io.activej.promise.Promise;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AIRouterOutputGenerator} — the adapter that bridges
 * YAPPC SDLC agents with the multi-model AI routing system.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AI router output generation and task type routing
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AIRouterOutputGenerator Tests")
class AIRouterOutputGeneratorTest extends EventloopTestBase {

  private AIModelRouter router;
  private PromptTemplateEngine templateEngine;
  private AIRouterOutputGenerator<String, String> generator;

  @BeforeEach
  void setUp() {
    router = mock(AIModelRouter.class);
    templateEngine = mock(PromptTemplateEngine.class);

    @SuppressWarnings("unchecked")
    ResultMapper<String> mapper = mock(ResultMapper.class);
    when(mapper.mapResponse(any(), any())).thenReturn("mapped-result");

    when(templateEngine.buildPrompt(any(), any())).thenReturn("generated prompt");

    generator = new AIRouterOutputGenerator<>(router, templateEngine, mapper);
  }

  // ===== Basic Generation Tests =====

  @Nested
  @DisplayName("Generate Output")
  class GenerateOutput {

    @Test
    @DisplayName("Should generate output by routing through AI model router")
    void shouldGenerateOutput() {
      AIResponse mockResponse = createMockResponse("gpt-4", 200);
      when(router.route(any())).thenReturn(Promise.of(mockResponse));

      Map<String, Object> context = new HashMap<>();
      context.put("stepName", "implement-api");

      String result = runPromise(() -> generator.generate("build REST API", context));

      assertThat(result).isEqualTo("mapped-result");
    }

    @Test
    @DisplayName("Should propagate error when router fails")
    void shouldPropagateRouterError() {
      when(router.route(any())).thenReturn(
          Promise.ofException(new RuntimeException("Router failure")));

      Map<String, Object> context = Map.of("stepName", "test");

      try {
        runPromise(() -> generator.generate("input", context));
        assertThat(false).as("Should have thrown").isTrue();
      } catch (Exception e) {
        assertThat(e.getMessage()).contains("Router failure");
      }
    }
  }

  // ===== Task Type Routing Tests =====

  @Nested
  @DisplayName("Task Type Determination")
  class TaskTypeDetermination {

    @Test
    @DisplayName("Should route implementation steps to CODE_GENERATION")
    void shouldRouteImplementToCodeGen() {
      AIResponse mockResponse = createMockResponse("gpt-4", 200);
      when(router.route(any())).thenAnswer(invocation -> {
        AIRequest req = invocation.getArgument(0);
        assertThat(req.getTaskType()).isEqualTo(TaskType.CODE_GENERATION);
        return Promise.of(mockResponse);
      });

      Map<String, Object> context = new HashMap<>();
      context.put("stepName", "implement-api");

      runPromise(() -> generator.generate("build code", context));
    }

    @Test
    @DisplayName("Should route analysis steps to CODE_ANALYSIS")
    void shouldRouteAnalyzeToCodeAnalysis() {
      AIResponse mockResponse = createMockResponse("claude-3", 150);
      when(router.route(any())).thenAnswer(invocation -> {
        AIRequest req = invocation.getArgument(0);
        assertThat(req.getTaskType()).isEqualTo(TaskType.CODE_ANALYSIS);
        return Promise.of(mockResponse);
      });

      Map<String, Object> context = new HashMap<>();
      context.put("stepName", "analyze-architecture");

      runPromise(() -> generator.generate("review code", context));
    }

    @Test
    @DisplayName("Should route test steps to TEST_GENERATION")
    void shouldRouteTestToTestGen() {
      AIResponse mockResponse = createMockResponse("gpt-4", 200);
      when(router.route(any())).thenAnswer(invocation -> {
        AIRequest req = invocation.getArgument(0);
        assertThat(req.getTaskType()).isEqualTo(TaskType.TEST_GENERATION);
        return Promise.of(mockResponse);
      });

      Map<String, Object> context = new HashMap<>();
      context.put("stepName", "test-generation");

      runPromise(() -> generator.generate("generate tests", context));
    }

    @Test
    @DisplayName("Should default to GENERAL for unknown step names")
    void shouldDefaultToGeneral() {
      AIResponse mockResponse = createMockResponse("gpt-4", 200);
      when(router.route(any())).thenAnswer(invocation -> {
        AIRequest req = invocation.getArgument(0);
        assertThat(req.getTaskType()).isEqualTo(TaskType.GENERAL);
        return Promise.of(mockResponse);
      });

      Map<String, Object> context = new HashMap<>();
      context.put("stepName", "unknown-step");

      runPromise(() -> generator.generate("do something", context));
    }
  }

  // ===== Accessor Tests =====

  @Nested
  @DisplayName("Accessors")
  class Accessors {

    @Test
    @DisplayName("Should expose underlying router")
    void shouldExposeRouter() {
      assertThat(generator.getRouter()).isSameAs(router);
    }

    @Test
    @DisplayName("Should expose cache statistics")
    void shouldExposeCacheStats() {
      CacheStatistics mockStats = mock(CacheStatistics.class);
      when(router.getCacheStatistics()).thenReturn(mockStats);

      assertThat(generator.getCacheStatistics()).isSameAs(mockStats);
    }
  }

  // ===== Test Helpers =====

  private AIResponse createMockResponse(String modelId, long latencyMs) {
    AIResponse response = mock(AIResponse.class);
    when(response.getModelId()).thenReturn(modelId);
    when(response.isCacheHit()).thenReturn(false);

    AIResponse.ResponseMetrics metrics = mock(AIResponse.ResponseMetrics.class);
    when(metrics.getLatencyMs()).thenReturn(latencyMs);
    when(response.getMetrics()).thenReturn(metrics);

    return response;
  }
}
