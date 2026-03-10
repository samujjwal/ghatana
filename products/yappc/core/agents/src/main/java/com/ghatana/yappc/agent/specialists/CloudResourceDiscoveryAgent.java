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
 * Worker agent that discovers and inventories cloud resources.
 *
 * @doc.type class
 * @doc.purpose Worker agent that discovers and inventories cloud resources
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class CloudResourceDiscoveryAgent extends YAPPCAgentBase<CloudResourceDiscoveryInput, CloudResourceDiscoveryOutput> {

  private static final Logger log = LoggerFactory.getLogger(CloudResourceDiscoveryAgent.class);

  private final MemoryStore memoryStore;

  public CloudResourceDiscoveryAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<CloudResourceDiscoveryInput>, StepResult<CloudResourceDiscoveryOutput>> generator) {
    super(
        "CloudResourceDiscoveryAgent",
        "worker.cloud-resource-discovery",
        new StepContract(
            "worker.cloud-resource-discovery",
            "#/definitions/CloudResourceDiscoveryInput",
            "#/definitions/CloudResourceDiscoveryOutput",
            List.of("cloud", "discovery", "inventory"),
            Map.of("description", "Worker agent that discovers and inventories cloud resources", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull CloudResourceDiscoveryInput input) {
    if (input.cloudAccountId() == null || input.cloudAccountId().isEmpty()) {
      return ValidationResult.fail("cloudAccountId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<CloudResourceDiscoveryInput> perceive(
      @NotNull StepRequest<CloudResourceDiscoveryInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.cloud-resource-discovery request: {}", request.input().cloudAccountId().substring(0, Math.min(50, request.input().cloudAccountId().length())));
    return request;
  }

  /** Rule-based generator for worker.cloud-resource-discovery. */
  public static class CloudResourceDiscoveryGenerator
      implements OutputGenerator<StepRequest<CloudResourceDiscoveryInput>, StepResult<CloudResourceDiscoveryOutput>> {

    private static final Logger log = LoggerFactory.getLogger(CloudResourceDiscoveryGenerator.class);

    @Override
    public @NotNull Promise<StepResult<CloudResourceDiscoveryOutput>> generate(
        @NotNull StepRequest<CloudResourceDiscoveryInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      CloudResourceDiscoveryInput stepInput = input.input();

      log.info("Executing worker.cloud-resource-discovery for: {}", stepInput.cloudAccountId());

      CloudResourceDiscoveryOutput output =
          new CloudResourceDiscoveryOutput(
              "worker.cloud-resource-discovery-" + UUID.randomUUID(),
              List.of(),
              0,
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.cloud-resource-discovery", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<CloudResourceDiscoveryInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("CloudResourceDiscoveryGenerator")
          .type("rule-based")
          .description("Worker agent that discovers and inventories cloud resources")
          .version("1.0.0")
          .build();
    }
  }
}
