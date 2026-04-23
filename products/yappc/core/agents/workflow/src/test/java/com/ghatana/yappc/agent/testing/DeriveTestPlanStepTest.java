package com.ghatana.yappc.agent.testing;

import static org.assertj.core.api.Assertions.assertThat;
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

@DisplayName("Testing DeriveTestPlanStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles derive test plan step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class DeriveTestPlanStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private DeriveTestPlanStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new DeriveTestPlanStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() { // GH-90000
    assertThat(step.getStepId()).isEqualTo("testing.derive_test_plan");
  }

  @Test
  @DisplayName("Should derive test plan from implementation")
  void shouldDeriveTestPlan() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("requirementsBaselineId", "req-001"); // GH-90000
    context.put("implementationBaselineId", "impl-001"); // GH-90000
    context.put("tenantId", "tenant-abc"); // GH-90000
    context.put( // GH-90000
        "units", List.of(Map.of("unitId", "u1", "name", "AuthService", "tests", List.of()))); // GH-90000

    // Mock database queries to return baselines
    Map<String, Object> reqBaseline =
        Map.of( // GH-90000
            "baselineId",
            "req-001",
            "content",
            Map.of( // GH-90000
                "requirements",
                List.of( // GH-90000
                    Map.of("id", "req1", "category", "FUNCTIONAL", "description", "User login"), // GH-90000
                    Map.of( // GH-90000
                        "id",
                        "req2",
                        "category",
                        "NON_FUNCTIONAL",
                        "description",
                        "Response time < 1s"))));
    Map<String, Object> implBaseline =
        Map.of( // GH-90000
            "baselineId",
            "impl-001",
            "units",
            List.of(Map.of("unitId", "u1", "name", "AuthService"))); // GH-90000

    when(dbClient.query(eq("requirements_published"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(reqBaseline))); // GH-90000
    when(dbClient.query(eq("implementation_published"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(implBaseline))); // GH-90000
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
    assertThat(result.get("implementationBaselineId")).isEqualTo("impl-001");
  }
}
