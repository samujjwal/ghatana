package com.ghatana.yappc.agent.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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

@DisplayName("Ops MonitorStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles monitor step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class MonitorStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private MonitorStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new MonitorStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should return correct step ID")
  void shouldReturnCorrectStepId() {
    assertThat(step.getStepId()).isEqualTo("ops.monitor");
  }

  @Test
  @DisplayName("Should monitor production deployment")
  void shouldMonitorDeployment() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("tenantId", "tenant-abc");
    context.put("deploymentId", "deploy-001");
    context.put("canaryId", "canary-001");

    Map<String, Object> mockCanary =
        Map.of(
            "canaryId", "canary-001",
            "deploymentId", "deploy-001",
            "environment", "PRODUCTION",
            "status", "SUCCESS");

    when(dbClient.query(anyString(), any(), anyInt())).thenReturn(Promise.of(List.of(mockCanary)));
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
  }
}
