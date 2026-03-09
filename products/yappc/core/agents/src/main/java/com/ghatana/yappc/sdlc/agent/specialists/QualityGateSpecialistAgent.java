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
 * Specialist agent for quality gate validation.
 *
 * @doc.type class
 * @doc.purpose Validates quality metrics against thresholds
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class QualityGateSpecialistAgent
    extends YAPPCAgentBase<QualityGateInput, QualityGateOutput> {

  private static final Logger log = LoggerFactory.getLogger(QualityGateSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public QualityGateSpecialistAgent(
      MemoryStore memoryStore, Map<String, OutputGenerator<?, ?>> generators) {
    super(
        "QualityGateSpecialistAgent",
        "implementation.qualityGate",
        new StepContract(
            "implementation.qualityGate",
            "#/definitions/QualityGateInput",
            "#/definitions/QualityGateOutput",
            List.of("implementation", "quality", "gate"),
            Map.of("description", "Validates quality metrics", "version", "1.0.0")),
        (OutputGenerator<StepRequest<QualityGateInput>, StepResult<QualityGateOutput>>)
            generators.get("implementation.qualityGate"));
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull QualityGateInput input) {
    if (input.buildId() == null || input.buildId().isEmpty()) {
      return ValidationResult.fail("Build ID cannot be empty");
    }
    if (input.coverageThreshold() < 0 || input.coverageThreshold() > 100) {
      return ValidationResult.fail("Coverage threshold must be between 0 and 100");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<QualityGateInput> perceive(
      @NotNull StepRequest<QualityGateInput> request, @NotNull AgentContext context) {
    log.info("Perceiving quality gate request for build: {}", request.input().buildId());
    return request;
  }

  /** Rule-based generator for quality gate validation. */
  public static class QualityGateGenerator
      implements OutputGenerator<StepRequest<QualityGateInput>, StepResult<QualityGateOutput>> {

    private static final Logger log = LoggerFactory.getLogger(QualityGateGenerator.class);

    @Override
    public @NotNull Promise<StepResult<QualityGateOutput>> generate(
        @NotNull StepRequest<QualityGateInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      QualityGateInput gateInput = input.input();

      log.info("Running quality gate validation for build: {}", gateInput.buildId());

      List<String> failures = new ArrayList<>();

      if (gateInput.actualCoverage() < gateInput.coverageThreshold()) {
        String failure =
            String.format(
                "Coverage %.1f%% < threshold %.1f%%",
                gateInput.actualCoverage(), gateInput.coverageThreshold());
        failures.add(failure);
        log.warn("Quality gate check failed: {}", failure);
      }

      if (gateInput.actualComplexity() > gateInput.complexityThreshold()) {
        String failure =
            String.format(
                "Complexity %d > threshold %d",
                gateInput.actualComplexity(), gateInput.complexityThreshold());
        failures.add(failure);
        log.warn("Quality gate check failed: {}", failure);
      }

      if (gateInput.criticalIssues().size() > gateInput.criticalIssuesThreshold()) {
        String failure =
            String.format(
                "Critical issues %d > threshold %d",
                gateInput.criticalIssues().size(), gateInput.criticalIssuesThreshold());
        failures.add(failure);
        log.warn("Quality gate check failed: {}", failure);
      }

      boolean passed = failures.isEmpty();
      String recommendation =
          passed
              ? "All quality checks passed. Proceed with publishing."
              : "Quality gate failed. Fix issues: " + String.join(", ", failures);

      QualityGateOutput output =
          new QualityGateOutput(gateInput.buildId(), passed, failures, recommendation, passed);

      return Promise.of(
          StepResult.success(
              output,
              Map.of(
                  "buildId",
                  gateInput.buildId(),
                  "passed",
                  passed,
                  "failureCount",
                  failures.size()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<QualityGateInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("QualityGateGenerator")
          .type("rule-based")
          .description("Validates quality metrics against thresholds")
          .version("1.0.0")
          .build();
    }
  }
}
