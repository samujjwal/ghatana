package com.ghatana.yappc.sdlc.testing;

import static org.assertj.core.api.Assertions.assertThat;
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

@DisplayName("Testing DeriveTestPlanStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles derive test plan step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class DeriveTestPlanStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private DeriveTestPlanStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new DeriveTestPlanStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("testing.derive_test_plan");
  }

  @Test
  @DisplayName("Should derive test plan from implementation")
  void shouldDeriveTestPlan() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("requirementsBaselineId", "req-001");
    context.put("implementationBaselineId", "impl-001");
    context.put("tenantId", "tenant-abc");
    context.put(
        "units", List.of(Map.of("unitId", "u1", "name", "AuthService", "tests", List.of())));

    // Mock database queries to return baselines
    Map<String, Object> reqBaseline =
        Map.of(
            "baselineId",
            "req-001",
            "content",
            Map.of(
                "requirements",
                List.of(
                    Map.of("id", "req1", "category", "FUNCTIONAL", "description", "User login"),
                    Map.of(
                        "id",
                        "req2",
                        "category",
                        "NON_FUNCTIONAL",
                        "description",
                        "Response time < 1s"))));
    Map<String, Object> implBaseline =
        Map.of(
            "baselineId",
            "impl-001",
            "units",
            List.of(Map.of("unitId", "u1", "name", "AuthService")));

    when(dbClient.query(eq("requirements_published"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(reqBaseline)));
    when(dbClient.query(eq("implementation_published"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(implBaseline)));
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("implementationBaselineId")).isEqualTo("impl-001");
  }
}
