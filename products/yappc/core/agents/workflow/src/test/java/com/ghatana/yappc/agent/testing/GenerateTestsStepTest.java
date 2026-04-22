package com.ghatana.yappc.agent.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.yappc.agent.EventPublisher;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowContext;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Testing GenerateTestsStep Tests [GH-90000]")
/**
 * @doc.type class
 * @doc.purpose Handles generate tests step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class GenerateTestsStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private GenerateTestsStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new GenerateTestsStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should generate test cases from test plan [GH-90000]")
  void shouldGenerateTestCases() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("testPlanId", "plan-001"); // GH-90000
    context.put("tenantId", "tenant-abc"); // GH-90000

    Map<String, Object> mockTestPlan =
        Map.of( // GH-90000
            "testPlanId", "plan-001",
            "requirementsBaselineId", "req-001",
            "implementationBaselineId", "impl-001");

    Map<String, Object> mockReqBaseline =
        Map.of( // GH-90000
            "baselineId",
            "req-001",
            "content",
            Map.of( // GH-90000
                "requirements",
                List.of( // GH-90000
                    Map.of("id", "req1", "category", "FUNCTIONAL", "description", "User login")))); // GH-90000

    when(dbClient.query(eq("test_plans [GH-90000]"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(mockTestPlan))); // GH-90000
    when(dbClient.query(eq("requirements_published [GH-90000]"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(mockReqBaseline))); // GH-90000
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.get("status [GH-90000]")).isEqualTo("COMPLETED [GH-90000]");
  }

  @Test
  @DisplayName("Should fail when testPlanId is missing [GH-90000]")
  void shouldFailWhenTestPlanIdMissing() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("tenantId", "tenant-abc"); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context))) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("testPlan [GH-90000]");
  }
}
