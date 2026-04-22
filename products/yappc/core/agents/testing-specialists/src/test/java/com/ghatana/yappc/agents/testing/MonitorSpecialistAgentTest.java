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
@DisplayName("MonitorSpecialistAgent Tests [GH-90000]")
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
  @DisplayName("Input Validation [GH-90000]")
  class InputValidation {

    @Test
    @DisplayName("Should accept valid monitor input [GH-90000]")
    void shouldAcceptValidInput() { // GH-90000
      MonitorInput input = new MonitorInput("deploy-123", 30); // GH-90000
      ValidationResult result = agent.validateInput(input); // GH-90000
      assertThat(result.ok()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should reject empty deployment ID [GH-90000]")
    void shouldRejectEmptyDeploymentId() { // GH-90000
      assertThatThrownBy(() -> new MonitorInput("", 30)) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }

    @Test
    @DisplayName("Should reject zero duration [GH-90000]")
    void shouldRejectZeroDuration() { // GH-90000
      assertThatThrownBy(() -> new MonitorInput("deploy-123", 0)) // GH-90000
          .isInstanceOf(IllegalArgumentException.class); // GH-90000
    }
  }

  @Nested
  @DisplayName("Generator [GH-90000]")
  class GeneratorTests {

    @Test
    @DisplayName("Should generate healthy monitoring output [GH-90000]")
    void shouldGenerateHealthyOutput() { // GH-90000
      MonitorInput monInput = new MonitorInput("deploy-456", 15); // GH-90000
      StepContext ctx = createStepContext(); // GH-90000
      StepRequest<MonitorInput> request = new StepRequest<>(monInput, ctx); // GH-90000
      AgentContext agentCtx = AgentContext.builder() // GH-90000
          .agentId("MonitorSpecialistAgent [GH-90000]")
          .turnId("turn-1 [GH-90000]")
          .tenantId("tenant-1 [GH-90000]")
          .userId("system [GH-90000]")
          .sessionId("ops [GH-90000]")
          .memoryStore(memoryStore) // GH-90000
          .config(Map.of()) // GH-90000
          .remainingBudget(10.0) // GH-90000
          .build(); // GH-90000

      MonitorSpecialistAgent.MonitorGenerator gen =
          new MonitorSpecialistAgent.MonitorGenerator(); // GH-90000
      StepResult<MonitorOutput> result =
          runPromise(() -> gen.generate(request, agentCtx)); // GH-90000

      assertThat(result.success()).isTrue(); // GH-90000
      assertThat(result.output().health()).isEqualTo("healthy [GH-90000]");
      assertThat(result.output().alerts()).isEmpty(); // GH-90000
      assertThat(result.output().metrics()).containsKeys("uptime", "errorRate", "cpuUsage"); // GH-90000
      assertThat(result.output().monitoringId()).startsWith("monitoring- [GH-90000]");
    }

    @Test
    @DisplayName("Should estimate zero cost for rule-based generator [GH-90000]")
    void shouldEstimateZeroCost() { // GH-90000
      MonitorInput monInput = new MonitorInput("deploy-789", 10); // GH-90000
      StepContext ctx = createStepContext(); // GH-90000
      StepRequest<MonitorInput> request = new StepRequest<>(monInput, ctx); // GH-90000
      AgentContext agentCtx = AgentContext.builder() // GH-90000
          .agentId("MonitorSpecialistAgent [GH-90000]")
          .turnId("turn-2 [GH-90000]")
          .tenantId("tenant-1 [GH-90000]")
          .userId("system [GH-90000]")
          .sessionId("ops [GH-90000]")
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
    @DisplayName("Should return correct generator metadata [GH-90000]")
    void shouldReturnMetadata() { // GH-90000
      MonitorSpecialistAgent.MonitorGenerator gen =
          new MonitorSpecialistAgent.MonitorGenerator(); // GH-90000
      var metadata = gen.getMetadata(); // GH-90000

      assertThat(metadata.getName()).isEqualTo("MonitorGenerator [GH-90000]");
      assertThat(metadata.getType()).isEqualTo("rule-based [GH-90000]");
    }
  }

  @Nested
  @DisplayName("Full Execution [GH-90000]")
  class FullExecution {

    @Test
    @DisplayName("Should execute monitoring step end-to-end [GH-90000]")
    void shouldExecuteEndToEnd() { // GH-90000
      MonitorInput input = new MonitorInput("deploy-e2e", 5); // GH-90000
      StepContext ctx = createStepContext(); // GH-90000

      StepResult<MonitorOutput> result = runPromise(() -> agent.execute(input, ctx)); // GH-90000

      assertThat(result).isNotNull(); // GH-90000
      assertThat(result.success()).isTrue(); // GH-90000
      assertThat(result.output().health()).isEqualTo("healthy [GH-90000]");
      assertThat(result.durationMs()).isGreaterThanOrEqualTo(0); // GH-90000
    }
  }

  @Nested
  @DisplayName("Contract [GH-90000]")
  class ContractTests {

    @Test
    @DisplayName("Should expose correct step name [GH-90000]")
    void shouldExposeStepName() { // GH-90000
      assertThat(agent.stepName()).isEqualTo("ops.monitor [GH-90000]");
    }

    @Test
    @DisplayName("Should expose contract with monitoring capabilities [GH-90000]")
    void shouldExposeContract() { // GH-90000
      StepContract contract = agent.contract(); // GH-90000
      assertThat(contract.name()).isEqualTo("ops.monitor [GH-90000]");
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
