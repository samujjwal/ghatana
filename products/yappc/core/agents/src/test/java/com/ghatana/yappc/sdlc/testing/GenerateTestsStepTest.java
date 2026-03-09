package com.ghatana.yappc.sdlc.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.workflow.WorkflowContext;
import io.activej.promise.Promise;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Testing GenerateTestsStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles generate tests step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class GenerateTestsStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private GenerateTestsStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new GenerateTestsStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should generate test cases from test plan")
  void shouldGenerateTestCases() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("testPlanId", "plan-001");
    context.put("tenantId", "tenant-abc");

    Map<String, Object> mockTestPlan =
        Map.of(
            "testPlanId", "plan-001",
            "requirementsBaselineId", "req-001",
            "implementationBaselineId", "impl-001");

    Map<String, Object> mockReqBaseline =
        Map.of(
            "baselineId",
            "req-001",
            "content",
            Map.of(
                "requirements",
                List.of(
                    Map.of("id", "req1", "category", "FUNCTIONAL", "description", "User login"))));

    when(dbClient.query(eq("test_plans"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(mockTestPlan)));
    when(dbClient.query(eq("requirements_published"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(mockReqBaseline)));
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("status")).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("Should fail when testPlanId is missing")
  void shouldFailWhenTestPlanIdMissing() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("tenantId", "tenant-abc");
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("testPlan");
  }
}
