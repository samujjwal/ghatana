package com.ghatana.yappc.agents.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import com.ghatana.yappc.agents.architecture.MonitorSpecialistAgent;
import io.activej.promise.Promise;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.ghatana.yappc.agents.code.MonitorInput;
import com.ghatana.yappc.agents.code.MonitorOutput;

/**
 * Tests for MonitorSpecialistAgent — production monitoring specialist.
 *
 * @doc.type class
 * @doc.purpose Unit tests for MonitorSpecialistAgent
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("MonitorSpecialistAgent Tests")
class MonitorSpecialistAgentTest extends EventloopTestBase {

  private MemoryStore memoryStore;
  private MonitorSpecialistAgent agent;

  @BeforeEach
  void setUp() { // GH-90000
    memoryStore = new EventLogMemoryStore(); // GH-90000
    agent = new MonitorSpecialistAgent( // GH-90000
        memoryStore, new MonitorSpecialistAgent.MonitorGenerator()); // GH-90000
    YAPPCAgentBase.setGlobalAepEventPublisher( // GH-90000
        (eventType, tenantId, payload) -> Promise.complete()); // GH-90000
  }

  @Nested
  @DisplayName("Input Validation")
  class InputValidation {

    @Test
    @DisplayName("Should accept valid monitor input")
    void shouldAcceptValidInput() { // GH-90000
      MonitorInput input = new MonitorInput("deploy-123", 30); // GH-90000
      ValidationResult result = agent.validateInput(input); // GH-90000
      assertThat(result.ok()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should reject empty deployment ID")
    void shouldRejectEmptyDeploymentId() { // GH-90000
      assertThatThrownBy(() -> new MonitorInput("", 30)) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Should reject zero duration")
    void shouldRejectZeroDuration() { // GH-90000
      assertThatThrownBy(() -> new MonitorInput("deploy-123", 0)) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
  }

  @Nested
  @DisplayName("Generator")
  class GeneratorTests {

    @Test
    @DisplayName("Should generate healthy monitoring output")
    void shouldGenerateHealthyOutput() { // GH-90000
      MonitorInput monInput = new MonitorInput("deploy-456", 15); // GH-90000
      StepContext ctx = createStepContext(); // GH-90000
      StepRequest<MonitorInput> request = new StepRequest<>(monInput, ctx); // GH-90000
      AgentContext agentCtx = AgentContext.builder() // GH-90000
          .agentId("MonitorSpecialistAgent")
          .turnId("turn-1")
          .tenantId("tenant-1")
          .userId("system")
          .sessionId("ops")
          .memoryStore(memoryStore) // GH-90000
          .config(Map.of()) // GH-90000
          .remainingBudget(10.0) // GH-90000
          .build(); // GH-90000

      MonitorSpecialistAgent.MonitorGenerator gen =
          new MonitorSpecialistAgent.MonitorGenerator(); // GH-90000
      StepResult<MonitorOutput> result =
          runPromise(() -> gen.generate(request, agentCtx)); // GH-90000

      assertThat(result.success()).isTrue(); // GH-90000
      assertThat(result.output().health()).isEqualTo("healthy");
      assertThat(result.output().alerts()).isEmpty(); // GH-90000
      assertThat(result.output().metrics()).containsKeys("uptime", "errorRate", "cpuUsage"); // GH-90000
      assertThat(result.output().monitoringId()).startsWith("monitoring-");
    }

    @Test
    @DisplayName("Should estimate zero cost for rule-based generator")
    void shouldEstimateZeroCost() { // GH-90000
      MonitorInput monInput = new MonitorInput("deploy-789", 10); // GH-90000
      StepContext ctx = createStepContext(); // GH-90000
      StepRequest<MonitorInput> request = new StepRequest<>(monInput, ctx); // GH-90000
      AgentContext agentCtx = AgentContext.builder() // GH-90000
          .agentId("MonitorSpecialistAgent")
          .turnId("turn-2")
          .tenantId("tenant-1")
          .userId("system")
          .sessionId("ops")
          .memoryStore(memoryStore) // GH-90000
          .config(Map.of()) // GH-90000
          .remainingBudget(10.0) // GH-90000
          .build(); // GH-90000

      MonitorSpecialistAgent.MonitorGenerator gen =
          new MonitorSpecialistAgent.MonitorGenerator(); // GH-90000
      double cost = runPromise(() -> gen.estimateCost(request, agentCtx)); // GH-90000

      assertThat(cost).isZero(); // GH-90000
    }

    @Test
    @DisplayName("Should return correct generator metadata")
    void shouldReturnMetadata() { // GH-90000
      MonitorSpecialistAgent.MonitorGenerator gen =
          new MonitorSpecialistAgent.MonitorGenerator(); // GH-90000
      var metadata = gen.getMetadata(); // GH-90000

      assertThat(metadata.getName()).isEqualTo("MonitorGenerator");
      assertThat(metadata.getType()).isEqualTo("rule-based");
    }
  }

  @Nested
  @DisplayName("Full Execution")
  class FullExecution {

    @Test
    @DisplayName("Should execute monitoring step end-to-end")
    void shouldExecuteEndToEnd() { // GH-90000
      MonitorInput input = new MonitorInput("deploy-e2e", 5); // GH-90000
      StepContext ctx = createStepContext(); // GH-90000

      StepResult<MonitorOutput> result = runPromise(() -> agent.execute(input, ctx)); // GH-90000

      assertThat(result).isNotNull(); // GH-90000
      assertThat(result.success()).isTrue(); // GH-90000
      assertThat(result.output().health()).isEqualTo("healthy");
      assertThat(result.durationMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }
  }

  @Nested
  @DisplayName("Contract")
  class ContractTests {

    @Test
    @DisplayName("Should expose correct step name")
    void shouldExposeStepName() { // GH-90000
      assertThat(agent.stepName()).isEqualTo("ops.monitor");
    }

    @Test
    @DisplayName("Should expose contract with monitoring capabilities")
    void shouldExposeContract() { // GH-90000
      StepContract contract = agent.contract(); // GH-90000
      assertThat(contract.name()).isEqualTo("ops.monitor");
      assertThat(contract.requiredCapabilities()) // GH-90000
          .containsExactlyInAnyOrder("ops", "monitoring", "observability"); // GH-90000
    }
  }

  private StepContext createStepContext() { // GH-90000
    return new StepContext( // GH-90000
        "run-monitor-1", "tenant-1", "ops-phase", "config-1",
        new StepBudget(10.0, 60_000)); // GH-90000
  }
}
