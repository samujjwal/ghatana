package com.ghatana.yappc.sdlc.agent.leads;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.sdlc.*;
import com.ghatana.yappc.sdlc.agent.StepRequest;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentBase;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentRegistry;
import io.activej.promise.Promise;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase Lead Agent for Architecture phase.
 *
 * <p>Coordinates architecture-related steps:
 *
 * <ul>
 *   <li>Requirements intake
 *   <li>Architecture design
 *   <li>Contract derivation
 *   <li>Data model design
 *   <li>Architecture validation
 *   <li>HITL review
 * </ul>
 *
 * <p>Delegates to specialist agents and aggregates results.
 *
 * @doc.type class
 * @doc.purpose Phase lead for architecture workflow coordination
 * @doc.layer product
 * @doc.pattern Coordinator
 * @doc.gaa.lifecycle act
 */
public class ArchitecturePhaseLeadAgent
    extends YAPPCAgentBase<ArchitectureRequest, ArchitectureResult> {

  private static final Logger log = LoggerFactory.getLogger(ArchitecturePhaseLeadAgent.class);

  private final YAPPCAgentRegistry agentRegistry;
  private final MemoryStore memoryStore;

  public ArchitecturePhaseLeadAgent(
      @NotNull YAPPCAgentRegistry agentRegistry,
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<ArchitectureRequest>, StepResult<ArchitectureResult>>
              generator) {
    super("ArchitecturePhaseLeadAgent", "architecture.coordinate", createContract(), generator);
    this.agentRegistry = agentRegistry;
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ArchitectureRequest input) {
    List<String> errors = new ArrayList<>();

    if (input.requirements() == null || input.requirements().isEmpty()) {
      errors.add("Requirements cannot be empty");
    }

    if (input.targetSteps() == null || input.targetSteps().isEmpty()) {
      errors.add("At least one target step must be specified");
    }

    return errors.isEmpty()
        ? ValidationResult.success()
        : ValidationResult.fail(errors.toArray(new String[0]));
  }

  @Override
  protected StepRequest<ArchitectureRequest> perceive(
      @NotNull StepRequest<ArchitectureRequest> request, @NotNull AgentContext context) {

    ArchitectureRequest archRequest = request.input();
    log.info(
        "Perceiving architecture request for: {} (steps: {})",
        archRequest.requirements(),
        archRequest.targetSteps());

    // Enrich with context
    ArchitectureRequest enriched =
        new ArchitectureRequest(
            archRequest.requirements(),
            archRequest.targetSteps(),
            enrichMetadata(archRequest, context));

    return new StepRequest<>(enriched, request.context());
  }

  @Override
  protected Promise<StepResult<ArchitectureResult>> act(
      @NotNull StepResult<ArchitectureResult> result, @NotNull AgentContext context) {

    log.info(
        "Acting on architecture result: {} steps completed", result.output().stepResults().size());

    // Publish architecture artifacts
    return publishArtifacts(result, context).map(v -> result);
  }

  @Override
  protected Promise<Void> reflect(
      @NotNull StepRequest<ArchitectureRequest> input,
      @NotNull StepResult<ArchitectureResult> output,
      @NotNull AgentContext context) {

    log.info("Reflecting on architecture phase execution");

    ArchitectureResult result = output.output();

    // Learn step duration patterns
    result
        .stepResults()
        .forEach(
            (step, stepResult) -> {
              log.debug("Step {} took {}ms", step, stepResult.durationMs());
            });

    // Learn quality patterns
    boolean needsReview = result.stepResults().values().stream().anyMatch(sr -> sr.needsReview());
    if (needsReview) {
      log.info("Architecture requires human review - storing pattern");
      // NOTE: Store pattern for similar requirements
    }

    return Promise.complete();
  }

  private static StepContract createContract() {
    return new StepContract(
        "architecture.coordinate",
        "#/definitions/ArchitectureRequest",
        "#/definitions/ArchitectureResult",
        List.of("architecture", "design", "contracts"),
        Map.of("description", "Coordinates architecture phase workflow", "version", "1.0.0"));
  }

  private Map<String, Object> enrichMetadata(ArchitectureRequest request, AgentContext context) {
    Map<String, Object> metadata = new HashMap<>(request.metadata());
    metadata.put("phase.lead", "architecture");
    metadata.put("phase.startTime", context.getStartTime().toString());
    return metadata;
  }

  private Promise<Void> publishArtifacts(
      StepResult<ArchitectureResult> result, AgentContext context) {
    log.info("Publishing architecture artifacts");
    // NOTE: Integrate with artifact storage
    return Promise.complete();
  }
}
