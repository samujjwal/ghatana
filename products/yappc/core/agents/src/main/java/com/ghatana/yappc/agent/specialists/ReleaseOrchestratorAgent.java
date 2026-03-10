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
 * L1 orchestrator for the entire release pipeline (Phase 8).
 *
 * <p>Coordinates SBOM generation, artifact signing, supply-chain verification,
 * release governance, and final publication. Downstream agents
 * ({@code SbomSignerAgent}, {@code SupplyChainVerifierAgent}, {@code ReleaseGovernanceAgent})
 * report to this orchestrator.
 *
 * @doc.type class
 * @doc.purpose Orchestrates the complete release pipeline from SBOM to publication
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ReleaseOrchestratorAgent
    extends YAPPCAgentBase<ReleaseOrchestratorInput, ReleaseOrchestratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(ReleaseOrchestratorAgent.class);

  private final MemoryStore memoryStore;

  /**
   * Constructs the release orchestrator.
   *
   * @param memoryStore memory store for release audit trail
   * @param generator   output generator (rule-based or LLM-powered)
   */
  public ReleaseOrchestratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ReleaseOrchestratorInput>,
          StepResult<ReleaseOrchestratorOutput>> generator) {
    super(
        "ReleaseOrchestratorAgent",
        "orchestrator.release",
        new StepContract(
            "orchestrator.release",
            "#/definitions/ReleaseOrchestratorInput",
            "#/definitions/ReleaseOrchestratorOutput",
            List.of("release-orchestration", "sbom-coordination", "supply-chain-verification"),
            Map.of(
                "description", "Orchestrates the complete release pipeline",
                "version", "1.0.0",
                "level", "L1",
                "reports_to", "head-of-devops")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ReleaseOrchestratorInput input) {
    if (input.releaseId() == null || input.releaseId().isEmpty()) {
      return ValidationResult.fail("releaseId cannot be empty");
    }
    if (input.version() == null || input.version().isEmpty()) {
      return ValidationResult.fail("version cannot be empty");
    }
    if (input.artifacts() == null || input.artifacts().isEmpty()) {
      return ValidationResult.fail("at least one artifact is required for release");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ReleaseOrchestratorInput> perceive(
      @NotNull StepRequest<ReleaseOrchestratorInput> request,
      @NotNull AgentContext context) {
    ReleaseOrchestratorInput input = request.input();
    log.info("Perceiving release request [{}] version={} type={} artifacts={}",
        input.releaseId(), input.version(), input.releaseType(), input.artifacts().size());
    return request;
  }

  /**
   * Rule-based release pipeline generator.
   *
   * <p>Executes release gates sequentially: SBOM verification, artifact signing,
   * supply-chain integrity, governance approval, and publication.
   */
  public static class ReleaseOrchestratorGenerator
      implements OutputGenerator<StepRequest<ReleaseOrchestratorInput>,
          StepResult<ReleaseOrchestratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ReleaseOrchestratorGenerator.class);

    /** Ordered set of release gates. */
    private static final List<String> RELEASE_GATES = List.of(
        "sbom-generation",
        "artifact-signing",
        "supply-chain-verification",
        "governance-approval",
        "staging-validation",
        "publication");

    @Override
    public @NotNull Promise<StepResult<ReleaseOrchestratorOutput>> generate(
        @NotNull StepRequest<ReleaseOrchestratorInput> input,
        @NotNull AgentContext context) {

      Instant start = Instant.now();
      ReleaseOrchestratorInput releaseInput = input.input();

      log.info("Orchestrating release [{}] v{}", releaseInput.releaseId(), releaseInput.version());

      // Simulate gate evaluation — in production each gate delegates to a specialist
      List<String> completedGates = new ArrayList<>();
      List<String> pendingGates = new ArrayList<>();

      for (String gate : RELEASE_GATES) {
        if (evaluateGate(gate, releaseInput)) {
          completedGates.add(gate);
        } else {
          pendingGates.add(gate);
        }
      }

      boolean allPassed = pendingGates.isEmpty();
      String status = allPassed
          ? ReleaseOrchestratorOutput.STATUS_READY
          : ReleaseOrchestratorOutput.STATUS_BLOCKED;

      // Generate SBOM digest placeholder
      String sbomDigest = allPassed
          ? "sha256:" + UUID.randomUUID().toString().replace("-", "").substring(0, 32)
          : "";

      ReleaseOrchestratorOutput output = new ReleaseOrchestratorOutput(
          releaseInput.releaseId(),
          status,
          completedGates,
          pendingGates,
          sbomDigest,
          Map.of(
              "generatedAt", start.toString(),
              "version", releaseInput.version(),
              "releaseType", releaseInput.releaseType(),
              "artifactCount", releaseInput.artifacts().size(),
              "gatesTotal", RELEASE_GATES.size(),
              "gatesPassed", completedGates.size()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of(
                  "stepId", "orchestrator.release",
                  "status", status,
                  "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    /** Evaluates a single release gate. In production, delegates to downstream agents. */
    private boolean evaluateGate(String gate, ReleaseOrchestratorInput input) {
      return switch (gate) {
        case "sbom-generation" -> !input.artifacts().isEmpty();
        case "artifact-signing" -> !input.artifacts().isEmpty();
        case "supply-chain-verification" -> !input.artifacts().isEmpty();
        case "governance-approval" -> input.context().containsKey("governanceApproved")
            || "patch".equals(input.releaseType());
        case "staging-validation" -> input.context().containsKey("stagingPassed")
            || "hotfix".equals(input.releaseType());
        case "publication" -> true; // Always ready if other gates pass
        default -> true;
      };
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ReleaseOrchestratorInput> input,
        @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ReleaseOrchestratorGenerator")
          .type("rule-based")
          .description("Orchestrates the release pipeline through sequential gates")
          .version("1.0.0")
          .build();
    }
  }
}
