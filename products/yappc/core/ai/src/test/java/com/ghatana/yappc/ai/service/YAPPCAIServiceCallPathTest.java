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
  void shouldExecuteServiceToRouterToAdapterPathForCodeGeneration() {
    AtomicReference<AIRequest> capturedRequest = new AtomicReference<>();
    AIModelRouter router = new AIModelRouter(
        AIRouterConfig.defaults(),
        config -> new RecordingAdapter(config, capturedRequest));
    YAPPCAIService service = YAPPCAIService.builder().router(router).build();

    runPromise(service::initialize);

    String result = runPromise(() -> service.generateCode(
        "Create a Java service for invoice generation",
        java.util.Map.of("language", "Java", "framework", "ActiveJ")));

    assertThat(result).contains("InvoiceService");
    assertThat(capturedRequest.get()).isNotNull();
    assertThat(capturedRequest.get().getTaskType()).isEqualTo(AIRequest.TaskType.CODE_GENERATION);
    assertThat(capturedRequest.get().getPrompt()).contains("invoice generation");
    assertThat(capturedRequest.get().getContext()).containsEntry("language", "Java");
    assertThat(service.getTotalRequests()).isEqualTo(1);
  }

  private static final class RecordingAdapter implements ModelAdapter {
    private final ModelConfig config;
    private final AtomicReference<AIRequest> capturedRequest;

    private RecordingAdapter(ModelConfig config, AtomicReference<AIRequest> capturedRequest) {
      this.config = config;
      this.capturedRequest = capturedRequest;
    }

    @Override
    public Promise<Void> initialize() {
      return Promise.complete();
    }

    @Override
    public Promise<AIResponse> execute(AIRequest request) {
      capturedRequest.set(request);
      return Promise.of(AIResponse.builder()
          .requestId(request.getRequestId())
          .modelId(config.getModelId())
          .content("```java\npublic final class InvoiceService {}\n```")
          .metrics(AIResponse.ResponseMetrics.builder()
              .latencyMs(12)
              .tokenCount(32)
              .promptTokens(12)
              .completionTokens(20)
              .cost(0.0)
              .build())
          .build());
    }

    @Override
    public ModelConfig getConfig() {
      return config;
    }

    @Override
    public Promise<Boolean> isAvailable() {
      return Promise.of(true);
    }

    @Override
    public Promise<Void> shutdown() {
      return Promise.complete();
    }
  }
}
