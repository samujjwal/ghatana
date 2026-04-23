package com.ghatana.yappc.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.ai.router.AIModelRouter;
import com.ghatana.yappc.ai.router.AIRequest;
import com.ghatana.yappc.ai.router.AIResponse;
import com.ghatana.yappc.ai.router.AIRouterConfig;
import com.ghatana.yappc.ai.router.ModelAdapter;
import com.ghatana.yappc.ai.router.ModelConfig;
import io.activej.promise.Promise;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("YAPPCAIService Call Path Tests")
class YAPPCAIServiceCallPathTest extends EventloopTestBase {

  @Test
  @DisplayName("Should execute service to router to adapter path for code generation")
  void shouldExecuteServiceToRouterToAdapterPathForCodeGeneration() { // GH-90000
    AtomicReference<AIRequest> capturedRequest = new AtomicReference<>(); // GH-90000
    AIModelRouter router = new AIModelRouter( // GH-90000
        AIRouterConfig.defaults(), // GH-90000
        config -> new RecordingAdapter(config, capturedRequest)); // GH-90000
    YAPPCAIService service = YAPPCAIService.builder().router(router).build(); // GH-90000

    runPromise(service::initialize); // GH-90000

    String result = runPromise(() -> service.generateCode( // GH-90000
        "Create a Java service for invoice generation",
        java.util.Map.of("language", "Java", "framework", "ActiveJ"))); // GH-90000

    assertThat(result).contains("InvoiceService");
    assertThat(capturedRequest.get()).isNotNull(); // GH-90000
    assertThat(capturedRequest.get().getTaskType()).isEqualTo(AIRequest.TaskType.CODE_GENERATION); // GH-90000
    assertThat(capturedRequest.get().getPrompt()).contains("invoice generation");
    assertThat(capturedRequest.get().getContext()).containsEntry("language", "Java"); // GH-90000
    assertThat(service.getTotalRequests()).isEqualTo(1); // GH-90000
  }

  private static final class RecordingAdapter implements ModelAdapter {
    private final ModelConfig config;
    private final AtomicReference<AIRequest> capturedRequest;

    private RecordingAdapter(ModelConfig config, AtomicReference<AIRequest> capturedRequest) { // GH-90000
      this.config = config;
      this.capturedRequest = capturedRequest;
    }

    @Override
    public Promise<Void> initialize() { // GH-90000
      return Promise.complete(); // GH-90000
    }

    @Override
    public Promise<AIResponse> execute(AIRequest request) { // GH-90000
      capturedRequest.set(request); // GH-90000
      return Promise.of(AIResponse.builder() // GH-90000
          .requestId(request.getRequestId()) // GH-90000
          .modelId(config.getModelId()) // GH-90000
          .content("```java\npublic final class InvoiceService {}\n```")
          .metrics(AIResponse.ResponseMetrics.builder() // GH-90000
              .latencyMs(12) // GH-90000
              .tokenCount(32) // GH-90000
              .promptTokens(12) // GH-90000
              .completionTokens(20) // GH-90000
              .cost(0.0) // GH-90000
              .build()) // GH-90000
          .build()); // GH-90000
    }

    @Override
    public ModelConfig getConfig() { // GH-90000
      return config;
    }

    @Override
    public Promise<Boolean> isAvailable() { // GH-90000
      return Promise.of(true); // GH-90000
    }

    @Override
    public Promise<Void> shutdown() { // GH-90000
      return Promise.complete(); // GH-90000
    }
  }
}
