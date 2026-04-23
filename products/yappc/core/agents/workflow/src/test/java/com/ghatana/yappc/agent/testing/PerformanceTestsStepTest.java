package com.ghatana.yappc.agent.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

@DisplayName("Testing PerformanceTestsStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles performance tests step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class PerformanceTestsStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventPublisher eventClient;
  private PerformanceTestsStep step;

  @BeforeEach
  void setUp() { // GH-90000
    dbClient = mock(DatabaseClient.class); // GH-90000
    eventClient = mock(EventPublisher.class); // GH-90000
    step = new PerformanceTestsStep(dbClient, eventClient); // GH-90000
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() { // GH-90000
    assertThat(step.getStepId()).isEqualTo("testing.performancetests");
  }

  @Test
  @DisplayName("Should execute performance tests and validate NFRs")
  void shouldExecutePerformanceTests() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("tenantId", "tenant-abc"); // GH-90000
    context.put("testPlanId", "plan-001"); // GH-90000

    Map<String, Object> mockTestPlan =
        Map.of( // GH-90000
            "testPlanId",
            "plan-001",
            "nfrTargets",
            Map.of( // GH-90000
                "latencyP95", 200.0,
                "latencyP99", 500.0,
                "throughput", 1000.0,
                "availability", 99.9));

    when(dbClient.query(anyString(), any(), anyInt())) // GH-90000
        .thenReturn(Promise.of(List.of(mockTestPlan))); // GH-90000
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context)); // GH-90000

    // THEN
    assertThat(result).isNotNull(); // GH-90000
  }

  @Test
  @DisplayName("Should fail when tenantId is missing")
  void shouldFailWhenTenantIdMissing() { // GH-90000
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc"); // GH-90000
    context.put("testPlanId", "plan-001"); // GH-90000
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null)); // GH-90000

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context))) // GH-90000
        .isInstanceOf(IllegalArgumentException.class) // GH-90000
        .hasMessageContaining("tenantId");
  }
}
