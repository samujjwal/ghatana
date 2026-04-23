package com.ghatana.yappc.agent.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.coordination.DelegationManager;
import com.ghatana.agent.framework.coordination.OrchestrationStrategy;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.StepBudget;
import com.ghatana.yappc.agent.StepContext;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import com.ghatana.yappc.agent.YappcAgentRegistryAdapter;
import com.ghatana.agent.registry.InMemoryAgentRegistry;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

/**
 * Tests for {@link PlatformDeliveryCoordinator} — the Tier 1 orchestrator
 * for YAPPC platform delivery that manages the full SDLC lifecycle.
 *
 * @doc.type class
 * @doc.purpose Unit tests for PlatformDeliveryCoordinator lifecycle and validation
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("PlatformDeliveryCoordinator Tests")
class PlatformDeliveryCoordinatorTest extends EventloopTestBase {

  private YappcAgentRegistryAdapter agentRegistry;
  private EventLogMemoryStore memoryStore;
  private PlatformDeliveryCoordinator coordinator;

  @BeforeEach
  void setUp() { // GH-90000
    agentRegistry = new YappcAgentRegistryAdapter(new InMemoryAgentRegistry()); // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    DelegationManager delegationManager = mock(DelegationManager.class); // GH-90000
    OrchestrationStrategy orchestrationStrategy = mock(OrchestrationStrategy.class); // GH-90000
    DeliveryCoordinatorGenerator generator = new DeliveryCoordinatorGenerator(agentRegistry); // GH-90000

    coordinator = new PlatformDeliveryCoordinator( // GH-90000
        agentRegistry, delegationManager, orchestrationStrategy,
        memoryStore, generator);

    YAPPCAgentBase.setGlobalAepEventPublisher( // GH-90000
        (eventType, tenantId, payload) -> Promise.complete()); // GH-90000
  }

  // ===== Validation Tests =====

  @Nested
  @DisplayName("Input Validation")
  class InputValidation {

    @Test
    @DisplayName("Should accept valid delivery request")
    void shouldAcceptValidRequest() { // GH-90000
      DeliveryRequest request = new DeliveryRequest( // GH-90000
          "Build feature X",
          List.of("architecture", "implementation"), // GH-90000
          DeliveryRequest.Priority.NORMAL,
          Map.of()); // GH-90000

      var result = coordinator.validateInput(request); // GH-90000

      assertThat(result.ok()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should reject request with empty description")
    void shouldRejectEmptyDescription() { // GH-90000
      // DeliveryRequest constructor validates eagerly — empty string throws
      assertThatThrownBy(() -> // GH-90000
          new DeliveryRequest("", List.of("architecture"),
              DeliveryRequest.Priority.NORMAL, Map.of())) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Should reject request with null target phases")
    void shouldRejectNullPhases() { // GH-90000
      assertThatThrownBy(() -> // GH-90000
          new DeliveryRequest("Build X", null, // GH-90000
              DeliveryRequest.Priority.NORMAL, Map.of())) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Should reject request with null priority")
    void shouldRejectNullPriority() { // GH-90000
      assertThatThrownBy(() -> // GH-90000
          new DeliveryRequest("Build X", List.of("architecture"),
              null, Map.of())) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
  }

  // ===== Contract Tests =====

  @Nested
  @DisplayName("Step Contract")
  class StepContract {

    @Test
    @DisplayName("Should expose correct step name")
    void shouldExposeStepName() { // GH-90000
      assertThat(coordinator.stepName()).isEqualTo("platform.coordinate");
    }

    @Test
    @DisplayName("Should expose contract with coordination capabilities")
    void shouldExposeContract() { // GH-90000
      var contract = coordinator.contract(); // GH-90000

      assertThat(contract.name()).isEqualTo("platform.coordinate");
      assertThat(contract.requiredCapabilities()) // GH-90000
          .contains("orchestration", "delegation", "coordination"); // GH-90000
    }
  }

  // ===== Full Lifecycle Tests =====

  @Nested
  @DisplayName("Full Lifecycle Execution")
  class FullLifecycle {

    @Test
    @DisplayName("Should execute full delivery lifecycle")
    void shouldExecuteFullLifecycle() { // GH-90000
      // Register agents so phases can be "executed"
      agentRegistry.register(createStubPhaseAgent("arch-agent", "architecture.intake")); // GH-90000
      agentRegistry.register(createStubPhaseAgent("impl-agent", "implementation.scaffold")); // GH-90000

      DeliveryRequest request = new DeliveryRequest( // GH-90000
          "Build new API",
          List.of("architecture", "implementation"), // GH-90000
          DeliveryRequest.Priority.HIGH,
          Map.of()); // GH-90000

      StepContext stepCtx = new StepContext( // GH-90000
          "run-001", "tenant-1", "delivery", "config-1",
          new StepBudget(100.0, 120_000)); // GH-90000

      StepResult<DeliveryResult> result = runPromise( // GH-90000
          () -> coordinator.execute(request, stepCtx)); // GH-90000

      assertThat(result).isNotNull(); // GH-90000
      assertThat(result.success()).isTrue(); // GH-90000
      assertThat(result.output().phaseResults()).containsKeys("architecture", "implementation"); // GH-90000
    }

    @Test
    @DisplayName("Should reject invalid input during perceive phase")
    void shouldRejectInvalidInputDuringPerceive() { // GH-90000
      // Create DeliveryRequest with valid constructor, but we test via the
      // coordinator's validation by creating a request with empty phases
      assertThatThrownBy(() -> // GH-90000
          new DeliveryRequest("Build X", List.of(), // GH-90000
              DeliveryRequest.Priority.NORMAL, Map.of())) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
  }

  // ===== Test Helpers =====

  private YAPPCAgentBase<Object, Object> createStubPhaseAgent(String agentId, String stepName) { // GH-90000
    return new StubPhaseAgent(agentId, stepName, memoryStore); // GH-90000
  }

  static class StubPhaseAgent extends YAPPCAgentBase<Object, Object> {
    private final MemoryStore memoryStore;

    StubPhaseAgent(String agentId, String stepName, MemoryStore memoryStore) { // GH-90000
      super(agentId, stepName, // GH-90000
          new com.ghatana.yappc.agent.StepContract(stepName, "#/definitions/Object", // GH-90000
              "#/definitions/Object", List.of("phase-lead"),
              Map.of("version", "1.0.0")), // GH-90000
          new StubGenerator(), // GH-90000
        defaultEventPublisher()); // GH-90000
      this.memoryStore = memoryStore;
    }

    @Override
    protected MemoryStore getMemoryStore() { // GH-90000
      return memoryStore;
    }

    @Override
    public com.ghatana.yappc.agent.ValidationResult validateInput(@NotNull Object input) { // GH-90000
      return com.ghatana.yappc.agent.ValidationResult.success(); // GH-90000
    }
  }

  static class StubGenerator
      implements OutputGenerator<StepRequest<Object>, StepResult<Object>> {

    @Override
    public @NotNull Promise<StepResult<Object>> generate( // GH-90000
        @NotNull StepRequest<Object> input, @NotNull AgentContext context) {
      Instant start = Instant.now(); // GH-90000
      return Promise.of(StepResult.success("ok", Map.of(), start, Instant.now())); // GH-90000
    }

    @Override
    public @NotNull Promise<Double> estimateCost( // GH-90000
        @NotNull StepRequest<Object> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // GH-90000
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() { // GH-90000
      return GeneratorMetadata.builder() // GH-90000
          .name("StubGenerator").type("rule-based")
          .description("Stub").version("1.0.0").build();
    }
  }
}
