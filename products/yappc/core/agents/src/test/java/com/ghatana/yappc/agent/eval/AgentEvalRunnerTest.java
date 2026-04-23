package com.ghatana.yappc.agent.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.yappc.agent.spi.AgentRuntimePort;
import io.activej.promise.Promise;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AgentEvalRunner} — the evaluation flywheel runner that
 * executes golden test sets against agents and produces reports.
 *
 * @doc.type class
 * @doc.purpose Unit tests for agent evaluation runner and assertion checking
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AgentEvalRunner Tests")
class AgentEvalRunnerTest extends EventloopTestBase {

  private AgentRuntimePort dispatcher;
  private AgentEvalRunner runner;
  private AgentContext ctx;

  @BeforeEach
  void setUp() { // GH-90000
    dispatcher = mock(AgentRuntimePort.class); // GH-90000
    runner = new AgentEvalRunner(dispatcher); // GH-90000
    ctx = AgentContext.builder() // GH-90000
        .agentId("eval-runner")
        .turnId("eval-turn-001")
        .tenantId("tenant-1")
        .sessionId("eval-session")
        .memoryStore(MemoryStore.noOp()) // GH-90000
        .build(); // GH-90000
  }

  // ===== Constructor Tests =====

  @Nested
  @DisplayName("Constructor")
  class Constructor {

    @Test
    @DisplayName("Should reject null dispatcher")
    void shouldRejectNullDispatcher() { // GH-90000
      assertThatThrownBy(() -> new AgentEvalRunner(null)) // GH-90000
          .isInstanceOf(NullPointerException.class) // GH-90000
          .hasMessageContaining("dispatcher");
    }
  }

  // ===== RunAll Tests =====

  @Nested
  @DisplayName("Run All Tasks")
  class RunAll {

    @Test
    @DisplayName("Should produce report with all tasks passing")
    void shouldProduceAllPassingReport() { // GH-90000
      AgentResult<Object> successResult = AgentResult.<Object>builder() // GH-90000
          .output("expected-output")
          .status(AgentResultStatus.SUCCESS) // GH-90000
          .confidence(0.95) // GH-90000
          .explanation("All good")
          .build(); // GH-90000

      when(dispatcher.<Object, Object>dispatch(eq("test-agent"), any(), any()))
          .thenReturn(Promise.of(successResult)); // GH-90000

      AgentEvalTask task = AgentEvalTask.builder() // GH-90000
          .id("eval-001")
          .description("Test task")
          .agentId("test-agent")
          .category("unit")
          .input("test-input")
          .maxLatencyMs(30000) // GH-90000
          .assertions(List.of( // GH-90000
              EvalAssertion.builder() // GH-90000
                  .type("CONTAINS")
                  .expected("expected")
                  .description("Should contain expected")
                  .build())) // GH-90000
          .build(); // GH-90000

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx)); // GH-90000

      assertThat(report.getTotalTasks()).isEqualTo(1); // GH-90000
      assertThat(report.getPassed()).isEqualTo(1); // GH-90000
      assertThat(report.getFailed()).isEqualTo(0); // GH-90000
      assertThat(report.isAllPassed()).isTrue(); // GH-90000
      assertThat(report.getRunId()).isNotEmpty(); // GH-90000
    }

    @Test
    @DisplayName("Should produce report with failing assertion")
    void shouldProduceFailingReport() { // GH-90000
      AgentResult<Object> result = AgentResult.<Object>builder() // GH-90000
          .output("actual-output")
          .status(AgentResultStatus.SUCCESS) // GH-90000
          .confidence(0.8) // GH-90000
          .explanation("Done")
          .build(); // GH-90000

      when(dispatcher.<Object, Object>dispatch(eq("test-agent"), any(), any()))
          .thenReturn(Promise.of(result)); // GH-90000

      AgentEvalTask task = AgentEvalTask.builder() // GH-90000
          .id("eval-002")
          .description("Failing task")
          .agentId("test-agent")
          .category("unit")
          .input("test-input")
          .maxLatencyMs(30000) // GH-90000
          .assertions(List.of( // GH-90000
              EvalAssertion.builder() // GH-90000
                  .type("EXACT_MATCH")
                  .expected("something-else")
                  .description("Should match exactly")
                  .build())) // GH-90000
          .build(); // GH-90000

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx)); // GH-90000

      assertThat(report.getPassed()).isEqualTo(0); // GH-90000
      assertThat(report.getFailed()).isEqualTo(1); // GH-90000
      assertThat(report.isAllPassed()).isFalse(); // GH-90000
      assertThat(report.getResults().get(0).getFailures()) // GH-90000
          .anyMatch(f -> f.contains("EXACT_MATCH failed"));
    }

    @Test
    @DisplayName("Should handle empty task list")
    void shouldHandleEmptyTaskList() { // GH-90000
      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(), ctx)); // GH-90000

      assertThat(report.getTotalTasks()).isEqualTo(0); // GH-90000
      assertThat(report.getPassed()).isEqualTo(0); // GH-90000
      assertThat(report.getFailed()).isEqualTo(0); // GH-90000
      assertThat(report.isAllPassed()).isTrue(); // GH-90000
    }
  }

  // ===== Assertion Type Tests =====

  @Nested
  @DisplayName("Assertion Types")
  class AssertionTypes {

    @Test
    @DisplayName("Should pass CONTAINS assertion when output contains expected")
    void shouldPassContains() { // GH-90000
      AgentResult<Object> result = AgentResult.<Object>builder() // GH-90000
          .output("Hello World from agent")
          .status(AgentResultStatus.SUCCESS) // GH-90000
          .confidence(0.9) // GH-90000
          .build(); // GH-90000

      when(dispatcher.<Object, Object>dispatch(any(), any(), any())) // GH-90000
          .thenReturn(Promise.of(result)); // GH-90000

      AgentEvalTask task = createTask("CONTAINS", "World"); // GH-90000

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx)); // GH-90000

      assertThat(report.isAllPassed()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should pass REGEX assertion when output matches pattern")
    void shouldPassRegex() { // GH-90000
      AgentResult<Object> result = AgentResult.<Object>builder() // GH-90000
          .output("Error code: 42")
          .status(AgentResultStatus.SUCCESS) // GH-90000
          .confidence(0.9) // GH-90000
          .build(); // GH-90000

      when(dispatcher.<Object, Object>dispatch(any(), any(), any())) // GH-90000
          .thenReturn(Promise.of(result)); // GH-90000

      AgentEvalTask task = createTask("REGEX", ".*code: \\d+.*"); // GH-90000

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx)); // GH-90000

      assertThat(report.isAllPassed()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should pass CONFIDENCE_MIN assertion when confidence is sufficient")
    void shouldPassConfidenceMin() { // GH-90000
      AgentResult<Object> result = AgentResult.<Object>builder() // GH-90000
          .output("output")
          .status(AgentResultStatus.SUCCESS) // GH-90000
          .confidence(0.85) // GH-90000
          .build(); // GH-90000

      when(dispatcher.<Object, Object>dispatch(any(), any(), any())) // GH-90000
          .thenReturn(Promise.of(result)); // GH-90000

      AgentEvalTask task = createTask("CONFIDENCE_MIN", "0.8"); // GH-90000

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx)); // GH-90000

      assertThat(report.isAllPassed()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should fail CONFIDENCE_MIN when confidence is below threshold")
    void shouldFailConfidenceMin() { // GH-90000
      AgentResult<Object> result = AgentResult.<Object>builder() // GH-90000
          .output("output")
          .status(AgentResultStatus.SUCCESS) // GH-90000
          .confidence(0.5) // GH-90000
          .build(); // GH-90000

      when(dispatcher.<Object, Object>dispatch(any(), any(), any())) // GH-90000
          .thenReturn(Promise.of(result)); // GH-90000

      AgentEvalTask task = createTask("CONFIDENCE_MIN", "0.8"); // GH-90000

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx)); // GH-90000

      assertThat(report.isAllPassed()).isFalse(); // GH-90000
      assertThat(report.getResults().get(0).getFailures()) // GH-90000
          .anyMatch(f -> f.contains("CONFIDENCE_MIN failed"));
    }

    @Test
    @DisplayName("Should pass STATUS assertion when status matches")
    void shouldPassStatus() { // GH-90000
      AgentResult<Object> result = AgentResult.<Object>builder() // GH-90000
          .output("output")
          .status(AgentResultStatus.SUCCESS) // GH-90000
          .confidence(0.9) // GH-90000
          .build(); // GH-90000

      when(dispatcher.<Object, Object>dispatch(any(), any(), any())) // GH-90000
          .thenReturn(Promise.of(result)); // GH-90000

      AgentEvalTask task = createTask("STATUS", "SUCCESS"); // GH-90000

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx)); // GH-90000

      assertThat(report.isAllPassed()).isTrue(); // GH-90000
    }

    @Test
    @DisplayName("Should fail when agent returns FAILED status")
    void shouldFailOnAgentFailure() { // GH-90000
      AgentResult<Object> result = AgentResult.<Object>builder() // GH-90000
          .output("error")
          .status(AgentResultStatus.FAILED) // GH-90000
          .confidence(0.0) // GH-90000
          .explanation("Agent crashed")
          .build(); // GH-90000

      when(dispatcher.<Object, Object>dispatch(any(), any(), any())) // GH-90000
          .thenReturn(Promise.of(result)); // GH-90000

      AgentEvalTask task = AgentEvalTask.builder() // GH-90000
          .id("fail-test")
          .description("Agent failure test")
          .agentId("test-agent")
          .category("unit")
          .input("input")
          .maxLatencyMs(30000) // GH-90000
          .assertions(List.of()) // GH-90000
          .build(); // GH-90000

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx)); // GH-90000

      assertThat(report.isAllPassed()).isFalse(); // GH-90000
      assertThat(report.getResults().get(0).getFailures()) // GH-90000
          .anyMatch(f -> f.contains("FAILED status"));
    }
  }

  // ===== Filtering Tests =====

  @Nested
  @DisplayName("Filtering")
  class Filtering {

    @Test
    @DisplayName("Should filter tasks by category")
    void shouldFilterByCategory() { // GH-90000
      AgentResult<Object> result = AgentResult.<Object>builder() // GH-90000
          .output("ok")
          .status(AgentResultStatus.SUCCESS) // GH-90000
          .confidence(0.9) // GH-90000
          .build(); // GH-90000

      when(dispatcher.<Object, Object>dispatch(any(), any(), any())) // GH-90000
          .thenReturn(Promise.of(result)); // GH-90000

      AgentEvalTask unitTask = AgentEvalTask.builder() // GH-90000
          .id("unit-001").description("Unit test").agentId("agent-a")
          .category("unit").input("input").maxLatencyMs(30000)
          .assertions(List.of()).build(); // GH-90000

      AgentEvalTask integrationTask = AgentEvalTask.builder() // GH-90000
          .id("int-001").description("Integration test").agentId("agent-b")
          .category("integration").input("input").maxLatencyMs(30000)
          .assertions(List.of()).build(); // GH-90000

      AgentEvalReport report = runPromise( // GH-90000
          () -> runner.runByCategory(List.of(unitTask, integrationTask), "unit", ctx)); // GH-90000

      assertThat(report.getTotalTasks()).isEqualTo(1); // GH-90000
    }

    @Test
    @DisplayName("Should filter tasks by agent ID")
    void shouldFilterByAgentId() { // GH-90000
      AgentResult<Object> result = AgentResult.<Object>builder() // GH-90000
          .output("ok")
          .status(AgentResultStatus.SUCCESS) // GH-90000
          .confidence(0.9) // GH-90000
          .build(); // GH-90000

      when(dispatcher.<Object, Object>dispatch(any(), any(), any())) // GH-90000
          .thenReturn(Promise.of(result)); // GH-90000

      AgentEvalTask taskA = AgentEvalTask.builder() // GH-90000
          .id("a-001").description("Task A").agentId("agent-a")
          .category("unit").input("input").maxLatencyMs(30000)
          .assertions(List.of()).build(); // GH-90000

      AgentEvalTask taskB = AgentEvalTask.builder() // GH-90000
          .id("b-001").description("Task B").agentId("agent-b")
          .category("unit").input("input").maxLatencyMs(30000)
          .assertions(List.of()).build(); // GH-90000

      AgentEvalReport report = runPromise( // GH-90000
          () -> runner.runByAgent(List.of(taskA, taskB), "agent-b", ctx)); // GH-90000

      assertThat(report.getTotalTasks()).isEqualTo(1); // GH-90000
    }
  }

  // ===== Report Structure Tests =====

  @Nested
  @DisplayName("Report Structure")
  class ReportStructure {

    @Test
    @DisplayName("Should track duration in report")
    void shouldTrackDuration() { // GH-90000
      AgentResult<Object> result = AgentResult.<Object>builder() // GH-90000
          .output("ok")
          .status(AgentResultStatus.SUCCESS) // GH-90000
          .confidence(0.9) // GH-90000
          .build(); // GH-90000

      when(dispatcher.<Object, Object>dispatch(any(), any(), any())) // GH-90000
          .thenReturn(Promise.of(result)); // GH-90000

      AgentEvalTask task = AgentEvalTask.builder() // GH-90000
          .id("dur-001").description("Duration test").agentId("test-agent")
          .category("unit").input("input").maxLatencyMs(30000)
          .assertions(List.of()).build(); // GH-90000

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx)); // GH-90000

      assertThat(report.getTotalDuration()).isNotNull(); // GH-90000
      assertThat(report.getTimestamp()).isNotNull(); // GH-90000
      assertThat(report.getResults()).hasSize(1); // GH-90000
      assertThat(report.getResults().get(0).getDuration()).isNotNull(); // GH-90000
    }
  }

  // ===== Test Helpers =====

  private AgentEvalTask createTask(String assertionType, String expected) { // GH-90000
    return AgentEvalTask.builder() // GH-90000
        .id("task-" + assertionType) // GH-90000
        .description("Test " + assertionType) // GH-90000
        .agentId("test-agent")
        .category("unit")
        .input("test-input")
        .maxLatencyMs(30000) // GH-90000
        .assertions(List.of( // GH-90000
            EvalAssertion.builder() // GH-90000
                .type(assertionType) // GH-90000
                .expected(expected) // GH-90000
                .description("Check " + assertionType) // GH-90000
                .build())) // GH-90000
        .build(); // GH-90000
  }
}
