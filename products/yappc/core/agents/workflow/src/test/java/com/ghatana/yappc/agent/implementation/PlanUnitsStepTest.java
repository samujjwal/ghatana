package com.ghatana.yappc.agent.implementation;

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

@DisplayName("Implementation PlanUnitsStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles plan units step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class PlanUnitsStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private PlanUnitsStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new PlanUnitsStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("implementation.plan_units");
  }

  @Test
  @DisplayName("Should plan implementation units from architecture baseline")
  void shouldPlanImplementationUnits() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("architectureBaselineId", "arch-baseline-001");
    context.put("tenantId", "tenant-abc"); // Required by step validation

    Map<String, Object> baseline =
        Map.of(
            "baselineId",
            "arch-baseline-001",
            "tenantId",
            "tenant-abc",
            "content",
            Map.of(
                "c4ContainerView", "Container1, Container2",
                "c4ComponentView", "Component1, Component2"),
            "components",
            List.of());
    when(dbClient.query(eq("architecture_published"), any(), anyInt()))
        .thenReturn(Promise.of(List.of(baseline)));
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("architectureBaselineId")).isEqualTo("arch-baseline-001");
  }
}
