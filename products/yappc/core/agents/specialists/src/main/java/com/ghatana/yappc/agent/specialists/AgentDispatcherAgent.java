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
 * L2 domain-expert that routes tasks to appropriate agents based on capabilities.
 *
 * <p>The platform's central task router. Accepts task requests and capability queries,
 * performs agent selection via capability matching and load balancing, then returns
 * an agent assignment with confidence score. Escalates to full-lifecycle-orchestrator
 * when no suitable agent is found.
 *
 * @doc.type class
 * @doc.purpose Routes tasks to appropriate agents based on capabilities
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class AgentDispatcherAgent
    extends YAPPCAgentBase<AgentDispatcherInput, AgentDispatcherOutput> {

  private static final Logger log = LoggerFactory.getLogger(AgentDispatcherAgent.class);

  private final MemoryStore memoryStore;
  private final YAPPCAgentRegistry agentRegistry;

  /**
   * Constructs the agent dispatcher with access to the agent registry for capability lookup.
   *
   * @param memoryStore   memory store for dispatch event tracking
   * @param generator     output generator
   * @param agentRegistry the agent registry for capability-based lookups
   */
  public AgentDispatcherAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator,
      @NotNull YAPPCAgentRegistry agentRegistry) {
    super(
        "AgentDispatcherAgent",
        "expert.agent-dispatcher",
        new StepContract(
            "expert.agent-dispatcher",
            "#/definitions/AgentDispatcherInput",
            "#/definitions/AgentDispatcherOutput",
            List.of("task-routing", "agent-selection", "load-balancing"),
            Map.of(
                "description", "Routes tasks to appropriate agents based on capabilities",
                "version", "1.0.0",
                "level", "L2",
                "escalates_to", "full-lifecycle-orchestrator")),
        generator);
    this.memoryStore = memoryStore;
    this.agentRegistry = Objects.requireNonNull(agentRegistry, "agentRegistry required");
  }

  /** Convenience constructor without explicit registry (uses a no-op registry). */
  public AgentDispatcherAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> generator) {
    this(memoryStore, generator, new YAPPCAgentRegistry());
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull AgentDispatcherInput input) {
    if (input.taskId() == null || input.taskId().isEmpty()) {
      return ValidationResult.fail("taskId cannot be empty");
    }
    if (input.taskDescription() == null || input.taskDescription().isEmpty()) {
      return ValidationResult.fail("taskDescription cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<AgentDispatcherInput> perceive(
      @NotNull StepRequest<AgentDispatcherInput> request,
      @NotNull AgentContext context) {
    AgentDispatcherInput input = request.input();
    log.info("Perceiving dispatch request [{}] caps={} priority={}",
        input.taskId(), input.requiredCapabilities(), input.priority());
    return request;
  }

  /**
   * Rule-based agent dispatch generator.
   *
   * <p>Matches required capabilities against registered agent capabilities
   * and selects the best-fit agent.
   */
  public static class AgentDispatcherGenerator
      implements OutputGenerator<StepRequest<AgentDispatcherInput>,
          StepResult<AgentDispatcherOutput>> {

    private static final Logger log = LoggerFactory.getLogger(AgentDispatcherGenerator.class);

    /** Capability-to-agent routing table (static mappings). */
    private static final Map<String, String> CAPABILITY_ROUTING = Map.ofEntries(
        Map.entry("requirements", "architecture.intake"),
        Map.entry("design", "architecture.design"),
        Map.entry("implementation", "implementation.implement"),
        Map.entry("testing", "testing.generateTests"),
        Map.entry("deployment", "ops.deployStaging"),
        Map.entry("monitoring", "ops.monitor"),
        Map.entry("security", "specialist.security-tests"),
        Map.entry("code-review", "implementation.review"),
        Map.entry("performance", "specialist.performance-tests"),
        Map.entry("governance", "orchestrator.governance"),
        Map.entry("release", "ops.validateRelease"),
        Map.entry("incident-response", "ops.incidentResponse"));

    private static final String FALLBACK_AGENT = "strategic.full-lifecycle";

    @Override
    public @NotNull Promise<StepResult<AgentDispatcherOutput>> generate(
        @NotNull StepRequest<AgentDispatcherInput> input,
        @NotNull AgentContext context) {

      Instant start = Instant.now();
      AgentDispatcherInput dispatchInput = input.input();

      log.info("Dispatching task [{}] with capabilities: {}",
          dispatchInput.taskId(), dispatchInput.requiredCapabilities());

      // Score each potential agent by capability match
      String bestAgent = FALLBACK_AGENT;
      double bestScore = 0.0;
      List<String> alternatives = new ArrayList<>();

      for (String capability : dispatchInput.requiredCapabilities()) {
        String candidate = CAPABILITY_ROUTING.get(capability.toLowerCase(Locale.ROOT));
        if (candidate != null) {
          double score = 1.0 / dispatchInput.requiredCapabilities().size();
          if (candidate.equals(bestAgent)) {
            bestScore += score;
          } else if (score > bestScore) {
            if (!bestAgent.equals(FALLBACK_AGENT)) {
              alternatives.add(bestAgent);
            }
            bestAgent = candidate;
            bestScore += score;
          } else {
            alternatives.add(candidate);
          }
        }
      }

      // Clamp confidence
      double confidence = Math.min(1.0, bestScore);
      if (bestAgent.equals(FALLBACK_AGENT)) {
        confidence = 0.3; // Low confidence for fallback
      }

      String routingReason = bestAgent.equals(FALLBACK_AGENT)
          ? "no-capability-match-escalating"
          : "capability-match";

      AgentDispatcherOutput output = new AgentDispatcherOutput(
          "dispatch-" + dispatchInput.taskId() + "-" + UUID.randomUUID(),
          bestAgent,
          routingReason,
          alternatives,
          confidence,
          Map.of(
              "generatedAt", start.toString(),
              "candidatesEvaluated", CAPABILITY_ROUTING.size(),
              "priority", dispatchInput.priority()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of(
                  "stepId", "expert.agent-dispatcher",
                  "assignedAgent", bestAgent,
                  "confidence", confidence,
                  "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<AgentDispatcherInput> input,
        @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("AgentDispatcherGenerator")
          .type("rule-based")
          .description("Routes tasks to agents via capability matching")
          .version("1.0.0")
          .build();
    }
  }
}
