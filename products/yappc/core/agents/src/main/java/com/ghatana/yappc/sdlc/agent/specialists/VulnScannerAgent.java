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
 * Worker agent that scans code and dependencies for vulnerabilities.
 *
 * @doc.type class
 * @doc.purpose Worker agent that scans code and dependencies for vulnerabilities
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class VulnScannerAgent extends YAPPCAgentBase<VulnScannerInput, VulnScannerOutput> {

  private static final Logger log = LoggerFactory.getLogger(VulnScannerAgent.class);

  private final MemoryStore memoryStore;

  public VulnScannerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<VulnScannerInput>, StepResult<VulnScannerOutput>> generator) {
    super(
        "VulnScannerAgent",
        "worker.vuln-scanner",
        new StepContract(
            "worker.vuln-scanner",
            "#/definitions/VulnScannerInput",
            "#/definitions/VulnScannerOutput",
            List.of("security", "vulnerability-scanning"),
            Map.of("description", "Worker agent that scans code and dependencies for vulnerabilities", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull VulnScannerInput input) {
    if (input.scanTarget() == null || input.scanTarget().isEmpty()) {
      return ValidationResult.fail("scanTarget cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<VulnScannerInput> perceive(
      @NotNull StepRequest<VulnScannerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.vuln-scanner request: {}", input.input().scanTarget().substring(0, Math.min(50, input.input().scanTarget().length())));
    return request;
  }

  /** Rule-based generator for worker.vuln-scanner. */
  public static class VulnScannerGenerator
      implements OutputGenerator<StepRequest<VulnScannerInput>, StepResult<VulnScannerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(VulnScannerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<VulnScannerOutput>> generate(
        @NotNull StepRequest<VulnScannerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      VulnScannerInput stepInput = input.input();

      log.info("Executing worker.vuln-scanner for: {}", stepInput.scanTarget());

      VulnScannerOutput output =
          new VulnScannerOutput(
              "worker.vuln-scanner-" + UUID.randomUUID(),
              List.of(),
              "Generated riskScore for " + stepInput.scanTarget(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.vuln-scanner", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<VulnScannerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("VulnScannerGenerator")
          .type("rule-based")
          .description("Worker agent that scans code and dependencies for vulnerabilities")
          .version("1.0.0")
          .build();
    }
  }
}
