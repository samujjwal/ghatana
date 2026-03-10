package com.ghatana.yappc.agent;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.Fact;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.memory.Policy;
import com.ghatana.agent.framework.runtime.BaseAgent;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.framework.core.config.FeatureFlag;
import com.ghatana.yappc.framework.core.config.FeatureFlags;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for all YAPPC workflow agents, bridging WorkflowStep with GAA
 * framework.
 *
 * <p>
 * Provides integration between YAPPC's workflow execution context and the
 * generic agent
 * framework. All YAPPC agents should extend this class to gain:
 *
 * <ul>
 * <li>GAA lifecycle (PERCEIVE → REASON → ACT → CAPTURE → REFLECT)
 * <li>Event-sourced memory integration via AEP
 * <li>Cost tracking and budget enforcement
 * <li>Observability and metrics
 * <li>WorkflowStep contract compliance
 * </ul>
 *
 * @param <I> Input type for this workflow step
 * @param <O> Output type for this workflow step
 * @doc.type class
 * @doc.purpose Bridge between YAPPC WorkflowStep and GAA framework with AEP
 *              integration
 * @doc.layer product
 * @doc.pattern Adapter
 */
public abstract class YAPPCAgentBase<I, O> extends BaseAgent<StepRequest<I>, StepResult<O>>
    implements WorkflowStep<I, O> {

  private static final Logger log = LoggerFactory.getLogger(YAPPCAgentBase.class);

  /**
   * @deprecated Use constructor-injected publisher instead. Will be removed in 3.0.
   */
  @Deprecated(since = "2.4.0", forRemoval = true)
  private static volatile AepEventPublisher globalAepEventPublisher = HttpAepEventPublisher.fromEnvironment();

  private final AepEventPublisher aepEventPublisher;
  private final String stepName;
  private final StepContract stepContract;

  /**
   * Constructs a YAPPC agent with the given configuration and an injected event publisher.
   *
   * @param agentId        unique agent identifier
   * @param stepName       fully qualified step name (e.g., "architecture.intake")
   * @param stepContract   contract defining input/output schemas
   * @param generator      output generator for this agent
   * @param eventPublisher event publisher for AEP integration
   */
  protected YAPPCAgentBase(
      @NotNull String agentId,
      @NotNull String stepName,
      @NotNull StepContract stepContract,
      @NotNull OutputGenerator<StepRequest<I>, StepResult<O>> generator,
      @NotNull AepEventPublisher eventPublisher) {
    super(agentId, generator);
    this.stepName = stepName;
    this.stepContract = stepContract;
    this.aepEventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
  }

  /**
   * Legacy constructor for backward compatibility. Uses global static publisher.
   *
   * @param agentId      unique agent identifier
   * @param stepName     fully qualified step name (e.g., "architecture.intake")
   * @param stepContract contract defining input/output schemas
   * @param generator    output generator for this agent
   * @deprecated Use the constructor that accepts {@link AepEventPublisher} for proper DI.
   */
  @Deprecated(since = "2.4.0", forRemoval = true)
  protected YAPPCAgentBase(
      @NotNull String agentId,
      @NotNull String stepName,
      @NotNull StepContract stepContract,
      @NotNull OutputGenerator<StepRequest<I>, StepResult<O>> generator) {
    super(agentId, generator);
    this.stepName = stepName;
    this.stepContract = stepContract;
    this.aepEventPublisher = globalAepEventPublisher;
  }

  @Override
  public String stepName() {
    return stepName;
  }

  @Override
  public StepContract contract() {
    return stepContract;
  }

  /** Convenience alias for {@link #stepName()} for compatibility. */
  public String getStepName() {
    return stepName;
  }

  /** Convenience alias for {@link #contract()} for compatibility. */
  public StepContract getStepContract() {
    return stepContract;
  }

  /**
   * Executes the workflow step using the GAA lifecycle.
   *
   * <p>
   * This method adapts the WorkflowStep.execute() contract to use the agent
   * framework's
   * executeTurn() method, which enforces the full GAA lifecycle.
   *
   * @param input   validated input data
   * @param context execution context with tenant, run, and configuration
   * @return Promise of step result
   */
  @Override
  public Promise<StepResult<O>> execute(@NotNull I input, @NotNull StepContext context) {
    // Create StepRequest wrapper
    StepRequest<I> request = new StepRequest<>(input, context);

    // Convert StepContext to AgentContext
    AgentContext agentContext = convertToAgentContext(context);

    // Execute through GAA lifecycle
    return executeTurn(request, agentContext);
  }

  /**
   * Converts YAPPC StepContext to generic AgentContext.
   *
   * @param stepContext YAPPC-specific context
   * @return generic agent context
   */
  protected AgentContext convertToAgentContext(@NotNull StepContext stepContext) {
    return AgentContext.builder()
        .agentId(getAgentId())
        .turnId(stepContext.runId())
        .tenantId(stepContext.tenantId())
        .userId("system") // YAPPC runs as system user
        .sessionId(stepContext.phase()) // Use phase as session grouping
        .memoryStore(getMemoryStore())
        .config(
            java.util.Map.of(
                "phase", stepContext.phase(),
                "configSnapshotId", stepContext.configSnapshotId()))
        .remainingBudget(stepContext.budget().maxCostUsd())
        .build();
  }

  /**
   * Gets the memory store for this agent. Subclasses must provide implementation.
   *
   * @return memory store instance
   */
  protected abstract MemoryStore getMemoryStore();

  /**
   * Performs YAPPC-specific perception with memory-informed context enrichment.
   *
   * <p>Validates input, then queries episodic and procedural memory for relevant
   * past experiences and policies that may inform the current step execution.
   */
  @Override
  protected StepRequest<I> perceive(
      @NotNull StepRequest<I> request, @NotNull AgentContext context) {
    // Validate input before processing
    ValidationResult validation = validateInput(request.input());
    if (!validation.ok()) {
      throw new IllegalArgumentException("Input validation failed: " + validation.errors());
    }

    // Query memory for relevant context to inform reasoning (best-effort, non-blocking)
    MemoryStore memory = getMemoryStore();
    try {
      // Retrieve recent episodes for this step — fire-and-forget async
      MemoryFilter filter = MemoryFilter.builder()
          .agentId(getAgentId())
          .tags(List.of(stepName))
          .build();
      memory.queryEpisodes(filter, 5)
          .whenResult(pastEpisodes -> {
            if (pastEpisodes != null && !pastEpisodes.isEmpty()) {
              log.debug("Retrieved {} past episodes for step {}", pastEpisodes.size(), stepName);
            }
          })
          .whenException(e ->
              log.warn("Episode query failed for step {}: {}", stepName, e.getMessage()));

      // Query applicable policies — fire-and-forget async
      memory.queryPolicies(stepName, 0.5)
          .whenResult(policies -> {
            if (policies != null && !policies.isEmpty()) {
              log.debug("Found {} applicable policies for step {}", policies.size(), stepName);
            }
          })
          .whenException(e ->
              log.warn("Policy query failed for step {}: {}", stepName, e.getMessage()));
    } catch (Exception e) {
      // Memory retrieval is best-effort — never fail the step
      log.warn("Memory retrieval failed during perceive: {}", e.getMessage());
    }

    return request;
  }

  /**
   * Override to perform YAPPC-specific action side effects.
   *
   * <p>
   * Default implementation publishes events and updates workflow state via AEP.
   */
  @Override
  protected Promise<StepResult<O>> act(
      @NotNull StepResult<O> result, @NotNull AgentContext context) {
    // Publish step completion event via AEP
    return publishStepEvent(result, context).map(v -> result);
  }

  /**
   * Captures an episodic memory record after each agent turn completes.
   *
   * <p>Records the input, output, success status, and execution metrics as an
   * {@link Episode} in the agent's memory store. This data feeds the reflection
   * and learning pipelines.
   *
   * @param input   original step request
   * @param output  step execution result
   * @param context agent execution context
   * @return Promise completing when episode is stored
   */
  @Override
  protected Promise<Void> capture(
      @NotNull StepRequest<I> input, @NotNull StepResult<O> output, @NotNull AgentContext context) {
    MemoryStore memory = getMemoryStore();
    try {
      Episode episode = Episode.builder()
          .agentId(getAgentId())
          .turnId(context.getTurnId())
          .timestamp(Instant.now())
          .input(input.input().toString())
          .output(output.success() ? output.output().toString() : "FAILED")
          .action(stepName)
          .context(Map.of(
              "phase", context.getSessionId(),
              "tenantId", context.getTenantId(),
              "durationMs", output.durationMs()))
          .tags(List.of(stepName, output.success() ? "success" : "failure"))
          .reward(output.success() ? 1.0 : -1.0)
          .build();

      return memory.storeEpisode(episode)
          .map(id -> {
            log.debug("Captured episode {} for step {}", id, stepName);
            return (Void) null;
          })
          .whenComplete((v, e) -> {
            if (e != null) {
              log.warn("Failed to capture episode for step {}: {}", stepName, e.getMessage());
            }
          });
    } catch (Exception e) {
      // Capture is best-effort — never fail the step
      log.warn("Episode capture failed for step {}: {}", stepName, e.getMessage());
      return Promise.complete();
    }
  }

  /**
   * Publishes workflow step completion event to AEP (Agentic Event Processor).
   *
   * <p>
   * Note: AEP handles EventCloud integration. YAPPC should NOT call EventCloud
   * directly.
   *
   * @param result  step execution result
   * @param context agent execution context
   * @return Promise that completes when event is published
   */
  protected Promise<Void> publishStepEvent(
      @NotNull StepResult<O> result, @NotNull AgentContext context) {

    // Check if AEP integration is enabled
    if (!FeatureFlags.isEnabled(FeatureFlag.AEP_INTEGRATION)) {
      log.debug("AEP integration disabled, skipping event publication");
      return Promise.complete();
    }

    // Build event payload
    Map<String, Object> eventPayload = Map.of(
        "agentId", getAgentId(),
        "stepName", stepName,
        "turnId", context.getTurnId(),
        "tenantId", context.getTenantId(),
        "sessionId", context.getSessionId(),
        "success", result.success(),
        "timestamp", Instant.now().toString(),
        "metrics", result.metrics());

    // Publish to AEP (which handles EventCloud integration)
    Promise<Void> promise = publishToAEP("yappc.workflow.step.completed", context.getTenantId(), eventPayload);
    promise.whenComplete((v, e) -> {
      if (e != null) {
        log.error("Failed to publish step event to AEP: {}", e.getMessage(), e);
      }
    });
    return promise;
  }

  /**
   * Publishes event to AEP (Agentic Event Processor).
   *
   * <p>
   * This is the correct integration point for YAPPC. AEP handles EventCloud
   * integration, not YAPPC directly.
   *
   * @param eventType type of event
   * @param tenantId  tenant identifier
   * @param payload   event payload
   * @return Promise that completes when event is published
   */
  protected Promise<Void> publishToAEP(String eventType, String tenantId, Map<String, Object> payload) {
    log.info("Publishing event to AEP: type={}, tenant={}", eventType, tenantId);
    return aepEventPublisher.publish(eventType, tenantId, payload);
  }

  /**
   * @deprecated Use constructor injection of {@link AepEventPublisher} instead.
   */
  @Deprecated(since = "2.4.0", forRemoval = true)
  public static void configureAepEventPublisher(AepEventPublisher publisher) {
    if (publisher != null) {
      globalAepEventPublisher = publisher;
    }
  }

  /**
   * Reflects on step execution to extract learned facts and policies.
   *
   * <p>Performs three actions:
   * <ol>
   *   <li>Stores a semantic fact about this step's outcome
   *   <li>Upserts a procedural policy for future similar situations
   *   <li>Publishes the pattern to AEP for cross-agent learning
   * </ol>
   */
  @Override
  protected Promise<Void> reflect(
      @NotNull StepRequest<I> input, @NotNull StepResult<O> output, @NotNull AgentContext context) {

    if (!FeatureFlags.isEnabled(FeatureFlag.PATTERN_LEARNING)) {
      log.debug("Pattern learning disabled, skipping reflection");
      return Promise.complete();
    }

    MemoryStore memory = getMemoryStore();

    // 1. Store a semantic fact about the outcome
    Fact fact = Fact.builder()
        .agentId(getAgentId())
        .subject(stepName)
        .predicate(output.success() ? "succeeded_with" : "failed_with")
        .object(output.success()
            ? "durationMs=" + output.durationMs()
            : String.join("; ", output.errors()))
        .confidence(output.success() ? 0.9 : 0.7)
        .source("reflection")
        .metadata(Map.of("turnId", context.getTurnId(), "timestamp", Instant.now().toString()))
        .build();

    Promise<Void> factPromise = memory.storeFact(fact)
        .map(id -> (Void) null)
        .whenComplete((v, e) -> {
          if (e != null) log.warn("Failed to store reflection fact: {}", e.getMessage());
        });

    // 2. Upsert a procedural policy for this step
    double confidence = output.success() ? 0.8 : 0.4;
    String action = output.success()
        ? "Execute with current approach (avg " + output.durationMs() + "ms)"
        : "Investigate failure mode and consider alternative approach";

    Policy policy = Policy.builder()
        .agentId(getAgentId())
        .situation("Executing step: " + stepName)
        .action(action)
        .confidence(confidence)
        .version("1.0")
        .metadata(Map.of(
            "learnedFrom", context.getTurnId(),
            "inputType", input.input().getClass().getSimpleName()))
        .build();

    Promise<Void> policyPromise = memory.storePolicy(policy)
        .map(id -> (Void) null)
        .whenComplete((v, e) -> {
          if (e != null) log.warn("Failed to store reflection policy: {}", e.getMessage());
        });

    // 3. Publish pattern to AEP for cross-agent learning
    ExecutionPattern pattern = extractPattern(input, output, context);
    Promise<Void> aepPromise = storePattern(pattern, context);

    return factPromise
        .then(v -> policyPromise)
        .then(v -> aepPromise)
        .whenComplete((v, e) -> {
          if (e != null) {
            log.error("Reflection completed with errors: {}", e.getMessage());
          }
        });
  }

  /**
   * Extracts execution pattern from step execution.
   *
   * @param input   step input
   * @param output  step output
   * @param context agent context
   * @return extracted pattern
   */
  private ExecutionPattern extractPattern(
      StepRequest<I> input, StepResult<O> output, AgentContext context) {
    return new ExecutionPattern(
        stepName,
        input.getClass().getSimpleName(),
        output.success(),
        output.durationMs(),
        context.getAllConfig());
  }

  /**
   * Stores execution pattern via AEP for future optimization.
   *
   * @param pattern execution pattern
   * @param context agent context
   * @return Promise that completes when pattern is stored
   */
  private Promise<Void> storePattern(ExecutionPattern pattern, AgentContext context) {
    Map<String, Object> patternPayload = Map.of(
        "stepName", pattern.stepName(),
        "inputType", pattern.inputType(),
        "success", pattern.success(),
        "durationMs", pattern.durationMs(),
        "config", pattern.config(),
        "timestamp", Instant.now().toString());

    return publishToAEP("yappc.pattern.execution", context.getTenantId(), patternPayload);
  }

  /**
   * Record for execution patterns.
   */
  private record ExecutionPattern(
      String stepName,
      String inputType,
      boolean success,
      long durationMs,
      Map<String, Object> config) {
  }
}
