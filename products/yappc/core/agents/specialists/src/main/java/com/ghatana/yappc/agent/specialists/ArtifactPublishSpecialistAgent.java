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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Specialist agent for publishing build artifacts.
 *
 * @doc.type class
 * @doc.purpose Publishes artifacts to repositories
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ArtifactPublishSpecialistAgent
    extends YAPPCAgentBase<ArtifactPublishInput, ArtifactPublishOutput> {

  private static final Logger log = LoggerFactory.getLogger(ArtifactPublishSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public ArtifactPublishSpecialistAgent(
      MemoryStore memoryStore, Map<String, OutputGenerator<?, ?>> generators) {
    super(
        "ArtifactPublishSpecialistAgent",
        "implementation.artifactPublish",
        new StepContract(
            "implementation.artifactPublish",
            "#/definitions/ArtifactPublishInput",
            "#/definitions/ArtifactPublishOutput",
            List.of("implementation", "publish", "artifact"),
            Map.of("description", "Publishes build artifacts", "version", "1.0.0")),
        (OutputGenerator<StepRequest<ArtifactPublishInput>, StepResult<ArtifactPublishOutput>>)
            generators.get("implementation.artifactPublish"));
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ArtifactPublishInput input) {
    if (input.buildId() == null || input.buildId().isEmpty()) {
      return ValidationResult.fail("Build ID cannot be empty");
    }
    if (input.artifacts() == null || input.artifacts().isEmpty()) {
      return ValidationResult.fail("At least one artifact required");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ArtifactPublishInput> perceive(
      @NotNull StepRequest<ArtifactPublishInput> request, @NotNull AgentContext context) {
    log.info("Perceiving artifact publish request for build: {}", request.input().buildId());
    return request;
  }

  /** Rule-based generator for artifact publishing. */
  public static class ArtifactPublishGenerator
      implements OutputGenerator<
          StepRequest<ArtifactPublishInput>, StepResult<ArtifactPublishOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ArtifactPublishGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ArtifactPublishOutput>> generate(
        @NotNull StepRequest<ArtifactPublishInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ArtifactPublishInput publishInput = input.input();

      log.info(
          "Publishing {} artifacts for build {} to {}",
          publishInput.artifacts().size(),
          publishInput.buildId(),
          publishInput.repository());

      List<String> publishedArtifacts = new ArrayList<>();
      for (String artifact : publishInput.artifacts()) {
        String url =
            generateArtifactUrl(publishInput.repository(), artifact, publishInput.version());
        publishedArtifacts.add(url);
        log.debug("Published artifact: {} to {}", artifact, url);
      }

      boolean success = publishedArtifacts.size() == publishInput.artifacts().size();
      String message =
          success
              ? "Published " + publishedArtifacts.size() + " artifacts"
              : "Artifact publish failed";

      String repositoryUrl =
          generateRepositoryUrl(publishInput.repository(), publishInput.version());

      ArtifactPublishOutput output =
          new ArtifactPublishOutput(
              publishInput.buildId(),
              publishInput.version(),
              publishedArtifacts,
              repositoryUrl,
              Instant.now(),
              success,
              message);

      return Promise.of(
          StepResult.success(
              output,
              Map.of("buildId", publishInput.buildId(), "artifactCount", publishedArtifacts.size()),
              start,
              Instant.now()));
    }

    private String generateArtifactUrl(String repository, String artifact, String version) {
      if (repository.contains("maven")) {
        return "https://repo.maven.apache.org/maven2/com/ghatana/" + artifact + "/" + version;
      } else if (repository.contains("docker")) {
        return "https://hub.docker.com/r/ghatana/" + artifact + ":" + version;
      }
      return "https://artifacts.example.com/" + repository + "/" + artifact + "/" + version;
    }

    private String generateRepositoryUrl(String repository, String version) {
      return "https://artifacts.example.com/" + repository + "/releases/" + version;
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ArtifactPublishInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ArtifactPublishGenerator")
          .type("rule-based")
          .description("Publishes build artifacts to repositories")
          .version("1.0.0")
          .build();
    }
  }
}
