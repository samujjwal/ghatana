package com.ghatana.yappc.agent.coordinator;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import com.ghatana.yappc.agent.YappcAgentRegistryAdapter;
import com.ghatana.yappc.agent.StepContext;
import com.ghatana.yappc.agent.StepBudget;
import com.ghatana.yappc.agent.StepContract;
import com.ghatana.yappc.agent.ValidationResult;
import com.ghatana.agent.registry.InMemoryAgentRegistry;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DeliveryCoordinatorGenerator} — the rule-based orchestration engine
 * that determines SDLC phase execution order and delegates to phase lead agents.
 *
 * @doc.type class
 * @doc.purpose Unit tests for delivery coordination orchestration logic
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("DeliveryCoordinatorGenerator Tests")
class DeliveryCoordinatorGeneratorTest extends EventloopTestBase {

  private YappcAgentRegistryAdapter agentRegistry;
  private DeliveryCoordinatorGenerator generator;
  private EventLogMemoryStore memoryStore;

  @BeforeEach
  void setUp() { // GH-90000
    agentRegistry = new YappcAgentRegistryAdapter(new InMemoryAgentRegistry()); // GH-90000
    generator = new DeliveryCoordinatorGenerator(agentRegistry); // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    YAPPCAgentBase.setGlobalAepEventPublisher( // GH-90000
        (eventType, tenantId, payload) -> Promise.complete()); // GH-90000
  }

  // ===== Phase Ordering Tests =====

  @Nested
  @DisplayName("Phase Execution Order")
  class PhaseExecutionOrder {

    @Test
    @DisplayName("Should execute phases in SDLC dependency order")
    void shouldExecutePhasesInOrder() { // GH-90000
      // Register agents for 3 phases
      registerPhaseAgent("architecture");
      registerPhaseAgent("implementation");
      registerPhaseAgent("testing");

      // Request phases in reverse order — generator should reorder them
      DeliveryRequest request = new DeliveryRequest( // GH-90000
          "Build new feature",
          List.of("testing", "architecture", "implementation"), // GH-90000
          DeliveryRequest.Priority.NORMAL,
          Map.of()); // GH-90000

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request); // GH-90000
      AgentContext ctx = createAgentContext(); // GH-90000

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx)); // GH-90000

      assertThat(result).isNotNull(); // GH-90000
      assertThat(result.success()).isTrue(); // GH-90000

      // Verify phase order is dependency-correct
      List<String> executedPhases = result.output().phaseResults().keySet().stream().toList(); // GH-90000
      assertThat(executedPhases).containsExactly("architecture", "implementation", "testing"); // GH-90000
    }

    @Test
    @DisplayName("Should only execute requested phases")
    void shouldOnlyExecuteRequestedPhases() { // GH-90000
      registerPhaseAgent("architecture");
      registerPhaseAgent("implementation");
      registerPhaseAgent("testing");
      registerPhaseAgent("ops");

      DeliveryRequest request = new DeliveryRequest( // GH-90000
          "Architecture review",
          List.of("architecture"),
          DeliveryRequest.Priority.HIGH,
          Map.of()); // GH-90000

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request); // GH-90000
      AgentContext ctx = createAgentContext(); // GH-90000

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx)); // GH-90000

      assertThat(result.output().phaseResults()).hasSize(1); // GH-90000
      assertThat(result.output().phaseResults()).containsKey("architecture");
    }
  }

  // ===== Agent Availability Tests =====

  @Nested
  @DisplayName("Agent Availability")
  class AgentAvailability {

    @Test
    @DisplayName("Should skip phases with no registered agents")
    void shouldSkipPhasesWithNoAgents() { // GH-90000
      // Only register agents for architecture, not implementation
      registerPhaseAgent("architecture");

      DeliveryRequest request = new DeliveryRequest( // GH-90000
          "Full build",
          List.of("architecture", "implementation"), // GH-90000
          DeliveryRequest.Priority.NORMAL,
          Map.of()); // GH-90000

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request); // GH-90000
      AgentContext ctx = createAgentContext(); // GH-90000

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx)); // GH-90000

      assertThat(result.output().phaseResults().get("architecture").status())
          .isEqualTo("SUCCESS");
      assertThat(result.output().phaseResults().get("implementation").status())
          .isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName("Should report not overall success when phases are skipped")
    void shouldReportPartialSuccess() { // GH-90000
      registerPhaseAgent("architecture");

      DeliveryRequest request = new DeliveryRequest( // GH-90000
          "Full delivery",
          List.of("architecture", "implementation"), // GH-90000
          DeliveryRequest.Priority.NORMAL,
          Map.of()); // GH-90000

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request); // GH-90000
      AgentContext ctx = createAgentContext(); // GH-90000

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx)); // GH-90000

      // SKIPPED != SUCCESS → not all successful
      assertThat(result.output().overallSuccess()).isFalse(); // GH-90000
    }
  }

  // ===== Cost Estimation Tests =====

  @Nested
  @DisplayName("Cost Estimation")
  class CostEstimation {

    @Test
    @DisplayName("Should estimate zero cost for rule-based coordination")
    void shouldEstimateZeroCost() { // GH-90000
      DeliveryRequest request = new DeliveryRequest( // GH-90000
          "Estimate cost",
          List.of("architecture"),
          DeliveryRequest.Priority.NORMAL,
          Map.of()); // GH-90000

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request); // GH-90000
      AgentContext ctx = createAgentContext(); // GH-90000

      Double cost = runPromise(() -> generator.estimateCost(stepRequest, ctx)); // GH-90000

      assertThat(cost).isEqualTo(0.0); // GH-90000
    }
  }

  // ===== Metadata Tests =====

  @Nested
  @DisplayName("Generator Metadata")
  class MetadataTests {

    @Test
    @DisplayName("Should provide accurate metadata")
    void shouldProvideMetadata() { // GH-90000
      GeneratorMetadata metadata = generator.getMetadata(); // GH-90000

      assertThat(metadata.getName()).isEqualTo("DeliveryCoordinatorGenerator");
      assertThat(metadata.getType()).isEqualTo("rule-based");
    }
  }

  // ===== Result Structure Tests =====

  @Nested
  @DisplayName("Result Structure")
  class ResultStructure {

    @Test
    @DisplayName("Should include all metadata in result")
    void shouldIncludeResultMetadata() { // GH-90000
      registerPhaseAgent("architecture");

      DeliveryRequest request = new DeliveryRequest( // GH-90000
          "Test metadata",
          List.of("architecture"),
          DeliveryRequest.Priority.CRITICAL,
          Map.of()); // GH-90000

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request); // GH-90000
      AgentContext ctx = createAgentContext(); // GH-90000

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx)); // GH-90000

      assertThat(result.output().metadata()) // GH-90000
          .containsEntry("coordinator", "PlatformDeliveryCoordinator") // GH-90000
          .containsEntry("priority", "CRITICAL") // GH-90000
          .containsEntry("totalPhases", 1); // GH-90000
    }

    @Test
    @DisplayName("Should track total execution time")
    void shouldTrackExecutionTime() { // GH-90000
      registerPhaseAgent("architecture");

      DeliveryRequest request = new DeliveryRequest( // GH-90000
          "Timing test",
          List.of("architecture"),
          DeliveryRequest.Priority.NORMAL,
          Map.of()); // GH-90000

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request); // GH-90000
      AgentContext ctx = createAgentContext(); // GH-90000

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx)); // GH-90000

      assertThat(result.output().totalExecutionTimeMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }

    @Test
    @DisplayName("Should report overall success when all phases succeed")
    void shouldReportOverallSuccess() { // GH-90000
      registerPhaseAgent("architecture");
      registerPhaseAgent("testing");

      DeliveryRequest request = new DeliveryRequest( // GH-90000
          "Successful delivery",
          List.of("architecture", "testing"), // GH-90000
          DeliveryRequest.Priority.NORMAL,
          Map.of()); // GH-90000

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request); // GH-90000
      AgentContext ctx = createAgentContext(); // GH-90000

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx)); // GH-90000

      assertThat(result.output().overallSuccess()).isTrue(); // GH-90000
    }
  }

  // ===== Test Helpers =====

  private void registerPhaseAgent(String phase) { // GH-90000
    String stepName = phase + ".step";
    String agentId = phase + "-agent";
    StubPhaseAgent agent = new StubPhaseAgent(agentId, stepName, memoryStore); // GH-90000
    agentRegistry.register(agent); // GH-90000
  }

  private StepRequest<DeliveryRequest> createStepRequest(DeliveryRequest request) { // GH-90000
    StepContext ctx = new StepContext( // GH-90000
        "run-001", "tenant-1", "delivery", "config-1",
        new StepBudget(100.0, 120_000)); // GH-90000
    return new StepRequest<>(request, ctx); // GH-90000
  }

  private AgentContext createAgentContext() { // GH-90000
    return AgentContext.builder() // GH-90000
        .agentId("DeliveryCoordinatorGenerator")
        .turnId("turn-001")
        .tenantId("tenant-1")
        .sessionId("session-1")
        .memoryStore(memoryStore) // GH-90000
        .build(); // GH-90000
  }

  // Stub agent that represents a phase lead
  static class StubPhaseAgent extends YAPPCAgentBase<Object, Object> {
    private final MemoryStore memoryStore;

    StubPhaseAgent(String agentId, String stepName, MemoryStore memoryStore) { // GH-90000
      super(agentId, stepName, // GH-90000
          new StepContract(stepName, "#/definitions/Object", // GH-90000
              "#/definitions/Object", List.of("phase-lead"),
              Map.of("description", "Stub phase agent", "version", "1.0.0")), // GH-90000
          new StubPhaseGenerator(), // GH-90000
        defaultEventPublisher()); // GH-90000
      this.memoryStore = memoryStore;
    }

    @Override
    protected MemoryStore getMemoryStore() { // GH-90000
      return memoryStore;
    }

    @Override
    public ValidationResult validateInput(@NotNull Object input) { // GH-90000
      return ValidationResult.success(); // GH-90000
    }
  }

  static class StubPhaseGenerator
      implements OutputGenerator<StepRequest<Object>, StepResult<Object>> {

    @Override
    public @NotNull Promise<StepResult<Object>> generate( // GH-90000
        @NotNull StepRequest<Object> input, @NotNull AgentContext context) {
      Instant start = Instant.now(); // GH-90000
      return Promise.of(StepResult.success("done", Map.of(), start, Instant.now())); // GH-90000
    }

    @Override
    public @NotNull Promise<Double> estimateCost( // GH-90000
        @NotNull StepRequest<Object> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // GH-90000
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() { // GH-90000
      return GeneratorMetadata.builder() // GH-90000
          .name("StubPhaseGenerator").type("rule-based")
          .description("Stub").version("1.0.0").build();
    }
  }
}
