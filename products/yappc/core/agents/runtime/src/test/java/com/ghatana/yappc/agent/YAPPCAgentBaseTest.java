package com.ghatana.yappc.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.Episode;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.Fact;
import com.ghatana.agent.framework.memory.MemoryFilter;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.agent.framework.memory.Policy;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.framework.core.config.FeatureFlag;
import com.ghatana.yappc.framework.core.config.FeatureFlags;
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
 * Tests for YAPPCAgentBase — the YAPPC workflow agent base class.
 *
 * <p>Covers the full GAA lifecycle: PERCEIVE → REASON → ACT → CAPTURE → REFLECT,
 * including memory integration, episode capture, and policy learning.
 *
 * @doc.type class
 * @doc.purpose Unit tests for YAPPCAgentBase lifecycle and memory integration
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YAPPCAgentBase Tests")
class YAPPCAgentBaseTest extends EventloopTestBase {

  private EventLogMemoryStore memoryStore;
  private TestAgent agent;

  @BeforeEach
  void setUp() {
    memoryStore = new EventLogMemoryStore();
    agent = new TestAgent(memoryStore, new TestGenerator());
    // Configure a no-op AEP publisher to avoid network calls
    YAPPCAgentBase.configureAepEventPublisher(
        (eventType, tenantId, payload) -> Promise.complete());
  }

  // ===== Lifecycle Tests =====

  @Nested
  @DisplayName("Execute Lifecycle")
  class ExecuteLifecycle {

    @Test
    @DisplayName("Should execute full lifecycle and return result")
    void shouldExecuteFullLifecycle() {
      TestInput input = new TestInput("test-123", "run something");
      StepContext ctx = createStepContext();

      StepResult<TestOutput> result = runPromise(() -> agent.execute(input, ctx));

      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();
      assertThat(result.output()).isNotNull();
      assertThat(result.output().resultId()).isNotEmpty();
    }

    @Test
    @DisplayName("Should reject invalid input during perceive")
    void shouldRejectInvalidInput() {
      TestInput input = new TestInput("", "invalid");
      StepContext ctx = createStepContext();

      assertThatThrownBy(() -> runPromise(() -> agent.execute(input, ctx)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Input validation failed");
      // Clear the fatal error recorded by the eventloop so @AfterEach teardown
      // does not re-throw the same exception and mark this test as failed.
      clearFatalError();
    }
  }

  // ===== Memory Integration Tests =====

  @Nested
  @DisplayName("Memory Integration")
  class MemoryIntegration {

    @Test
    @DisplayName("Should capture episode after execution")
    void shouldCaptureEpisode() {
      TestInput input = new TestInput("capture-test", "run something");
      StepContext ctx = createStepContext();

      StepResult<TestOutput> result = runPromise(() -> agent.execute(input, ctx));

      // Query for captured episodes
      MemoryFilter filter = MemoryFilter.builder()
          .agentId("TestAgent")
          .build();
      List<Episode> episodes = runPromise(() -> memoryStore.queryEpisodes(filter, 10));

      assertThat(episodes).isNotEmpty();
      Episode captured = episodes.get(0);
      assertThat(captured.getAgentId()).isEqualTo("TestAgent");
      assertThat(captured.getAction()).isEqualTo("test.step");
      assertThat(captured.getTags()).contains("test.step", "success");
      assertThat(captured.getReward()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should capture failure episode with negative reward")
    void shouldCaptureFailureEpisode() {
      TestAgent failAgent = new TestAgent(memoryStore, new FailingGenerator());
      StepContext ctx = createStepContext();
      TestInput input = new TestInput("fail-test", "this will fail");

      StepResult<TestOutput> result = runPromise(() -> failAgent.execute(input, ctx));

      MemoryFilter filter = MemoryFilter.builder()
          .agentId("TestAgent")
          .build();
      List<Episode> episodes = runPromise(() -> memoryStore.queryEpisodes(filter, 10));

      assertThat(episodes).isNotEmpty();
      Episode captured = episodes.get(0);
      assertThat(captured.getTags()).contains("failure");
      assertThat(captured.getReward()).isEqualTo(-1.0);
    }

    @Test
    @DisplayName("Should store facts during reflection when pattern learning enabled")
    void shouldStoreFactsDuringReflection() {
      FeatureFlags.override(FeatureFlag.PATTERN_LEARNING, true);
      try {
        TestInput input = new TestInput("reflect-test", "learn something");
        StepContext ctx = createStepContext();

        runPromise(() -> agent.execute(input, ctx));

        // Query for learned facts
        List<Fact> facts = runPromise(() ->
            memoryStore.queryFacts("test.step", "succeeded_with", null));

        assertThat(facts).isNotEmpty();
        Fact fact = facts.get(0);
        assertThat(fact.getAgentId()).isEqualTo("TestAgent");
        assertThat(fact.getSubject()).isEqualTo("test.step");
        assertThat(fact.getPredicate()).isEqualTo("succeeded_with");
      } finally {
        FeatureFlags.clearOverrides();
      }
    }

    @Test
    @DisplayName("Should store policies during reflection")
    void shouldStorePoliciesDuringReflection() {
      FeatureFlags.override(FeatureFlag.PATTERN_LEARNING, true);
      try {
        TestInput input = new TestInput("policy-test", "create policy");
        StepContext ctx = createStepContext();

        runPromise(() -> agent.execute(input, ctx));

        List<Policy> policies = runPromise(() ->
            memoryStore.queryPolicies("test.step", 0.5));

        assertThat(policies).isNotEmpty();
        Policy policy = policies.get(0);
        assertThat(policy.getAgentId()).isEqualTo("TestAgent");
        assertThat(policy.getSituation()).contains("test.step");
        assertThat(policy.getConfidence()).isGreaterThanOrEqualTo(0.5);
      } finally {
        FeatureFlags.clearOverrides();
      }
    }

    @Test
    @DisplayName("Should gracefully handle memory failures")
    void shouldHandleMemoryFailures() {
      // Use NoOp memory store — operations succeed but store nothing
      TestAgent noMemAgent = new TestAgent(MemoryStore.noOp(), new TestGenerator());
      TestInput input = new TestInput("nomem-test", "no memory available");
      StepContext ctx = createStepContext();

      // Should not throw even with no-op memory
      StepResult<TestOutput> result = runPromise(() -> noMemAgent.execute(input, ctx));
      assertThat(result.success()).isTrue();
    }
  }

  // ===== Contract Tests =====

  @Nested
  @DisplayName("WorkflowStep Contract")
  class WorkflowStepContract {

    @Test
    @DisplayName("Should expose step name")
    void shouldExposeStepName() {
      assertThat(agent.stepName()).isEqualTo("test.step");
    }

    @Test
    @DisplayName("Should expose step contract")
    void shouldExposeContract() {
      StepContract contract = agent.contract();
      assertThat(contract.name()).isEqualTo("test.step");
      assertThat(contract.requiredCapabilities()).containsExactly("testing");
    }
  }

  // ===== Context Conversion Tests =====

  @Nested
  @DisplayName("Context Conversion")
  class ContextConversion {

    @Test
    @DisplayName("Should convert StepContext to AgentContext")
    void shouldConvertContext() {
      StepContext ctx = createStepContext();
      AgentContext agentCtx = agent.convertToAgentContext(ctx);

      assertThat(agentCtx.getAgentId()).isEqualTo("TestAgent");
      assertThat(agentCtx.getTurnId()).isEqualTo("run-123");
      assertThat(agentCtx.getTenantId()).isEqualTo("tenant-1");
      assertThat(agentCtx.getSessionId()).isEqualTo("testing-phase");
    }
  }

  // ===== Test Helpers =====

  private StepContext createStepContext() {
    return new StepContext(
        "run-123",
        "tenant-1",
        "testing-phase",
        "config-snap-1",
        new StepBudget(10.0, 60_000));
  }

  // ===== Test Agent Implementation =====

  static class TestAgent extends YAPPCAgentBase<TestInput, TestOutput> {
    private final MemoryStore memoryStore;

    TestAgent(MemoryStore memoryStore,
        OutputGenerator<StepRequest<TestInput>, StepResult<TestOutput>> generator) {
      super("TestAgent", "test.step",
          new StepContract("test.step", "#/definitions/TestInput",
              "#/definitions/TestOutput", List.of("testing"),
              Map.of("description", "Test agent", "version", "1.0.0")),
          generator);
      this.memoryStore = memoryStore;
    }

    @Override
    protected MemoryStore getMemoryStore() {
      return memoryStore;
    }

    @Override
    public ValidationResult validateInput(@NotNull TestInput input) {
      if (input.id() == null || input.id().isEmpty()) {
        return ValidationResult.fail("id cannot be empty");
      }
      return ValidationResult.success();
    }
  }

  record TestInput(String id, String description) {}

  record TestOutput(String resultId, String summary, Map<String, Object> metadata) {}

  static class TestGenerator
      implements OutputGenerator<StepRequest<TestInput>, StepResult<TestOutput>> {

    @Override
    public @NotNull Promise<StepResult<TestOutput>> generate(
        @NotNull StepRequest<TestInput> input, @NotNull AgentContext context) {
      Instant start = Instant.now();
      TestOutput output = new TestOutput(
          "result-" + input.input().id(),
          "Processed: " + input.input().description(),
          Map.of("processedAt", start.toString()));
      return Promise.of(StepResult.success(output, Map.of(), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<TestInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("TestGenerator").type("rule-based")
          .description("Test generator").version("1.0.0").build();
    }
  }

  static class FailingGenerator
      implements OutputGenerator<StepRequest<TestInput>, StepResult<TestOutput>> {

    @Override
    public @NotNull Promise<StepResult<TestOutput>> generate(
        @NotNull StepRequest<TestInput> input, @NotNull AgentContext context) {
      Instant start = Instant.now();
      return Promise.of(StepResult.failed(
          List.of("Simulated failure"), Map.of(), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<TestInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("FailingGenerator").type("rule-based")
          .description("Always fails").version("1.0.0").build();
    }
  }
}
