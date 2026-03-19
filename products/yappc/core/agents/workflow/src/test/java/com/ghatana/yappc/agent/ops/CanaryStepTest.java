package com.ghatana.yappc.agent.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

@DisplayName("Ops CanaryStep Tests")
/**
 * @doc.type class
 * @doc.purpose Handles canary step test operations
 * @doc.layer core
 * @doc.pattern Test
 */
class CanaryStepTest extends EventloopTestBase {

  private DatabaseClient dbClient;
  private EventCloud eventClient;
  private CanaryStep step;

  @BeforeEach
  void setUp() {
    dbClient = mock(DatabaseClient.class);
    eventClient = mock(EventCloud.class);
    step = new CanaryStep(dbClient, eventClient);
  }

  @Test
  @DisplayName("Should perform canary deployment with traffic shifting")
  void shouldPerformCanaryDeployment() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("tenantId", "tenant-abc");
    context.put("deploymentId", "deploy-001");
    context.put("validated", true);

    Map<String, Object> mockDeployment =
        Map.of(
            "deploymentId",
            "deploy-001",
            "environment",
            "STAGING",
            "validated",
            true,
            "version",
            "v1.2.0",
            "imageTag",
            "v1.2.0");

    when(dbClient.query(anyString(), any(), anyInt()))
        .thenReturn(Promise.of(List.of(mockDeployment)));
    when(dbClient.insert(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), any())).thenReturn(Promise.of((Void) null));
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN
    WorkflowContext result = runPromise(() -> step.execute(context));

    // THEN
    assertThat(result).isNotNull();
    assertThat(result.get("status")).isEqualTo("COMPLETED");
    assertThat(result.get("canaryId")).isNotNull();
  }

  @Test
  @DisplayName("Should fail when deployment is not validated")
  void shouldFailWhenDeploymentNotValidated() {
    // GIVEN
    WorkflowContext context = WorkflowContext.forWorkflow("workflow-123", "tenant-abc");
    context.put("tenantId", "tenant-abc");
    context.put("validated", false);
    when(eventClient.publish(anyString(), anyString(), any())).thenReturn(Promise.of((Void) null));

    // WHEN/THEN
    assertThatThrownBy(() -> runPromise(() -> step.execute(context)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not validated");
  }
}
