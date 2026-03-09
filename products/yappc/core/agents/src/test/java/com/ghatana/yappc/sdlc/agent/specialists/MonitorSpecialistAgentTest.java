package com.ghatana.yappc.sdlc.agent.specialists;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.EventLogMemoryStore;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.sdlc.*;
import com.ghatana.yappc.sdlc.agent.StepRequest;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
  void setUp() {
    memoryStore = new EventLogMemoryStore();
    agent = new MonitorSpecialistAgent(
        memoryStore, new MonitorSpecialistAgent.MonitorGenerator());
    YAPPCAgentBase.configureAepEventPublisher(
        (eventType, tenantId, payload) -> Promise.complete());
  }

  @Nested
  @DisplayName("Input Validation")
  class InputValidation {

    @Test
    @DisplayName("Should accept valid monitor input")
    void shouldAcceptValidInput() {
      MonitorInput input = new MonitorInput("deploy-123", 30);
      ValidationResult result = agent.validateInput(input);
      assertThat(result.ok()).isTrue();
    }

    @Test
    @DisplayName("Should reject empty deployment ID")
    void shouldRejectEmptyDeploymentId() {
      assertThatThrownBy(() -> new MonitorInput("", 30))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject zero duration")
    void shouldRejectZeroDuration() {
      assertThatThrownBy(() -> new MonitorInput("deploy-123", 0))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("Generator")
  class GeneratorTests {

    @Test
    @DisplayName("Should generate healthy monitoring output")
    void shouldGenerateHealthyOutput() {
      MonitorInput monInput = new MonitorInput("deploy-456", 15);
      StepContext ctx = createStepContext();
      StepRequest<MonitorInput> request = new StepRequest<>(monInput, ctx);
      AgentContext agentCtx = AgentContext.builder()
          .agentId("MonitorSpecialistAgent")
          .turnId("turn-1")
          .tenantId("tenant-1")
          .userId("system")
          .sessionId("ops")
          .memoryStore(memoryStore)
          .config(Map.of())
          .remainingBudget(10.0)
          .build();

      MonitorSpecialistAgent.MonitorGenerator gen =
          new MonitorSpecialistAgent.MonitorGenerator();
      StepResult<MonitorOutput> result =
          runPromise(() -> gen.generate(request, agentCtx));

      assertThat(result.success()).isTrue();
      assertThat(result.output().health()).isEqualTo("healthy");
      assertThat(result.output().alerts()).isEmpty();
      assertThat(result.output().metrics()).containsKeys("uptime", "errorRate", "cpuUsage");
      assertThat(result.output().monitoringId()).startsWith("monitoring-");
    }

    @Test
    @DisplayName("Should estimate zero cost for rule-based generator")
    void shouldEstimateZeroCost() {
      MonitorInput monInput = new MonitorInput("deploy-789", 10);
      StepContext ctx = createStepContext();
      StepRequest<MonitorInput> request = new StepRequest<>(monInput, ctx);
      AgentContext agentCtx = AgentContext.builder()
          .agentId("MonitorSpecialistAgent")
          .turnId("turn-2")
          .tenantId("tenant-1")
          .userId("system")
          .sessionId("ops")
          .memoryStore(memoryStore)
          .config(Map.of())
          .remainingBudget(10.0)
          .build();

      MonitorSpecialistAgent.MonitorGenerator gen =
          new MonitorSpecialistAgent.MonitorGenerator();
      double cost = runPromise(() -> gen.estimateCost(request, agentCtx));

      assertThat(cost).isZero();
    }

    @Test
    @DisplayName("Should return correct generator metadata")
    void shouldReturnMetadata() {
      MonitorSpecialistAgent.MonitorGenerator gen =
          new MonitorSpecialistAgent.MonitorGenerator();
      var metadata = gen.getMetadata();

      assertThat(metadata.getName()).isEqualTo("MonitorGenerator");
      assertThat(metadata.getType()).isEqualTo("rule-based");
    }
  }

  @Nested
  @DisplayName("Full Execution")
  class FullExecution {

    @Test
    @DisplayName("Should execute monitoring step end-to-end")
    void shouldExecuteEndToEnd() {
      MonitorInput input = new MonitorInput("deploy-e2e", 5);
      StepContext ctx = createStepContext();

      StepResult<MonitorOutput> result = runPromise(() -> agent.execute(input, ctx));

      assertThat(result).isNotNull();
      assertThat(result.success()).isTrue();
      assertThat(result.output().health()).isEqualTo("healthy");
      assertThat(result.durationMs()).isGreaterThanOrEqualTo(0);
    }
  }

  @Nested
  @DisplayName("Contract")
  class ContractTests {

    @Test
    @DisplayName("Should expose correct step name")
    void shouldExposeStepName() {
      assertThat(agent.stepName()).isEqualTo("ops.monitor");
    }

    @Test
    @DisplayName("Should expose contract with monitoring capabilities")
    void shouldExposeContract() {
      StepContract contract = agent.contract();
      assertThat(contract.name()).isEqualTo("ops.monitor");
      assertThat(contract.requiredCapabilities())
          .containsExactlyInAnyOrder("ops", "monitoring", "observability");
    }
  }

  private StepContext createStepContext() {
    return new StepContext(
        "run-monitor-1", "tenant-1", "ops-phase", "config-1",
        new StepBudget(10.0, 60_000));
  }
}
