package com.ghatana.yappc.sdlc.agent.specialists;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.sdlc.*;
import com.ghatana.yappc.sdlc.agent.StepRequest;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Integration bridge agent for multi-channel notifications.
 *
 * @doc.type class
 * @doc.purpose Integration bridge agent for multi-channel notifications
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class NotificationAgent extends YAPPCAgentBase<NotificationInput, NotificationOutput> {

  private static final Logger log = LoggerFactory.getLogger(NotificationAgent.class);

  private final MemoryStore memoryStore;

  public NotificationAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<NotificationInput>, StepResult<NotificationOutput>> generator) {
    super(
        "NotificationAgent",
        "integration.notification",
        new StepContract(
            "integration.notification",
            "#/definitions/NotificationInput",
            "#/definitions/NotificationOutput",
            List.of("integration", "notification", "messaging"),
            Map.of("description", "Integration bridge agent for multi-channel notifications", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull NotificationInput input) {
    if (input.channel() == null || input.channel().isEmpty()) {
      return ValidationResult.fail("channel cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<NotificationInput> perceive(
      @NotNull StepRequest<NotificationInput> request, @NotNull AgentContext context) {
    log.info("Perceiving integration.notification request: {}", input.input().channel().substring(0, Math.min(50, input.input().channel().length())));
    return request;
  }

  /** Rule-based generator for integration.notification. */
  public static class NotificationGenerator
      implements OutputGenerator<StepRequest<NotificationInput>, StepResult<NotificationOutput>> {

    private static final Logger log = LoggerFactory.getLogger(NotificationGenerator.class);

    @Override
    public @NotNull Promise<StepResult<NotificationOutput>> generate(
        @NotNull StepRequest<NotificationInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      NotificationInput stepInput = input.input();

      log.info("Executing integration.notification for: {}", stepInput.channel());

      NotificationOutput output =
          new NotificationOutput(
              "integration.notification-" + UUID.randomUUID(),
              "Generated deliveryStatus for " + stepInput.channel(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "integration.notification", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<NotificationInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("NotificationGenerator")
          .type("rule-based")
          .description("Integration bridge agent for multi-channel notifications")
          .version("1.0.0")
          .build();
    }
  }
}
