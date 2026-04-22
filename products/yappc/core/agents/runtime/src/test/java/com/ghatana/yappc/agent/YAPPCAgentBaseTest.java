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
@DisplayName("YAPPCAgentBase Tests [GH-90000]")
class YAPPCAgentBaseTest extends EventloopTestBase {

  private EventLogMemoryStore memoryStore;
  private TestAgent agent;

  @BeforeEach
  void setUp() { // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    agent = new TestAgent(memoryStore, new TestGenerator()); // GH-90000
    // Configure a no-op AEP publisher to avoid network calls
    YAPPCAgentBase.setGlobalAepEventPublisher( // GH-90000
        (eventType, tenantId, payload) -> Promise.complete()); // GH-90000
  }

  // ===== Lifecycle Tests =====

  @Nested
  @DisplayName("Execute Lifecycle [GH-90000]")
  class ExecuteLifecycle {

    @Test
    @DisplayName("Should execute full lifecycle and return result [GH-90000]")
    void shouldExecuteFullLifecycle() { // GH-90000
      TestInput input = new TestInput("test-123", "run something"); // GH-90000
      StepContext ctx = createStepContext(); // GH-90000

      StepResult<TestOutput> result = runPromise(() -> agent.execute(input, ctx)); // GH-90000

      assertThat(result).isNotNull(); // GH-90000
      assertThat(result.success()).isTrue(); // GH-90000
      assertThat(result.output()).isNotNull(); // GH-90000
      assertThat(result.output().resultId()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should reject invalid input during perceive [GH-90000]")
    void shouldRejectInvalidInput() { // GH-90000
      TestInput input = new TestInput("", "invalid"); // GH-90000
      StepContext ctx = createStepContext(); // GH-90000

      assertThatThrownBy(() -> runPromise(() -> agent.execute(input, ctx))) // GH-90000
          .isInstanceOf(IllegalArgumentException.class) // GH-90000
          .hasMessageContaining("Input validation failed [GH-90000]");
      // Clear the fatal error recorded by the eventloop so @AfterEach teardown
      // does not re-throw the same exception and mark this test as failed.
      clearFatalError(); // GH-90000
    }
  }

  // ===== Memory Integration Tests =====

  @Nested
  @DisplayName("Memory Integration [GH-90000]")
  class MemoryIntegration {

    @Test
    @DisplayName("Should capture episode after execution [GH-90000]")
    void shouldCaptureEpisode() { // GH-90000
      TestInput input = new TestInput("capture-test", "run something"); // GH-90000
      StepContext ctx = createStepContext(); // GH-90000

      StepResult<TestOutput> result = runPromise(() -> agent.execute(input, ctx)); // GH-90000

      // Query for captured episodes
      MemoryFilter filter = MemoryFilter.builder() // GH-90000
          .agentId("TestAgent [GH-90000]")
          .build(); // GH-90000
      List<Episode> episodes = runPromise(() -> memoryStore.queryEpisodes(filter, 10)); // GH-90000

      assertThat(episodes).isNotEmpty(); // GH-90000
      Episode captured = episodes.get(0); // GH-90000
      assertThat(captured.getAgentId()).isEqualTo("TestAgent [GH-90000]");
      assertThat(captured.getAction()).isEqualTo("test.step [GH-90000]");
      assertThat(captured.getTags()).contains("test.step", "success"); // GH-90000
      assertThat(captured.getReward()).isEqualTo(1.0); // GH-90000
    }

    @Test
    @DisplayName("Should capture failure episode with negative reward [GH-90000]")
    void shouldCaptureFailureEpisode() { // GH-90000
      TestAgent failAgent = new TestAgent(memoryStore, new FailingGenerator()); // GH-90000
      StepContext ctx = createStepContext(); // GH-90000
      TestInput input = new TestInput("fail-test", "this will fail"); // GH-90000

      StepResult<TestOutput> result = runPromise(() -> failAgent.execute(input, ctx)); // GH-90000

      MemoryFilter filter = MemoryFilter.builder() // GH-90000
          .agentId("TestAgent [GH-90000]")
          .build(); // GH-90000
      List<Episode> episodes = runPromise(() -> memoryStore.queryEpisodes(filter, 10)); // GH-90000

      assertThat(episodes).isNotEmpty(); // GH-90000
      Episode captured = episodes.get(0); // GH-90000
      assertThat(captured.getTags()).contains("failure [GH-90000]");
      assertThat(captured.getReward()).isEqualTo(-1.0); // GH-90000
    }

    @Test
    @DisplayName("Should store facts during reflection when pattern learning enabled [GH-90000]")
    void shouldStoreFactsDuringReflection() { // GH-90000
      FeatureFlags.override(FeatureFlag.PATTERN_LEARNING, true); // GH-90000
      try {
        TestInput input = new TestInput("reflect-test", "learn something"); // GH-90000
        StepContext ctx = createStepContext(); // GH-90000

        runPromise(() -> agent.execute(input, ctx)); // GH-90000

        // Query for learned facts
        List<Fact> facts = runPromise(() -> // GH-90000
            memoryStore.queryFacts("test.step", "succeeded_with", null)); // GH-90000

        assertThat(facts).isNotEmpty(); // GH-90000
        Fact fact = facts.get(0); // GH-90000
        assertThat(fact.getAgentId()).isEqualTo("TestAgent [GH-90000]");
        assertThat(fact.getSubject()).isEqualTo("test.step [GH-90000]");
        assertThat(fact.getPredicate()).isEqualTo("succeeded_with [GH-90000]");
      } finally {
        FeatureFlags.clearOverrides(); // GH-90000
      }
    }

    @Test
    @DisplayName("Should store policies during reflection [GH-90000]")
    void shouldStorePoliciesDuringReflection() { // GH-90000
      FeatureFlags.override(FeatureFlag.PATTERN_LEARNING, true); // GH-90000
      try {
        TestInput input = new TestInput("policy-test", "create policy"); // GH-90000
        StepContext ctx = createStepContext(); // GH-90000

        runPromise(() -> agent.execute(input, ctx)); // GH-90000

        List<Policy> policies = runPromise(() -> // GH-90000
            memoryStore.queryPolicies("test.step", 0.5)); // GH-90000

        assertThat(policies).isNotEmpty(); // GH-90000
        Policy policy = policies.get(0); // GH-90000
        assertThat(policy.getAgentId()).isEqualTo("TestAgent [GH-90000]");
        assertThat(policy.getSituation()).contains("test.step [GH-90000]");
        assertThat(policy.getConfidence()).isGreaterThanOrEqualTo(0.5); // GH-90000
      } finally {
        FeatureFlags.clearOverrides(); // GH-90000
      }
    }

    @Test
    @DisplayName("Should gracefully handle memory failures [GH-90000]")
    void shouldHandleMemoryFailures() { // GH-90000
      // Use NoOp memory store — operations succeed but store nothing
      TestAgent noMemAgent = new TestAgent(MemoryStore.noOp(), new TestGenerator()); // GH-90000
      TestInput input = new TestInput("nomem-test", "no memory available"); // GH-90000
      StepContext ctx = createStepContext(); // GH-90000

      // Should not throw even with no-op memory
      StepResult<TestOutput> result = runPromise(() -> noMemAgent.execute(input, ctx)); // GH-90000
      assertThat(result.success()).isTrue(); // GH-90000
    }
  }

  // ===== Contract Tests =====

  @Nested
  @DisplayName("WorkflowStep Contract [GH-90000]")
  class WorkflowStepContract {

    @Test
    @DisplayName("Should expose step name [GH-90000]")
    void shouldExposeStepName() { // GH-90000
      assertThat(agent.stepName()).isEqualTo("test.step [GH-90000]");
    }

    @Test
    @DisplayName("Should expose step contract [GH-90000]")
    void shouldExposeContract() { // GH-90000
      StepContract contract = agent.contract(); // GH-90000
      assertThat(contract.name()).isEqualTo("test.step [GH-90000]");
      assertThat(contract.requiredCapabilities()).containsExactly("testing [GH-90000]");
    }
  }

  // ===== Context Conversion Tests =====

  @Nested
  @DisplayName("Context Conversion [GH-90000]")
  class ContextConversion {

    @Test
    @DisplayName("Should convert StepContext to AgentContext [GH-90000]")
    void shouldConvertContext() { // GH-90000
      StepContext ctx = createStepContext(); // GH-90000
      AgentContext agentCtx = agent.convertToAgentContext(ctx); // GH-90000

      assertThat(agentCtx.getAgentId()).isEqualTo("TestAgent [GH-90000]");
      assertThat(agentCtx.getTurnId()).isEqualTo("run-123 [GH-90000]");
      assertThat(agentCtx.getTenantId()).isEqualTo("tenant-1 [GH-90000]");
      assertThat(agentCtx.getSessionId()).isEqualTo("testing-phase [GH-90000]");
    }
  }

  // ===== Test Helpers =====

  private StepContext createStepContext() { // GH-90000
    return new StepContext( // GH-90000
        "run-123",
        "tenant-1",
        "testing-phase",
        "config-snap-1",
        new StepBudget(10.0, 60_000)); // GH-90000
  }

  // ===== Test Agent Implementation =====

  static class TestAgent extends YAPPCAgentBase<TestInput, TestOutput> {
    private final MemoryStore memoryStore;

    TestAgent(MemoryStore memoryStore, // GH-90000
        OutputGenerator<StepRequest<TestInput>, StepResult<TestOutput>> generator) {
      super("TestAgent", "test.step", // GH-90000
          new StepContract("test.step", "#/definitions/TestInput", // GH-90000
              "#/definitions/TestOutput", List.of("testing [GH-90000]"),
              Map.of("description", "Test agent", "version", "1.0.0")), // GH-90000
          generator,
        defaultEventPublisher()); // GH-90000
      this.memoryStore = memoryStore;
    }

    @Override
    protected MemoryStore getMemoryStore() { // GH-90000
      return memoryStore;
    }

    @Override
    public ValidationResult validateInput(@NotNull TestInput input) { // GH-90000
      if (input.id() == null || input.id().isEmpty()) { // GH-90000
        return ValidationResult.fail("id cannot be empty [GH-90000]");
      }
      return ValidationResult.success(); // GH-90000
    }
  }

  record TestInput(String id, String description) {} // GH-90000

  record TestOutput(String resultId, String summary, Map<String, Object> metadata) {} // GH-90000

  static class TestGenerator
      implements OutputGenerator<StepRequest<TestInput>, StepResult<TestOutput>> {

    @Override
    public @NotNull Promise<StepResult<TestOutput>> generate( // GH-90000
        @NotNull StepRequest<TestInput> input, @NotNull AgentContext context) {
      Instant start = Instant.now(); // GH-90000
      TestOutput output = new TestOutput( // GH-90000
          "result-" + input.input().id(), // GH-90000
          "Processed: " + input.input().description(), // GH-90000
          Map.of("processedAt", start.toString())); // GH-90000
      return Promise.of(StepResult.success(output, Map.of(), start, Instant.now())); // GH-90000
    }

    @Override
    public @NotNull Promise<Double> estimateCost( // GH-90000
        @NotNull StepRequest<TestInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // GH-90000
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() { // GH-90000
      return GeneratorMetadata.builder() // GH-90000
          .name("TestGenerator [GH-90000]").type("rule-based [GH-90000]")
          .description("Test generator [GH-90000]").version("1.0.0 [GH-90000]").build();
    }
  }

  static class FailingGenerator
      implements OutputGenerator<StepRequest<TestInput>, StepResult<TestOutput>> {

    @Override
    public @NotNull Promise<StepResult<TestOutput>> generate( // GH-90000
        @NotNull StepRequest<TestInput> input, @NotNull AgentContext context) {
      Instant start = Instant.now(); // GH-90000
      return Promise.of(StepResult.failed( // GH-90000
          List.of("Simulated failure [GH-90000]"), Map.of(), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost( // GH-90000
        @NotNull StepRequest<TestInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // GH-90000
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() { // GH-90000
      return GeneratorMetadata.builder() // GH-90000
          .name("FailingGenerator [GH-90000]").type("rule-based [GH-90000]")
          .description("Always fails [GH-90000]").version("1.0.0 [GH-90000]").build();
    }
  }
}
