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
 * Specialist agent for publishing release notes and announcements.
 *
 * @doc.type class
 * @doc.purpose Publishes release information to configured channels
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class PublishSpecialistAgent extends YAPPCAgentBase<PublishInput, PublishOutput> {

  private static final Logger log = LoggerFactory.getLogger(PublishSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public PublishSpecialistAgent(
      MemoryStore memoryStore, Map<String, OutputGenerator<?, ?>> generators) {
    super(
        "PublishSpecialistAgent",
        "ops.publish",
        new StepContract(
            "ops.publish",
            "#/definitions/PublishInput",
            "#/definitions/PublishOutput",
            List.of("ops", "publish", "release"),
            Map.of("description", "Publishes release notes and announcements", "version", "1.0.0")),
        (OutputGenerator<StepRequest<PublishInput>, StepResult<PublishOutput>>)
            generators.get("ops.publish"));
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull PublishInput input) {
    if (input.releaseId() == null || input.releaseId().isEmpty()) {
      return ValidationResult.fail("Release ID cannot be empty");
    }
    if (input.channels() == null || input.channels().isEmpty()) {
      return ValidationResult.fail("At least one publish channel required");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<PublishInput> perceive(
      @NotNull StepRequest<PublishInput> request, @NotNull AgentContext context) {
    log.info("Perceiving publish request for release: {}", request.input().releaseId());
    return request;
  }

  /** Rule-based generator for publishing. */
  public static class PublishGenerator
      implements OutputGenerator<StepRequest<PublishInput>, StepResult<PublishOutput>> {

    private static final Logger log = LoggerFactory.getLogger(PublishGenerator.class);

    @Override
    public @NotNull Promise<StepResult<PublishOutput>> generate(
        @NotNull StepRequest<PublishInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      PublishInput publishInput = input.input();

      log.info(
          "Publishing release {} to {} channels",
          publishInput.releaseId(),
          publishInput.channels().size());

      Map<String, String> publishedUrls = new HashMap<>();
      for (String channel : publishInput.channels()) {
        String url = generatePublishUrl(channel, publishInput.releaseId(), publishInput.version());
        publishedUrls.put(channel, url);
        log.debug("Published to {}: {}", channel, url);
      }

      boolean success = !publishedUrls.isEmpty();
      String message =
          success ? "Published to " + publishedUrls.size() + " channels" : "Publication failed";

      PublishOutput output =
          new PublishOutput(
              publishInput.releaseId(),
              publishInput.version(),
              publishedUrls,
              Instant.now(),
              success,
              message);

      return Promise.of(
          StepResult.success(
              output,
              Map.of("releaseId", publishInput.releaseId(), "channelCount", publishedUrls.size()),
              start,
              Instant.now()));
    }

    private String generatePublishUrl(String channel, String releaseId, String version) {
      return switch (channel) {
        case "slack" -> "https://slack.example.com/releases/" + releaseId;
        case "email" -> "https://email.example.com/campaigns/" + releaseId;
        case "docs" -> "https://docs.example.com/releases/" + version;
        default -> "https://example.com/releases/" + releaseId;
      };
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<PublishInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("PublishGenerator")
          .type("rule-based")
          .description("Publishes release notes and announcements")
          .version("1.0.0")
          .build();
    }
  }
}
