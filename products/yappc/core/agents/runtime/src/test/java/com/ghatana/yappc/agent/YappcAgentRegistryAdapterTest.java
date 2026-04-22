package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.registry.InMemoryAgentRegistry;
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
 * Tests for {@link YappcAgentRegistryAdapter} — the platform-backed registry adapter for YAPPC workflow agents.
 *
 * <p>Covers registration, lookup (by step name, ID, phase), lifecycle management // GH-90000
 * (initialization, shutdown), health monitoring, and edge cases. // GH-90000
 *
 * @doc.type class
 * @doc.purpose Unit tests for YappcAgentRegistryAdapter
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YappcAgentRegistryAdapter Tests [GH-90000]")
class YappcAgentRegistryAdapterTest extends EventloopTestBase {

  private YappcAgentRegistryAdapter registry;
  private EventLogMemoryStore memoryStore;

  @BeforeEach
  void setUp() { // GH-90000
    registry = new YappcAgentRegistryAdapter(new InMemoryAgentRegistry()); // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    YAPPCAgentBase.setGlobalAepEventPublisher( // GH-90000
        (eventType, tenantId, payload) -> Promise.complete()); // GH-90000
  }

  // ===== Registration Tests =====

  @Nested
  @DisplayName("Registration [GH-90000]")
  class Registration {

    @Test
    @DisplayName("Should register agent and increment count [GH-90000]")
    void shouldRegisterAgent() { // GH-90000
      StubAgent agent = createAgent("arch-intake", "architecture.intake"); // GH-90000

      runPromise(() -> registry.register(agent)); // GH-90000

      assertThat(registry.getAgentCount()).isEqualTo(1); // GH-90000
      assertThat(registry.hasAgent("architecture.intake [GH-90000]")).isTrue();
    }

    @Test
    @DisplayName("Should support method chaining on register [GH-90000]")
    void shouldSupportChaining() { // GH-90000
      StubAgent agent1 = createAgent("arch-intake", "architecture.intake"); // GH-90000
      StubAgent agent2 = createAgent("impl-scaffold", "implementation.scaffold"); // GH-90000

      runPromise(() -> registry.register(agent1)); // GH-90000
      runPromise(() -> registry.register(agent2)); // GH-90000

      assertThat(registry.getAgentCount()).isEqualTo(2); // GH-90000
    }

    @Test
    @DisplayName("Should register multiple agents for the same phase [GH-90000]")
    void shouldRegisterMultipleAgentsForSamePhase() { // GH-90000
      runPromise(() -> registry.register(createAgent("arch-intake", "architecture.intake"))); // GH-90000
      runPromise(() -> registry.register(createAgent("arch-derive", "architecture.derive"))); // GH-90000
      runPromise(() -> registry.register(createAgent("arch-validate", "architecture.validate"))); // GH-90000

      List<YAPPCAgentBase<?, ?>> phaseAgents = registry.getAgentsByPhase("architecture [GH-90000]");

      assertThat(phaseAgents).hasSize(3); // GH-90000
    }
  }

  // ===== Lookup Tests =====

  @Nested
  @DisplayName("Lookup [GH-90000]")
  class Lookup {

    @BeforeEach
    void registerAgents() { // GH-90000
      runPromise(() -> registry.register(createAgent("arch-intake", "architecture.intake"))); // GH-90000
      runPromise(() -> registry.register(createAgent("impl-scaffold", "implementation.scaffold"))); // GH-90000
      runPromise(() -> registry.register(createAgent("test-plan", "testing.plan"))); // GH-90000
    }

    @Test
    @DisplayName("Should find agent by step name [GH-90000]")
    void shouldFindByStepName() { // GH-90000
      WorkflowStep<?, ?> agent = registry.getAgent("architecture.intake [GH-90000]");

      assertThat(agent).isNotNull(); // GH-90000
      assertThat(agent.stepName()).isEqualTo("architecture.intake [GH-90000]");
    }

    @Test
    @DisplayName("Should find agent by agent ID [GH-90000]")
    void shouldFindByAgentId() { // GH-90000
      WorkflowStep<?, ?> agent = registry.getAgentById("arch-intake [GH-90000]");

      assertThat(agent).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should return null for unknown step name [GH-90000]")
    void shouldReturnNullForUnknownStep() { // GH-90000
      WorkflowStep<?, ?> agent = registry.getAgent("nonexistent.step [GH-90000]");

      assertThat(agent).isNull(); // GH-90000
    }

    @Test
    @DisplayName("Should return null for unknown agent ID [GH-90000]")
    void shouldReturnNullForUnknownId() { // GH-90000
      WorkflowStep<?, ?> agent = registry.getAgentById("nonexistent-id [GH-90000]");

      assertThat(agent).isNull(); // GH-90000
    }

    @Test
    @DisplayName("Should return empty list for unknown phase [GH-90000]")
    void shouldReturnEmptyForUnknownPhase() { // GH-90000
      List<YAPPCAgentBase<?, ?>> agents = registry.getAgentsByPhase("unknown [GH-90000]");

      assertThat(agents).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should return all registered step names [GH-90000]")
    void shouldReturnAllStepNames() { // GH-90000
      Set<String> stepNames = registry.getAllStepNames(); // GH-90000

      assertThat(stepNames).containsExactlyInAnyOrder( // GH-90000
          "architecture.intake", "implementation.scaffold", "testing.plan");
    }

    @Test
    @DisplayName("Should return all registered phases [GH-90000]")
    void shouldReturnAllPhases() { // GH-90000
      Set<String> phases = registry.getAllPhases(); // GH-90000

      assertThat(phases).containsExactlyInAnyOrder( // GH-90000
          "architecture", "implementation", "testing");
    }

    @Test
    @DisplayName("Should report false for unregistered step [GH-90000]")
    void shouldReturnFalseForUnregisteredStep() { // GH-90000
      assertThat(registry.hasAgent("ops.deploy [GH-90000]")).isFalse();
    }
  }

  // ===== Lifecycle Tests =====

  @Nested
  @DisplayName("Lifecycle Management [GH-90000]")
  class LifecycleManagement {

    @Test
    @DisplayName("Should initialize all agents and set status to READY [GH-90000]")
    void shouldInitializeAllAgents() { // GH-90000
      runPromise(() -> registry.register(createAgent("agent-1", "phase1.step1"))); // GH-90000
      runPromise(() -> registry.register(createAgent("agent-2", "phase2.step2"))); // GH-90000

      runPromise(registry::initializeAll); // GH-90000

        Map<String, AgentLifecycleStatus> health = registry.getHealthStatus(); // GH-90000
      assertThat(health.values()) // GH-90000
          .allMatch(s -> s == AgentLifecycleStatus.READY); // GH-90000
    }

    @Test
    @DisplayName("Should shutdown all agents and set status to STOPPED [GH-90000]")
    void shouldShutdownAllAgents() { // GH-90000
      runPromise(() -> registry.register(createAgent("agent-1", "phase1.step1"))); // GH-90000
      runPromise(() -> registry.register(createAgent("agent-2", "phase2.step2"))); // GH-90000

      runPromise(registry::initializeAll); // GH-90000
      runPromise(registry::shutdownAll); // GH-90000

        Map<String, AgentLifecycleStatus> health = registry.getHealthStatus(); // GH-90000
      assertThat(health.values()) // GH-90000
          .allMatch(s -> s == AgentLifecycleStatus.STOPPED); // GH-90000
    }

    @Test
    @DisplayName("Should report REGISTERED status before initialization [GH-90000]")
    void shouldReportRegisteredBeforeInit() { // GH-90000
      runPromise(() -> registry.register(createAgent("agent-1", "phase1.step1"))); // GH-90000

        Map<String, AgentLifecycleStatus> health = registry.getHealthStatus(); // GH-90000
      assertThat(health.get("agent-1 [GH-90000]"))
          .isEqualTo(AgentLifecycleStatus.REGISTERED); // GH-90000
    }
  }

  // ===== Phase Extraction Tests =====

  @Nested
  @DisplayName("Phase Extraction [GH-90000]")
  class PhaseExtraction {

    @Test
    @DisplayName("Should extract phase from dotted step name [GH-90000]")
    void shouldExtractPhaseFromDottedName() { // GH-90000
      runPromise(() -> registry.register(createAgent("agent-1", "architecture.intake"))); // GH-90000

      assertThat(registry.getAllPhases()).contains("architecture [GH-90000]");
    }

    @Test
    @DisplayName("Should use full step name when no dot separator [GH-90000]")
    void shouldUseFullNameWhenNoDot() { // GH-90000
      runPromise(() -> registry.register(createAgent("agent-1", "coordinator"))); // GH-90000

      assertThat(registry.getAllPhases()).contains("coordinator [GH-90000]");
    }
  }

  // ===== Empty Registry Edge Cases =====

  @Nested
  @DisplayName("Empty Registry [GH-90000]")
  class EmptyRegistry {

    @Test
    @DisplayName("Should report zero count when empty [GH-90000]")
    void shouldReportZeroCount() { // GH-90000
      assertThat(registry.getAgentCount()).isEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Should initialize empty registry without error [GH-90000]")
    void shouldInitializeEmptyRegistry() { // GH-90000
      runPromise(registry::initializeAll); // GH-90000

      assertThat(registry.getHealthStatus()).isEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should return empty sets for queries [GH-90000]")
    void shouldReturnEmptySets() { // GH-90000
      assertThat(registry.getAllStepNames()).isEmpty(); // GH-90000
      assertThat(registry.getAllPhases()).isEmpty(); // GH-90000
    }
  }

  // ===== Test Helpers =====

  private StubAgent createAgent(String agentId, String stepName) { // GH-90000
    return new StubAgent(agentId, stepName, memoryStore, new StubGenerator()); // GH-90000
  }

  static class StubAgent extends YAPPCAgentBase<StubInput, StubOutput> {
    private final MemoryStore memoryStore;

    StubAgent(String agentId, String stepName, MemoryStore memoryStore, // GH-90000
        OutputGenerator<StepRequest<StubInput>, StepResult<StubOutput>> generator) {
      super(agentId, stepName, // GH-90000
          new StepContract(stepName, "#/definitions/StubInput", // GH-90000
              "#/definitions/StubOutput", List.of("stub [GH-90000]"),
              Map.of("description", "Stub agent", "version", "1.0.0")), // GH-90000
          generator,
        defaultEventPublisher()); // GH-90000
      this.memoryStore = memoryStore;
    }

    @Override
    protected MemoryStore getMemoryStore() { // GH-90000
      return memoryStore;
    }

    @Override
    public ValidationResult validateInput(@NotNull StubInput input) { // GH-90000
      return ValidationResult.success(); // GH-90000
    }
  }

  record StubInput(String id) {} // GH-90000

  record StubOutput(String resultId) {} // GH-90000

  static class StubGenerator
      implements OutputGenerator<StepRequest<StubInput>, StepResult<StubOutput>> {

    @Override
    public @NotNull Promise<StepResult<StubOutput>> generate( // GH-90000
        @NotNull StepRequest<StubInput> input, @NotNull AgentContext context) {
      Instant start = Instant.now(); // GH-90000
      return Promise.of(StepResult.success( // GH-90000
          new StubOutput("result-" + input.input().id()), // GH-90000
          Map.of(), start, Instant.now())); // GH-90000
    }

    @Override
    public @NotNull Promise<Double> estimateCost( // GH-90000
        @NotNull StepRequest<StubInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // GH-90000
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() { // GH-90000
      return GeneratorMetadata.builder() // GH-90000
          .name("StubGenerator [GH-90000]").type("rule-based [GH-90000]")
          .description("Stub [GH-90000]").version("1.0.0 [GH-90000]").build();
    }
  }
}
