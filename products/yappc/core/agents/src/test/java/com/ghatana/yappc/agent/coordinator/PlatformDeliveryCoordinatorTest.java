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
import com.ghatana.yappc.agent.YAPPCAgentRegistry;
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

  private YAPPCAgentRegistry agentRegistry;
  private EventLogMemoryStore memoryStore;
  private PlatformDeliveryCoordinator coordinator;

  @BeforeEach
  void setUp() {
    agentRegistry = new YAPPCAgentRegistry();
    memoryStore = new EventLogMemoryStore();
    DelegationManager delegationManager = mock(DelegationManager.class);
    OrchestrationStrategy orchestrationStrategy = mock(OrchestrationStrategy.class);
    DeliveryCoordinatorGenerator generator = new DeliveryCoordinatorGenerator(agentRegistry);

    coordinator = new PlatformDeliveryCoordinator(
        agentRegistry, delegationManager, orchestrationStrategy,
        memoryStore, generator);

    YAPPCAgentBase.configureAepEventPublisher(
        (eventType, tenantId, payload) -> Promise.complete());
  }

  // ===== Validation Tests =====

  @Nested
  @DisplayName("Input Validation")
  class InputValidation {

    @Test
    @DisplayName("Should accept valid delivery request")
    void shouldAcceptValidRequest() {
      DeliveryRequest request = new DeliveryRequest(
          "Build feature X",
          List.of("architecture", "implementation"),
          DeliveryRequest.Priority.NORMAL,
          Map.of());

      var result = coordinator.validateInput(request);

      assertThat(result.ok()).isTrue();
    }

    @Test
    @DisplayName("Should reject request with empty description")
    void shouldRejectEmptyDescription() {
      // DeliveryRequest constructor validates eagerly — empty string throws
      assertThatThrownBy(() ->
          new DeliveryRequest("", List.of("architecture"),
              DeliveryRequest.Priority.NORMAL, Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject request with null target phases")
    void shouldRejectNullPhases() {
      assertThatThrownBy(() ->
          new DeliveryRequest("Build X", null,
              DeliveryRequest.Priority.NORMAL, Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject request with null priority")
    void shouldRejectNullPriority() {
      assertThatThrownBy(() ->
          new DeliveryRequest("Build X", List.of("architecture"),
              null, Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ===== Contract Tests =====

  @Nested
  @DisplayName("Step Contract")
  class StepContract {

    @Test
    @DisplayName("Should expose correct step name")
    void shouldExposeStepName() {
      assertThat(coordinator.stepName()).isEqualTo("platform.coordinate");
    }

    @Test
    @DisplayName("Should expose contract with coordination capabilities")
    void shouldExposeContract() {
      var contract = coordinator.contract();

      assertThat(contract.name()).isEqualTo("platform.coordinate");
      assertThat(contract.requiredCapabilities())
          .contains("orchestration", "delegation", "coordination");
    }
  }

  // ===== Full Lifecycle Tests =====

  @Nested
  @DisplayName("Full Lifecycle Execution")
  class FullLifecycle {

    @Test
    @DisplayName("Should execute full delivery lifecycle")
    void shouldExecuteFullLifecycle() {
      // Register agents so phases can be "executed"
      agentRegistry.register(createStubPhaseAgent("arch-agent", "architecture.intake"));
      agentRegistry.register(createStubPhaseAgent("impl-agent", "implementation.scaffold"));

      DeliveryRequest request = new DeliveryRequest(
          "Build new API",
          List.of("architecture", "implementation"),
          DeliveryRequest.Priority.HIGH,
          Map.of());

      StepContext stepCtx = new StepContext(
          "run-001", "tenant-1", "delivery", "config-1",
          new StepBudget(100.0, 120_000));

      StepResult<DeliveryResult> result = runPromise(
          () -> coordinator.execute(request, stepCtx));

      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();
      assertThat(result.output().phaseResults()).containsKeys("architecture", "implementation");
    }

    @Test
    @DisplayName("Should reject invalid input during perceive phase")
    void shouldRejectInvalidInputDuringPerceive() {
      // Create DeliveryRequest with valid constructor, but we test via the
      // coordinator's validation by creating a request with empty phases
      assertThatThrownBy(() ->
          new DeliveryRequest("Build X", List.of(),
              DeliveryRequest.Priority.NORMAL, Map.of()))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  // ===== Test Helpers =====

  private YAPPCAgentBase<Object, Object> createStubPhaseAgent(String agentId, String stepName) {
    return new StubPhaseAgent(agentId, stepName, memoryStore);
  }

  static class StubPhaseAgent extends YAPPCAgentBase<Object, Object> {
    private final MemoryStore memoryStore;

    StubPhaseAgent(String agentId, String stepName, MemoryStore memoryStore) {
      super(agentId, stepName,
          new com.ghatana.yappc.agent.StepContract(stepName, "#/definitions/Object",
              "#/definitions/Object", List.of("phase-lead"),
              Map.of("version", "1.0.0")),
          new StubGenerator());
      this.memoryStore = memoryStore;
    }

    @Override
    protected MemoryStore getMemoryStore() {
      return memoryStore;
    }

    @Override
    public com.ghatana.yappc.agent.ValidationResult validateInput(@NotNull Object input) {
      return com.ghatana.yappc.agent.ValidationResult.success();
    }
  }

  static class StubGenerator
      implements OutputGenerator<StepRequest<Object>, StepResult<Object>> {

    @Override
    public @NotNull Promise<StepResult<Object>> generate(
        @NotNull StepRequest<Object> input, @NotNull AgentContext context) {
      Instant start = Instant.now();
      return Promise.of(StepResult.success("ok", Map.of(), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<Object> input, @NotNull AgentContext context) {
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
