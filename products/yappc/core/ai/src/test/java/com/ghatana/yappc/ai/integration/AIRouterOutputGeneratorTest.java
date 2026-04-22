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
@DisplayName("AIRouterOutputGenerator Tests [GH-90000]")
class AIRouterOutputGeneratorTest extends EventloopTestBase {

  private AIModelRouter router;
  private PromptTemplateEngine templateEngine;
  private AIRouterOutputGenerator<String, String> generator;

  @BeforeEach
  void setUp() { // GH-90000
    router = mock(AIModelRouter.class); // GH-90000
    templateEngine = mock(PromptTemplateEngine.class); // GH-90000

    @SuppressWarnings("unchecked [GH-90000]")
    ResultMapper<String> mapper = mock(ResultMapper.class); // GH-90000
    when(mapper.mapResponse(any(), any())).thenReturn("mapped-result [GH-90000]");

    when(templateEngine.buildPrompt(any(), any())).thenReturn("generated prompt [GH-90000]");

    generator = new AIRouterOutputGenerator<>(router, templateEngine, mapper); // GH-90000
  }

  // ===== Basic Generation Tests =====

  @Nested
  @DisplayName("Generate Output [GH-90000]")
  class GenerateOutput {

    @Test
    @DisplayName("Should generate output by routing through AI model router [GH-90000]")
    void shouldGenerateOutput() { // GH-90000
      AIResponse mockResponse = createMockResponse("gpt-4", 200); // GH-90000
      when(router.route(any())).thenReturn(Promise.of(mockResponse)); // GH-90000

      Map<String, Object> context = new HashMap<>(); // GH-90000
      context.put("stepName", "implement-api"); // GH-90000

      String result = runPromise(() -> generator.generate("build REST API", context)); // GH-90000

      assertThat(result).isEqualTo("mapped-result [GH-90000]");
    }

    @Test
    @DisplayName("Should propagate error when router fails [GH-90000]")
    void shouldPropagateRouterError() { // GH-90000
      when(router.route(any())).thenReturn( // GH-90000
          Promise.ofException(new RuntimeException("Router failure [GH-90000]")));

      Map<String, Object> context = Map.of("stepName", "test"); // GH-90000

      try {
        runPromise(() -> generator.generate("input", context)); // GH-90000
        assertThat(false).as("Should have thrown [GH-90000]").isTrue();
      } catch (Exception e) { // GH-90000
        assertThat(e.getMessage()).contains("Router failure [GH-90000]");
      }
    }
  }

  // ===== Task Type Routing Tests =====

  @Nested
  @DisplayName("Task Type Determination [GH-90000]")
  class TaskTypeDetermination {

    @Test
    @DisplayName("Should route implementation steps to CODE_GENERATION [GH-90000]")
    void shouldRouteImplementToCodeGen() { // GH-90000
      AIResponse mockResponse = createMockResponse("gpt-4", 200); // GH-90000
      when(router.route(any())).thenAnswer(invocation -> { // GH-90000
        AIRequest req = invocation.getArgument(0); // GH-90000
        assertThat(req.getTaskType()).isEqualTo(TaskType.CODE_GENERATION); // GH-90000
        return Promise.of(mockResponse); // GH-90000
      });

      Map<String, Object> context = new HashMap<>(); // GH-90000
      context.put("stepName", "implement-api"); // GH-90000

      runPromise(() -> generator.generate("build code", context)); // GH-90000
    }

    @Test
    @DisplayName("Should route analysis steps to CODE_ANALYSIS [GH-90000]")
    void shouldRouteAnalyzeToCodeAnalysis() { // GH-90000
      AIResponse mockResponse = createMockResponse("claude-3", 150); // GH-90000
      when(router.route(any())).thenAnswer(invocation -> { // GH-90000
        AIRequest req = invocation.getArgument(0); // GH-90000
        assertThat(req.getTaskType()).isEqualTo(TaskType.CODE_ANALYSIS); // GH-90000
        return Promise.of(mockResponse); // GH-90000
      });

      Map<String, Object> context = new HashMap<>(); // GH-90000
      context.put("stepName", "analyze-architecture"); // GH-90000

      runPromise(() -> generator.generate("review code", context)); // GH-90000
    }

    @Test
    @DisplayName("Should route test steps to TEST_GENERATION [GH-90000]")
    void shouldRouteTestToTestGen() { // GH-90000
      AIResponse mockResponse = createMockResponse("gpt-4", 200); // GH-90000
      when(router.route(any())).thenAnswer(invocation -> { // GH-90000
        AIRequest req = invocation.getArgument(0); // GH-90000
        assertThat(req.getTaskType()).isEqualTo(TaskType.TEST_GENERATION); // GH-90000
        return Promise.of(mockResponse); // GH-90000
      });

      Map<String, Object> context = new HashMap<>(); // GH-90000
      context.put("stepName", "test-generation"); // GH-90000

      runPromise(() -> generator.generate("generate tests", context)); // GH-90000
    }

    @Test
    @DisplayName("Should default to GENERAL for unknown step names [GH-90000]")
    void shouldDefaultToGeneral() { // GH-90000
      AIResponse mockResponse = createMockResponse("gpt-4", 200); // GH-90000
      when(router.route(any())).thenAnswer(invocation -> { // GH-90000
        AIRequest req = invocation.getArgument(0); // GH-90000
        assertThat(req.getTaskType()).isEqualTo(TaskType.GENERAL); // GH-90000
        return Promise.of(mockResponse); // GH-90000
      });

      Map<String, Object> context = new HashMap<>(); // GH-90000
      context.put("stepName", "unknown-step"); // GH-90000

      runPromise(() -> generator.generate("do something", context)); // GH-90000
    }
  }

  // ===== Accessor Tests =====

  @Nested
  @DisplayName("Accessors [GH-90000]")
  class Accessors {

    @Test
    @DisplayName("Should expose underlying router [GH-90000]")
    void shouldExposeRouter() { // GH-90000
      assertThat(generator.getRouter()).isSameAs(router); // GH-90000
    }

    @Test
    @DisplayName("Should expose cache statistics [GH-90000]")
    void shouldExposeCacheStats() { // GH-90000
      CacheStatistics mockStats = mock(CacheStatistics.class); // GH-90000
      when(router.getCacheStatistics()).thenReturn(mockStats); // GH-90000

      assertThat(generator.getCacheStatistics()).isSameAs(mockStats); // GH-90000
    }
  }

  // ===== Test Helpers =====

  private AIResponse createMockResponse(String modelId, long latencyMs) { // GH-90000
    AIResponse response = mock(AIResponse.class); // GH-90000
    when(response.getModelId()).thenReturn(modelId); // GH-90000
    when(response.isCacheHit()).thenReturn(false); // GH-90000

    AIResponse.ResponseMetrics metrics = mock(AIResponse.ResponseMetrics.class); // GH-90000
    when(metrics.getLatencyMs()).thenReturn(latencyMs); // GH-90000
    when(response.getMetrics()).thenReturn(metrics); // GH-90000

    return response;
  }
}
