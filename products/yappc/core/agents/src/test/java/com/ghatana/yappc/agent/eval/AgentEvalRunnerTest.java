package com.ghatana.yappc.agent.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
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

  private AgentDispatcher dispatcher;
  private AgentEvalRunner runner;
  private AgentContext ctx;

  @BeforeEach
  void setUp() {
    dispatcher = mock(AgentDispatcher.class);
    runner = new AgentEvalRunner(dispatcher);
    ctx = AgentContext.builder()
        .agentId("eval-runner")
        .turnId("eval-turn-001")
        .tenantId("tenant-1")
        .sessionId("eval-session")
        .memoryStore(MemoryStore.noOp())
        .build();
  }

  // ===== Constructor Tests =====

  @Nested
  @DisplayName("Constructor")
  class Constructor {

    @Test
    @DisplayName("Should reject null dispatcher")
    void shouldRejectNullDispatcher() {
      assertThatThrownBy(() -> new AgentEvalRunner(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessageContaining("dispatcher");
    }
  }

  // ===== RunAll Tests =====

  @Nested
  @DisplayName("Run All Tasks")
  class RunAll {

    @Test
    @DisplayName("Should produce report with all tasks passing")
    void shouldProduceAllPassingReport() {
      AgentResult<Object> successResult = AgentResult.<Object>builder()
          .output("expected-output")
          .status(AgentResultStatus.SUCCESS)
          .confidence(0.95)
          .explanation("All good")
          .build();

      when(dispatcher.<Object, Object>dispatch(eq("test-agent"), any(), any()))
          .thenReturn(Promise.of(successResult));

      AgentEvalTask task = AgentEvalTask.builder()
          .id("eval-001")
          .description("Test task")
          .agentId("test-agent")
          .category("unit")
          .input("test-input")
          .maxLatencyMs(30000)
          .assertions(List.of(
              EvalAssertion.builder()
                  .type("CONTAINS")
                  .expected("expected")
                  .description("Should contain expected")
                  .build()))
          .build();

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx));

      assertThat(report.getTotalTasks()).isEqualTo(1);
      assertThat(report.getPassed()).isEqualTo(1);
      assertThat(report.getFailed()).isEqualTo(0);
      assertThat(report.isAllPassed()).isTrue();
      assertThat(report.getRunId()).isNotEmpty();
    }

    @Test
    @DisplayName("Should produce report with failing assertion")
    void shouldProduceFailingReport() {
      AgentResult<Object> result = AgentResult.<Object>builder()
          .output("actual-output")
          .status(AgentResultStatus.SUCCESS)
          .confidence(0.8)
          .explanation("Done")
          .build();

      when(dispatcher.<Object, Object>dispatch(eq("test-agent"), any(), any()))
          .thenReturn(Promise.of(result));

      AgentEvalTask task = AgentEvalTask.builder()
          .id("eval-002")
          .description("Failing task")
          .agentId("test-agent")
          .category("unit")
          .input("test-input")
          .maxLatencyMs(30000)
          .assertions(List.of(
              EvalAssertion.builder()
                  .type("EXACT_MATCH")
                  .expected("something-else")
                  .description("Should match exactly")
                  .build()))
          .build();

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx));

      assertThat(report.getPassed()).isEqualTo(0);
      assertThat(report.getFailed()).isEqualTo(1);
      assertThat(report.isAllPassed()).isFalse();
      assertThat(report.getResults().get(0).getFailures())
          .anyMatch(f -> f.contains("EXACT_MATCH failed"));
    }

    @Test
    @DisplayName("Should handle empty task list")
    void shouldHandleEmptyTaskList() {
      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(), ctx));

      assertThat(report.getTotalTasks()).isEqualTo(0);
      assertThat(report.getPassed()).isEqualTo(0);
      assertThat(report.getFailed()).isEqualTo(0);
      assertThat(report.isAllPassed()).isTrue();
    }
  }

  // ===== Assertion Type Tests =====

  @Nested
  @DisplayName("Assertion Types")
  class AssertionTypes {

    @Test
    @DisplayName("Should pass CONTAINS assertion when output contains expected")
    void shouldPassContains() {
      AgentResult<Object> result = AgentResult.<Object>builder()
          .output("Hello World from agent")
          .status(AgentResultStatus.SUCCESS)
          .confidence(0.9)
          .build();

      when(dispatcher.<Object, Object>dispatch(any(), any(), any()))
          .thenReturn(Promise.of(result));

      AgentEvalTask task = createTask("CONTAINS", "World");

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx));

      assertThat(report.isAllPassed()).isTrue();
    }

    @Test
    @DisplayName("Should pass REGEX assertion when output matches pattern")
    void shouldPassRegex() {
      AgentResult<Object> result = AgentResult.<Object>builder()
          .output("Error code: 42")
          .status(AgentResultStatus.SUCCESS)
          .confidence(0.9)
          .build();

      when(dispatcher.<Object, Object>dispatch(any(), any(), any()))
          .thenReturn(Promise.of(result));

      AgentEvalTask task = createTask("REGEX", ".*code: \\d+.*");

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx));

      assertThat(report.isAllPassed()).isTrue();
    }

    @Test
    @DisplayName("Should pass CONFIDENCE_MIN assertion when confidence is sufficient")
    void shouldPassConfidenceMin() {
      AgentResult<Object> result = AgentResult.<Object>builder()
          .output("output")
          .status(AgentResultStatus.SUCCESS)
          .confidence(0.85)
          .build();

      when(dispatcher.<Object, Object>dispatch(any(), any(), any()))
          .thenReturn(Promise.of(result));

      AgentEvalTask task = createTask("CONFIDENCE_MIN", "0.8");

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx));

      assertThat(report.isAllPassed()).isTrue();
    }

    @Test
    @DisplayName("Should fail CONFIDENCE_MIN when confidence is below threshold")
    void shouldFailConfidenceMin() {
      AgentResult<Object> result = AgentResult.<Object>builder()
          .output("output")
          .status(AgentResultStatus.SUCCESS)
          .confidence(0.5)
          .build();

      when(dispatcher.<Object, Object>dispatch(any(), any(), any()))
          .thenReturn(Promise.of(result));

      AgentEvalTask task = createTask("CONFIDENCE_MIN", "0.8");

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx));

      assertThat(report.isAllPassed()).isFalse();
      assertThat(report.getResults().get(0).getFailures())
          .anyMatch(f -> f.contains("CONFIDENCE_MIN failed"));
    }

    @Test
    @DisplayName("Should pass STATUS assertion when status matches")
    void shouldPassStatus() {
      AgentResult<Object> result = AgentResult.<Object>builder()
          .output("output")
          .status(AgentResultStatus.SUCCESS)
          .confidence(0.9)
          .build();

      when(dispatcher.<Object, Object>dispatch(any(), any(), any()))
          .thenReturn(Promise.of(result));

      AgentEvalTask task = createTask("STATUS", "SUCCESS");

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx));

      assertThat(report.isAllPassed()).isTrue();
    }

    @Test
    @DisplayName("Should fail when agent returns FAILED status")
    void shouldFailOnAgentFailure() {
      AgentResult<Object> result = AgentResult.<Object>builder()
          .output("error")
          .status(AgentResultStatus.FAILED)
          .confidence(0.0)
          .explanation("Agent crashed")
          .build();

      when(dispatcher.<Object, Object>dispatch(any(), any(), any()))
          .thenReturn(Promise.of(result));

      AgentEvalTask task = AgentEvalTask.builder()
          .id("fail-test")
          .description("Agent failure test")
          .agentId("test-agent")
          .category("unit")
          .input("input")
          .maxLatencyMs(30000)
          .assertions(List.of())
          .build();

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx));

      assertThat(report.isAllPassed()).isFalse();
      assertThat(report.getResults().get(0).getFailures())
          .anyMatch(f -> f.contains("FAILED status"));
    }
  }

  // ===== Filtering Tests =====

  @Nested
  @DisplayName("Filtering")
  class Filtering {

    @Test
    @DisplayName("Should filter tasks by category")
    void shouldFilterByCategory() {
      AgentResult<Object> result = AgentResult.<Object>builder()
          .output("ok")
          .status(AgentResultStatus.SUCCESS)
          .confidence(0.9)
          .build();

      when(dispatcher.<Object, Object>dispatch(any(), any(), any()))
          .thenReturn(Promise.of(result));

      AgentEvalTask unitTask = AgentEvalTask.builder()
          .id("unit-001").description("Unit test").agentId("agent-a")
          .category("unit").input("input").maxLatencyMs(30000)
          .assertions(List.of()).build();

      AgentEvalTask integrationTask = AgentEvalTask.builder()
          .id("int-001").description("Integration test").agentId("agent-b")
          .category("integration").input("input").maxLatencyMs(30000)
          .assertions(List.of()).build();

      AgentEvalReport report = runPromise(
          () -> runner.runByCategory(List.of(unitTask, integrationTask), "unit", ctx));

      assertThat(report.getTotalTasks()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should filter tasks by agent ID")
    void shouldFilterByAgentId() {
      AgentResult<Object> result = AgentResult.<Object>builder()
          .output("ok")
          .status(AgentResultStatus.SUCCESS)
          .confidence(0.9)
          .build();

      when(dispatcher.<Object, Object>dispatch(any(), any(), any()))
          .thenReturn(Promise.of(result));

      AgentEvalTask taskA = AgentEvalTask.builder()
          .id("a-001").description("Task A").agentId("agent-a")
          .category("unit").input("input").maxLatencyMs(30000)
          .assertions(List.of()).build();

      AgentEvalTask taskB = AgentEvalTask.builder()
          .id("b-001").description("Task B").agentId("agent-b")
          .category("unit").input("input").maxLatencyMs(30000)
          .assertions(List.of()).build();

      AgentEvalReport report = runPromise(
          () -> runner.runByAgent(List.of(taskA, taskB), "agent-b", ctx));

      assertThat(report.getTotalTasks()).isEqualTo(1);
    }
  }

  // ===== Report Structure Tests =====

  @Nested
  @DisplayName("Report Structure")
  class ReportStructure {

    @Test
    @DisplayName("Should track duration in report")
    void shouldTrackDuration() {
      AgentResult<Object> result = AgentResult.<Object>builder()
          .output("ok")
          .status(AgentResultStatus.SUCCESS)
          .confidence(0.9)
          .build();

      when(dispatcher.<Object, Object>dispatch(any(), any(), any()))
          .thenReturn(Promise.of(result));

      AgentEvalTask task = AgentEvalTask.builder()
          .id("dur-001").description("Duration test").agentId("test-agent")
          .category("unit").input("input").maxLatencyMs(30000)
          .assertions(List.of()).build();

      AgentEvalReport report = runPromise(() -> runner.runAll(List.of(task), ctx));

      assertThat(report.getTotalDuration()).isNotNull();
      assertThat(report.getTimestamp()).isNotNull();
      assertThat(report.getResults()).hasSize(1);
      assertThat(report.getResults().get(0).getDuration()).isNotNull();
    }
  }

  // ===== Test Helpers =====

  private AgentEvalTask createTask(String assertionType, String expected) {
    return AgentEvalTask.builder()
        .id("task-" + assertionType)
        .description("Test " + assertionType)
        .agentId("test-agent")
        .category("unit")
        .input("test-input")
        .maxLatencyMs(30000)
        .assertions(List.of(
            EvalAssertion.builder()
                .type(assertionType)
                .expected(expected)
                .description("Check " + assertionType)
                .build()))
        .build();
  }
}
