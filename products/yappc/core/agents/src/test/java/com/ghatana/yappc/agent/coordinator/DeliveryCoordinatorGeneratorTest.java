package com.ghatana.yappc.agent.coordinator;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import com.ghatana.yappc.agent.YAPPCAgentRegistry;
import com.ghatana.yappc.agent.StepContext;
import com.ghatana.yappc.agent.StepBudget;
import com.ghatana.yappc.agent.StepContract;
import com.ghatana.yappc.agent.ValidationResult;
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

  private YAPPCAgentRegistry agentRegistry;
  private DeliveryCoordinatorGenerator generator;
  private EventLogMemoryStore memoryStore;

  @BeforeEach
  void setUp() {
    agentRegistry = new YAPPCAgentRegistry();
    generator = new DeliveryCoordinatorGenerator(agentRegistry);
    memoryStore = new EventLogMemoryStore();
    YAPPCAgentBase.configureAepEventPublisher(
        (eventType, tenantId, payload) -> Promise.complete());
  }

  // ===== Phase Ordering Tests =====

  @Nested
  @DisplayName("Phase Execution Order")
  class PhaseExecutionOrder {

    @Test
    @DisplayName("Should execute phases in SDLC dependency order")
    void shouldExecutePhasesInOrder() {
      // Register agents for 3 phases
      registerPhaseAgent("architecture");
      registerPhaseAgent("implementation");
      registerPhaseAgent("testing");

      // Request phases in reverse order — generator should reorder them
      DeliveryRequest request = new DeliveryRequest(
          "Build new feature",
          List.of("testing", "architecture", "implementation"),
          DeliveryRequest.Priority.NORMAL,
          Map.of());

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request);
      AgentContext ctx = createAgentContext();

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx));

      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();

      // Verify phase order is dependency-correct
      List<String> executedPhases = result.output().phaseResults().keySet().stream().toList();
      assertThat(executedPhases).containsExactly("architecture", "implementation", "testing");
    }

    @Test
    @DisplayName("Should only execute requested phases")
    void shouldOnlyExecuteRequestedPhases() {
      registerPhaseAgent("architecture");
      registerPhaseAgent("implementation");
      registerPhaseAgent("testing");
      registerPhaseAgent("ops");

      DeliveryRequest request = new DeliveryRequest(
          "Architecture review",
          List.of("architecture"),
          DeliveryRequest.Priority.HIGH,
          Map.of());

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request);
      AgentContext ctx = createAgentContext();

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx));

      assertThat(result.output().phaseResults()).hasSize(1);
      assertThat(result.output().phaseResults()).containsKey("architecture");
    }
  }

  // ===== Agent Availability Tests =====

  @Nested
  @DisplayName("Agent Availability")
  class AgentAvailability {

    @Test
    @DisplayName("Should skip phases with no registered agents")
    void shouldSkipPhasesWithNoAgents() {
      // Only register agents for architecture, not implementation
      registerPhaseAgent("architecture");

      DeliveryRequest request = new DeliveryRequest(
          "Full build",
          List.of("architecture", "implementation"),
          DeliveryRequest.Priority.NORMAL,
          Map.of());

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request);
      AgentContext ctx = createAgentContext();

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx));

      assertThat(result.output().phaseResults().get("architecture").status())
          .isEqualTo("SUCCESS");
      assertThat(result.output().phaseResults().get("implementation").status())
          .isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName("Should report not overall success when phases are skipped")
    void shouldReportPartialSuccess() {
      registerPhaseAgent("architecture");

      DeliveryRequest request = new DeliveryRequest(
          "Full delivery",
          List.of("architecture", "implementation"),
          DeliveryRequest.Priority.NORMAL,
          Map.of());

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request);
      AgentContext ctx = createAgentContext();

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx));

      // SKIPPED != SUCCESS → not all successful
      assertThat(result.output().overallSuccess()).isFalse();
    }
  }

  // ===== Cost Estimation Tests =====

  @Nested
  @DisplayName("Cost Estimation")
  class CostEstimation {

    @Test
    @DisplayName("Should estimate zero cost for rule-based coordination")
    void shouldEstimateZeroCost() {
      DeliveryRequest request = new DeliveryRequest(
          "Estimate cost",
          List.of("architecture"),
          DeliveryRequest.Priority.NORMAL,
          Map.of());

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request);
      AgentContext ctx = createAgentContext();

      Double cost = runPromise(() -> generator.estimateCost(stepRequest, ctx));

      assertThat(cost).isEqualTo(0.0);
    }
  }

  // ===== Metadata Tests =====

  @Nested
  @DisplayName("Generator Metadata")
  class MetadataTests {

    @Test
    @DisplayName("Should provide accurate metadata")
    void shouldProvideMetadata() {
      GeneratorMetadata metadata = generator.getMetadata();

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
    void shouldIncludeResultMetadata() {
      registerPhaseAgent("architecture");

      DeliveryRequest request = new DeliveryRequest(
          "Test metadata",
          List.of("architecture"),
          DeliveryRequest.Priority.CRITICAL,
          Map.of());

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request);
      AgentContext ctx = createAgentContext();

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx));

      assertThat(result.output().metadata())
          .containsEntry("coordinator", "PlatformDeliveryCoordinator")
          .containsEntry("priority", "CRITICAL")
          .containsEntry("totalPhases", 1);
    }

    @Test
    @DisplayName("Should track total execution time")
    void shouldTrackExecutionTime() {
      registerPhaseAgent("architecture");

      DeliveryRequest request = new DeliveryRequest(
          "Timing test",
          List.of("architecture"),
          DeliveryRequest.Priority.NORMAL,
          Map.of());

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request);
      AgentContext ctx = createAgentContext();

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx));

      assertThat(result.output().totalExecutionTimeMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should report overall success when all phases succeed")
    void shouldReportOverallSuccess() {
      registerPhaseAgent("architecture");
      registerPhaseAgent("testing");

      DeliveryRequest request = new DeliveryRequest(
          "Successful delivery",
          List.of("architecture", "testing"),
          DeliveryRequest.Priority.NORMAL,
          Map.of());

      StepRequest<DeliveryRequest> stepRequest = createStepRequest(request);
      AgentContext ctx = createAgentContext();

      StepResult<DeliveryResult> result = runPromise(() -> generator.generate(stepRequest, ctx));

      assertThat(result.output().overallSuccess()).isTrue();
    }
  }

  // ===== Test Helpers =====

  private void registerPhaseAgent(String phase) {
    String stepName = phase + ".step";
    String agentId = phase + "-agent";
    StubPhaseAgent agent = new StubPhaseAgent(agentId, stepName, memoryStore);
    agentRegistry.register(agent);
  }

  private StepRequest<DeliveryRequest> createStepRequest(DeliveryRequest request) {
    StepContext ctx = new StepContext(
        "run-001", "tenant-1", "delivery", "config-1",
        new StepBudget(100.0, 120_000));
    return new StepRequest<>(request, ctx);
  }

  private AgentContext createAgentContext() {
    return AgentContext.builder()
        .agentId("DeliveryCoordinatorGenerator")
        .turnId("turn-001")
        .tenantId("tenant-1")
        .sessionId("session-1")
        .memoryStore(memoryStore)
        .build();
  }

  // Stub agent that represents a phase lead
  static class StubPhaseAgent extends YAPPCAgentBase<Object, Object> {
    private final MemoryStore memoryStore;

    StubPhaseAgent(String agentId, String stepName, MemoryStore memoryStore) {
      super(agentId, stepName,
          new StepContract(stepName, "#/definitions/Object",
              "#/definitions/Object", List.of("phase-lead"),
              Map.of("description", "Stub phase agent", "version", "1.0.0")),
          new StubPhaseGenerator());
      this.memoryStore = memoryStore;
    }

    @Override
    protected MemoryStore getMemoryStore() {
      return memoryStore;
    }

    @Override
    public ValidationResult validateInput(@NotNull Object input) {
      return ValidationResult.success();
    }
  }

  static class StubPhaseGenerator
      implements OutputGenerator<StepRequest<Object>, StepResult<Object>> {

    @Override
    public @NotNull Promise<StepResult<Object>> generate(
        @NotNull StepRequest<Object> input, @NotNull AgentContext context) {
      Instant start = Instant.now();
      return Promise.of(StepResult.success("done", Map.of(), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<Object> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("StubPhaseGenerator").type("rule-based")
          .description("Stub").version("1.0.0").build();
    }
  }
}
