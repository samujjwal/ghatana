package com.ghatana.yappc.agent.specialists;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;

/**
 * Specialist agent for publishing architecture documentation.
 *
 * <p>Publishes design documents to Confluence/Notion, generates and publishes architecture diagrams
 * (PlantUML/Mermaid/C4), publishes API contracts to developer portal, publishes data models to data
 * catalog, creates version-tagged documentation for reference, notifies stakeholders via Slack of
 * published architecture.
 *
 * @doc.type class
 * @doc.purpose Publishes architecture documentation and diagrams
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class PublishArchitectureSpecialistAgent
    extends YAPPCAgentBase<PublishArchitectureInput, PublishArchitectureOutput> {

  private final MemoryStore memoryStore;

  public PublishArchitectureSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<
                  StepRequest<PublishArchitectureInput>, StepResult<PublishArchitectureOutput>>
              generator) {
    super(
        "PublishArchitectureSpecialistAgent",
        "architecture.publishArchitecture",
        new StepContract(
            "architecture.publishArchitecture",
            "#/definitions/PublishArchitectureInput",
            "#/definitions/PublishArchitectureOutput",
            List.of("architecture", "documentation", "publishing", "collaboration"),
            Map.of(
                "description",
                "Publishes architecture documentation and diagrams",
                "version",
                "1.0.0",
                "estimatedDuration",
                "3m")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull PublishArchitectureInput input) {
    List<String> errors = new ArrayList<>();

    if (input.architectureId().isBlank()) {
      errors.add("architectureId cannot be blank");
    }
    if (input.designDocument().isBlank()) {
      errors.add("designDocument cannot be blank");
    }
    if (input.version().isBlank()) {
      errors.add("version cannot be blank");
    }
    if (input.targetChannels().isEmpty()) {
      errors.add("targetChannels cannot be empty");
    }

    return errors.isEmpty()
        ? ValidationResult.success()
        : ValidationResult.fail(errors.toArray(new String[0]));
  }

  @Override
  protected StepRequest<PublishArchitectureInput> perceive(
      @NotNull StepRequest<PublishArchitectureInput> request, @NotNull AgentContext context) {
    return request;
  }

  /**
   * Generator for architecture publishing (rule-based simulation).
   *
   * @doc.type class
   * @doc.purpose Publishes architecture documentation to various channels
   * @doc.layer product
   * @doc.pattern Strategy
   * @doc.gaa.lifecycle act
   */
  public static class PublishArchitectureGenerator
      implements OutputGenerator<
          StepRequest<PublishArchitectureInput>, StepResult<PublishArchitectureOutput>> {

    @Override
    public Promise<StepResult<PublishArchitectureOutput>> generate(
        StepRequest<PublishArchitectureInput> input, AgentContext context) {
      Instant start = Instant.now();

      PublishArchitectureInput req = input.input();

      // Simulate publishing to various channels
      Map<String, String> publishedUrls = new HashMap<>();

      for (String channel : req.targetChannels()) {
        String url =
            switch (channel.toLowerCase()) {
              case "confluence" -> String.format(
                  "https://confluence.company.com/architecture/%s/v%s",
                  req.architectureId(), req.version());
              case "notion" -> String.format(
                  "https://notion.so/company/architecture/%s-v%s",
                  req.architectureId(), req.version().replace(".", "-"));
              case "github" -> String.format(
                  "https://github.com/company/docs/tree/main/architecture/%s/v%s",
                  req.architectureId(), req.version());
              case "portal" -> String.format(
                  "https://developer.company.com/architecture/%s/v%s",
                  req.architectureId(), req.version());
              case "slack" -> String.format(
                  "https://company.slack.com/archives/C123/p%d", System.currentTimeMillis() / 1000);
              default -> String.format(
                  "https://docs.company.com/%s/architecture/%s/v%s",
                  channel, req.architectureId(), req.version());
            };
        publishedUrls.put(channel, url);
      }

      int documentsPublished = 1; // Design document
      int diagramsPublished = req.diagrams().size();
      int contractsPublished = req.contracts().size();

      PublishArchitectureOutput output =
          new PublishArchitectureOutput(
              req.architectureId(),
              req.version(),
              publishedUrls,
              documentsPublished,
              diagramsPublished,
              contractsPublished,
              Instant.now(),
              true,
              String.format(
                  "Published architecture %s v%s to %d channel(s): %d document(s), %d diagram(s), %d contract(s)",
                  req.architectureId(),
                  req.version(),
                  req.targetChannels().size(),
                  documentsPublished,
                  diagramsPublished,
                  contractsPublished));

      Instant end = Instant.now();
      Map<String, Object> metadata =
          Map.of(
              "architectureId",
              req.architectureId(),
              "version",
              req.version(),
              "channelsPublished",
              req.targetChannels().size(),
              "totalArtifacts",
              documentsPublished + diagramsPublished + contractsPublished);

      return Promise.of(StepResult.success(output, metadata, start, end));
    }

    @Override
    public Promise<Double> estimateCost(
        StepRequest<PublishArchitectureInput> input, AgentContext context) {
      return Promise.of(0.0); // Rule-based, no LLM cost
    }

    @Override
    public GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("PublishArchitectureGenerator")
          .type("rule-based")
          .description("Publishes architecture documentation to multiple channels")
          .version("1.0.0")
          .build();
    }
  }
}
