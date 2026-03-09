package com.ghatana.yappc.sdlc.agent.coordinator;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.coordination.*;
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
 * Tier 1 coordinator for YAPPC platform delivery.
 *
 * <p>Orchestrates the entire software delivery lifecycle by:
 *
 * <ul>
 *   <li>Decomposing high-level platform requests into SDLC phases
 *   <li>Delegating to Phase Lead agents (Architecture, Implementation, Testing, Ops)
 *   <li>Managing workflow state and transitions
 *   <li>Aggregating results and feedback
 *   <li>Continuous learning from execution patterns
 * </ul>
 *
 * <p>This is the top-level orchestrator in the YAPPC agent hierarchy.
 *
 * @doc.type class
 * @doc.purpose Top-level orchestrator for platform delivery workflows
 * @doc.layer product
 * @doc.pattern Coordinator
 */
public class PlatformDeliveryCoordinator extends YAPPCAgentBase<DeliveryRequest, DeliveryResult> {

  private static final Logger log = LoggerFactory.getLogger(PlatformDeliveryCoordinator.class);

  private final YAPPCAgentRegistry agentRegistry;
  private final DelegationManager delegationManager;
  private final OrchestrationStrategy orchestrationStrategy;
  private final MemoryStore memoryStore;

  /**
   * Creates the platform delivery coordinator.
   *
   * @param agentRegistry registry of all YAPPC agents
   * @param delegationManager delegation manager for task routing
   * @param orchestrationStrategy strategy for coordinating multiple agents
   * @param memoryStore memory store for learning and adaptation
   * @param generator output generator for decision making
   */
  public PlatformDeliveryCoordinator(
      @NotNull YAPPCAgentRegistry agentRegistry,
      @NotNull DelegationManager delegationManager,
      @NotNull OrchestrationStrategy orchestrationStrategy,
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<DeliveryRequest>, StepResult<DeliveryResult>> generator) {
    super("PlatformDeliveryCoordinator", "platform.coordinate", createContract(), generator);
    this.agentRegistry = agentRegistry;
    this.delegationManager = delegationManager;
    this.orchestrationStrategy = orchestrationStrategy;
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DeliveryRequest input) {
    List<String> errors = new ArrayList<>();

    if (input.request() == null || input.request().isEmpty()) {
      errors.add("Request description cannot be empty");
    }

    if (input.targetPhases() == null || input.targetPhases().isEmpty()) {
      errors.add("At least one target phase must be specified");
    }

    if (input.priority() == null) {
      errors.add("Priority must be specified");
    }

    return errors.isEmpty()
        ? ValidationResult.success()
        : ValidationResult.fail(errors.toArray(new String[0]));
  }

  @Override
  protected StepRequest<DeliveryRequest> perceive(
      @NotNull StepRequest<DeliveryRequest> request, @NotNull AgentContext context) {

    // Validate input
    ValidationResult validation = validateInput(request.input());
    if (!validation.ok()) {
      throw new IllegalArgumentException("Input validation failed: " + validation.errors());
    }

    DeliveryRequest deliveryRequest = request.input();
    log.info(
        "Perceiving delivery request: {} (phases: {}, priority: {})",
        deliveryRequest.request(),
        deliveryRequest.targetPhases(),
        deliveryRequest.priority());

    // Enrich request with context
    DeliveryRequest enrichedRequest =
        new DeliveryRequest(
            deliveryRequest.request(),
            deliveryRequest.targetPhases(),
            deliveryRequest.priority(),
            enrichedMetadata(deliveryRequest, context));

    return new StepRequest<>(enrichedRequest, request.context());
  }

  @Override
  protected Promise<StepResult<DeliveryResult>> act(
      @NotNull StepResult<DeliveryResult> result, @NotNull AgentContext context) {

    log.info(
        "Acting on delivery result: {} phases completed, {} total time",
        result.output().phaseResults().size(),
        result.output().totalExecutionTimeMs());

    // Publish completion events for each phase
    return publishPhaseCompletionEvents(result, context).map(v -> result);
  }

  @Override
  protected Promise<Void> reflect(
      @NotNull StepRequest<DeliveryRequest> input,
      @NotNull StepResult<DeliveryResult> output,
      @NotNull AgentContext context) {

    log.info("Reflecting on delivery execution for continuous improvement");

    // Extract patterns from execution
    DeliveryResult result = output.output();

    // Learn phase duration patterns
    result
        .phaseResults()
        .forEach(
            (phase, phaseResult) -> {
              double durationMs = phaseResult.executionTimeMs();
              log.debug("Phase {} took {}ms", phase, durationMs);
              // NOTE: Store pattern for future estimation
            });

    // Learn success/failure patterns
    boolean allSuccessful =
        result.phaseResults().values().stream().allMatch(pr -> "SUCCESS".equals(pr.status()));

    if (allSuccessful) {
      log.info("Delivery completed successfully - reinforcing successful pattern");
      // NOTE: Store successful execution pattern as policy
    } else {
      log.warn("Some phases failed - analyzing failure patterns");
      // NOTE: Extract failure patterns for prevention
    }

    return Promise.complete();
  }

  /** Creates the step contract for this coordinator. */
  private static StepContract createContract() {
    return new StepContract(
        "platform.coordinate",
        "#/definitions/DeliveryRequest",
        "#/definitions/DeliveryResult",
        List.of("orchestration", "delegation", "coordination"),
        Map.of(
            "description", "Coordinates platform delivery across all SDLC phases",
            "version", "1.0.0"));
  }

  /** Enriches request metadata with context information. */
  private Map<String, Object> enrichedMetadata(DeliveryRequest request, AgentContext context) {
    Map<String, Object> metadata = new HashMap<>(request.metadata());
    metadata.put("coordinator.startTime", context.getStartTime().toString());
    metadata.put("coordinator.turnId", context.getTurnId());
    metadata.put("coordinator.tenantId", context.getTenantId());
    return metadata;
  }

  /** Publishes phase completion events. */
  private Promise<Void> publishPhaseCompletionEvents(
      StepResult<DeliveryResult> result, AgentContext context) {

    List<Promise<Void>> promises = new ArrayList<>();

    result
        .output()
        .phaseResults()
        .forEach(
            (phase, phaseResult) -> {
              log.info("Publishing completion event for phase: {}", phase);
              // NOTE: Integrate with EventCloudHelper
              promises.add(Promise.complete());
            });

    return io.activej.promise.Promises.all(promises).toVoid();
  }
}
