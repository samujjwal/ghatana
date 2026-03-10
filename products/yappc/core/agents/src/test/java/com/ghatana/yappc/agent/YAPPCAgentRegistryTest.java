package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link YAPPCAgentRegistry} — the central registry for all YAPPC workflow agents.
 *
 * <p>Covers registration, lookup (by step name, ID, phase), lifecycle management
 * (initialization, shutdown), health monitoring, and edge cases.
 *
 * @doc.type class
 * @doc.purpose Unit tests for YAPPCAgentRegistry
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YAPPCAgentRegistry Tests")
class YAPPCAgentRegistryTest extends EventloopTestBase {

  private YAPPCAgentRegistry registry;
  private EventLogMemoryStore memoryStore;

  @BeforeEach
  void setUp() {
    registry = new YAPPCAgentRegistry();
    memoryStore = new EventLogMemoryStore();
    YAPPCAgentBase.configureAepEventPublisher(
        (eventType, tenantId, payload) -> Promise.complete());
  }

  // ===== Registration Tests =====

  @Nested
  @DisplayName("Registration")
  class Registration {

    @Test
    @DisplayName("Should register agent and increment count")
    void shouldRegisterAgent() {
      StubAgent agent = createAgent("arch-intake", "architecture.intake");

      registry.register(agent);

      assertThat(registry.getAgentCount()).isEqualTo(1);
      assertThat(registry.hasAgent("architecture.intake")).isTrue();
    }

    @Test
    @DisplayName("Should support method chaining on register")
    void shouldSupportChaining() {
      StubAgent agent1 = createAgent("arch-intake", "architecture.intake");
      StubAgent agent2 = createAgent("impl-scaffold", "implementation.scaffold");

      YAPPCAgentRegistry result = registry.register(agent1).register(agent2);

      assertThat(result).isSameAs(registry);
      assertThat(registry.getAgentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should register multiple agents for the same phase")
    void shouldRegisterMultipleAgentsForSamePhase() {
      registry.register(createAgent("arch-intake", "architecture.intake"));
      registry.register(createAgent("arch-derive", "architecture.derive"));
      registry.register(createAgent("arch-validate", "architecture.validate"));

      List<YAPPCAgentBase<?, ?>> phaseAgents = registry.getAgentsByPhase("architecture");

      assertThat(phaseAgents).hasSize(3);
    }
  }

  // ===== Lookup Tests =====

  @Nested
  @DisplayName("Lookup")
  class Lookup {

    @BeforeEach
    void registerAgents() {
      registry.register(createAgent("arch-intake", "architecture.intake"));
      registry.register(createAgent("impl-scaffold", "implementation.scaffold"));
      registry.register(createAgent("test-plan", "testing.plan"));
    }

    @Test
    @DisplayName("Should find agent by step name")
    void shouldFindByStepName() {
      WorkflowStep<?, ?> agent = registry.getAgent("architecture.intake");

      assertThat(agent).isNotNull();
      assertThat(agent.stepName()).isEqualTo("architecture.intake");
    }

    @Test
    @DisplayName("Should find agent by agent ID")
    void shouldFindByAgentId() {
      WorkflowStep<?, ?> agent = registry.getAgentById("arch-intake");

      assertThat(agent).isNotNull();
    }

    @Test
    @DisplayName("Should return null for unknown step name")
    void shouldReturnNullForUnknownStep() {
      WorkflowStep<?, ?> agent = registry.getAgent("nonexistent.step");

      assertThat(agent).isNull();
    }

    @Test
    @DisplayName("Should return null for unknown agent ID")
    void shouldReturnNullForUnknownId() {
      WorkflowStep<?, ?> agent = registry.getAgentById("nonexistent-id");

      assertThat(agent).isNull();
    }

    @Test
    @DisplayName("Should return empty list for unknown phase")
    void shouldReturnEmptyForUnknownPhase() {
      List<YAPPCAgentBase<?, ?>> agents = registry.getAgentsByPhase("unknown");

      assertThat(agents).isEmpty();
    }

    @Test
    @DisplayName("Should return all registered step names")
    void shouldReturnAllStepNames() {
      Set<String> stepNames = registry.getAllStepNames();

      assertThat(stepNames).containsExactlyInAnyOrder(
          "architecture.intake", "implementation.scaffold", "testing.plan");
    }

    @Test
    @DisplayName("Should return all registered phases")
    void shouldReturnAllPhases() {
      Set<String> phases = registry.getAllPhases();

      assertThat(phases).containsExactlyInAnyOrder(
          "architecture", "implementation", "testing");
    }

    @Test
    @DisplayName("Should report false for unregistered step")
    void shouldReturnFalseForUnregisteredStep() {
      assertThat(registry.hasAgent("ops.deploy")).isFalse();
    }
  }

  // ===== Lifecycle Tests =====

  @Nested
  @DisplayName("Lifecycle Management")
  class LifecycleManagement {

    @Test
    @DisplayName("Should initialize all agents and set status to READY")
    void shouldInitializeAllAgents() {
      registry.register(createAgent("agent-1", "phase1.step1"));
      registry.register(createAgent("agent-2", "phase2.step2"));

      runPromise(registry::initializeAll);

      Map<String, YAPPCAgentRegistry.AgentStatus> health = registry.getHealthStatus();
      assertThat(health.values())
          .allMatch(s -> s == YAPPCAgentRegistry.AgentStatus.READY);
    }

    @Test
    @DisplayName("Should shutdown all agents and set status to STOPPED")
    void shouldShutdownAllAgents() {
      registry.register(createAgent("agent-1", "phase1.step1"));
      registry.register(createAgent("agent-2", "phase2.step2"));

      runPromise(registry::initializeAll);
      runPromise(registry::shutdownAll);

      Map<String, YAPPCAgentRegistry.AgentStatus> health = registry.getHealthStatus();
      assertThat(health.values())
          .allMatch(s -> s == YAPPCAgentRegistry.AgentStatus.STOPPED);
    }

    @Test
    @DisplayName("Should report REGISTERED status before initialization")
    void shouldReportRegisteredBeforeInit() {
      registry.register(createAgent("agent-1", "phase1.step1"));

      Map<String, YAPPCAgentRegistry.AgentStatus> health = registry.getHealthStatus();
      assertThat(health.get("agent-1"))
          .isEqualTo(YAPPCAgentRegistry.AgentStatus.REGISTERED);
    }
  }

  // ===== Phase Extraction Tests =====

  @Nested
  @DisplayName("Phase Extraction")
  class PhaseExtraction {

    @Test
    @DisplayName("Should extract phase from dotted step name")
    void shouldExtractPhaseFromDottedName() {
      registry.register(createAgent("agent-1", "architecture.intake"));

      assertThat(registry.getAllPhases()).contains("architecture");
    }

    @Test
    @DisplayName("Should use full step name when no dot separator")
    void shouldUseFullNameWhenNoDot() {
      registry.register(createAgent("agent-1", "coordinator"));

      assertThat(registry.getAllPhases()).contains("coordinator");
    }
  }

  // ===== Empty Registry Edge Cases =====

  @Nested
  @DisplayName("Empty Registry")
  class EmptyRegistry {

    @Test
    @DisplayName("Should report zero count when empty")
    void shouldReportZeroCount() {
      assertThat(registry.getAgentCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should initialize empty registry without error")
    void shouldInitializeEmptyRegistry() {
      runPromise(registry::initializeAll);

      assertThat(registry.getHealthStatus()).isEmpty();
    }

    @Test
    @DisplayName("Should return empty sets for queries")
    void shouldReturnEmptySets() {
      assertThat(registry.getAllStepNames()).isEmpty();
      assertThat(registry.getAllPhases()).isEmpty();
    }
  }

  // ===== Test Helpers =====

  private StubAgent createAgent(String agentId, String stepName) {
    return new StubAgent(agentId, stepName, memoryStore, new StubGenerator());
  }

  static class StubAgent extends YAPPCAgentBase<StubInput, StubOutput> {
    private final MemoryStore memoryStore;

    StubAgent(String agentId, String stepName, MemoryStore memoryStore,
        OutputGenerator<StepRequest<StubInput>, StepResult<StubOutput>> generator) {
      super(agentId, stepName,
          new StepContract(stepName, "#/definitions/StubInput",
              "#/definitions/StubOutput", List.of("stub"),
              Map.of("description", "Stub agent", "version", "1.0.0")),
          generator);
      this.memoryStore = memoryStore;
    }

    @Override
    protected MemoryStore getMemoryStore() {
      return memoryStore;
    }

    @Override
    public ValidationResult validateInput(@NotNull StubInput input) {
      return ValidationResult.success();
    }
  }

  record StubInput(String id) {}

  record StubOutput(String resultId) {}

  static class StubGenerator
      implements OutputGenerator<StepRequest<StubInput>, StepResult<StubOutput>> {

    @Override
    public @NotNull Promise<StepResult<StubOutput>> generate(
        @NotNull StepRequest<StubInput> input, @NotNull AgentContext context) {
      Instant start = Instant.now();
      return Promise.of(StepResult.success(
          new StubOutput("result-" + input.input().id()),
          Map.of(), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<StubInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("StubGenerator").type("rule-based")
          .description("Stub").version("1.0.0").build();
    }
  }
}
